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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import android.R.drawable;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MusicRemote extends Remote {

    /** Menu entry identifiers */
    final private static int 
        MENU_OSDMENU = 0, MENU_SHUFFLE = 1, MENU_REPEAT = 2, 
        MENU_VISUALISE = 3, MENU_CHANGE_VISUAL = 4, MENU_EDIT = 5;
    
    final private static int DIALOG_QUIT = 0; 
    
    final private static HashMap<Integer, Key>
        ctrls = new HashMap<Integer, Key>(20);
    
    static {
        ctrls.put(R.id.music_seek_back,    Key.MUSIC_REWIND);
        ctrls.put(R.id.music_skip_back,    Key.MUSIC_PREV);
        ctrls.put(R.id.music_skip_forward, Key.MUSIC_NEXT);
        ctrls.put(R.id.music_seek_forward, Key.MUSIC_FFWD);
    }
    
    final private Handler handler = new Handler();
    final private Context ctx     = this;
        
    final private WeakHashMap<Integer, Bitmap> artCache = 
        new WeakHashMap<Integer, Bitmap>(8);
    
    private boolean         jump = true;
    private MDDManager      mddMgr  = null;
    private TextView        titleView = null, detailView = null;
    private ProgressBar     pBar = null;
    private ImageView       artView = null;
    private Drawable        defaultArt = null;
    private ShuffleMode     shuffle = null;
    private RepeatMode      repeat = null;
    private String          lastTrack = null, lastDetails = null;
    private int             lastArtid = -1, lastProgress = 0;
        
    private enum RepeatMode {
        Off   (0),
        Track (1),
        All   (2);
        
        private int value;
        
        static final private Map<Integer, RepeatMode> revMap =
            new HashMap<Integer, RepeatMode>(3);

        static {
            for (RepeatMode m : EnumSet.allOf(RepeatMode.class))
                revMap.put(m.value(), m);
        }

        private RepeatMode(int code) {
            value = code;
        }

        public int value() {
            return value;
        }
        
        public static RepeatMode get(int code) {
            return revMap.get(code);
        }
        
    };
    
    private enum ShuffleMode {
        Off         (0),
        Random      (1),
        Intelligent (2),
        Album       (3),
        Artist      (4);
        
        private int value;
        
        static final private Map<Integer, ShuffleMode> revMap =
            new HashMap<Integer, ShuffleMode>(5);

        static {
            for (ShuffleMode m : EnumSet.allOf(ShuffleMode.class))
                revMap.put(m.value(), m);
        }

        private ShuffleMode(int code) {
            value = code;
        }

        public int value() {
            return value;
        }
        
        public static ShuffleMode get(int code) {
            return revMap.get(code);
        }
        
    };
    
    private class mddListener implements MusicListener {
        @Override
        public void onMusic(
            final String artist, final String album, 
            final String track, final int artid
        ) {
            final String details = artist + " ~ " + album;
            handler.post(
                new Runnable() {
                    public void run() {
                        titleView.setText(track);
                        detailView.setText(details);
                        if (artid != -1)
                            artView.setImageBitmap(getAlbumArt(artid));
                        else
                            artView.setImageDrawable(defaultArt);
                        
                        lastTrack = track;
                        lastDetails = details; 
                        lastArtid = artid;
                        
                    }
                }
            );
            
        }

        @Override
        public void onPlayerProp(final String prop, final int value) {
            handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        if (prop.equals("SHUFFLE")) {
                            ShuffleMode s = ShuffleMode.get(value);
                            if (shuffle != null && shuffle != s)
                                Toast.makeText(
                                    ctx, "Shuffle mode is " + s, 
                                    Toast.LENGTH_SHORT
                                ).show();
                            shuffle = s;
                        }

                        else if (prop.equals("REPEAT")) {
                            RepeatMode r = RepeatMode.get(value);
                            if (repeat != null && repeat != r)
                                Toast.makeText(
                                    ctx, "Repeat mode is " + r, 
                                    Toast.LENGTH_SHORT
                                ).show();
                            repeat = r;
                        }
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
                        pBar.setProgress(pos);
                        lastProgress = pos;
                    }
                }
            );
        }
    };
    
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        jump = !getIntent().hasExtra(Extras.DONTJUMP.toString());
        defaultArt = getResources().getDrawable(R.drawable.mdmusic);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        try {
            feMgr = MythDroid.connectFrontend(this);
        } catch (IOException e) {
            Util.err(this, e);
            finish();
            return;
        }
        
        try {
            mddMgr = new MDDManager(MythDroid.feMgr.getAddress());
        } catch (IOException e) { mddMgr = null; }
        
        setupViews();
        
        if (mddMgr != null)
            mddMgr.setMusicListener(new mddListener());
      
        try {
            if (jump && !feMgr.getLoc().music)  
                feMgr.jumpTo("playmusic");
        } catch (IOException e) {
            Util.err(this, e);
            finish();
            return;
        }
    }
    
    private void cleanup() {
        try {
            if (feMgr != null)  
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
    public void onClick(View v) {

        final Key key = (Key)v.getTag();
       
        try {
            feMgr.sendKey(key);
        } catch (IOException e) { Util.err(this, e); }

        super.onClick(v);

    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_SHUFFLE, Menu.NONE, R.string.shuffle_mode)
            .setIcon(R.drawable.ic_menu_refresh);
        menu.add(Menu.NONE, MENU_REPEAT, Menu.NONE, R.string.repeat_mode)
            .setIcon(drawable.ic_menu_revert);
        menu.add(Menu.NONE, MENU_VISUALISE, Menu.NONE, R.string.tog_vis)
            .setIcon(drawable.ic_menu_upload_you_tube);
        menu.add(Menu.NONE, MENU_CHANGE_VISUAL, Menu.NONE, R.string.chg_vis)
            .setIcon(drawable.ic_menu_slideshow);
        menu.add(Menu.NONE, MENU_EDIT, Menu.NONE, R.string.pl_edit)
            .setIcon(drawable.ic_menu_edit);
        menu.add(Menu.NONE, MENU_OSDMENU, Menu.NONE, R.string.osd_menu)
            .setIcon(drawable.ic_menu_more);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        Key key = null;
        Class<?> activity = null;
        
        switch (item.getItemId()) {
            case MENU_SHUFFLE:
                key = Key.MUSIC_SHUFFLE;
                break;
            case MENU_REPEAT:
                key = Key.MUSIC_REPEAT;
                break;
            case MENU_VISUALISE:
                key = Key.MUSIC_VISUALISE;
                break;
            case MENU_CHANGE_VISUAL:
                key = Key.MUSIC_CHANGE_VISUAL;
                break;
            case MENU_EDIT:
                key = Key.MUSIC_EDIT;
                activity = NavRemote.class;
                break;
            case MENU_OSDMENU:
                key = Key.MENU;
                activity = NavRemote.class;
                break;
        }
        
        try {
            feMgr.sendKey(key);
        } catch (IOException e) { Util.err(this, e); }
        
        if (activity != null)
            startActivityForResult(
                new Intent().setClass(ctx, activity), 0
            );
      
        return true;
                
    }
    
    @Override
    public Dialog onCreateDialog(int id) {

        switch (id) {
            
            case DIALOG_QUIT:

                OnClickListener cl = new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        dialog.dismiss();
                        
                        try {
                            switch(which) {
                                case Dialog.BUTTON_POSITIVE: 
                                    feMgr.jumpTo(MythDroid.lastLocation);
                                    break;
                                case Dialog.BUTTON_NEUTRAL:
                                    feMgr.sendKey(Key.ESCAPE);
                                    feMgr.sendKey(Key.DOWN);
                                    feMgr.sendKey(Key.ENTER);
                                    break;
                                default:
                                    return;
                            }
                        } catch (IOException e) { Util.err(ctx, e); }
                        
                        finish();

                    }
                };

                return 
                    new AlertDialog.Builder(ctx)
                        .setTitle(R.string.leave_remote)
                        .setMessage(R.string.halt_playback)
                        .setPositiveButton(R.string.yes, cl)
                        .setNeutralButton(R.string.no, cl)
                        .setNegativeButton(R.string.cancel, cl)
                        .create();
                
        }
        
        return null;
        
    }
    
    @Override
    public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_BACK) {
            showDialog(DIALOG_QUIT);
            return true;
        }
        else 
            return super.onKeyDown(code, event);
    }
    
    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        setupViews();
    }
 
    private void setupViews() {

        setContentView(R.layout.music_remote);
        
        for (int id : ctrls.keySet()) {
            final View v = findViewById(id);
            Key key = ctrls.get(id);
            v.setOnClickListener(this);
            v.setFocusable(false);
            v.setTag(key);
        }
        
        titleView = (TextView)findViewById(R.id.music_title);
        detailView = (TextView)findViewById(R.id.music_details);
        artView = (ImageView)findViewById(R.id.music_coverart);
        pBar = (ProgressBar)findViewById(R.id.music_progress);
        
        if (mddMgr == null) {
            titleView.setVisibility(View.GONE);
            detailView.setVisibility(View.GONE);
            pBar.setVisibility(View.GONE);
        }
        
        else {
            pBar.setMax(100);
            pBar.setProgress(lastProgress);            
            if (lastTrack != null)
                titleView.setText(lastTrack);
            if (lastDetails != null)
                detailView.setText(lastDetails);
            if (lastArtid != -1)
                artView.setImageBitmap(getAlbumArt(lastArtid));
        }
        
        listenToGestures(true);
        
    }
    
    private Bitmap getAlbumArt(int artid) {
        
        Bitmap bm = artCache.get(artid);
        
        if (bm != null)
            return bm;
        
        URL url = null;
        try {
            url = new URL(
                MythDroid.beMgr.getStatusURL() +
                "/Myth/GetAlbumArt?" + 
                "Id=" + artid +
                "&Width=" + artView.getWidth() +
                "&Height=" + artView.getHeight()
            );
        } catch (MalformedURLException e) {}
        

        try {
            URLConnection conn = url.openConnection();
            conn.connect();
            bm = BitmapFactory.decodeStream(conn.getInputStream());
            artCache.put(artid, bm);
            return bm;
        } catch (IOException e) { 
            Util.err(this, e);
            return null;
        }
        
    }

    @Override
    protected void onScrollDown() {
        try {
            feMgr.sendKey(Key.VOL_DOWN);
            feMgr.sendKey(Key.VOL_DOWN);
        } catch (IOException e) { Util.err(this, e); }
    }

    @Override
    protected void onScrollLeft() {
        try {
            feMgr.sendKey(Key.MUSIC_REWIND);
        } catch (IOException e) { Util.err(this, e); }
    }

    @Override
    protected void onScrollRight() {
        try {
            feMgr.sendKey(Key.MUSIC_FFWD);
        } catch (IOException e) { Util.err(this, e); }
    }

    @Override
    protected void onScrollUp() {
        try {
            feMgr.sendKey(Key.VOL_UP);
            feMgr.sendKey(Key.VOL_UP);
        } catch (IOException e) { Util.err(this, e); }
    }
    
    @Override
    protected void onFlingLeft() {
        try {
            feMgr.sendKey(Key.MUSIC_PREV);
        } catch (IOException e) { Util.err(this, e); }
    }
    
    @Override
    protected void onFlingRight() {
        try {
            feMgr.sendKey(Key.MUSIC_NEXT);
        } catch (IOException e) { Util.err(this, e); }
    }

    @Override
    protected void onTap() {
        try {
            feMgr.sendKey(Key.PAUSE);
        } catch (IOException e) { Util.err(this, e); }
    }
    
}
