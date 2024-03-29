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
import java.util.EnumSet;

import org.mythdroid.Enums.Extras;
import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.mdd.MDDMusicListener;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.util.LogUtil;
import org.mythdroid.Enums.Key;

import android.R.drawable;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/** Remote for music playback */
public class MusicRemote extends Remote {

    /** Menu entry identifiers */
    final private static int
        MENU_OSDMENU = 0, MENU_SHUFFLE = 1, MENU_REPEAT = 2,
        MENU_VISUALISE = 3, MENU_CHANGE_VISUAL = 4, MENU_EDIT = 5;

    final private static int DIALOG_QUIT = 0;

    final private static SparseArray<Key>
        ctrls = new SparseArray<Key>(20);

    static {
        ctrls.put(R.id.seekBack,    Key.MUSIC_REWIND);
        ctrls.put(R.id.skipBack,    Key.MUSIC_PREV);
        ctrls.put(R.id.skipForward, Key.MUSIC_NEXT);
        ctrls.put(R.id.seekForward, Key.MUSIC_FFWD);
    }

    final private Handler handler = new Handler();
    final private Context ctx     = this;

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

        static final private SparseArray<RepeatMode> revMap =
            new SparseArray<RepeatMode>(3);

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

        static final private SparseArray<ShuffleMode> revMap =
            new SparseArray<ShuffleMode>(5);

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

