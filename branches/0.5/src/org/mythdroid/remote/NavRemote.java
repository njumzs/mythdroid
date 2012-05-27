/*
    MythDroid: Android MythTV Remote
    Copyright (C) 2009-2010 foobum@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.mythdroid.remote;

import java.io.IOException;

import org.mythdroid.Enums.Extras;
import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.Enums.Key;
import org.mythdroid.frontend.FrontendLocation;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.mdd.MDDMenuListener;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;


import android.R.drawable;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

/** Remote for menu / guide navigation */
public class NavRemote extends Remote {

    final private static int MENU_GESTURE = 0, MENU_BUTTON = 1, MENU_MENU = 2;

    final private Handler    handler = new Handler();

    private FrontendLocation lastLoc = null;
    private MDDManager       mddMgr  = null;
    private TextView         locView = null, itemView = null;
    private String           locS = null,    itemS = null;

    private boolean
        gesture = false, jumpGuide = false, calledByRemote = false,
        calledByTVRemote = false, calledByMusicRemote = false;
    
    // Update the location displays, posted from updateLoc() with a delay 
    private Runnable updateLocViews = new Runnable() {
        @Override
        public void run() {
            if (locS != null && locView != null)
                locView.setText(locS);
            if (itemS != null && itemView != null)
                itemView.setText(itemS);
        }
    };

    private class mddListener implements MDDMenuListener {
        @Override
        public void onMenuItem(final String menu, final String item) {
           handler.post(
               new Runnable() {
                   @Override
                   public void run() {
                       
                       if (locView == null || itemView == null) return;
                       
                       // Cancel any pending updates from updateLoc()
                       handler.removeCallbacks(updateLocViews);
                       
                       if (menu != null && menu.length() > 0) {
                           locView.setText(menu);
                           locS = menu;
                       }
                       itemView.setText(item);
                       itemS = item;
                       
                   }
               }
           );
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        gesture = PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("tvDefaultStyle", "") //$NON-NLS-1$ //$NON-NLS-2$
                        .equals(Messages.getString("NavRemote.0")); // Gesture //$NON-NLS-1$

        setupViews(gesture);
        listenToGestures(gesture);

        if (getIntent().hasExtra(Extras.GUIDE.toString()))
            jumpGuide = true;

        String calledBy = null;

        ComponentName caller = getCallingActivity();
        if (caller != null)
            calledBy = caller.getShortClassName();

        if (calledBy != null) {
            if(calledBy.endsWith(".TVRemote")) //$NON-NLS-1$
                calledByTVRemote = true;
            else if (calledBy.endsWith(".MusicRemote")) //$NON-NLS-1$
                calledByMusicRemote = true;
        }

        calledByRemote = calledByTVRemote || calledByMusicRemote;

    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            feMgr = Globals.getFrontend(this);
        } catch (IOException e) {
            ErrUtil.err(this, e);
            finish();
            return;
        }

        // Preserve the menu item string through updateLoc(), if we have one
        String itemSTmp = itemS;
        
        try {
            if (jumpGuide)
                feMgr.jumpTo("guidegrid"); //$NON-NLS-1$
            updateLoc();
        } catch (IOException e) { ErrUtil.err(this, e); }

        if (!calledByRemote) {
            try {
                mddMgr = new MDDManager(feMgr.addr);
            } catch (IOException e) { mddMgr = null; }

            if (mddMgr != null) {
                mddMgr.setMenuListener(new mddListener());
            }
        }
        
        // Restore the menu item string that got wiped by updateLoc()
        itemS = itemSTmp;
        updateLocViews.run();
        
    }

    private void cleanup() {
        if (feMgr != null && !calledByRemote)
            feMgr.disconnect();
        feMgr = null;
        if (mddMgr != null)
            mddMgr.shutdown();
        mddMgr = null;
    }

