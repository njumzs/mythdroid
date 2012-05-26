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

import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.Enums.Extras;
import org.mythdroid.Enums.RecStatus;
import org.mythdroid.activities.Guide;
import org.mythdroid.activities.MDFragmentActivity;
import org.mythdroid.activities.Recordings;
import org.mythdroid.activities.VideoPlayer;
import org.mythdroid.data.Program;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Shows a Recording's details.
 * Allows user to stop, delete or play the recording
 */
public class RecDetailFragment extends Fragment {
    
    private MDFragmentActivity activity = null;
    private Program prog                = null;
    private Button stop                 = null;
    private View view                   = null;
    private int containerId;
    private boolean livetv   = false, guide    = false,
                    embedded = false, dualPane = false;
    
    private OnClickListener no = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
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
        
        View detailsFrame = getActivity().findViewById(R.id.recdetails);
        dualPane = detailsFrame != null && 
                   detailsFrame.getVisibility() == View.VISIBLE;
        embedded = 
            activity.getClass().getName().endsWith("Recordings"); //$NON-NLS-1$
        if (!embedded)
            activity.addHereToFrontendChooser(VideoPlayer.class);
        
        containerId = container.getId();
        
        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!embedded && activity != null) 
            activity.setResult(Activity.RESULT_OK);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity == null) activity = (MDFragmentActivity)getActivity();
        if ((prog = Globals.curProg) == null) {
            if (!dualPane) getFragmentManager().popBackStack(); 
            if (!embedded) activity.finish();
            return;
        }
        setViews();
    }

    @Override
    public void onActivityResult(int reqCode, int resCode, Intent data) {
        if (!guide || resCode != Guide.REFRESH_NEEDED)
            return;
        activity.setResult(Guide.REFRESH_NEEDED);
    }

    /**
     * Setup all the views
     */
    public void setViews() {

        ((ImageView)view.findViewById(R.id.image))
            .setImageBitmap(prog.previewImage());
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
                    FragmentTransaction ft = 
                        getFragmentManager().beginTransaction();
                    ft.replace(containerId, new RecEditFragment());
                    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                    ft.addToBackStack(null);
                    ft.commitAllowingStateLoss();
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
                            new stopDialog().show(
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
                                new deleteDialog().show(
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
    
    private class deleteDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle icicle) {
            return
                new AlertDialog.Builder(activity)
                    .setTitle(R.string.delRec)
                    .setMessage(R.string.sure)
                    .setPositiveButton(R.string.yes,
                        new OnClickListener() {
                            @Override
                            public void onClick(
                                DialogInterface dialog, int which) {
                                try {
                                    Globals.getBackend().deleteRecording(prog);
                                } catch (IOException e) {
                                    ErrUtil.err(activity, e);
                                    dialog.dismiss();
                                    return;
                                }
                                dialog.dismiss();
                                if (embedded) {
                                    ((Recordings)activity).deleteRecording();
                                    if (!dualPane)
                                        getFragmentManager()
                                            .popBackStack();
                                }
                                else
                                    activity.finish();
                            }
                        }
                    )
                    .setNegativeButton(R.string.no, no)
                    .create();
        }
        
    }

    private class stopDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle icicle) {
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
                                try {
                                    Globals.getBackend().stopRecording(prog);
                                } catch (IOException e) {
                                    ErrUtil.err(getActivity(), e);
                                    dialog.dismiss();
                                    return;
                                }
                                stop.setVisibility(View.GONE);
                                prog.Status = RecStatus.RECORDED;
                                setViews();
                                if (embedded)
                                    ((Recordings)activity).invalidate();
                                dialog.dismiss();
                            }
                        }
                    )
                    .setNegativeButton(R.string.no, no)
                    .create();
        }
    }
    
}