    final private MDDMusicListener mddListener = new MDDMusicListener() {
        @Override
        public void onMusic(
            final String artist, final String album,
            final String track, final int artid
        ) {
            final String details = artist + " ~ " + album; //$NON-NLS-1$
            handler.post(
                new Runnable() {
                    @Override
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
                        if (prop.equals("SHUFFLE")) { //$NON-NLS-1$
                            ShuffleMode s = ShuffleMode.get(value);
                            if (shuffle != null && shuffle != s)
                                Toast.makeText(
                                    ctx, Messages.getString("MusicRemote.2") + //$NON-NLS-1$
                                    s,
                                    Toast.LENGTH_SHORT
                                ).show();
                            shuffle = s;
                        }

                        else if (prop.equals("REPEAT")) { //$NON-NLS-1$
                            RepeatMode r = RepeatMode.get(value);
                            if (repeat != null && repeat != r)
                                Toast.makeText(
                                    ctx, Messages.getString("MusicRemote.4") + //$NON-NLS-1$
                                    r,
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
            feMgr = Globals.getFrontend(this);
        } catch (IOException e) {
            ErrUtil.err(this, e);
            finish();
            return;
        }

        try {
            mddMgr = new MDDManager(feMgr.addr);
        } catch (IOException e) { mddMgr = null; }

        setupViews();

        if (mddMgr != null)
            mddMgr.setMusicListener(mddListener);

        try {
            if (jump && !feMgr.getLoc().music)
                feMgr.jumpTo("playmusic"); //$NON-NLS-1$
        } catch (IOException e) {
            ErrUtil.err(this, e);
            finish();
            return;
        } catch (IllegalArgumentException e) {
            ErrUtil.reportErr(ctx, e);
            finish();
            return;
        }
    }

    private void cleanup() {
        if (feMgr != null)
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
    public void onClick(View v) {

        final Key key = (Key)v.getTag();

        if (feMgr != null)
            try {
                feMgr.sendKey(key);
            } catch (IOException e) { ErrUtil.err(this, e); }
              catch (IllegalArgumentException e) { ErrUtil.reportErr(this, e); }

        super.onClick(v);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        MenuItemCompat.setShowAsAction(
            menu.add(Menu.NONE, MENU_SHUFFLE, Menu.NONE, R.string.shuffle)
                .setIcon(R.drawable.ic_menu_refresh),
            MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
        );
        MenuItemCompat.setShowAsAction(
            menu.add(Menu.NONE, MENU_REPEAT, Menu.NONE, R.string.repeat)
                .setIcon(drawable.ic_menu_revert),
            MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
        );
        MenuItemCompat.setShowAsAction(
            menu.add(Menu.NONE, MENU_EDIT, Menu.NONE, R.string.plEdit)
                .setIcon(drawable.ic_menu_edit),
            MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
        );
        MenuItemCompat.setShowAsAction(
            menu.add(Menu.NONE, MENU_OSDMENU, Menu.NONE, R.string.osdMenu)
                .setIcon(drawable.ic_menu_more),
            MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
        );
        MenuItemCompat.setShowAsAction(
            menu.add(Menu.NONE, MENU_VISUALISE, Menu.NONE, R.string.visualiser)
            .setIcon(drawable.ic_menu_upload_you_tube),
            MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
        );
        MenuItemCompat.setShowAsAction(
            menu.add(Menu.NONE, MENU_CHANGE_VISUAL, Menu.NONE, R.string.chVisual)
                .setIcon(drawable.ic_menu_slideshow),
            MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
        );
        
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
            default:
                return super.onOptionsItemSelected(item);
        }

        if (feMgr == null) return true;
        
        try {
            feMgr.sendKey(key);
        } catch (IOException e) { 
            ErrUtil.err(this, e);
            return true;
        } catch (IllegalArgumentException e) { 
            ErrUtil.reportErr(this, e);
            return true;
        }

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
                                    if (jump) {
                                        LogUtil.debug(
                                            "MusicRemote is finishing, jumping" + //$NON-NLS-1$
                                            " back to " + //$NON-NLS-1$
                                            Globals.getLastLocation().location
                                        );
                                        feMgr.jumpTo(Globals.getLastLocation());
                                    }
                                    else {
                                        feMgr.sendKey(Key.ESCAPE);
                                        feMgr.sendKey(Key.ENTER);
                                    }
                                    break;
                                case Dialog.BUTTON_NEUTRAL:
                                    feMgr.sendKey(Key.ESCAPE);
                                    feMgr.sendKey(Key.DOWN);
                                    feMgr.sendKey(Key.ENTER);
                                    break;
                                default:
                                    return;
                            }
                        } catch (IOException e) { ErrUtil.err(ctx, e); }
                          catch (IllegalArgumentException e) {
                              ErrUtil.reportErr(ctx, e); 
                          }

                        finish();

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
    public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_BACK) {
            showDialog(DIALOG_QUIT);
            return true;
        }
        return super.onKeyDown(code, event);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        setupViews();
    }

    private void setupViews() {

        setContentView(R.layout.music_remote);

        int size = ctrls.size();
        
        for (int i = 0; i < size; i++) {
            int id = ctrls.keyAt(i);
            final View v = findViewById(id);
            Key key = ctrls.get(id);
            v.setOnClickListener(this);
            v.setFocusable(false);
            v.setTag(key);
        }

        titleView  = (TextView)findViewById(R.id.title);
        detailView = (TextView)findViewById(R.id.details);
        artView    = (ImageView)findViewById(R.id.image);
        pBar       = (ProgressBar)findViewById(R.id.progress);

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

        String url = (Globals.haveServices() ? "/Content/" : "/Myth/") + //$NON-NLS-1$ //$NON-NLS-2$
            "GetAlbumArt?" + "Id=" + artid + //$NON-NLS-1$ //$NON-NLS-2$
            "&Width=" + artView.getWidth() + //$NON-NLS-1$
            "&Height=" + artView.getHeight(); //$NON-NLS-1$
        
        try {
            return Globals.getBackend().getImage(url);
        } catch (IOException e) {
            ErrUtil.logWarn(e);
            return null;
        }

    }

    @Override
    protected void onScrollDown() {
        try {
            feMgr.sendKey(Key.VOL_DOWN);
            feMgr.sendKey(Key.VOL_DOWN);
        } catch (IOException e) { ErrUtil.err(this, e); }
    }

    @Override
    protected void onScrollLeft() {
        try {
            feMgr.sendKey(Key.MUSIC_REWIND);
        } catch (IOException e) { ErrUtil.err(this, e); }
    }

    @Override
    protected void onScrollRight() {
        try {
            feMgr.sendKey(Key.MUSIC_FFWD);
        } catch (IOException e) { ErrUtil.err(this, e); }
    }

    @Override
    protected void onScrollUp() {
        try {
            feMgr.sendKey(Key.VOL_UP);
            feMgr.sendKey(Key.VOL_UP);
        } catch (IOException e) { ErrUtil.err(this, e); }
    }

    @Override
    protected void onFlingLeft() {
        try {
            feMgr.sendKey(Key.MUSIC_PREV);
        } catch (IOException e) { ErrUtil.err(this, e); }
    }

    @Override
    protected void onFlingRight() {
        try {
            feMgr.sendKey(Key.MUSIC_NEXT);
        } catch (IOException e) { ErrUtil.err(this, e); }
    }

    @Override
    protected void onTap() {
        try {
            feMgr.sendKey(Key.PAUSE);
        } catch (IOException e) { ErrUtil.err(this, e); }
    }

}
