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

import org.mythdroid.R;
import org.mythdroid.Enums.RecDupIn;
import org.mythdroid.Enums.RecDupMethod;
import org.mythdroid.Enums.RecEpiFilter;
import org.mythdroid.activities.MDFragmentActivity;
import org.mythdroid.fragments.RecEditFragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;

/** Edit scheduling options for a recording rule */
public class RecEditSchedFragment extends Fragment {
    
    private MDFragmentActivity activity = null;
    private View view = null;
    private RecEditFragment ref = null;
    private boolean enabled = false;
    
    private Spinner dupMatchSpinner = null, dupInSpinner = null,
                    epiFilterSpinner = null;

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle icicle
    ) {
        if (container == null) return null;
        activity = (MDFragmentActivity)getActivity();
        view = inflater.inflate(R.layout.recording_edit_sched, null, false);
        ref = (RecEditFragment)getFragmentManager()
                  .findFragmentByTag("RecEditFragment"); //$NON-NLS-1$
        setViews();
        return view;
    }

    private void setViews() {

        dupMatchSpinner = ((Spinner)view.findViewById(R.id.dupMatch));

        final ArrayAdapter<String> dupMatchAdapter = new ArrayAdapter<String>(
            activity, android.R.layout.simple_spinner_item
        );
        dupMatchAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );

        for (RecDupMethod method : RecDupMethod.values())
            dupMatchAdapter.add(method.msg());

        dupMatchSpinner.setAdapter(dupMatchAdapter);
        if (ref.recId == 0 || ref.dupMethod == null)
            dupMatchSpinner.setSelection(
                dupMatchAdapter.getPosition(RecDupMethod.SUBTHENDESC.msg())
            );
        else
            dupMatchSpinner.setSelection(ref.dupMethod.ordinal());
        dupMatchSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    ref.dupMethod = RecDupMethod.values()[pos];
                    ref.checkChildren();
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}

            }
        );

        dupInSpinner = ((Spinner)view.findViewById(R.id.dupIn));

        final ArrayAdapter<String> dupInAdapter = new ArrayAdapter<String>(
            activity, android.R.layout.simple_spinner_item
        );
        dupInAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );

        for (RecDupIn set : RecDupIn.values())
            dupInAdapter.add(set.msg());

        dupInSpinner.setAdapter(dupInAdapter);
        if (ref.recId == 0 || ref.dupIn == null)
            dupInSpinner.setSelection(
                dupInAdapter.getPosition(RecDupIn.ALL.msg())
            );
        else
            dupInSpinner.setSelection(ref.dupIn.ordinal());
        dupInSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    ref.dupIn = RecDupIn.values()[pos];
                    ref.checkChildren();
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}

            }
        );

        epiFilterSpinner = ((Spinner)view.findViewById(R.id.epiFilter));

        ArrayAdapter<String> epiFilterAdapter = new ArrayAdapter<String>(
            activity, android.R.layout.simple_spinner_item
        );
        epiFilterAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );

        for (RecEpiFilter filter : RecEpiFilter.values())
            epiFilterAdapter.add(filter.msg());

        epiFilterSpinner.setAdapter(epiFilterAdapter);
        if (ref.epiFilter == null)
            epiFilterSpinner.setSelection(RecEpiFilter.NONE.ordinal());
        else
            epiFilterSpinner.setSelection(ref.epiFilter.ordinal());
        epiFilterSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    ref.epiFilter = RecEpiFilter.values()[pos];
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
        if (dupMatchSpinner == null) return;
        dupMatchSpinner.setEnabled(enabled);
        dupInSpinner.setEnabled(enabled);
        epiFilterSpinner.setEnabled(enabled);
    }
    
    /**
     * Get the enabled state of this fragment's spinners
     * @return enabled state
     */
    public boolean isEnabled() {
        return enabled;
    }
}
