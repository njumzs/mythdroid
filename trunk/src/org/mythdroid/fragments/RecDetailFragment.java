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

package org.mythdroid.fragments;

import java.io.IOException;

import org.mythdroid.Enums.ArtworkType;
import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.Enums.Extras;
import org.mythdroid.Enums.RecStatus;
import org.mythdroid.activities.MDFragmentActivity;
import org.mythdroid.activities.Recordings;
import org.mythdroid.activities.VideoPlayer;
import org.mythdroid.data.Program;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;
import org.mythdroid.services.GuideService;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.views.PreviewImageView;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/**
 * Shows a Recording's details.
 * Allows user to stop, delete, edit or play the recording
 */
public class RecDetailFragment extends Fragment {
    
    private MDFragmentActivity activity = null;
    private RecDetailFragment rdf       = this;
    private Handler handler             = new Handler();
    private Program prog                = null;
    private Button stop                 = null;
    private View view                   = null;
    private int containerId;
    private boolean livetv   = false, guide    = false, refresh = false,
                    embedded = false, dualPane = false;
    
    private OnClickListener no = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
        }
    };
    
    private Runnable refreshRunnable = 
        new Runnable() {
        @Override
        public void run() {
            activity.showLoadingDialog();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
            try {
                Globals.curProg = prog = 
                    new GuideService(Globals.getBackend().addr)
                        .GetProgram(prog.ChanID, prog.StartTime);
            } catch (Exception e) { ErrUtil.postErr(activity, e); }
            activity.dismissLoadingDialog();
            handler.post(
                new Runnable() {
                    @Override
                    public void run() { setViews(); }
                }
            );
        }
    };
    
    /**
     * Create a new RecDetailFragment
     * @param livetv called from livetv?
     * @param guide called from the guide?
     * @return a new RecDetailFragment
     */
    public static RecDetailFragment newInstance(
        boolean livetv, boolean guide
    ) {
        RecDetailFragment rdf = new RecDetailFragment();
        Bundle icicle = new Bundle();
        icicle.putBoolean(Extras.LIVETV.toString(), livetv);
        icicle.putBoolean(Extras.GUIDE.toString(), guide);
        rdf.setArguments(icicle);
        return rdf;
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle icicle
    ) {
        if (container == null) return null;
        activity = (MDFragmentActivity)getActivity();
        view = inflater.inflate(R.layout.recording_detail, null, false);
        
        Bundle args = getArguments();
        if (args != null) {
            livetv = args.getBoolean(Extras.LIVETV.toString());
            guide  = args.getBoolean(Extras.GUIDE.toString());
        }
        
        View detailsFrame = activity.findViewById(R.id.recdetails);
        dualPane = detailsFrame != null && 
                   detailsFrame.getVisibility() == View.VISIBLE;
        embedded = activity.getClass().equals(Recordings.class);
        
        if (!embedded)
            activity.addHereToFrontendChooser(VideoPlayer.class);
        
        containerId = container.getId();
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (view == null) return;
        if (activity == null) activity = (MDFragmentActivity)getActivity();
        if ((prog = Globals.curProg) == null) {
            if (!dualPane) getFragmentManager().popBackStack(); 
            if (!embedded) activity.finish();
            return;
        }
        setImages();
        if (refresh && Globals.haveServices()) {
            refresh = false;
            Globals.runOnThreadPool(refreshRunnable);
        }
        else
            setViews();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (view == null) return;
        ((PreviewImageView)view.findViewById(R.id.image)).setImageBitmap(null);
        view.setBackgroundDrawable(null);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!embedded && activity != null) 
            activity.setResult(Activity.RESULT_OK);
    }
    
    /**
     * Tell RecDetailFragment to pull new program information via the services
     * api, if it can, when it resumes
     */
    public void refresh() {
        refresh = true;
    }

    /**
     * Setup all the views
     */
    public void setViews() {

        ((TextView)view.findViewById(R.id.title)).setText(prog.Title);
        ((TextView)view.findViewById(R.id.subtitle)).setText(prog.SubTitle);
        ((TextView)view.findViewById(R.id.channel)).setText(prog.Channel);
        ((TextView)view.findViewById(R.id.start)).setText(prog.startString());
        ((TextView)view.findViewById(R.id.category))
            .setText(
                Messages.getString("RecordingDetail.0") + prog.Category //$NON-NLS-1$
            ); 
        ((TextView)view.findViewById(R.id.status))
            .setText(
                Messages.getString("RecordingDetail.1") + prog.Status.msg() //$NON-NLS-1$
            ); 
        ((TextView)view.findViewById(R.id.desc)).setText(prog.Description);
        
        final Button edit = (Button)view.findViewById(R.id.edit);
        edit.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getFragmentManager().beginTransaction()
                        .replace(
                            containerId, new RecEditFragment(),
                            "RecEditFragment" //$NON-NLS-1$
                        )
                        .setTransition(
                            FragmentTransaction.TRANSIT_FRAGMENT_FADE
                        )
                        .addToBackStack(null)
                        .commitAllowingStateLoss();
                }
            }
        );

        // The rest only apply to non-livetv recordings
        if (livetv || prog.Status == null) return;

        switch (prog.Status) {

            case RECORDING:
                stop = (Button)view.findViewById(R.id.stop);
                stop.setVisibility(View.VISIBLE);
                stop.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            stopDialog sd = new stopDialog();
                            sd.setTargetFragment(rdf, 0);
                            sd.show(
                                getFragmentManager(), "stopDialog" //$NON-NLS-1$
                            );
                        }
                    }
                );

            //$FALL-THROUGH$
            case RECORDED:
            case CURRENT:

                if (!guide) {
                    final Button del = (Button)view.findViewById(R.id.del);
                    del.setVisibility(View.VISIBLE);
                    del.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                deleteDialog dd = new deleteDialog();
                                dd.setTargetFragment(rdf, 0);
                                dd.show(
                                    getFragmentManager(), "deleteDialog" //$NON-NLS-1$
                                );
                            }
                        }
                    );
                }

                final Button play = (Button)view.findViewById(R.id.play);
                play.setVisibility(View.VISIBLE);
                play.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!Globals.isCurrentFrontendHere()) {
                                startActivity(
                                    new Intent().setClass(
                                        activity, TVRemote.class
                                    )
                                );
                            } else {
                                final Intent intent = new Intent();
                                intent.setClass(activity, VideoPlayer.class);
                                startActivity(intent);
                            }
                        }
                    }
                );
                play.setOnLongClickListener(
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            activity.nextActivity = TVRemote.class;
                            activity.showDialog(Recordings.FRONTEND_CHOOSER);
                            return true;
                        }
                    }
                );

        }
    }
    
    /**
     * Get the Program under display
     * @return Program being displayed
     */
    public Program getProg() {
        return prog;
    }
    
    private void setImages() {
        
        final DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        final int height  = dm.heightPixels;
        final int width   = dm.widthPixels;
        
        final PreviewImageView iv = 
            ((PreviewImageView)view.findViewById(R.id.image));
        if (width > height)
            iv.setWidth((int)(width > height * 1.5 ? width / 3.5 : width / 3));
        else
            iv.setHeight(height / 4);
        
        final int artWidth     = (int)(width / 1.5);
        final ArtworkType type =
            (width > height) ? ArtworkType.fanart : ArtworkType.coverart;
        
        if (Globals.haveServices())
            Globals.runOnThreadPool(
                new Runnable() {
                    @Override
                    public void run() {
                        Bitmap bm = prog.getArtwork(type, artWidth, 0);
                        if (bm == null) return;
                        final BitmapDrawable d = new BitmapDrawable(
                            activity.getResources(), bm
                        );
                        d.setAlpha(65);
                        handler.post(
                            new Runnable() {
                                @Override
                                public void run() {
                                    if (view == null) return;
                                    view.setBackgroundDrawable(d);
                                }
                            }
                        );
                    }
                }
            );
        
        Globals.runOnThreadPool(
            new Runnable() {
                @Override
                public void run() {
                    final Bitmap bm = prog.previewImage();
                    if (bm == null) return;
                    handler.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                iv.setImageBitmap(bm);
                            }
                        }
                    );
                }
            }
        );
        
    }
    
    @SuppressWarnings("javadoc")
    public static class deleteDialog extends DialogFragment {
        
        private Runnable delete = new Runnable() {
            @Override
            public void run() {
                
                final MDFragmentActivity activity =
                    (MDFragmentActivity)getActivity();
                final RecDetailFragment rdf =
                    (RecDetailFragment)getTargetFragment();

                activity.showLoadingDialog();
                try {
                    Globals.getBackend().deleteRecording(rdf.prog);
                } catch (IOException e) {
                    ErrUtil.postErr(activity, e);
                    return;
                } finally {
                    activity.dismissLoadingDialog();
                }
                
                rdf.handler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (rdf.embedded) {
                                ((Recordings)activity).deleteRecording();
                                if (!rdf.dualPane)
                                    rdf.getFragmentManager().popBackStack();
                            }
                            else
                                activity.finish();
                        }
                    }
                );
                
            }
        };
        
        @Override
        public Dialog onCreateDialog(Bundle icicle) {
            final MDFragmentActivity activity =
                (MDFragmentActivity)getActivity();
            final RecDetailFragment rdf =
                (RecDetailFragment)getTargetFragment();
            return
                new AlertDialog.Builder(activity)
                    .setTitle(R.string.delRec)
                    .setMessage(R.string.sure)
                    .setPositiveButton(R.string.yes,
                        new OnClickListener() {
                            @Override
                            public void onClick(
                                DialogInterface dialog, int which
                            ) {
                                dialog.dismiss();
                                Globals.runOnThreadPool(delete);
                            }
                        }
                    )
                    .setNegativeButton(R.string.no, rdf.no)
                    .create();
        }
        
    }

    @SuppressWarnings("javadoc")
    public static class stopDialog extends DialogFragment {
        
        private Runnable stop = new Runnable() {
            @Override
            public void run() {
                
                final MDFragmentActivity activity =
                    (MDFragmentActivity)getActivity();
                final RecDetailFragment rdf =
                    (RecDetailFragment)getTargetFragment();
                
                activity.showLoadingDialog();
                
                try {
                    Globals.getBackend().stopRecording(rdf.prog);
                } catch (IOException e) {
                    ErrUtil.postErr(activity, e);
                    return;
                } finally {
                    activity.dismissLoadingDialog();
                }
                
                rdf.handler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            rdf.stop.setVisibility(View.GONE);
                            rdf.prog.Status = RecStatus.RECORDED;
                            rdf.setViews();
                            if (rdf.embedded)
                                ((Recordings)activity).invalidate();
                        }
                    }
                );
            }
            
        };
        
        @Override
        public Dialog onCreateDialog(Bundle icicle) {
            final MDFragmentActivity activity =
                (MDFragmentActivity)getActivity();
            final RecDetailFragment rdf =
                (RecDetailFragment)getTargetFragment();
            return
                new AlertDialog.Builder(activity)
                    .setTitle(R.string.stopRecording)
                    .setMessage(R.string.sure)
                    .setPositiveButton(R.string.yes,
                        new OnClickListener() {
                            @Override
                            public void onClick(
                                DialogInterface dialog, int which
                            ) {
                                dialog.dismiss();
                                Globals.runOnThreadPool(stop);
                            }
                        }
                    )
                    .setNegativeButton(R.string.no, rdf.no)
                    .create();
        }
        
    }
    
}



