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

import org.mythdroid.activities.MDFragmentActivity;
import org.mythdroid.activities.Recordings;
import org.mythdroid.fragments.RecEditFragment;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.resource.Messages;
import org.mythdroid.services.MythService;
import org.mythdroid.util.ErrUtil;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import android.widget.AdapterView.OnItemSelectedListener;

/** Edit recording and storage groups for a recording rule */
public class RecEditGroupsFragment extends Fragment {

    private MDFragmentActivity activity = null;
    private View view                   = null;
    private RecEditFragment ref         = null;
    private Handler handler             = new Handler();
    private String[] recGroups          = null;
    private String[] storGroups         = null;


    private boolean embedded = false, enabled = false;
    private Spinner recGroupSpinner = null, storGroupSpinner = null;
    

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle icicle
    ) {
        if (container == null) return null;
        activity = (MDFragmentActivity)getActivity();
        embedded = activity.getClass().equals(Recordings.class);
        view = inflater.inflate(R.layout.recording_edit_groups, null, false);
        ref = (RecEditFragment)getFragmentManager()
                  .findFragmentByTag("RecEditFragment"); //$NON-NLS-1$
        
        Globals.runOnThreadPool(
            new Runnable() {
                @Override
                public void run() {
                    activity.showLoadingDialog();
                    try {
                        if (!Globals.haveServices()) {
                            recGroups  = MDDManager.getRecGroups(
                                Globals.getBackend().addr
                            );
                            storGroups = MDDManager.getStorageGroups(
                                Globals.getBackend().addr
                            );
                        }
                        else {
                            MythService myth =
                                new MythService(Globals.getBackend().addr);
                            recGroups = new String[1];
                            recGroups[0] =
                                Messages.getString("RecEditGroupsFragment.0"); //$NON-NLS-1$
                            storGroups = myth.getStorageGroups();
                        }
                    } catch (IOException e) {
                        ErrUtil.postErr(activity, e);
                        done();
                        return;
                    } finally {
                        activity.dismissLoadingDialog();
                    }
                    if (recGroups == null || storGroups == null) {
                        ErrUtil.postErr(
                            activity,
                            Messages.getString("RecordingEditGroups.0") //$NON-NLS-1$
                        );
                        done();
                        return;
                    }
                    handler.post(
                        new Runnable() {
                            @Override
                            public void run() { setViews(); }
                            
                        }
                    );
                }
            }
        );
        return view;
    }

    private void setViews() {

        if (recGroups == null || storGroups == null)
            return;
        
        recGroupSpinner = ((Spinner)view.findViewById(R.id.recGroup));
        final ArrayAdapter<String> recGroupAdapter = new ArrayAdapter<String>(
            activity, android.R.layout.simple_spinner_item, recGroups
        );
        recGroupAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );

        int pos = 0, defaultPos =-1;

        if (ref.recGroup != null)
            for (pos = 0; pos < recGroups.length; pos++) {
                if (recGroups[pos].equals(ref.recGroup))
                    break;
                if (recGroups[pos].equals("Default")) //$NON-NLS-1$
                    defaultPos = pos;
            }

        if (pos >= recGroups.length)
            pos = defaultPos != -1 ? defaultPos : 0;
        
        recGroupSpinner.setAdapter(recGroupAdapter);
        recGroupSpinner.setSelection(pos);
        recGroupSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    ref.recGroup = recGroups[pos];
                    ref.checkChildren();
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}

            }
        );

        storGroupSpinner = ((Spinner)view.findViewById(R.id.storGroup));
        final ArrayAdapter<String> storGroupAdapter = new ArrayAdapter<String>(
            activity, android.R.layout.simple_spinner_item, storGroups
        );
        storGroupAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );

        pos = 0; 
        defaultPos = -1;

        if (ref.storGroup != null)
            for (pos = 0; pos < storGroups.length; pos++) {
                if (storGroups[pos].equals(ref.storGroup))
                    break;
                if (storGroups[pos].equals("Default")) //$NON-NLS-1$
                    defaultPos = pos;
            }
        
        if (pos >= storGroups.length)
            pos = defaultPos != -1 ? defaultPos : 0;
        
        storGroupSpinner.setAdapter(storGroupAdapter);
        storGroupSpinner.setSelection(pos);
        storGroupSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    ref.storGroup = storGroups[pos];
                    ref.checkChildren();
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}

            }
        );
    }
    
    /**
     * Set the enabled state of this fragment's spinners 
     * @param enable new state
     */
    public void setEnabled(boolean enable) {
        enabled = enable;
        if (recGroupSpinner == null) return;
        recGroupSpinner.setEnabled(enabled);
        storGroupSpinner.setEnabled(enabled);
    }
    
    /**
     * Get the enabled state of this fragment's spinners
     * @return enabled state
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    private void done() {
        if (embedded) getFragmentManager().popBackStack();
        else activity.finish();
    }
    
}
