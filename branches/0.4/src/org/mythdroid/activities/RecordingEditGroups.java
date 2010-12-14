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

import org.mythdroid.mdd.MDDManager;
import org.mythdroid.util.ErrUtil;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import android.widget.AdapterView.OnItemSelectedListener;

public class RecordingEditGroups extends MDActivity {
  
    private String[] recGroups = null;
    private String[] storGroups = null;
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.recording_edit_groups);
        try {
            recGroups  = MDDManager.getRecGroups(
                MythDroid.getBackend().addr
            );
            storGroups = MDDManager.getStorageGroups(
                MythDroid.getBackend().addr
            ); 
        } catch (Exception e) {
            ErrUtil.err(this, e);
            finish();
        }
        setViews();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        setResult(Activity.RESULT_OK);
    }
    
    private void setViews() {
       
        final Spinner recGroupSpinner = ((Spinner)findViewById(R.id.recedit_recGroup));
        final ArrayAdapter<String> recGroupAdapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, recGroups
        );
        recGroupAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );
        
        int pos = 0;
        
        if (RecordingEdit.recGroup != null)
            for (pos = 0; pos < recGroups.length; pos++)
                if (recGroups[pos].equals(RecordingEdit.recGroup))
                    break;
        
        recGroupSpinner.setAdapter(recGroupAdapter);
        recGroupSpinner.setSelection(pos);
        recGroupSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    RecordingEdit.recGroup = recGroups[pos];
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}
                
            }
        );
        
        final Spinner storGroupSpinner = ((Spinner)findViewById(R.id.recedit_storGroup));
        final ArrayAdapter<String> storGroupAdapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, storGroups
        );
        storGroupAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );
        
        pos = 0;
        
        if (RecordingEdit.storGroup != null)
            for (pos = 0; pos < storGroups.length; pos++)
                if (storGroups[pos].equals(RecordingEdit.storGroup))
                    break;
        
        storGroupSpinner.setAdapter(storGroupAdapter);
        storGroupSpinner.setSelection(pos);
        storGroupSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    RecordingEdit.storGroup = storGroups[pos];
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}
                
            }
        );
    }
}