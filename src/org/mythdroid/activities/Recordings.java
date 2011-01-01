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
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.data.Program;
import org.mythdroid.data.ProgramAdapter;

import android.R.drawable;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * MDListActivity shows list of Recordings
 */
public class Recordings extends MDListActivity implements
    AdapterView.OnItemLongClickListener {

    /** Child activities can set their result to this to indicate that the
     * list of recordings should be refreshed when Recordings resumes
     */
    final public static int  REFRESH_NEEDED = Activity.RESULT_FIRST_USER;

    final private static int FILTER_DIALOG  = 0;

    final private static int 
        MENU_REFRESH   = 0, MENU_FILTER = 1, MENU_FILTER_RESET = 2;
    
    final private Handler handler = new Handler(); 
    
    final private Context ctx = this;
    /** A list of recordings (Programs) */
    private ArrayList<Program> recordings = null;
    /** The title we should filter on */
    private String filter = null;

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
                ArrayList<Program> newList = new ArrayList<Program>();

                for (Program p : recordings)
                    if (p.Title.equals(filter))
                        newList.add(p);

                recordings = newList;
            }

            handler.post(
                new Runnable() {
                    @Override
                public void run() {
                        try {
                            dismissDialog(DIALOG_LOAD);
                        } catch (IllegalArgumentException e) {}
                        setListAdapter(
                            new ProgramAdapter(
                                ctx, R.layout.recording_list_item, recordings
                            )
                        );
                    }
                }
            );

        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setListAdapter(null);
        addHereToFrontendChooser(VideoPlayer.class);
        getListView().setOnItemLongClickListener(this);
        refresh();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        empty();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        getListView().setOnItemLongClickListener(this);
    }

    @Override
    public void onActivityResult(int reqCode, int resCode, Intent data) {
        if (resCode == REFRESH_NEEDED) 
            refresh();
    }

    @Override
    public void onListItemClick(ListView list, View item, int pos, long id) {
        Globals.curProg = (Program)list.getItemAtPosition(pos);
        startActivityForResult(
            new Intent().setClass(this, RecordingDetail.class), 0
        );
    }

    @Override
    public boolean onItemLongClick(
        AdapterView<?> adapter, View item, int pos, long itemid
    ) {
        Globals.curProg = (Program)adapter.getItemAtPosition(pos);
        nextActivity = TVRemote.class;
        showDialog(FRONTEND_CHOOSER);
        return true;
    }

    @Override
    public Dialog onCreateDialog(int id) {

        switch (id) {

            case FILTER_DIALOG:

                final ArrayList<String> titles = new ArrayList<String>();

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

    private void empty() {
        if (recordings != null) 
            recordings.clear();
        recordings = null;
        setListAdapter(null);
        Globals.curProg = null;
    }

    private void refresh() {
        empty();
        showDialog(DIALOG_LOAD);
        Globals.getWorker().post(getRecordings);
    }

}
