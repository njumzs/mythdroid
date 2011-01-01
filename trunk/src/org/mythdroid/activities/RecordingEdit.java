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

import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.Enums.RecDupIn;
import org.mythdroid.Enums.RecDupMethod;
import org.mythdroid.Enums.RecEpiFilter;
import org.mythdroid.Enums.RecType;
import org.mythdroid.backend.BackendManager;
import org.mythdroid.data.Program;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

public class RecordingEdit extends MDActivity {
    
    static public RecDupMethod  dupMethod;
    static public RecDupIn      dupIn;
    static public RecEpiFilter  epiFilter;
    static public String        recGroup;
    static public String        storGroup; 
    
    private Program prog         = null;
    private BackendManager beMgr = null;
    private RecType type;
    private int prio;
    
    private Button  save, schedOptions, groupOptions;
    private Spinner prioSpinner;
    private boolean childrenModified = false, modified = false;
    private String  updates = ""; //$NON-NLS-1$
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.recording_edit);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
    
    @Override
    public void onResume() {
        
        super.onResume();
        try {
            beMgr = Globals.getBackend();
        } catch (Exception e) {
            ErrUtil.err(this, e);
        }
        
        try {
            new MDDManager(beMgr.addr);
        } catch (IOException e) {
            ErrUtil.err(
                this, Messages.getString("RecordingEdit.2") + beMgr.addr //$NON-NLS-1$
            ); 
            finish();
            return;
        }
        
        prog = Globals.curProg;
        if (prog == null) {
            finish();
            return;
        }
        
        type = prog.Type;
        prio = prog.RecPrio;
        
        dupMethod = prog.DupMethod;
        dupIn = prog.DupIn;
        epiFilter = prog.EpiFilter;
        recGroup = prog.RecGroup;
        storGroup = prog.StorGroup;
        
        if (prog.RecID != -1) {
            try {
                type = prog.Type = 
                    MDDManager.getRecType(beMgr.addr, prog.RecID);
                
                if (storGroup == null) {
                    storGroup = prog.StorGroup =
                        MDDManager.getStorageGroup(beMgr.addr, prog.RecID);
                }
            } catch (IOException e) {
                ErrUtil.err(this, e);
                finish();
            }
        }
        
        setViews();
        
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        setContentView(R.layout.recording_edit);
        setViews();
    }
    
    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data) {
        super.onActivityResult(reqCode, resCode, data);
        if (prog == null || recGroup == null || storGroup == null) return;
        if (
            dupMethod == prog.DupMethod && dupIn == prog.DupIn && 
            epiFilter == prog.EpiFilter && recGroup.equals(prog.RecGroup) &&
            storGroup.equals(prog.StorGroup)
        ) 
            childrenModified = false;
        else
            childrenModified = true;
        updateSaveEnabled();
    }
    
    private void setViews() {
       
        ((TextView)findViewById(R.id.recedit_title)).setText(prog.Title);
        ((TextView)findViewById(R.id.recedit_subtitle)).setText(prog.SubTitle);
        ((TextView)findViewById(R.id.recedit_channel)).setText(prog.Channel);
        ((TextView)findViewById(R.id.recedit_start))
            .setText(prog.startString());
        
        final Spinner typeSpinner = ((Spinner)findViewById(R.id.recedit_type));
        final ArrayAdapter<String> typeAdapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item
        );
        typeAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );
        
        for (RecType type : RecType.values()) {
            if (type == RecType.DONT || type == RecType.OVERRIDE)
                continue;
            typeAdapter.add(type.msg());
        }
        
        typeSpinner.setAdapter(typeAdapter);
        typeSpinner.setSelection(type.ordinal());
        typeSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    type = RecType.values()[pos];
                    if (type != prog.Type || prio != prog.RecPrio)
                        modified = true;
                    else
                        modified = false;
                    updateSaveEnabled();
                    
                    if (type == RecType.NOT) {
                        prioSpinner.setEnabled(false);
                        schedOptions.setEnabled(false);
                        groupOptions.setEnabled(false);
                    }
                    else {
                        prioSpinner.setEnabled(true);
                        schedOptions.setEnabled(true);
                        groupOptions.setEnabled(true);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}
                
            }
        );
        
        prioSpinner = ((Spinner)findViewById(R.id.recedit_prio));
        
        final String[] prios = new String[21];
        for (int i = -10, j = 0; i < 11; i++)
            prios[j++] = String.valueOf(i);
        
        final ArrayAdapter<String> prioAdapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, prios
        );
        prioAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );
        
        prioSpinner.setAdapter(prioAdapter);
        prioSpinner.setSelection(prio + 10);
        prioSpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int pos, long id
                ) {
                    prio = pos - 10;
                    if (type != prog.Type || prio != prog.RecPrio)
                        modified = true;
                    else
                        modified = false;
                    updateSaveEnabled();
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}
                
            }
        );
        prioSpinner.setEnabled(type == RecType.NOT ? false : true);

        schedOptions = (Button)findViewById(R.id.recedit_schedoptions);
        schedOptions.setOnClickListener(
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivityForResult(
                        new Intent().setClass(ctx, RecordingEditSched.class), 0
                    );
                }
            }
        );
        schedOptions.setEnabled(type == RecType.NOT ? false : true);
        
        groupOptions = (Button)findViewById(R.id.recedit_groupoptions);
        groupOptions.setOnClickListener(
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivityForResult(
                        new Intent().setClass(ctx, RecordingEditGroups.class), 0
                    );
                }
            }
        );
        groupOptions.setEnabled(type == RecType.NOT ? false : true);
        
        save = (Button)findViewById(R.id.recedit_save);
        save.setOnClickListener(
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    doSave();
                }
            }
        );
        save.setEnabled(false);
        
    }
    
    private void updateSaveEnabled() {
        if (modified || childrenModified)
            save.setEnabled(true);
        else
            save.setEnabled(false);
    }
    
    private void doSave() {
        
        int recid = -1;
        
        if (modified) {
            if (prio != prog.RecPrio) {
                addUpdate("recpriority = " + prio); //$NON-NLS-1$
                prog.RecPrio = prio;
            }
            if (type != prog.Type) {
                
                addUpdate("type = " + type.value()); //$NON-NLS-1$
                prog.Type = type;
                
                if (type == RecType.NOT && prog.RecID != -1) {
                    try {
                        MDDManager.deleteRecording(beMgr.addr, prog.RecID);
                        beMgr.reschedule(prog.RecID);
                    } catch (IOException e) {
                        ErrUtil.err(ctx, e);
                    }
                    prog.RecID = -1;
                    setResult(Guide.REFRESH_NEEDED);
                    finish();
                    return;
                }
                
                addUpdate("findday = " + prog.StartTime.getDay()); //$NON-NLS-1$
                addUpdate(
                    "findtime = '" +  //$NON-NLS-1$
                        prog.StartTime.getHours() + ":" + //$NON-NLS-1$
                        prog.StartTime.getMinutes() + ":" + //$NON-NLS-1$
                        prog.StartTime.getSeconds() + "'" //$NON-NLS-1$
                );
                addUpdate(
                    "findid = " +  //$NON-NLS-1$
                        ((prog.StartTime.getTime() / (1000*60*60*24)) + 719528)
                );

            }
        }
        
        if (childrenModified) {
            if (dupMethod != prog.DupMethod) {
                addUpdate("dupmethod = " + dupMethod.value()); //$NON-NLS-1$
                prog.DupMethod = dupMethod;
            }
            if (dupIn != prog.DupIn || epiFilter != prog.EpiFilter) {
                addUpdate("dupin = " + (dupIn.value() | epiFilter.value())); //$NON-NLS-1$
                prog.DupIn = dupIn;
            }
            if (recGroup != prog.RecGroup) {
                addUpdate("recgroup = '" + recGroup + "'"); //$NON-NLS-1$ //$NON-NLS-2$
                prog.RecGroup = recGroup;
            }
            if (storGroup != prog.StorGroup) {
                addUpdate("storagegroup = '" + storGroup + "'"); //$NON-NLS-1$ //$NON-NLS-2$
                prog.StorGroup = storGroup;
            }
        }
        
        if (updates.length() == 0) {
            finish();
            return;
        }
        
        try {
            recid = MDDManager.updateRecording(beMgr.addr, prog, updates); 
        } catch (IOException e) {
            ErrUtil.err(ctx, e);
        }
        
        prog.RecID = recid;
        
        if (recid == -1) {
            ErrUtil.err(this, Messages.getString("RecordingEdit.0")); //$NON-NLS-1$
            finish();
            return;
        }
        
        try {
            beMgr.reschedule(recid);
        } catch (IOException e) {
            ErrUtil.err(this, e);
        }
        
        setResult(Guide.REFRESH_NEEDED);
        finish();
 
    }
    
    private void addUpdate(String update) {
        if (updates.length() > 0)
            updates += ", "; //$NON-NLS-1$
        updates += update;
    }
}