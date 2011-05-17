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

import java.util.ArrayList;
import java.util.Collections;

import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.data.Program;
import org.mythdroid.fragments.RecListFragment;


import android.R.drawable;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;

/**
 * MDFragmentActivity shows list of Recordings
 */
public class Recordings extends MDFragmentActivity {

    /**
     * Child activities can set their result to this to indicate that the
     * list of recordings should be refreshed when Recordings resumes
     */
    final public static int  REFRESH_NEEDED = Activity.RESULT_FIRST_USER;
    
    /** Currently selected index in the recordings list */
    public int index = 0;

    final private static int FILTER_DIALOG  = 0;

    final private static int
        MENU_REFRESH   = 0, MENU_FILTER = 1, MENU_FILTER_RESET = 2;
    
    final private Handler handler = new Handler();
    
    /** A list of recordings (Programs) */
    private ArrayList<Program> recordings = null;
    
    /** The title we should filter on */
    private String filter = null;
    
    private RecListFragment listFragment = null;
    
    /** Populates recordings, filters if necessary */
    final private Runnable getRecordings  = new Runnable() {
        @Override
        public void run() {

            try {
                recordings = Globals.getBackend().getRecordings();
            } catch (Exception e) {
                ErrUtil.postErr(ctx, Messages.getString("Recordings.0")); //$NON-NLS-1$
                try {
                    dismissDialog(DIALOG_LOAD);
                } catch (IllegalArgumentException e1) {}
                finish();
            }

            if (recordings == null)
                return;

            if (filter != null) {
                Program[] recs =
                    recordings.toArray(new Program[recordings.size()]);
                int numrecs = recs.length;
                ArrayList<Program> newList = new ArrayList<Program>(numrecs/10);
                for (int i = 0; i < numrecs; i++)
                    if (recs[i].Title.equals(filter))
                        newList.add(recs[i]);
                recordings = newList;
            }

            handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            dismissDialog(DIALOG_LOAD);
                        } catch (IllegalArgumentException e) {}
                        listFragment.setAdapter(recordings);
                    }
                }
            );

        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.recordings);
        listFragment = new RecListFragment();
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.reclistframe, listFragment);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        ft.commit();
    }
    
    @Override
    public void onPause() {
        super.onPause();
         try {
            dismissDialog(DIALOG_LOAD);
        } catch (IllegalArgumentException e) {}
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        empty();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (!isPaused)
            if (listFragment != null && hasRecordings())
                listFragment.setAdapter(recordings);
    }
    
    @Override
    public Dialog onCreateDialog(int id) {
        
        OnClickListener no = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        };

        switch (id) {

            case FILTER_DIALOG:

                final ArrayList<String> titles = new ArrayList<String>();

                if (recordings == null)
                	return new AlertDialog.Builder(this)
                		.setTitle(R.string.filter_rec)
                		.setMessage(R.string.no_recs)
                		.setPositiveButton(R.string.ok, no)
                		.create();
                
                for (Program prog : recordings) {
                    if (titles.contains(prog.Title)) continue;
                    titles.add(prog.Title);
                }

                Collections.sort(titles);

                return
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.filter_rec)
                        .setAdapter(
                            new ArrayAdapter<String>(
                                this, R.layout.simple_list_item_1, titles
                            ),
                            new OnClickListener() {
                                @Override
                                public void onClick(
                                    DialogInterface dialog, int which
                                ) {
                                    dialog.dismiss();
                                    filter = titles.get(which);
                                    refresh();
                                }

                            }
                        )
                        .create();
   
            default:
                return super.onCreateDialog(id);

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE, R.string.refresh)
            .setIcon(R.drawable.ic_menu_refresh);
        menu.add(Menu.NONE, MENU_FILTER, Menu.NONE, R.string.filter)
            .setIcon(drawable.ic_menu_search);
        menu.add(Menu.NONE, MENU_FILTER_RESET, Menu.NONE, R.string.reset_filter)
            .setIcon(drawable.ic_menu_revert);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case MENU_REFRESH:
                refresh();
                return true;
            case MENU_FILTER:
                showDialog(FILTER_DIALOG);
                return true;
            case MENU_FILTER_RESET:
                filter = null;
                refresh();
                return true;
        }

        return false;

    }
    
    /**
     * Is the recordings list populated?
     * @return true if so
     */
    public boolean hasRecordings() {
        return recordings != null && !recordings.isEmpty();
    }
    
    /**
     * Empty the recordings list
     */
    public void empty() {
        if (recordings != null)
            recordings.clear();
        recordings = null;
        Globals.curProg = null;
    }

    /**
     * Refresh the recordings list
     */
    public void refresh() {
        empty();
        showDialog(DIALOG_LOAD);
        Globals.getWorker().post(getRecordings);
    }
   
    @Override
    protected void resetContentView() {
        
        final FragmentManager fm = getSupportFragmentManager();
        final Bundle args = new Bundle();
        
        Fragment lf = fm.findFragmentById(R.id.reclistframe);
        Fragment df = fm.findFragmentById(R.id.recdetails);
        
        // The old backstack is useless now        
        int backStackSize = fm.getBackStackEntryCount();
        for (int i = 0; i < backStackSize; i++)
            fm.popBackStackImmediate();
        
        setContentView(R.layout.recordings);
        
        boolean dualPane = findViewById(R.id.recdetails) != null;
        
        args.putString(
            "detailsFragClass",  //$NON-NLS-1$
            (dualPane || df == null ? lf : df).getClass().getName()
        );
         
        args.putParcelable(
            "detailsFragBundle", //$NON-NLS-1$
            (dualPane || df == null ? lf : df).getArguments()
        );
            
        listFragment = new RecListFragment();
        listFragment.setArguments(args);
        
        fm.beginTransaction().replace(R.id.reclistframe, listFragment).commit();
        fm.executePendingTransactions();

    }
    
}