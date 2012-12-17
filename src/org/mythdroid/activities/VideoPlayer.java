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

package org.mythdroid.activities;

import java.io.IOException;

import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.Enums.Extras;
import org.mythdroid.R.id;
import org.mythdroid.R.layout;
import org.mythdroid.backend.BackendManager;
import org.mythdroid.data.Program;
import org.mythdroid.data.StreamInfo;
import org.mythdroid.frontend.MovePlaybackHelper;
import org.mythdroid.frontend.OnFrontendReady;
import org.mythdroid.frontend.OnPlaybackMoved;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.resource.Messages;
import org.mythdroid.services.ContentService;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.util.LogUtil;
import org.mythdroid.views.MDMediaController;
import org.mythdroid.views.MDVideoView;
import org.mythdroid.views.MDVideoView.OnSeekListener;
import org.mythdroid.vlc.VLCRemote;

import android.R.drawable;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnDismissListener;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

/** MDActivity that displays streamed Video */
public class VideoPlayer extends MDActivity {
    
    final private static int DIALOG_QUALITY = 1, DIALOG_MOVE = 2;
    
    final private Context ctx        = this;
    final private Handler handler    = new Handler();
    
    private int            vb = 0, ab = 0, vwidth = 0, vheight = 0, retries = 0;
    private boolean        prepared       = false, doneSeek = false;
    private MDVideoView    videoView      = null;
    private BackendManager beMgr          = null;
    private VLCRemote      vlc            = null;
    private MediaPlayer    mplayer        = null;
    private Uri            url            = null;
    private ContentService contentService = null;
    private StreamInfo     streamInfo     = null;
    private int            seekTo         = 0;
    private MovePlaybackHelper moveHelper = null;
    private String         title          = null;
    
    // OnFrontendReady callback for "moveTo"
    final private OnFrontendReady onReady = new OnFrontendReady() {
        @Override
        public void onFrontendReady(String name) {
            handler.post(
                new Runnable() {
                    @Override
                    public void run() { showDialog(DIALOG_MOVE); }
                }
            );
        }
    };
    
    // OnPlaybackMoved callback for "moveTo"
    final private OnPlaybackMoved onMoved = new OnPlaybackMoved() {
        @Override
        public void onPlaybackMoved() { finish(); }
    };
    
