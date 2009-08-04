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
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import android.R.drawable;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class TVRemote extends Remote {

    /** Menu entry identifiers */
    final private static int 
        MENU_OSDMENU = 0, MENU_GESTURE = 1, MENU_BUTTON = 2;

    final private static int 
        DIALOG_LOAD  = 0, DIALOG_NUMPAD = 1, DIALOG_GUIDE = 2;

    private static HashMap<Integer, Key>
        ctrls = new HashMap<Integer, Key>(12),
        nums = new HashMap<Integer, Key>(20);

    static {
        nums.put(R.id.one,              Key.ONE);
        nums.put(R.id.two,              Key.TWO);
        nums.put(R.id.three,            Key.THREE);
        nums.put(R.id.four,             Key.FOUR);
        nums.put(R.id.five,             Key.FIVE);
        nums.put(R.id.six,              Key.SIX);
        nums.put(R.id.seven,            Key.SEVEN);
        nums.put(R.id.eight,            Key.EIGHT);
        nums.put(R.id.nine,             Key.NINE);
        nums.put(R.id.zero,             Key.ZERO);
        nums.put(R.id.enter,            Key.ENTER);
        ctrls.put(R.id.tv_back,         Key.ESCAPE);
        ctrls.put(R.id.tv_num,          Key.NUMPAD);
        ctrls.put(R.id.tv_vol_up,       Key.VOL_UP);
        ctrls.put(R.id.tv_vol_down,     Key.VOL_DOWN);
        ctrls.put(R.id.tv_vol_mute,     Key.VOL_MUTE);
        ctrls.put(R.id.tv_up,           Key.UP);
        ctrls.put(R.id.tv_down,         Key.DOWN);
        ctrls.put(R.id.tv_left,         Key.LEFT);
        ctrls.put(R.id.tv_right,        Key.RIGHT);
        ctrls.put(R.id.tv_enter,        Key.ENTER);
        ctrls.put(R.id.tv_pause,        Key.PAUSE);
        ctrls.put(R.id.tv_seek_back,    Key.SEEK_BACK);
        ctrls.put(R.id.tv_skip_back,    Key.SKIP_BACK);
        ctrls.put(R.id.tv_skip_forward, Key.SKIP_FORWARD);
        ctrls.put(R.id.tv_seek_forward, Key.SEEK_FORWARD);
        ctrls.put(R.id.tv_info,         Key.INFO);
        ctrls.put(R.id.tv_skip,         Key.SKIP_COMMERCIAL);
    }

    final private Handler    handler      = new Handler();
    final private Context    ctx          = this;
    private ProgressBar      pBar         = null;
    private String           lastFilename;
    private Timer            timer        = null;
    private int              progress     = 0, end = 0, jumpChan = -1;
    private UpdateStatusTask updateStatus = null;

    private boolean 
        paused = false, livetv = false, jump = false, 
        gesture = false, wasPaused = false;
    
    

    /** Run periodically to update title and progress bar */
    private class UpdateStatusTask extends TimerTask {
        @Override
        public void run() {

            try {
                
                final FrontendLocation loc = feMgr.getLoc();
            
                if (!loc.video) {
                    finish();
                    return;
                }

                if (livetv && !loc.filename.equals(lastFilename))
                    handler.post(
                        new Runnable() { 
                            @Override
                            public void run() { setupStatus(loc); }
                        }
                    );

                else if (!livetv) {
                    progress = loc.position;
                    pBar.setProgress(progress);
                }

            } catch (IOException e) {
                Util.posterr(ctx, e);
                finish();
                return;
            }

        }
    };
    
    private Runnable ready =  new Runnable() {
        @Override
        public void run() {
            if (jump)
                dismissDialog(DIALOG_LOAD);
            setupStatus(null);
            updateStatus = new UpdateStatusTask();
            timer.scheduleAtFixedRate(updateStatus, 8000, 8000);
        }
    };

    private Runnable jumpRun = new Runnable() {
        @Override
        public void run() {
            
            try {
                if (livetv) {
                    feMgr.jumpTo("livetv");
                    if (!feMgr.getLoc().livetv) {
                        Util.posterr(ctx, "Timeout entering LiveTV");
                        finish();
                        return;
                    }
                    if (jumpChan >= 0) { 
                        while(feMgr.getLoc().position < 1)
                            Thread.sleep(500);
                        feMgr.playChan(jumpChan);
                    }
                }
                else
                    feMgr.playRec(MythDroid.curProg);
            } catch (IOException e) {
                Util.posterr(ctx, e);
            } catch (InterruptedException e) {}

            handler.post(ready);
        
        }
    };

    @Override
    public void onCreate(Bundle icicle) {

        super.onCreate(icicle);

        livetv = getIntent().hasExtra(MythDroid.LIVETV);
        jump = !getIntent().hasExtra(MythDroid.DONTJUMP);
        jumpChan = getIntent().getIntExtra(MythDroid.JUMPCHAN, -1);

        fillCtrls(livetv);

        setupViews(gesture);

        feMgr = MythDroid.connectFrontend(this);

        if (feMgr == null) {
            finish();
            return;
        }

        timer = new Timer();

        if (jump) {
            showDialog(DIALOG_LOAD);
            MythDroid.wHandler.post(jumpRun);
        }

        else
            handler.post(ready);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
        if (feMgr != null && jump) {
            try {
                feMgr.jumpTo(MythDroid.lastLocation);
                feMgr.disconnect();
            } catch (IOException e) {
                Util.err(this, e);
            }
        }
        ctrls.remove(R.id.tv_rec);
        ctrls.remove(R.id.tv_guide);
    }

    @Override
    public void onPause() {
        super.onPause();
        wasPaused = true;
        timer.cancel();
        timer.purge();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (feMgr == null) {
            finish();
            return;
        }
        if (wasPaused) {
            timer = new Timer();
            handler.post(ready);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        setupViews(gesture);
        setupStatus(null);
    }

    @Override
    public void onAction() {
        updateStatus.run();
    }

    @Override
    public void onClick(View v) {

        final Key key = (Key)v.getTag();

        if (key == Key.PAUSE) {
            paused = !paused;
            ((ImageButton)v).setImageResource(
                paused ? R.drawable.play : R.drawable.pause
            );
        }

        else if (key == Key.NUMPAD) {
            showDialog(DIALOG_NUMPAD);
            return;
        }

        try {
            feMgr.sendKey(key);
        } catch (IOException e) { Util.err(this, e); }

        if (key == Key.GUIDE)
            startActivityForResult(
                new Intent().setClass(ctx, NavRemote.class), 0
            );

        super.onClick(v);

    }

    @Override
    public Dialog onCreateDialog(int id) {

        switch (id) {

            case DIALOG_LOAD:
                final ProgressDialog d = new ProgressDialog(this);
                d.setIndeterminate(true);
                d.setMessage("Loading");
                return d;

            case DIALOG_NUMPAD:
                final Dialog pad = new Dialog(this);
                pad.setContentView(R.layout.numpad);
                pad.findViewById(android.R.id.title).setVisibility(View.GONE);

                View button;
                for (int viewId : nums.keySet()) {
                    button = pad.findViewById(viewId);
                    button.setTag(nums.get(viewId));
                    button.setOnClickListener(this);
                }
                return pad;

            case DIALOG_GUIDE:
                return new AlertDialog.Builder(ctx)
                    .setIcon(drawable.ic_menu_upload_you_tube)
                    .setTitle("Display Guide")
                    .setAdapter(
                        new ArrayAdapter<String>(
                            ctx, R.layout.simple_list_item_1, new String[] {}
                        )
                        , null
                    )
                    .create();

        }

        return null;

    }

    @Override
    public void onPrepareDialog(int id, final Dialog dialog) {
        
        if (id != DIALOG_GUIDE) return;

        final String[] items = new String[] { "Here", "On " + feMgr.name() };

        final ListView lv = ((AlertDialog)dialog).getListView();

        lv.setAdapter(
            new ArrayAdapter<String>(ctx, R.layout.simple_list_item_1, items)
        );

        lv.setOnItemClickListener(
            new OnItemClickListener() {
                @Override
                public void onItemClick(
                    AdapterView<?> av, View v, int pos, long id
                ) {
                    dialog.dismiss();
                    
                    switch (pos) {
                        
                        case 0:
                            startActivity(
                                new Intent().setClass(ctx, Guide.class)
                            );
                            return;
                            
                        case 1:
                            try {
                                feMgr.sendKey(Key.GUIDE);
                            } catch (IOException e) {
                                Util.err(ctx, e.getMessage());
                                return;
                            }
                            startActivityForResult(
                                new Intent().setClass(ctx, NavRemote.class), 0
                            );
                            return;
                    }

                }
            }
        );
    }

    /** Compose the menu */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_BUTTON, Menu.NONE, "Button Interface")
            .setIcon(drawable.ic_menu_add);
        menu.add(Menu.NONE, MENU_GESTURE, Menu.NONE, "Gesture Interface")
            .setIcon(R.drawable.ic_menu_finger);
        menu.add(Menu.NONE, MENU_OSDMENU, Menu.NONE, "OSD Menu")
            .setIcon(drawable.ic_menu_more);
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
            case MENU_OSDMENU:
                try {
                    feMgr.sendKey(Key.MENU);
                } catch (IOException e) {
                    Util.err(this, e);
                }
                return true;
            case MENU_GESTURE:
                gesture = true;
                break;
            case MENU_BUTTON:
                gesture = false;
                break;
        }

        setupViews(gesture);
        setupStatus(null);
        listenToGestures(gesture);

        return true;

    }

    private void setupViews(boolean gesture) {

        if (gesture) {
            setContentView(R.layout.tv_gesture_remote);
            for (
                int id : new int[] { 
                    R.id.tv_back, R.id.tv_num, R.id.tv_vol_mute, 
                    R.id.tv_enter, R.id.tv_info, R.id.tv_skip 
                }
           ) {
                View v = findViewById(id);
                v.setTag(ctrls.get(id));
                v.setOnClickListener(this);
                v.setFocusable(false);

            }

        }
        else {
            setContentView(R.layout.tv_remote);
            
            for (int id : ctrls.keySet()) {
                
                final View v = findViewById(id);
                Key key = ctrls.get(id);
                
                if (key == Key.GUIDE) {
                    
                    if (livetv) {
                        v.setOnLongClickListener(
                            new OnLongClickListener() {
                                @Override
                                public boolean onLongClick(View v) {
                                    showDialog(DIALOG_GUIDE);
                                    return true;
                                }

                            }
                       );
                    }
                    
                    else {
                        v.setVisibility(View.INVISIBLE);
                        continue;
                    }
                
                }
                
                v.setOnClickListener(this);
                v.setFocusable(false);
                v.setTag(key);

            }
        }

        removeDialog(DIALOG_NUMPAD);
    }

    private void setupStatus(FrontendLocation loc) {

        Program prog = null;

        try {
            if (loc == null)
                loc = feMgr.getLoc();
            if (!loc.video) {
                finish();
                return;
            }
            prog = MythDroid.beMgr.getRecording(loc.filename);
        } catch (IOException e) {
            Util.err(this, e);
            finish();
            return;
        } catch (NullPointerException e) {
            Util.err(this, "Backend connection gone away");
            finish();
            return;
        }

        TextView tv = (TextView)findViewById(R.id.tv_title);
        tv.setText(prog.Title);
        tv.setFocusable(false);

        pBar = (ProgressBar)findViewById(R.id.tv_progress);

        if (livetv) {
            pBar.setVisibility(View.GONE);
            lastFilename = loc.filename;
            return;
        }

        end = loc.end;
        pBar.setMax(end);
        progress = loc.position;
        pBar.setProgress(progress);
        pBar.setFocusable(false);

    }

    private void fillCtrls(boolean livetv) {
        if (livetv) {
            ctrls.put(R.id.tv_guide, Key.GUIDE);
            ctrls.put(R.id.tv_rec, Key.RECORD);
        }
        else
            ctrls.put(R.id.tv_rec, Key.EDIT);
    }

    @Override
    protected void onScrollDown() {
        try {
            feMgr.sendKey(Key.VOL_DOWN);
            feMgr.sendKey(Key.VOL_DOWN);
        } catch (IOException e) { Util.err(this, e); }
        onAction();
    }

    @Override
    protected void onScrollLeft() {
        try {
            feMgr.sendKey(Key.SEEK_BACK);
        } catch (IOException e) { Util.err(this, e); }
        onAction();
    }

    @Override
    protected void onScrollRight() {
        try {
            feMgr.sendKey(Key.SEEK_FORWARD);
        } catch (IOException e) { Util.err(this, e); }
        onAction();
    }

    @Override
    protected void onScrollUp() {
        try {
            feMgr.sendKey(Key.VOL_UP);
            feMgr.sendKey(Key.VOL_UP);
        } catch (IOException e) { Util.err(this, e); }
        onAction();
    }

    @Override
    protected void onTap() {
        try {
            feMgr.sendKey(Key.PAUSE);
        } catch (IOException e) { Util.err(this, e); }
        onAction();
    }

}
