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
import org.mythdroid.Enums.RecDupIn;
import org.mythdroid.Enums.RecDupMethod;
import org.mythdroid.Enums.RecEpiFilter;
import org.mythdroid.Enums.RecType;
import org.mythdroid.activities.Guide;
import org.mythdroid.activities.MDFragmentActivity;
import org.mythdroid.backend.BackendManager;
import org.mythdroid.data.Program;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

/** Edit a recording rule */
public class RecEditFragment extends Fragment {

    /** Duplicate matching method for the current recording rule */
    static public RecDupMethod  dupMethod;
    /** Duplicate matching type for the current recording rule */
    static public RecDupIn      dupIn;
    /** Episode filter for the current recording rule */
    static public RecEpiFilter  epiFilter;
    /** Recording group for the current recording rule */
    static public String        recGroup;
    /** Storage group for the current recording rule */
    static public String        storGroup;

    private MDFragmentActivity activity = null;
    private View view                   = null;
    private Program prog                = null;
    private BackendManager beMgr        = null;
    private RecType type;
    private int prio;
    
    RecEditSchedFragment  resf = null;
    RecEditGroupsFragment regf = null;

    private Button  save, schedOptions, groupOptions;
    private Spinner prioSpinner;
    
    private boolean childrenModified = false, modified = false,
                    inlineOpts = false, initErr = false;
    
    private int containerId;
    
    private String  updates = ""; //$NON-NLS-1$
    
    @Override
    public void onCreate(Bundle icicle) {
        
        super.onCreate(icicle);
        
        activity = (MDFragmentActivity)getActivity();
        
        try {
            beMgr = Globals.getBackend();
        } catch (Exception e) {
        	initError(e);
        	return;
        }
        
        prog = Globals.curProg;
        if (prog == null) {
        	initError(null);
            return;
        }

        type = prog.Type;
        prio = prog.RecPrio;

        dupMethod = prog.DupMethod;
        dupIn = prog.DupIn;
        epiFilter = prog.EpiFilter;
        recGroup = prog.RecGroup;
        storGroup = prog.StorGroup;

        if (prog.RecID != -1)
            try {
                type = prog.Type =
                    MDDManager.getRecType(beMgr.addr, prog.RecID);

                if (storGroup == null) {
                    storGroup = prog.StorGroup =
                        MDDManager.getStorageGroup(beMgr.addr, prog.RecID);
                }
            } catch (IOException e) {
                initError(e);
            }
        
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle icicle
    ) {
        
        if (container == null) return null;
        
        containerId = container.getId();
        
        view = inflater.inflate(R.layout.recording_edit, null, false);
        
        View schedOptFrame = view.findViewById(R.id.recedit_schedoptframe);
        inlineOpts = schedOptFrame != null &&
                     schedOptFrame.getVisibility() == View.VISIBLE;
        
        if (inlineOpts && !initErr) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            resf = RecEditSchedFragment.newInstance(getId());
            regf = RecEditGroupsFragment.newInstance(getId());
            ft.replace(R.id.recedit_schedoptframe, resf);
            ft.replace(R.id.recedit_groupoptframe, regf);
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.commit();
        }

        setViews();
        if (!inlineOpts)
            checkChildren();
        
        return view;
    }

    @Override
    public void onResume() {

        super.onResume();
        try {
            beMgr = Globals.getBackend();
        } catch (Exception e) {
            initError(e);
            return;
        }

        try {
            MDDManager mdd = new MDDManager(beMgr.addr);
            mdd.shutdown();
        } catch (IOException e) {
        	initError(
        		new IOException(
        			Messages.getString("RecordingEdit.2") + beMgr.addr //$NON-NLS-1$
        		)
        	);
        }
        
    }

    /**
     * Check for changes that child fragments might have made
     */
    public void checkChildren() {
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

        ((TextView)view.findViewById(R.id.recedit_title))
            .setText(prog.Title);
        ((TextView)view.findViewById(R.id.recedit_subtitle))
            .setText(prog.SubTitle);
        ((TextView)view.findViewById(R.id.recedit_channel))
            .setText(prog.Channel);
        ((TextView)view.findViewById(R.id.recedit_start))
            .setText(prog.startString());

        final Spinner typeSpinner = ((Spinner)view.findViewById
                                        (R.id.recedit_type));
        final ArrayAdapter<String> typeAdapter = new ArrayAdapter<String>(
            activity, android.R.layout.simple_spinner_item
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
                        if (!inlineOpts) {
                            schedOptions.setEnabled(false);
                            groupOptions.setEnabled(false);
                        }
                        else {
                            resf.setEnabled(false);
                            regf.setEnabled(false);
                        }
                    }
                    else {
                        prioSpinner.setEnabled(true);
                        if (!inlineOpts) {
                            schedOptions.setEnabled(true);
                            groupOptions.setEnabled(true);
                        }
                        else {
                            resf.setEnabled(true);
                            regf.setEnabled(true);
                        }
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}

            }
        );

        prioSpinner = ((Spinner)view.findViewById(R.id.recedit_prio));

        final String[] prios = new String[21];
        for (int i = -10, j = 0; i < 11; i++)
            prios[j++] = String.valueOf(i);

        final ArrayAdapter<String> prioAdapter = new ArrayAdapter<String>(
            activity, android.R.layout.simple_spinner_item, prios
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
        
        if (!inlineOpts) {
            schedOptions = (Button)view.findViewById(R.id.recedit_schedoptions);
            schedOptions.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FragmentTransaction ft = 
                            getFragmentManager().beginTransaction();
                        ft.replace(containerId, new RecEditSchedFragment());
                        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                        ft.addToBackStack(null);
                        ft.commit();
                    }
                }
            );
            schedOptions.setEnabled(type == RecType.NOT ? false : true);

            groupOptions = (Button)view.findViewById(R.id.recedit_groupoptions);
            groupOptions.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FragmentTransaction ft = 
                            getFragmentManager().beginTransaction();
                        ft.replace(containerId, new RecEditGroupsFragment());
                        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                        ft.addToBackStack(null);
                        ft.commit();
                    }
                }
            );
            groupOptions.setEnabled(type == RecType.NOT ? false : true);
        }

        save = (Button)view.findViewById(R.id.recedit_save);
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
                        ErrUtil.err(activity, e);
                    }
                    prog.RecID = -1;
                    activity.setResult(Guide.REFRESH_NEEDED);
                    done();
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
            done();
            return;
        }

        try {
            recid = MDDManager.updateRecording(beMgr.addr, prog, updates);
        } catch (IOException e) {
            ErrUtil.err(activity, e);
        }

        prog.RecID = recid;

        if (recid == -1) {
            ErrUtil.err(activity, Messages.getString("RecordingEdit.0")); //$NON-NLS-1$
            done();
            return;
        }

        try {
            beMgr.reschedule(recid);
        } catch (IOException e) {
            ErrUtil.err(activity, e);
        }

        activity.setResult(Guide.REFRESH_NEEDED);
        done();

    }

    private void addUpdate(String update) {
        if (updates.length() > 0)
            updates += ", "; //$NON-NLS-1$
        updates += update;
    }
    
    private void initError(Exception e) {
    	if (e != null)
    		ErrUtil.err(activity, e);
    	getFragmentManager().popBackStack();
    	initErr = true;
    }
    
    private void done() {
        getFragmentManager().popBackStackImmediate();
    }
}
