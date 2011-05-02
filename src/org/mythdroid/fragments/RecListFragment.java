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

import java.util.ArrayList;

import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.activities.RecordingDetail;
import org.mythdroid.activities.Recordings;
import org.mythdroid.activities.VideoPlayer;
import org.mythdroid.data.Program;
import org.mythdroid.data.ProgramAdapter;
import org.mythdroid.remote.TVRemote;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

/**
 * A Fragment that displays the list of recordings
 */
public class RecListFragment extends ListFragment
    implements AdapterView.OnItemLongClickListener{

    private Recordings activity = null;
    private ListView lv = null;
    private boolean dualPane = false;
    private int selected = -1;
    
    /**
     * Create a new RecListFragment
     * @param index the previously selected index in the list
     * @return a new RecListFragment
     */
    public static RecListFragment newInstance(int index) {
        RecListFragment rlf = new RecListFragment();
        Bundle icicle = new Bundle();
        icicle.putInt("selected", index); //$NON-NLS-1$
        rlf.setArguments(icicle);
        return rlf;
    }
    
    @Override
    public void onActivityCreated(Bundle icicle) {
        
        super.onActivityCreated(icicle);
        setListAdapter(null);
        
        lv = getListView();
        
        activity = (Recordings)getActivity();
        activity.addHereToFrontendChooser(VideoPlayer.class);
        lv.setOnItemLongClickListener(this);
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        
        View detailsFrame = getActivity().findViewById(R.id.recdetails);
        dualPane = detailsFrame != null && 
                   detailsFrame.getVisibility() == View.VISIBLE;
        
        if (!activity.hasRecordings())
            activity.refresh();
        
        Bundle args = getArguments();
        if (args != null) 
            selected = args.getInt("selected", -1); //$NON-NLS-1$
        if (selected < 0) selected = 0;

    }
    
    @Override
    public void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        icicle.putInt("selected", selected); //$NON-NLS-1$
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        setListAdapter(null);
    }
    
    @Override
    public void onListItemClick(ListView list, View v, int pos, long id) {
        Globals.curProg = (Program)list.getItemAtPosition(pos);
        selected = pos;
        showDetails();
    }

    @Override
    public boolean onItemLongClick(
        AdapterView<?> adapter, View item, int pos, long itemid
    ) {
        Globals.curProg = (Program)adapter.getItemAtPosition(pos);
        activity.nextActivity = TVRemote.class;
        activity.showDialog(Recordings.FRONTEND_CHOOSER);
        return true;
    }
    
    @Override
    public void onActivityResult(int reqCode, int resCode, Intent data) {
        if (resCode == Recordings.REFRESH_NEEDED)
            activity.refresh();
    }
    
    /**
     * Get the index in the list of the selected recording
     * @return index of the currently selected recording
     */
    public int getIndex() {
        return selected;
    }
    
    /**
     * Populate the list 
     * @param recordings - list of recordings
     */
    public void setAdapter(ArrayList<Program> recordings) {
        setListAdapter(
            new ProgramAdapter(
                activity, R.layout.recording_list_item,
                recordings
            )
        );
        updateSelection();
    }
    
    /**
     * Update which recording is selected
     */
    public void updateSelection() {
        Globals.curProg = (Program)lv.getItemAtPosition(selected); 
        if (dualPane) 
            showDetails();
    }
    
    private void showDetails() {
        if (dualPane) {
            lv.setItemChecked(selected, true);
            RecDetailFragment rdf = 
                RecDetailFragment.newInstance(false, false);
            FragmentTransaction ft = 
                getFragmentManager().beginTransaction();
            ft.replace(R.id.recdetails, rdf);
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            ft.commit();
        }
        else {
            lv.setItemChecked(selected, false);
            startActivityForResult(
                new Intent().setClass(activity, RecordingDetail.class), 0
            );
        }
    }
    
}
