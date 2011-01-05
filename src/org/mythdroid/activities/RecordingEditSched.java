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

import org.mythdroid.R;
import org.mythdroid.Enums.RecDupIn;
import org.mythdroid.Enums.RecDupMethod;
import org.mythdroid.Enums.RecEpiFilter;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;

/** Edit scheduling options for a recording rule */
public class RecordingEditSched extends MDActivity {
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.recording_edit_sched);
        setViews();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        setResult(Activity.RESULT_OK);
    }
    
    private void setViews() {
        
        final Spinner dupMatchSpinner = 
            ((Spinner)findViewById(R.id.recedit_dupMatch));
        
        final ArrayAdapter<String> dupMatchAdapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item
        );
        dupMatchAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );
        
        for (RecDupMethod method : RecDupMethod.values())
            dupMatchAdapter.add(method.msg());
        
        dupMatchSpinner.setAdapter(dupMatchAdapter);
        dupMatchSpinner.setSelection(RecordingEdit.dupMethod.ordinal());
        dupMatchSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    RecordingEdit.dupMethod = RecDupMethod.values()[pos];
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}
                
            }
        );
        
        final Spinner dupInSpinner = 
            ((Spinner)findViewById(R.id.recedit_dupIn));
        
        final ArrayAdapter<String> dupInAdapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item
        );
        dupInAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );
        
        for (RecDupIn set : RecDupIn.values())
            dupInAdapter.add(set.msg());
        
        dupInSpinner.setAdapter(dupInAdapter);
        dupInSpinner.setSelection(RecordingEdit.dupIn.ordinal());
        dupInSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    RecordingEdit.dupIn = RecDupIn.values()[pos];
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}
                
            }
        );
        
        final Spinner epiFilterSpinner = 
            ((Spinner)findViewById(R.id.recedit_epiFilter));
        
        ArrayAdapter<String> epiFilterAdapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item
        );
        epiFilterAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );
        
        for (RecEpiFilter filter : RecEpiFilter.values())
            epiFilterAdapter.add(filter.msg());
        
        epiFilterSpinner.setAdapter(epiFilterAdapter);
        epiFilterSpinner.setSelection(RecordingEdit.epiFilter.ordinal());
        epiFilterSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    RecordingEdit.epiFilter = RecEpiFilter.values()[pos];
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}
                
            }
        );
    }
}