    @SuppressWarnings("unused")
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(layout.video_player);
        videoView = (MDVideoView)findViewById(id.videoview);
        seekTo = getIntent().getIntExtra(Extras.SEEKTO.toString(), 0);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        showDialog(DIALOG_QUALITY);
        moveHelper = new MovePlaybackHelper(this, onReady, onMoved);
        // We don't use HLS yet
        if (Globals.haveServices() && false) 
            try {
                contentService = new ContentService(Globals.getBackend().addr);
            } catch (IOException e) { ErrUtil.err(this, e); }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            videoView.stopPlayback();
        } catch (IllegalArgumentException e) {}
        if (streamInfo == null) {
            if (beMgr != null)
                try {
                    MDDManager.stopStream(beMgr.addr);
                } catch (IOException e) { ErrUtil.err(ctx, e); }
            if (vlc != null)
                try {
                    vlc.disconnect();
                } catch (IOException e) { ErrUtil.err(ctx, e); }
        }
        else if (contentService != null)
            contentService.RemoveStream(streamInfo.id);
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            beMgr = Globals.getBackend();
        } catch (IOException e) { initError(e.getMessage()); }
    }

    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_QUALITY:    return createQualityDialog();
            case FRONTEND_CHOOSER:  return moveHelper.frontendChooserDialog();
            case DIALOG_MOVE: 
                return moveHelper.movePromptDialog(vlc, videoView); 
            default:                return super.onCreateDialog(id);
        }
    }
    
    @Override
    public void onPrepareDialog(int id, final Dialog dialog) {
        if (id == DIALOG_MOVE) {
            moveHelper.prepareMoveDialog(dialog);
            return;
        }
        super.onPrepareDialog(id, dialog);
    }
    
    private Dialog createQualityDialog() {

        final AlertDialog d = new AlertDialog.Builder(ctx)
            .setItems(R.array.streamingRates, null)
            .setIcon(drawable.ic_menu_upload_you_tube)
            .setTitle(R.string.streamQuality)
            .create();

        d.setOnDismissListener(
            new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (vb == 0) {
                        finish();
                        return;
                    }
                    startStream();
                }
            }
       );

        d.getListView().setOnItemClickListener(
            new OnItemClickListener() {
                @Override
                public void onItemClick(
                    AdapterView<?> av, View v, int pos, long id
                ) {

                    switch (pos) {
                        case 0: vb = 1024; ab = 160; break;
                        case 1: vb = 512;  ab = 128; break;
                        case 2: vb = 384;  ab = 96;  break;
                        case 3: vb = 192;  ab = 64;  break;
                    }
                    d.dismiss();
                }
            }
        );

        return d;
    }

    /** Ask for the stream to be started and start up our media player */
    private void startStream() {

        showLoadingDialog();

        DisplayMetrics dm = getResources().getDisplayMetrics();
        
        int w = (dm.widthPixels + 15) & ~0xf;
        if (w > 1280) w = 1280;

        try {
            Intent intent = getIntent();
            String path = null, sg = null;
            int id = -1;
            
            /* HLS is disabled in onCreate() -
               MythTV HLS is really low bitrate and seeking isn't supported */
            if (Globals.haveServices() && contentService != null) {

                if (intent.hasExtra(Extras.VIDEOID.toString())) {
                    id = intent.getIntExtra(Extras.VIDEOID.toString(), -1);
                    streamInfo = contentService.StreamFile(
                        id, w, 0, vb * 1000, ab * 1000
                    );
                }
                else {
                    Program prog = Globals.curProg;
                    if (prog == null) {
                        ErrUtil.report(Messages.getString("VideoPlayer.0")); //$NON-NLS-1$
                        initError(Messages.getString("VideoPlayer.0")); //$NON-NLS-1$
                        return;
                    }
                    title = prog.Title;
                    streamInfo = contentService.StreamFile(
                        prog.ChanID, Globals.utcFmt.format(prog.RecStartTime), 
                        w, 0, vb * 1000, ab * 1000
                    );
                }

            }
            // Stream via MDD / VLC
            else {
            
                if (intent.hasExtra(Extras.FILENAME.toString())) {
                    path = intent.getStringExtra(Extras.FILENAME.toString());
                    sg = "Default"; //$NON-NLS-1$
                    title = intent.getStringExtra(Extras.TITLE.toString());
                }
                else {
                    Program prog = Globals.curProg;
                    if (prog == null) {
                        ErrUtil.report(Messages.getString("VideoPlayer.0")); //$NON-NLS-1$
                        initError(Messages.getString("VideoPlayer.0")); //$NON-NLS-1$
                        return;
                    }
                    title = prog.Title;
                    path  = prog.Path;
                    if (prog.StorGroup != null)
                        sg = prog.StorGroup;
                    else
                        sg = MDDManager.getStorageGroup(beMgr.addr, prog.RecID);
                }
            
                int enc = Integer.valueOf(
                    PreferenceManager.getDefaultSharedPreferences(this)
                        .getString("streamComplexity", "0") //$NON-NLS-1$ //$NON-NLS-2$
                    );

                MDDManager.streamFile(
                    beMgr.addr, path, sg,
                    dm.widthPixels, dm.heightPixels, enc, vb, ab
                );
                
            }
        } catch (IOException e) {
            initError(e.getMessage());
            return;
        }
        
        // Give MDD a chance to exec vlc and vlc a chance to start streaming
        new Handler().postDelayed(
            new Runnable() {
                @Override
                public void run() {
                    playVideo();
                }
            }, 4000
        );

    }
    
    /** Get a VLC remote */
    private VLCRemote getVLC() {
        
        int attempts = 0;
        
        while (attempts < 8 && vlc == null)
            try {
                vlc = new VLCRemote(beMgr.addr);
            } catch (IOException e) { 
                attempts++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {}
            }
            
        if (vlc != null) return vlc;
        
        try {
            vlc = new VLCRemote(beMgr.addr);
        } catch (IOException e) { ErrUtil.err(this, e); }
      
        return vlc;
        
    }
    
    @Override
    public void onActivityResult(int a, int b, Intent data) {
        finish();
    }
    
    /** Prepare and startup the media player */
    private void playVideo() {
            
        if (streamInfo != null)
            url = Uri.parse(streamInfo.url);
        else {
            String sdpAddr = beMgr.addr;

            /* If the backend address is localhost, assume SSH port forwarding
               We must connect to the RTSP server directly otherwise the RTP
               goes astray, so use the public address for the backend if it's
               configured. This requires that port 5554/tcp is forwarded to the 
               backend */
            String sdpPublicAddr =
                PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("backendPublicAddr", null);  //$NON-NLS-1$
    
            if (
                   (sdpAddr.equals("127.0.0.1") || sdpAddr.equals("localhost")) //$NON-NLS-1$ //$NON-NLS-2$
                   && sdpPublicAddr != null
               )
               sdpAddr = sdpPublicAddr;
    
            url = Uri.parse("rtsp://" + sdpAddr + ":5554/stream"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        // Check to see if we're using an external player
        if (
            PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("streamExternalPlayer", false) //$NON-NLS-1$
        ) {
            Intent intent = new Intent(Intent.ACTION_VIEW, url);
            if (streamInfo != null)
                intent.setDataAndType(url, "video/x-mpegurl"); //$NON-NLS-1$
            dismissLoadingDialog();
            startActivityForResult(intent, 0);
            return;
        }
        
        videoView.setOnCompletionListener(
            new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (prepared)
                        finish();
                    else
                        initError(Messages.getString("VideoPlayer.3")); //$NON-NLS-1$
                }
            }
        );
        
        videoView.setOnErrorListener(
            new OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    LogUtil.warn("MediaPlayer err " + what + " extra " + extra); //$NON-NLS-1$ //$NON-NLS-2$
                    dismissLoadingDialog();
                    if (retries > 2) {
                        if (seekTo != 0) {
                            initError(Messages.getString("VideoPlayer.1")); //$NON-NLS-1$
                            return true;
                        }
                        return false;
                    }
                    // Try again
                    if (mp != null)  mp.reset();
                    retries++;
                    showLoadingDialog();
                    new Handler().postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                playVideo();
                            }
                        }, 2000
                    );
                    return true;
                }
            }
        );
        
        if (streamInfo == null)
            videoView.setVLC(getVLC());
        
        // Do we need to seek the stream?
        if (seekTo != 0 && !doneSeek) {
            if (vlc == null) { 
                initError(Messages.getString("VideoPlayer.2")); //$NON-NLS-1$
                return;
            }
            try {
                vlc.seek(seekTo * 1000);
            } catch (IOException e) { 
                initError(e.getMessage());
                return;
            }
            doneSeek = true;
        }
        
        // Add our custom media controller 
        MDMediaController mctrl = new MDMediaController(ctx);
        mctrl.setTitle(title);
        mctrl.setMoveToListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDialog(FRONTEND_CHOOSER);
                }
            }
        );
        videoView.setMediaController(mctrl);
        if (streamInfo == null)
            videoView.setOnSeekListener(
                new OnSeekListener() {
                    @Override
                    public void onSeek() {
                        showLoadingDialog();
                        mplayer.pause();
                        // Dump the buffer, no better way of doing it :(
                        mplayer.reset();
                        try {
                            mplayer.setDataSource(ctx, url);
                            mplayer.prepareAsync();
                        } catch (Exception e) {
                            ErrUtil.reportErr(ctx, e);
                            dismissLoadingDialog();
                            finish();
                        }
                    }
                }
            );
  
        videoView.setOnPreparedListener(
            new OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    prepared = true;
                    dismissLoadingDialog();
                    vwidth = mp.getVideoWidth();
                    vheight = mp.getVideoHeight();
                    videoView.setVideoSize(vwidth, vheight);
                    setupMediaPlayer(mp);
                    mplayer = mp;
                    videoView.start();
                }
            }
        );
        
        if (streamInfo != null)
            contentService.WaitForStream(streamInfo.id);

        videoView.setVideoURI(url);
        
    }
    
    /** Setup the MediaPlayer */
    private void setupMediaPlayer(final MediaPlayer mp) {
        
        mp.setOnVideoSizeChangedListener(
            new OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(
                    MediaPlayer mp, int width, int height
                ) {
                   videoView.setVideoSize(width, height);
                }
            }
        );
        
        mp.setOnInfoListener(
            new OnInfoListener() {
                @Override
                public boolean onInfo(
                    MediaPlayer mp, int what, int extra
                ) {
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING)
                        LogUtil.warn("Video decode is too slow"); //$NON-NLS-1$
                    return false;
                }
            }
        );
        
    }
    
    private void initError(String e) {
        if (e != null)
            ErrUtil.err(this, e);
        dismissLoadingDialog();
        if (seekTo != 0) {
            moveHelper.abortMove();
            return;
        }
        finish();
    }
    
}
