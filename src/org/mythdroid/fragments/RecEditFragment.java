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

import org.mythdroid.Enums.RecStatus;
import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.Enums.RecDupIn;
import org.mythdroid.Enums.RecDupMethod;
import org.mythdroid.Enums.RecEpiFilter;
import org.mythdroid.Enums.RecType;
import org.mythdroid.activities.MDFragmentActivity;
import org.mythdroid.backend.BackendManager;
import org.mythdroid.data.Program;
import org.mythdroid.data.RecordingRule;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.resource.Messages;
import org.mythdroid.services.DvrService;
import org.mythdroid.util.ErrUtil;

import android.os.Bundle;
import android.os.Handler;
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

    int             recId;
    RecDupMethod    dupMethod;
    RecDupIn        dupIn;
    RecEpiFilter    epiFilter;
    String          recGroup, storGroup;
    
    private MDFragmentActivity activity = null;
    private View view                   = null;
    private Program prog                = null;
    private BackendManager beMgr        = null;
    private Handler handler             = new Handler();
    private Object initLock             = new Object();
    
    private int     prio;
    private RecType type;
    
    private RecEditSchedFragment  resf = null;
    private RecEditGroupsFragment regf = null;

    private Button  save, schedOptions, groupOptions;
    private Spinner prioSpinner;
    
    private boolean childrenModified = false, modified = false,
                    inlineOpts = false, initErr = false;
    
    private int containerId;
    
    private DvrService dvr = null;
    private RecordingRule rule = null;
    
    private StringBuilder updates = new StringBuilder(16);
    
    private Runnable doneRunnable = new Runnable() {
        @Override
        public void run() { done(); }
    };
    
    
    private Runnable getRuleRunnable = new Runnable() {
        @Override
        public void run() {
            
            synchronized (initLock) {
                
                activity.showLoadingDialog();
                try {
                    beMgr = Globals.getBackend();
                } catch (IOException e) {
                    activity.dismissLoadingDialog();
                    initError(e);
                    initLock.notify();
                    return;
                } 
                if ((prog = Globals.curProg) == null) {
                    ErrUtil.report(Messages.getString("RecEditFragment.0")); //$NON-NLS-1$
                    activity.dismissLoadingDialog();
                    initError(
                        new IllegalArgumentException(
                            Messages.getString("RecEditFragment.0") //$NON-NLS-1$
                        )
                    );
                    initLock.notify();
                    return;
                }
                
                recId       = prog.RecID;        
                type        = prog.Type;
                prio        = prog.RecPrio;
                dupMethod   = prog.DupMethod;
                dupIn       = prog.DupIn;
                epiFilter   = prog.EpiFilter;
                recGroup    = prog.RecGroup;
                storGroup   = prog.StorGroup;
    
                if (!Globals.haveServices()) {
                    if (recId != -1)
                        try {
                            type = prog.Type =
                                MDDManager.getRecType(beMgr.addr, recId);
                            if (storGroup == null) {
                                storGroup = prog.StorGroup =
                                    MDDManager.getStorageGroup(
                                        beMgr.addr, recId
                                    );
                            }
                        } catch (IOException e) {
                            activity.dismissLoadingDialog();
                            initError(e);
                            initLock.notify();
                            return;
                        }
                    rule = new RecordingRule();
                }
                else {
                    dvr = new DvrService(beMgr.addr);
                    try {
                        rule = dvr.getRecRule(recId);
                    } catch (Exception e) {
                        activity.dismissLoadingDialog();
                        initError(e);
                        initLock.notify();
                        return;
                    }
                    if (rule == null)
                        rule = new RecordingRule(prog);
                    else {
                        type        = prog.Type      = rule.type;
                        prio        = prog.RecPrio   = rule.recpriority;
                        dupMethod   = prog.DupMethod = rule.dupMethod;
                        dupIn       = prog.DupIn     = rule.dupIn;
                        recGroup    = prog.RecGroup  = rule.recGroup;
                        storGroup   = prog.StorGroup = rule.storGroup;
                    }
                }
                activity.dismissLoadingDialog();
                initLock.notify();
            }
        }
    };

    private Runnable saveRunnable = new Runnable() {
        @Override
        public void run() {
        
            activity.showLoadingDialog();
            
            int recid = -1;
    
            if (modified) {
                if (prio != prog.RecPrio) {
                    addUpdate("RecPriority", prio); //$NON-NLS-1$
                    prog.RecPrio = rule.recpriority = prio;
                }
                if (type != prog.Type) {
    
                    addUpdate("Type", type.value()); //$NON-NLS-1$
                    prog.Type = rule.type = type;
    
                    if (type == RecType.NOT && prog.RecID != -1) {
                        
                        try {
                            if (!Globals.haveServices()) {
                                MDDManager.deleteRecording(
                                    beMgr.addr, prog.RecID
                                );
                                beMgr.reschedule(prog.RecID);
                            }
                            else
                                dvr.deleteRecording(prog.RecID);
                        } catch (IOException e) {
                            ErrUtil.postErr(activity, e);
                            handler.post(doneRunnable);
                            return;
                        } finally {
                            activity.dismissLoadingDialog();
                        }
                        prog.RecID = -1;
                        if (prog.Status != RecStatus.RECORDED)
                            ((RecDetailFragment)
                                getFragmentManager()
                                    .findFragmentByTag("RecDetailFragment") //$NON-NLS-1$
                            ).refresh();
                        handler.post(doneRunnable);
                        return;
                    }
    
                    addUpdate("FindDay", prog.StartTime.getDay()); //$NON-NLS-1$
                    rule.day = prog.StartTime.getDay();
                    String time = 
                        prog.StartTime.getHours() + ":" + //$NON-NLS-1$
                            prog.StartTime.getMinutes() + ":" + //$NON-NLS-1$
                                prog.StartTime.getSeconds(); 
                    addUpdate("FindTime", time);//$NON-NLS-1$
                    rule.time = time;
                    long id =
                        (prog.StartTime.getTime() / (1000*60*60*24)) + 719528;
                    addUpdate("FindId", id); //$NON-NLS-1$
                    rule.findid = id;
    
                }
            }
    
            if (childrenModified) {
                if (dupMethod != prog.DupMethod) {
                    addUpdate("DupMethod", dupMethod.value()); //$NON-NLS-1$
                    prog.DupMethod = rule.dupMethod = dupMethod;
                }
                if (dupIn != prog.DupIn || epiFilter != prog.EpiFilter) {
                    addUpdate("DupIn", (dupIn.value() | epiFilter.value())); //$NON-NLS-1$
                    prog.DupIn = rule.dupIn = dupIn;
                }
                if (recGroup != prog.RecGroup) {
                    addUpdate("RecGroup", recGroup); //$NON-NLS-1$
                    prog.RecGroup = rule.recGroup = recGroup;
                }
                if (storGroup != prog.StorGroup) {
                    addUpdate("StorageGroup", storGroup); //$NON-NLS-1$
                    prog.StorGroup = rule.storGroup = storGroup;
                }
            }
    
            if (updates.length() != 0)
                updateRecording(recid, updates);
            
            activity.dismissLoadingDialog();
            handler.post(doneRunnable);
            
        }

    };
    
    @Override
    public void onCreate(Bundle icicle) {
        
        super.onCreate(icicle);
        activity = (MDFragmentActivity)getActivity();
        Globals.runOnThreadPool(getRuleRunnable);
        
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle icicle
    ) {

        synchronized (initLock) {
            while (!initErr && rule == null)
                try {
                    initLock.wait();
                } catch (InterruptedException e) {}
        }
        
        if (container == null || initErr) return null;
        
        containerId = container.getId();
        
        view = inflater.inflate(R.layout.recording_edit, null, false);
        
        View schedOptFrame = view.findViewById(R.id.schedOptFrame);
        inlineOpts = schedOptFrame != null &&
                     schedOptFrame.getVisibility() == View.VISIBLE;
        
        if (inlineOpts) {
            resf = new RecEditSchedFragment();
            regf = new RecEditGroupsFragment();
            getFragmentManager().beginTransaction()
                .replace(
                    R.id.schedOptFrame, resf, resf.getClass().getSimpleName()
                )
                .replace(
                    R.id.groupOptFrame, regf, regf.getClass().getSimpleName()
                )
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commitAllowingStateLoss();
        }

        if ((prog = Globals.curProg) == null) {
            initError(
                new IllegalArgumentException(
                    Messages.getString("RecEditFragment.0") //$NON-NLS-1$
                )
            );
            return view;
        }
        
        setViews();
        if (!inlineOpts)
            checkChildren();
        
        return view;
    }

    @Override
    public void onResume() {

        super.onResume();
        
        Globals.runOnThreadPool(
            new Runnable() {
                @Override
                public void run() {
                    activity.showLoadingDialog();
                    try {
                        beMgr = Globals.getBackend();
                    } catch (IOException e) {
                        initError(e);
                        return;
                    } finally {
                        activity.dismissLoadingDialog();
                    }
            
                    if (!Globals.haveServices())
                        try {
                            MDDManager mdd =
                                new MDDManager(beMgr.addr, Globals.muxConns);
                            mdd.shutdown();
                        } catch (IOException e) {
                            initError(
                                new IOException(
                                    Messages.getString("RecordingEdit.2") +  //$NON-NLS-1$
                                    beMgr.addr
                                )
                            );
                        }
                }
            }
        );
        
    }

    /**
     * Check for changes that child fragments might have made
     */
    public void checkChildren() {
        if (prog == null) return;
        if (resf != null && !resf.isEnabled())
            childrenModified = false;
        else if
            (
                dupMethod != prog.DupMethod       ||
                dupIn     != prog.DupIn           ||
                epiFilter != prog.EpiFilter       ||
                !recGroup.equals(prog.RecGroup)   ||
                !storGroup.equals(prog.StorGroup)
            )
            childrenModified = true;
        else
            childrenModified = false;
        updateSaveEnabled();
    }

    private void setViews() {

        ((TextView)view.findViewById(R.id.title)).setText(prog.Title);
        ((TextView)view.findViewById(R.id.subtitle)).setText(prog.SubTitle);
        ((TextView)view.findViewById(R.id.channel)).setText(prog.Channel);
        ((TextView)view.findViewById(R.id.start)).setText(prog.startString());

        final Spinner typeSpinner = ((Spinner)view.findViewById(R.id.type));
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

        prioSpinner = ((Spinner)view.findViewById(R.id.prio));

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
            schedOptions = (Button)view.findViewById(R.id.schedOptions);
            schedOptions.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getFragmentManager().beginTransaction()
                            .replace(
                                containerId, new RecEditSchedFragment(),
                                "RecEditSchedFragment" //$NON-NLS-1$
                            )
                            .setTransition(
                                FragmentTransaction.TRANSIT_FRAGMENT_FADE
                            )
                            .addToBackStack(null)
                            .commitAllowingStateLoss();
                    }
                }
            );
            schedOptions.setEnabled(type == RecType.NOT ? false : true);

            groupOptions = (Button)view.findViewById(R.id.groupOptions);
            groupOptions.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getFragmentManager().beginTransaction()
                        .replace(
                            containerId, new RecEditGroupsFragment(),
                            "RecEditGroupsFragment" //$NON-NLS-1$
                        )
                        .setTransition(
                            FragmentTransaction.TRANSIT_FRAGMENT_FADE
                        )
                        .addToBackStack(null)
                        .commitAllowingStateLoss();
                    }
                }
            );
            groupOptions.setEnabled(type == RecType.NOT ? false : true);
        }

        save = (Button)view.findViewById(R.id.save);
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

    // Save the changes and reschedule the rule
    private void doSave() {
       Globals.runOnThreadPool(saveRunnable);
    }
    
    private void updateRecording(int recid, StringBuilder updates) {
        
        // Send the schedule updates to MDD, get back the (new) recid
        if (!Globals.haveServices())
            try {
                recid = MDDManager.updateRecording(
                    beMgr.addr, prog, updates.toString()
                );
            } catch (IOException e) {
                ErrUtil.postErr(activity, e);
                return;
            }
        else {
            /* We need to use the program start time, not that of the rule
               Strange, but true because of the implementation of 
               AddRecordSchedule in the services api */
            rule.startTime = prog.StartTime;
            
            // New recording, fill in some sensible defaults
            if (prog.RecID != 1) {
                rule.autoexpire = true;
                rule.autoflag   = true;
                rule.autometa   = true;
            }
            try {
                recid = dvr.updateRecording(rule);
            } catch (IOException e) { 
                ErrUtil.postErr(activity, e);
                return;
            }
        }
        prog.RecID = recid;

        if (recid == -1) {
            ErrUtil.postErr(
                activity, Messages.getString("RecordingEdit.0") //$NON-NLS-1$
            );
            return;
        }

        // Tell the backend to reschedule this rule
        try {
            beMgr.reschedule(recid);
        } catch (IOException e) { ErrUtil.postErr(activity, e); }
        
        if (prog.Status != RecStatus.RECORDED) {
            RecDetailFragment rdf =
                ((RecDetailFragment)
                    getFragmentManager()
                        .findFragmentByTag("RecDetailFragment")); //$NON-NLS-1$
            if (rdf != null)
                rdf.refresh();
        }
        
    }

    /**
     * Add to the list of schedule updates to send to MDD 
     * @param update new schedule update
     */
    private void addUpdate(String update, long value) {
        if (updates.length() > 0)
            updates.append(", "); //$NON-NLS-1$
        updates.append(update).append(" = ").append(value); //$NON-NLS-1$
    }
    
    /**
     * Add to the list of schedule updates to send to MDD 
     * @param update new schedule update
     */
    private void addUpdate(String update, String value) {
        if (updates.length() > 0)
            updates.append(", "); //$NON-NLS-1$
        updates.append(update).append(" = '").append(value).append("'"); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    private void removeInlineFrags() {
        if (!inlineOpts && resf == null && regf == null)
            return;
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.remove(resf);
        ft.remove(regf);
        ft.commitAllowingStateLoss();
    }
    
    private void initError(Exception e) {
        if (e != null) ErrUtil.postErr(activity, e);
        initErr = true;
        removeInlineFrags();
        getFragmentManager().popBackStack();
    }
    
    private void done() {
        removeInlineFrags();
        getFragmentManager().popBackStackImmediate();
    }
}
