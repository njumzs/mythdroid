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

import org.mythdroid.R;
import org.mythdroid.Enums.Extras;
import org.mythdroid.R.id;
import org.mythdroid.R.layout;
import org.mythdroid.backend.BackendManager;
import org.mythdroid.data.Program;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.util.ErrUtil;

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
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.AdapterView;
import android.widget.VideoView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * MDActivity displays streamed Video
 */
public class VideoPlayer extends MDActivity {
    
    final private Context ctx = this;
    final private int DIALOG_QUALITY = 1;
    private int vb = 0, ab = 0;
    private VideoView videoView = null;
    private BackendManager beMgr = null;
     
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(layout.video_player);
        videoView = (VideoView)findViewById(id.videoview);
        showDialog(DIALOG_QUALITY);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        videoView.stopPlayback();
        try {
            MDDManager.stopStream(beMgr.addr);
        } catch (IOException e) {
            ErrUtil.err(ctx, e);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        try {
            beMgr = MythDroid.getBackend();
        } catch (Exception e) {
            ErrUtil.err(this, e);
        }
    }
    
    @Override
    public Dialog onCreateDialog(int id) {

        switch (id) {
            
            case DIALOG_QUALITY:
                return createQualityDialog();
            default:
                return super.onCreateDialog(id);

        }
        
    }
   
    private Dialog createQualityDialog() {
        
        final AlertDialog d = new AlertDialog.Builder(ctx)
            .setItems(R.array.streamingRates, null)
            .setIcon(drawable.ic_menu_upload_you_tube)
            .setTitle(R.string.stream_quality)
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
                        case 0:
                            vb = 448;
                            ab = 128;
                            break;
                        case 1:
                            vb = 320;
                            ab = 96;
                            break;
                        case 2:
                            vb = 192;
                            ab = 64;
                            break;
                    }
                    d.dismiss();
                }
            }
        );
        
        return d;
    }
    
    private void startStream() {
        
        showDialog(DIALOG_LOAD);
        
        DisplayMetrics dm = getResources().getDisplayMetrics();
        
        try {
            Intent intent = getIntent();
            String path = null, sg = null;
            
            if (intent.hasExtra(Extras.FILENAME.toString())) { 
                path = getIntent().getStringExtra(Extras.FILENAME.toString());
                sg = "Default"; //$NON-NLS-1$
            }
            else {
                Program prog = MythDroid.curProg;
                path = prog.Path;
                if (prog.StorGroup != null)
                    sg = prog.StorGroup;
                else
                    sg = MDDManager.getStorageGroup(beMgr.addr, prog.RecID);
            }
                
            MDDManager.streamFile(
                beMgr.addr, path, sg, 
                dm.widthPixels, dm.heightPixels, vb, ab
            );
        } catch (IOException e) {
            ErrUtil.err(this, e);
            finish();
        }
        
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {}
        
        String sdpAddr = beMgr.addr;

        // If the backend address is localhost, assume SSH port forwarding
        // We must connect to the RTSP server directly otherwise the RTP
        // goes astray, so use the public address for the backend if it's
        // configured. This requires that port 5554/tcp is forwarded to the backend
        String sdpPublicAddr = 
            PreferenceManager.getDefaultSharedPreferences(this)
                .getString("backendPublicAddr", null);  //$NON-NLS-1$
        
        if (
               (sdpAddr.equals("127.0.0.1") || sdpAddr.equals("localhost")) && //$NON-NLS-1$ //$NON-NLS-2$
                sdpPublicAddr != null
           )
           sdpAddr = sdpPublicAddr;
        
        videoView.setVideoURI(
            Uri.parse("rtsp://" + sdpAddr + ":5554/stream") //$NON-NLS-1$ //$NON-NLS-2$
        );
        
        videoView.setOnPreparedListener(
            new OnPreparedListener(){
                @Override
                public void onPrepared(MediaPlayer mp) {
                    dismissDialog(DIALOG_LOAD);
                }
            }
        );
        
        videoView.setOnCompletionListener(
            new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    finish();
                }
            }
        );
        
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
 
        videoView.start();
    }
}