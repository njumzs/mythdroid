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
import org.mythdroid.Enums.RecStatus;
import org.mythdroid.data.Program;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * MDActivity showing a Recording's details
 * Allows user to stop, delete or play the recording
 */
public class RecordingDetail extends MDActivity {

    final static private int DELETE_DIALOG = 0, STOP_DIALOG = 1;
    
    final private Context ctx = this;

    private boolean livetv = false, guide = false;
    private Button stop = null;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addHereToFrontendChooser(VideoPlayer.class);
        setContentView(R.layout.recording_detail);
        livetv = getIntent().getBooleanExtra(Extras.LIVETV.toString(), false);
        guide = getIntent().getBooleanExtra(Extras.GUIDE.toString(), false);
        setViews();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        setResult(Activity.RESULT_OK);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        setContentView(R.layout.recording_detail);
        setViews();
    }
    
    @Override
    public void onActivityResult(int reqCode, int resCode, Intent data) {
        if (!guide || resCode != Guide.REFRESH_NEEDED)
            return;
        setResult(Guide.REFRESH_NEEDED);
    }

    @Override
    public Dialog onCreateDialog(int id) {

        OnClickListener no = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        };

        switch (id) {

            case DELETE_DIALOG:
                return 
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.del_rec)
                        .setMessage(R.string.sure)
                        .setPositiveButton(R.string.yes,
                            new OnClickListener() {
                                @Override
                                public void onClick(
                                    DialogInterface dialog, int which) {
                                    try {
                                        MythDroid.beMgr.deleteRecording(
                                            MythDroid.curProg
                                        );
                                    } catch (IOException e) { 
                                        ErrUtil.err(ctx, e); 
                                    }
                                    setResult(Recordings.REFRESH_NEEDED);
                                    dialog.dismiss();
                                    finish();
                                }
                            }
                        )
                        .setNegativeButton(R.string.no, no)
                        .create();

            case STOP_DIALOG:
                return
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.stop_rec)
                        .setMessage(R.string.sure)
                        .setPositiveButton(R.string.yes,
                            new OnClickListener() {
                                @Override
                                public void onClick(
                                    DialogInterface dialog, int which
                                ) {
                                    try {
                                        MythDroid.beMgr.stopRecording(
                                            MythDroid.curProg
                                        );
                                    } catch (IOException e) {
                                        ErrUtil.err(ctx, e);
                                        dialog.dismiss();
                                        return;
                                    }
                                    setResult(Recordings.REFRESH_NEEDED);
                                    stop.setVisibility(View.GONE);
                                    MythDroid.curProg.Status = RecStatus.RECORDED;
                                    setViews();
                                    dialog.dismiss();
                                }
                            }
                        )
                        .setNegativeButton(R.string.no, no)
                        .create();
             default:
                 return super.onCreateDialog(id);

        }
        
    }

    private void setViews() {

        final Program prog = MythDroid.curProg;
        ((ImageView)findViewById(R.id.rec_thumb))
            .setImageBitmap(prog.previewImage());
        ((TextView)findViewById(R.id.rec_title)).setText(prog.Title);
        ((TextView)findViewById(R.id.rec_subtitle)).setText(prog.SubTitle);
        ((TextView)findViewById(R.id.rec_channel)).setText(prog.Channel);
        ((TextView)findViewById(R.id.rec_start)).setText(prog.startString());
        ((TextView)findViewById(R.id.rec_category))
            .setText(Messages.getString("RecordingDetail.0") + prog.Category); // type: //$NON-NLS-1$
        ((TextView)findViewById(R.id.rec_status))
            .setText(Messages.getString("RecordingDetail.1") + prog.Status.msg()); // status: //$NON-NLS-1$
        ((TextView)findViewById(R.id.rec_desc)).setText(prog.Description);
        
        final Button edit = (Button) findViewById(R.id.rec_edit);
        edit.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivityForResult(
                        new Intent().setClass(ctx, RecordingEdit.class), 0
                    );
                }
            }
        );

        if (livetv) return;
        
        switch (prog.Status) {

            case RECORDING:
                stop = (Button) findViewById(R.id.rec_stop);
                stop.setVisibility(View.VISIBLE);
                stop.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showDialog(STOP_DIALOG);
                        }
                    }
                );
                        
            //$FALL-THROUGH$
            case RECORDED:
            case CURRENT:

                if (!guide) {
                    final Button del = (Button) findViewById(R.id.rec_del);
                    del.setVisibility(View.VISIBLE);
                
                    del.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                showDialog(DELETE_DIALOG);
                            }
                        }
                    );
                }

                final Button play = (Button) findViewById(R.id.rec_play);
                
                play.setVisibility(View.VISIBLE);
                play.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startActivity(
                                new Intent().setClass(ctx, TVRemote.class)
                            );
                        }
                    }
                );
                play.setOnLongClickListener(
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            nextActivity = TVRemote.class;
                            showDialog(FRONTEND_CHOOSER);
                            return true;
                        }
                    }
                );

        }
    }
}
