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
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.mythdroid.Enums.Extras;
import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.Enums.Key;
import org.mythdroid.data.Program;
import org.mythdroid.data.Program.Commercial;
import org.mythdroid.frontend.FrontendLocation;
import org.mythdroid.mdd.MDDChannelListener;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.views.CutListDrawable;
import org.mythdroid.activities.Guide;

import android.R.drawable;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/** Remote for controlling tv/recording/video playback */
public class TVRemote extends Remote {

    /** Menu entry identifiers */
    final private static int
        MENU_OSDMENU = 0, MENU_GESTURE = 1, MENU_BUTTON = 2;

    final private static int
        DIALOG_LOAD  = 0, DIALOG_NUMPAD = 1, DIALOG_GUIDE = 2, DIALOG_QUIT = 3;

    private static SparseArray<Key>
        ctrls = new SparseArray<Key>(20),
        nums  = new SparseArray<Key>(12);

    static {
        nums.put(R.id.one,          Key.ONE);
        nums.put(R.id.two,          Key.TWO);
        nums.put(R.id.three,        Key.THREE);
        nums.put(R.id.four,         Key.FOUR);
        nums.put(R.id.five,         Key.FIVE);
        nums.put(R.id.six,          Key.SIX);
        nums.put(R.id.seven,        Key.SEVEN);
        nums.put(R.id.eight,        Key.EIGHT);
        nums.put(R.id.nine,         Key.NINE);
        nums.put(R.id.zero,         Key.ZERO);
        nums.put(R.id.enter,        Key.ENTER);
        ctrls.put(R.id.back,        Key.ESCAPE);
        ctrls.put(R.id.num,         Key.NUMPAD);
        ctrls.put(R.id.volUp,       Key.VOL_UP);
        ctrls.put(R.id.volDown,     Key.VOL_DOWN);
        ctrls.put(R.id.volMute,     Key.VOL_MUTE);
        ctrls.put(R.id.up,          Key.UP);
        ctrls.put(R.id.down,        Key.DOWN);
        ctrls.put(R.id.left,        Key.LEFT);
        ctrls.put(R.id.right,       Key.RIGHT);
        ctrls.put(R.id.enter,       Key.ENTER);
        ctrls.put(R.id.pause,       Key.PAUSE);
        ctrls.put(R.id.seekBack,    Key.SEEK_BACK);
        ctrls.put(R.id.skipBack,    Key.SKIP_BACK);
        ctrls.put(R.id.skipForward, Key.SKIP_FORWARD);
        ctrls.put(R.id.seekForward, Key.SEEK_FORWARD);
        ctrls.put(R.id.info,        Key.INFO);
        ctrls.put(R.id.skip,        Key.SKIP_COMMERCIAL);
        ctrls.put(R.id.guide,       Key.GUIDE);
    }

    final private Handler    handler      = new Handler();
    final private Object     feLock       = new Object();
    final private Context    ctx          = this;
    private TextView         titleView    = null;
    private SeekBar          pBar         = null;
    private Timer            timer        = null;
    private int              jumpChan     = -1,     lastProgress    = 0,
                             endTime      = -1;
    private UpdateStatusTask updateStatus = null;
    private MDDManager       mddMgr       = null;
    private String           lastFilename = null,   filename     = null,
                             videoTitle   = null;


    private boolean
        paused = false, livetv = false, jump = false,
        gesture = false, wasPaused = false;

