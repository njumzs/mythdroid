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

package org.mythdroid;

import java.io.IOException;

import android.R.drawable;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class NavRemote extends Remote implements View.OnClickListener {

    final private static int MENU_GESTURE = 0, MENU_BUTTON = 1;

    final private Handler    handler = new Handler();
    
    private FrontendLocation lastLoc = null;
    private MDDManager       mddMgr  = null;
    private TextView         locView = null, itemView = null;
    
    private boolean 
        gesture = false, jumpGuide = false, calledByRemote = false,
        calledByTVRemote = false, calledByMusicRemote = false;
    
    private class mddListener implements MenuListener {
        @Override
        public void onMenuItem(final String menu, final String item) {
           handler.post(
               new Runnable() {
                   @Override
                   public void run() {
                       if (locView == null || itemView == null) return;
                       locView.setText(menu);
                       itemView.setText(item);
                   }
               }
           );
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setupViews(gesture);

        if (getIntent().hasExtra(MythDroid.GUIDE)) 
            jumpGuide = true;

        String calledBy = null;
        
        ComponentName caller = getCallingActivity();
        if (caller != null) 
            calledBy = caller.getShortClassName();
        
        if (calledBy != null) { 
            if(calledBy.equals(".TVRemote"))
                calledByTVRemote = true;
            else if (calledBy.equals(".MusicRemote"))
                calledByMusicRemote = true;
        }
        
        calledByRemote = calledByTVRemote || calledByMusicRemote;

    }

    @Override
    public void onResume() {
        super.onResume();
        if((feMgr = MythDroid.connectFrontend(this)) == null) {
            finish();
            return;
        }

        try {
            if (jumpGuide) 
                feMgr.jumpTo("guidegrid");
            updateLoc();
        } catch (IOException e) { Util.err(this, e); }
        
        if (!calledByRemote) {
            try {
                mddMgr = new MDDManager(MythDroid.beMgr.getAddress());
            } catch (IOException e) { mddMgr = null; }
            
            if (mddMgr != null) {
                mddMgr.setMenuListener(new mddListener());
            }
        }
    }
    
    private void cleanup() {
        try {
            if (feMgr != null && !calledByRemote)  
                feMgr.disconnect();
            feMgr = null;
            if (mddMgr != null)
                mddMgr.shutdown();
            mddMgr = null;
        } catch (IOException e) { Util.err(this, e); }
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
            updateLoc();
        } catch (IOException e) { Util.err(this, e); }
    }

    @Override
    public void onClick(View v) {
        
        try {
            switch (v.getId()) {
                case R.id.nav_back:
                    feMgr.sendKey(Key.ESCAPE);
                    break;
                case R.id.nav_enter:
                    feMgr.sendKey(Key.ENTER);
                    break;
                case R.id.nav_up:
                    feMgr.sendKey(Key.UP);
                    break;
                case R.id.nav_down:
                    feMgr.sendKey(Key.DOWN);
                    break;
                case R.id.nav_left:
                    feMgr.sendKey(Key.LEFT);
                    break;
                case R.id.nav_right:
                    feMgr.sendKey(Key.RIGHT);
                    break;
            }
        } catch (IOException e) { Util.err(this, e); }

        super.onClick(v);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_BUTTON, Menu.NONE, "Button Interface")
            .setIcon(drawable.ic_menu_add);
        menu.add(Menu.NONE, MENU_GESTURE, Menu.NONE, "Gesture Interface")
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
        
        if (code == KeyEvent.KEYCODE_BACK && calledByTVRemote) {
            try {
                feMgr.sendKey(Key.ESCAPE);
            } catch (IOException e) { 
                Util.err(this, e);
            }
            return true;
        }

        super.onKeyDown(code, event);
        return false;
        
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
        } catch (IOException e) { Util.err(this, e); }
    }
    
    private void setupViews(boolean gesture) {
        setContentView(
            gesture ? R.layout.nav_gesture_remote : R.layout.nav_remote
        );

        locView = (TextView)findViewById(R.id.nav_loc);
        itemView = (TextView)findViewById(R.id.nav_item);

        if (feMgr != null) 
            try {
                updateLoc();
            } catch (IOException e) { Util.err(this, e); }

        if (gesture) {
            findViewById(R.id.nav_back).setOnClickListener(this);
            return;
        }

        final View view = findViewById(R.id.nav_remote);

        for (final View v : view.getTouchables()) {
            v.setOnClickListener(this);
            v.setFocusable(false);
        }

    }

    private void updateLoc() throws IOException {
        
        if (calledByMusicRemote) return;
        
        if (locView == null || itemView == null) return;

        final FrontendLocation newLoc = feMgr.getLoc();
        locView.setText(newLoc.niceLocation);
        itemView.setText("");

        if (newLoc.video) {
            if (lastLoc != null) 
                MythDroid.lastLocation = lastLoc;
            if (calledByRemote)
                finish();
            else {
                final Intent intent = 
                    new Intent().setClass(this, TVRemote.class)
                                .putExtra(MythDroid.DONTJUMP, true);
                
                if (newLoc.livetv) 
                    intent.putExtra(MythDroid.LIVETV, true);
                startActivityForResult(intent, 0);
            }
        }
        
        else if (newLoc.music) {
            if (lastLoc != null)
                MythDroid.lastLocation = lastLoc;
            startActivity(
                new Intent()
                    .setClass(this, MusicRemote.class)
                    .putExtra(MythDroid.DONTJUMP, true)
            );
        }
         
        else
            lastLoc = newLoc;

    }

}