    @Override
    public void onPause() {
        super.onPause();
        cleanup();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanup();
    }


    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        setupViews(gesture);
        try {
            if (feMgr == null)
                onResume();
            updateLoc();
        } catch (IOException e) { 
            ErrUtil.err(this, e);
            finish();
        }
    }

    @Override
    public void onClick(View v) {

        if (feMgr != null)
            try {
                switch (v.getId()) {
                    case R.id.back:
                        feMgr.sendKey(Key.ESCAPE);
                        break;
                    case R.id.enter:
                        feMgr.sendKey(Key.ENTER);
                        break;
                    case R.id.up:
                        feMgr.sendKey(Key.UP);
                        break;
                    case R.id.down:
                        feMgr.sendKey(Key.DOWN);
                        break;
                    case R.id.left:
                        feMgr.sendKey(Key.LEFT);
                        break;
                    case R.id.right:
                        feMgr.sendKey(Key.RIGHT);
                        break;
                }
            } catch (IOException e) { ErrUtil.err(this, e); }
              catch (IllegalArgumentException e) { ErrUtil.err(this, e); }

        super.onClick(v);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_MENU, Menu.NONE, R.string.menu)
            .setIcon(drawable.ic_menu_more);
        menu.add(Menu.NONE, MENU_BUTTON, Menu.NONE, R.string.btnIface)
            .setIcon(android.R.drawable.ic_menu_add);
        menu.add(Menu.NONE, MENU_GESTURE, Menu.NONE, R.string.gestIface)
            .setIcon(R.drawable.ic_menu_finger);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (gesture) {
            menu.findItem(MENU_BUTTON).setVisible(true);
            menu.findItem(MENU_GESTURE).setVisible(false);
        }
        else {
            menu.findItem(MENU_BUTTON).setVisible(false);
            menu.findItem(MENU_GESTURE).setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case MENU_MENU:
                try {
                    if (feMgr != null) feMgr.sendKey(Key.MENU);
                } catch (IOException e) { ErrUtil.err(this, e); }
                return true;
            case MENU_GESTURE:
                gesture = true;
                break;
            case MENU_BUTTON:
                gesture = false;
                break;
        }

        setupViews(gesture);
        listenToGestures(gesture);
        return true;

    }

    @Override
    public boolean onKeyDown(int code, KeyEvent event) {

        if (
            feMgr != null && code == KeyEvent.KEYCODE_BACK && calledByTVRemote
        ) {
            try {
                feMgr.sendKey(Key.ESCAPE);
            } catch (IOException e) {
                ErrUtil.err(this, e);
            }
            return true;
        }

        return super.onKeyDown(code, event);

    }

    @Override
    public void onActivityResult(int reqCode, int resCode, Intent data) {
        if (resCode == REMOTE_RESULT_FINISH)
            finish();
    }

    @Override
    protected void onAction() {
        try {
            updateLoc();
        } catch (IOException e) { ErrUtil.err(this, e); }
    }

    /**
     * Setup the interactive views
     * @param gesture true for 'gesture' layout, false for 'button'
     */
    private void setupViews(boolean gesture) {
        setContentView(
            gesture ? R.layout.nav_gesture_remote : R.layout.nav_remote
        );

        locView = (TextView)findViewById(R.id.loc);
        itemView = (TextView)findViewById(R.id.item);
        
        if (locS != null)
            locView.setText(locS);
        if (itemS != null)
            itemView.setText(itemS);

        if (feMgr != null)
            try {
                updateLoc();
            } catch (IOException e) { ErrUtil.err(this, e); }

        if (gesture) {
            findViewById(R.id.back).setOnClickListener(this);
            return;
        }

        final View view = findViewById(R.id.nav_remote);

        for (final View v : view.getTouchables()) {
            v.setOnClickListener(this);
            v.setFocusable(false);
        }

    }

    /**
     * Update the frontend location display, start another remote if appropriate
     */
    private void updateLoc() throws IOException {

        if (
            calledByMusicRemote || feMgr == null    ||
            locView == null     || itemView == null
        ) return;

        final FrontendLocation newLoc = feMgr.getLoc();
        locS = newLoc.niceLocation;
        itemS = ""; //$NON-NLS-1$
        if (mddMgr == null) {
            locView.setText(locS);
            itemView.setText(itemS);
        }
        else if (locS != null && !locS.equals("Unknown")) { //$NON-NLS-1$
            /*
             * The location displays will be updated by MDD at some point but
             * if it happens to be >= a few ms after we set it from the 
             * FrontendLocation we get an ugly 'flicker'. So we delay the
             * update from FrontendLocation by 50ms and if an update from
             * MDD arrives in the meantime, cancel the FrontendLocation based
             * update.
             */
            handler.postDelayed(updateLocViews, 50);
        }

        if (newLoc.video) {
            if (lastLoc != null)
                Globals.lastLocation = lastLoc;
            if (calledByRemote)
                finish();
            else {
                final Intent intent =
                    new Intent().setClass(this, TVRemote.class)
                                .putExtra(Extras.DONTJUMP.toString(), true);

                if (newLoc.livetv)
                    intent.putExtra(Extras.LIVETV.toString(), true);
                startActivityForResult(intent, 0);
            }
        }

        else if (newLoc.music) {
            if (lastLoc != null)
                Globals.lastLocation = lastLoc;
            startActivity(
                new Intent()
                    .setClass(this, MusicRemote.class)
                    .putExtra(Extras.DONTJUMP.toString(), true)
            );
        }

        else
            lastLoc = newLoc;

    }
    
}
