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

/** Edit scheduling options for a recording rule */
public class RecEditSchedFragment extends Fragment {
    
    private MDFragmentActivity activity = null;
    private View view = null;
    private RecEditFragment ref = null;
    
    private Spinner dupMatchSpinner = null, dupInSpinner = null,
                    epiFilterSpinner = null;

    /**
     * Instantiate a new RecEditSechedFragment
     * @param id - id of parent RecEditFragment
     * @return new instance
     */
    public static RecEditSchedFragment newInstance(int id) {
        RecEditSchedFragment resf = new RecEditSchedFragment();
        Bundle args = new Bundle();
        args.putInt("RecEditFragId", id); //$NON-NLS-1$
        resf.setArguments(args);
        return resf;
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle icicle
    ) {
        if (container == null) return null;
        activity = (MDFragmentActivity)getActivity();
        view = inflater.inflate(R.layout.recording_edit_sched, null, false);
        Bundle args = getArguments();
        if (args != null) {
            int id = args.getInt("RecEditFragId", -1); //$NON-NLS-1$
            if (id != -1)
                ref = (RecEditFragment)getFragmentManager()
                          .findFragmentById(id);
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

        dupMatchSpinner =
            ((Spinner)view.findViewById(R.id.recedit_dupMatch));

        final ArrayAdapter<String> dupMatchAdapter = new ArrayAdapter<String>(
            activity, android.R.layout.simple_spinner_item
        );
        dupMatchAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );

        for (RecDupMethod method : RecDupMethod.values())
            dupMatchAdapter.add(method.msg());

        dupMatchSpinner.setAdapter(dupMatchAdapter);
        dupMatchSpinner.setSelection(RecEditFragment.dupMethod.ordinal());
        dupMatchSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    RecEditFragment.dupMethod = RecDupMethod.values()[pos];
                    if (ref != null)
                        ref.checkChildren();
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}

            }
        );

        dupInSpinner =
            ((Spinner)view.findViewById(R.id.recedit_dupIn));

        final ArrayAdapter<String> dupInAdapter = new ArrayAdapter<String>(
            activity, android.R.layout.simple_spinner_item
        );
        dupInAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );

        for (RecDupIn set : RecDupIn.values())
            dupInAdapter.add(set.msg());

        dupInSpinner.setAdapter(dupInAdapter);
        dupInSpinner.setSelection(RecEditFragment.dupIn.ordinal());
        dupInSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    RecEditFragment.dupIn = RecDupIn.values()[pos];
                    if (ref != null)
                        ref.checkChildren();
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}

            }
        );

        epiFilterSpinner =
            ((Spinner)view.findViewById(R.id.recedit_epiFilter));

        ArrayAdapter<String> epiFilterAdapter = new ArrayAdapter<String>(
            activity, android.R.layout.simple_spinner_item
        );
        epiFilterAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );

        for (RecEpiFilter filter : RecEpiFilter.values())
            epiFilterAdapter.add(filter.msg());

        epiFilterSpinner.setAdapter(epiFilterAdapter);
        epiFilterSpinner.setSelection(RecEditFragment.epiFilter.ordinal());
        epiFilterSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    RecEditFragment.epiFilter = RecEpiFilter.values()[pos];
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
        if (dupMatchSpinner == null) return;
        dupMatchSpinner.setEnabled(enabled);
        dupInSpinner.setEnabled(enabled);
        epiFilterSpinner.setEnabled(enabled);
    }
}
