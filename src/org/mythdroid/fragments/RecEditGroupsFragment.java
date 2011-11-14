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
import org.mythdroid.fragments.RecEditFragment;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;

import android.app.Activity;
import android.os.Bundle;
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
    private boolean embedded = false;
    private View view = null;
    private RecEditFragment ref = null;
    
    private Spinner recGroupSpinner = null, storGroupSpinner = null;
    
    private String[] recGroups = null;
    private String[] storGroups = null;
    
    /**
     * Instantiate a new RecEditGroupsFragment
     * @param id - id of parent RecEditFragment
     * @return new instance
     */
    public static RecEditGroupsFragment newInstance(int id) {
        RecEditGroupsFragment regf = new RecEditGroupsFragment();
        Bundle args = new Bundle();
        args.putInt("RecEditFragId", id); //$NON-NLS-1$
        regf.setArguments(args);
        return regf;
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle icicle
    ) {
        if (container == null) return null;
        activity = (MDFragmentActivity)getActivity();
        embedded = 
            activity.getClass().getName().endsWith("Recordings"); //$NON-NLS-1$
        view = inflater.inflate(R.layout.recording_edit_groups, null, false);
        
        Bundle args = getArguments();
        if (args != null) {
            int id = args.getInt("RecEditFragId", -1); //$NON-NLS-1$
            if (id != -1)
                ref = (RecEditFragment)getFragmentManager()
                          .findFragmentById(id);
        }
        
        try {
            recGroups  = MDDManager.getRecGroups(
                Globals.getBackend().addr
            );
            storGroups = MDDManager.getStorageGroups(
                Globals.getBackend().addr
            );
        } catch (IOException e) {
            ErrUtil.err(activity, e);
            done();
        }

        if (recGroups == null || storGroups == null) {
            ErrUtil.err(activity, Messages.getString("RecordingEditGroups.0")); //$NON-NLS-1$
            done();
        }
        
        setViews();
        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        activity.setResult(Activity.RESULT_OK);
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

        int pos = 0;

        if (RecEditFragment.recGroup != null)
            for (pos = 0; pos < recGroups.length; pos++)
                if (recGroups[pos].equals(RecEditFragment.recGroup))
                    break;

        if (pos >= recGroups.length)
            pos = 0;
        
        recGroupSpinner.setAdapter(recGroupAdapter);
        recGroupSpinner.setSelection(pos);
        recGroupSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    RecEditFragment.recGroup = recGroups[pos];
                    if (ref != null)
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

        if (RecEditFragment.storGroup != null)
            for (pos = 0; pos < storGroups.length; pos++)
                if (storGroups[pos].equals(RecEditFragment.storGroup))
                    break;

        if (pos >= storGroups.length)
            pos = 0;
        
        storGroupSpinner.setAdapter(storGroupAdapter);
        storGroupSpinner.setSelection(pos);
        storGroupSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    RecEditFragment.storGroup = storGroups[pos];
                    if (ref != null)
                        ref.checkChildren();
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}

            }
        );
    }
    
    /**
     * Set the enabled state of this fragment's spinners 
     * @param enabled new state
     */
    public void setEnabled(boolean enabled) {
        if (recGroupSpinner == null) return;
        recGroupSpinner.setEnabled(enabled);
        storGroupSpinner.setEnabled(enabled);
    }
    
    private void done() {
        if (embedded) getFragmentManager().popBackStack();
        else activity.finish();
    }
    
}