    /** Run periodically to update title and progress bar */
    private class UpdateStatusTask extends TimerTask {
        @Override
        public void run() {

            FrontendLocation loc = null;
            
            synchronized (feLock) {
                if (feMgr == null || !feMgr.isConnected()) return;
            }
            
            try {
                synchronized (feLock) { loc = feMgr.getLoc(); }
            } catch (IOException e) {
                ErrUtil.postErr(ctx, e);
                done();
                return;
            }
            
            if (!loc.video) {
                done();
                return;
            }
            
            final String name = loc.filename;

            if (livetv && !name.equals(lastFilename))
                handler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Program prog =
                                    Globals.getBackend().getRecording(name);
                                titleView.setText(prog.Title);
                            } catch (IOException e) {
                                ErrUtil.postErr(ctx, e);
                                return;
                            }
                        }
                    }
                );

            else if (!livetv) {
                pBar.setProgress(loc.position);
            }

        }
    };

    private Runnable ready =  new Runnable() {
        @Override
        public void run() {
            if (jump)
                try {
                    dismissDialog(DIALOG_LOAD);
                } catch (IllegalArgumentException e) {}
            
            synchronized (feLock) { if (feMgr == null) return; }
            
            setupStatus();
            if (mddMgr == null) {
                updateStatus = new UpdateStatusTask();
                if (timer == null) timer = new Timer();
                timer.scheduleAtFixedRate(updateStatus, 8000, 8000);
            }
            else
                mddMgr.setChannelListener(new mddListener());
        }
    };

    private Runnable jumpRun = new Runnable() {
        @Override
        public void run() {

            synchronized (feLock) { if (feMgr == null) return; }
            
            try {
                
                if (livetv) {
                    
                    FrontendLocation loc = null;
                    synchronized (feLock) { 
                        if (!(loc = feMgr.getLoc()).livetv)
                            feMgr.jumpTo("livetv"); //$NON-NLS-1$
                    }
                    
                    synchronized (feLock) { loc = feMgr.getLoc(); }
                    if (!loc.livetv) {
                        ErrUtil.postErr(ctx, Messages.getString("TVRemote.1")); //$NON-NLS-1$
                        done();
                        return;
                    }
                    
                    if (jumpChan >= 0)
                        synchronized (feLock) {
                            while(feMgr.getLoc().position < 1)
                                Thread.sleep(500);
                            feMgr.playChan(jumpChan);
                        }
                    
                }
                
                else if (filename != null)
                    synchronized (feLock) { feMgr.playFile(filename); }
                else
                    synchronized (feLock) { feMgr.playRec(Globals.curProg); }
                
            } catch (IOException e) {
                ErrUtil.postErr(ctx, e);
                done();
                return;
            } catch (InterruptedException e) {}
              catch (IllegalArgumentException e) { 
                  ErrUtil.postErr(ctx, e);
                  done();
                  return;
              }
            
            
            handler.post(ready);

        }
    };

    private class mddListener implements MDDChannelListener {

        @Override
        public void onChannel(
            final String channel, final String title, final String subtitle
        ) {
            if (videoTitle != null)
                return;
            handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        titleView.setText(title);
                    }
                }
            );
        }

        @Override
        public void onProgress(final int pos) {
            handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        if (livetv) return;
                        pBar.setProgress(pos);
                        lastProgress = pos;
                    }
                }
            );
        }

        @Override
        public void onExit() {
                done();
        }

    };

    @Override
    public void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        Intent intent = getIntent();

        livetv = intent.hasExtra(Extras.LIVETV.toString());
        jump = !intent.hasExtra(Extras.DONTJUMP.toString());
        if (intent.hasExtra(Extras.FILENAME.toString()))
            filename = intent.getStringExtra(Extras.FILENAME.toString());
        if (intent.hasExtra(Extras.TITLE.toString()))
            videoTitle = intent.getStringExtra(Extras.TITLE.toString());
        jumpChan = intent.getIntExtra(Extras.JUMPCHAN.toString(), -1);

        setResult(RESULT_OK);

        ctrls.put(R.id.rec, livetv ? Key.RECORD : Key.EDIT);

        gesture = PreferenceManager.getDefaultSharedPreferences(this)
                      .getString("tvDefaultStyle", "") //$NON-NLS-1$ //$NON-NLS-2$
                          .equals(Messages.getString("TVRemote.0")); // Gesture //$NON-NLS-1$

        setupViews(gesture);
        listenToGestures(gesture);

    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            synchronized (feLock) { feMgr = Globals.getFrontend(this); }
        } catch (IOException e) {
            ErrUtil.err(this, e);
            finish();
            return;
        }

        if (mddMgr == null)
            try {
                mddMgr = new MDDManager(feMgr.addr);
            } catch (IOException e) {
                mddMgr = null;
                timer = new Timer();
            }

        if (jump && !wasPaused) {
            showDialog(DIALOG_LOAD);
            Globals.getWorker().post(jumpRun);
        }
        else
            handler.post(ready);
    }

    private void cleanup() {

        synchronized (feLock) {
            if (feMgr != null) 
                feMgr.disconnect();
            feMgr = null;
        }

        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        cleanup();
        wasPaused = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanup();
        if (mddMgr != null)
            mddMgr.shutdown();
        mddMgr = null;
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        setupViews(gesture);
        if (feMgr == null) onResume();
        setupStatus();
    }

    @Override
    public void onAction() {
        if (mddMgr != null || updateStatus == null) return;
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
        
        synchronized (feLock) {
            if (feMgr != null)
                try {
                    feMgr.sendKey(key);
                } catch (IOException e) { ErrUtil.err(this, e); }
                  catch (IllegalArgumentException e) { ErrUtil.err(this, e); }
        }
              
        if (key == Key.GUIDE)
            startActivityForResult(
                new Intent().setClass(ctx, NavRemote.class), 0
            );

        super.onClick(v);

    }

    @Override
    public Dialog onCreateDialog(int id) {

        switch (id) {

            case DIALOG_NUMPAD:
                final Dialog pad = new Dialog(this);
                pad.setContentView(R.layout.numpad);
                pad.findViewById(android.R.id.title).setVisibility(View.GONE);
                View button;
                int size = nums.size();
                for (int i = 0; i < size; i++) {
                    int viewId = nums.keyAt(i);
                    button = pad.findViewById(viewId);
                    button.setTag(nums.get(viewId));
                    button.setOnClickListener(this);
                }
                return pad;

            case DIALOG_GUIDE:
                return new AlertDialog.Builder(ctx)
                    .setIcon(drawable.ic_menu_upload_you_tube)
                    .setTitle(R.string.dispGuide)
                    .setAdapter(
                        new ArrayAdapter<String>(
                            ctx, R.layout.simple_list_item_1, new String[] {}
                        )
                        , null
                    )
                    .create();

            case DIALOG_QUIT:
                OnClickListener cl = new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        switch(which) {
                            case Dialog.BUTTON_POSITIVE:
                                jump = true;
                                break;
                            case Dialog.BUTTON_NEUTRAL:
                                jump = false;
                                setResult(REMOTE_RESULT_FINISH);
                                break;
                            default:
                                return;

                        }
                        done();
                    }
                };

                return
                    new AlertDialog.Builder(ctx)
                        .setTitle(R.string.leaveRemote)
                        .setMessage(R.string.haltPlayback)
                        .setPositiveButton(R.string.yes, cl)
                        .setNeutralButton(R.string.no, cl)
                        .setNegativeButton(R.string.cancel, cl)
                        .create();

        }

        return null;

    }

    @Override
    public void onPrepareDialog(int id, final Dialog dialog) {

        if (id != DIALOG_GUIDE) return;

        final String[] items = new String[] {
                Messages.getString("TVRemote.2"),  //$NON-NLS-1$
                Messages.getString("TVRemote.3") + feMgr.name  //$NON-NLS-1$
        };

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
                                synchronized (feLock) { 
                                    if (feMgr != null) feMgr.sendKey(Key.GUIDE);
                                }
                            } catch (IOException e) {
                                ErrUtil.err(ctx, e.getMessage());
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
        menu.add(Menu.NONE, MENU_BUTTON, Menu.NONE, R.string.btnIface)
            .setIcon(drawable.ic_menu_add);
        menu.add(Menu.NONE, MENU_GESTURE, Menu.NONE, R.string.gestIface)
            .setIcon(R.drawable.ic_menu_finger);
        menu.add(Menu.NONE, MENU_OSDMENU, Menu.NONE, R.string.osdMenu)
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
                    synchronized (feLock) {
                        if (feMgr != null) feMgr.sendKey(Key.MENU);
                    }
                } catch (IOException e) { ErrUtil.err(this, e); }
                return true;
            case MENU_GESTURE:
                gesture = true;
                break;
            case MENU_BUTTON:
                gesture = false;
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        setupViews(gesture);
        setupStatus();
        listenToGestures(gesture);

        return true;

    }

    @Override
    public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_BACK) {
            showDialog(DIALOG_QUIT);
            return true;
        }
        return super.onKeyDown(code, event);
    }

    /**
     * Setup the interactive views
     * @param gesture true for 'gesture' layout, false for 'button'
     */
    private void setupViews(boolean gesture) {

        if (gesture) {
            setContentView(R.layout.tv_gesture_remote);
            for (
                int id : new int[] { 
                    R.id.back,  R.id.num,  R.id.volMute, R.id.guide,
                    R.id.enter, R.id.info, R.id.skip
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

            int size = ctrls.size();
            
            for (int i = 0; i < size; i++) {
                
                int id = ctrls.keyAt(i);
                final View v = findViewById(id);
                Key key = ctrls.get(id);
                v.setOnClickListener(this);
                v.setFocusable(false);
                v.setTag(key);

            }
        }
        
        View v = findViewById(R.id.guide);

        if (!livetv)
            v.setVisibility(View.INVISIBLE);
        else
            v.setOnLongClickListener(
                new OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        showDialog(DIALOG_GUIDE);
                        return true;
                    }
                }
            );
        
        v = findViewById(R.id.skip);
        v.setOnLongClickListener(
            new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    try {
                        synchronized (feLock) {
                            if (feMgr != null)
                                feMgr.sendKey(Key.SKIP_PREV_COMMERCIAL);
                        }
                    } catch (IOException e) { ErrUtil.err(ctx, e); }
                    return true;
                }
            }
        );

        removeDialog(DIALOG_NUMPAD);
        
    }

    /** Setup the status widgets (progress bar, program title) */
    private void setupStatus() {

        titleView = (TextView)findViewById(R.id.title);
        pBar = (SeekBar)findViewById(R.id.progress);
        titleView.setFocusable(false);
        pBar.setFocusable(false);

        if (livetv || videoTitle != null)
            pBar.setVisibility(View.GONE);
        
        if (videoTitle != null) {
            titleView.setText(videoTitle);
            return;
        }

        Program prog = null;
        FrontendLocation loc = null;

        try {
            synchronized (feLock) { loc = feMgr.getLoc(); }
            if (!loc.video) {
                done();
                return;
            }
            if (loc.filename.equals("Video")) { //$NON-NLS-1$ 
                pBar.setVisibility(View.GONE);
                return;
            }
            prog = Globals.getBackend().getRecording(loc.filename);
        } catch (IOException e) {
            ErrUtil.err(this, e);
            done();
            return;
        }
        
        endTime = loc.end;
        
        titleView.setText(prog.Title);

        if (livetv) {
            lastFilename = loc.filename;
            return;
        }

        if (mddMgr != null)
            setupProgressBar(1000, lastProgress, prog, loc.fps);
        else
            setupProgressBar(loc.end, loc.position, null, loc.fps);
        
    }
    
    private void setupProgressBar(
        int max, int progress, Program prog, float fps
    ) {
        
        pBar.setMax(max);
        pBar.setProgress(progress);
        
        pBar.setOnSeekBarChangeListener(
            new OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(
                    SeekBar seekBar, int progress, boolean fromUser
                ) {
                    if (!fromUser || seekBar.getMax() <= 0) return;
                    if (mddMgr != null) {
                        FrontendLocation loc = null;
                        try {
                            synchronized (feLock) { loc = feMgr.getLoc(); }
                        } catch (IOException e) { return; }
                        if (loc.end <= 0) return;
                        progress = (loc.end * progress) / 1000;
                    }
                    try {
                        synchronized (feLock) { feMgr.seekTo(progress); }
                    } catch (IOException e) {}
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
                    
            }
        );
        
        if (prog == null || endTime <= 0 || fps == 0) return;
        
        ArrayList<Commercial> cuts = prog.getCutList(feMgr.addr);
        if (cuts.isEmpty()) return;
        
        Drawable[] layers = new Drawable[2];
        layers[0] = pBar.getProgressDrawable();
        Rect bounds = layers[0].getBounds();
        layers[1] = new CutListDrawable(cuts, fps, endTime, bounds);
        LayerDrawable lg = new LayerDrawable(layers);
        lg.setBounds(bounds);
        pBar.setProgressDrawable(lg);
        
    }

    /** Call finish() but jump to lastLocation first, if possible */
    private synchronized void done() {
        synchronized (feLock) {
            if (feMgr != null && feMgr.isConnected() && jump) {
                try {
                    feMgr.jumpTo(Globals.lastLocation);
                } catch (IOException e) { ErrUtil.postErr(this, e); }
                catch (IllegalArgumentException e) { ErrUtil.postErr(this, e); }
            }
        }
        jump = false;
        if (!isFinishing()) finish();
    }

    @Override
    protected void onScrollDown() {
        synchronized (feLock) {
            if (feMgr == null) return;
            try {
                feMgr.sendKey(Key.VOL_DOWN);
                feMgr.sendKey(Key.VOL_DOWN);
            } catch (IOException e) { ErrUtil.err(this, e); }
        }
        onAction();
    }

    @Override
    protected void onScrollLeft() {
        synchronized (feLock) {
            if (feMgr == null) return;
            try {
                feMgr.sendKey(Key.SEEK_BACK);
            } catch (IOException e) { ErrUtil.err(this, e); }
        }
        onAction();
    }

    @Override
    protected void onScrollRight() {
        synchronized (feLock) {
            if (feMgr == null) return;
            try {
                feMgr.sendKey(Key.SEEK_FORWARD);
            } catch (IOException e) { ErrUtil.err(this, e); }
        }
        onAction();
    }

    @Override
    protected void onScrollUp() {
        synchronized (feLock) {
            if (feMgr == null) return;
            try {
                feMgr.sendKey(Key.VOL_UP);
                feMgr.sendKey(Key.VOL_UP);
            } catch (IOException e) { ErrUtil.err(this, e); }
        }
        onAction();
    }

    @Override
    protected void onTap() {
        synchronized (feLock) {
            if (feMgr == null) return;
            try {
                feMgr.sendKey(Key.PAUSE);
            } catch (IOException e) { ErrUtil.err(this, e); }
        }
        onAction();
    }

}
