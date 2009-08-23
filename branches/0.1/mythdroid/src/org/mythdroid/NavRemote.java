/*
    MythDroid: Android MythTV Remote
    
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
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class NavRemote extends Remote implements View.OnClickListener {

    final private static int MENU_GESTURE = 0, MENU_BUTTON = 1;

    private FrontendLocation lastLoc = null;
    private TextView         locView = null;
    private boolean 
        gesture = false, calledByTVRemote = false, jumpGuide = false;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setupViews(gesture);

        if (getIntent().hasExtra(MythDroid.GUIDE)) 
            jumpGuide = true;

        ComponentName caller = getCallingActivity();
        if (caller != null && caller.getShortClassName().equals(".TVRemote"))
            calledByTVRemote = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        feMgr = MythDroid.connectFrontend(this);

        if (feMgr == null) {
            finish();
            return;
        }

        try {
            if (jumpGuide) 
                feMgr.jumpTo("guidegrid");
            updateLoc();
        } catch (IOException e) { Util.err(this, e); }
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
    public void onDestroy() {
        super.onDestroy();
        if (feMgr != null && !calledByTVRemote) try {
            feMgr.disconnect();
            feMgr = null;
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
    protected void onAction() {
        try {
            updateLoc();
        } catch (IOException e) { Util.err(this, e); }
    }

    private void setupViews(boolean gesture) {
        setContentView(
            gesture ? R.layout.nav_gesture_remote : R.layout.nav_remote
        );

        locView = (TextView) findViewById(R.id.nav_loc);

        if (feMgr != null) try {
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
        
        if (locView == null) return;

        final FrontendLocation newLoc = feMgr.getLoc();
        locView.setText(newLoc.niceLocation);

        if (newLoc.video) {
            if (lastLoc != null) 
                MythDroid.lastLocation = lastLoc;
            if (calledByTVRemote)
                finish();
            else {
                final Intent intent = 
                    new Intent().setClass(this, TVRemote.class)
                                .putExtra(MythDroid.DONTJUMP, true);
                
                if (newLoc.livetv) 
                    intent.putExtra(MythDroid.LIVETV, true);
                startActivity(intent);
            }
        }

        else
            lastLoc = newLoc;

    }

}
