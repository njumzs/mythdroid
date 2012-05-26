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
import org.mythdroid.activities.Recordings;
import org.mythdroid.activities.VideoPlayer;
import org.mythdroid.data.Program;
import org.mythdroid.data.ProgramAdapter;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Displays a list of recordings
 */
public class RecListFragment extends ListFragment
    implements AdapterView.OnItemLongClickListener {
    
    private Recordings activity      = null;
    private ListView lv              = null;
    private boolean dualPane         = false;

    @Override
    public void onActivityCreated(Bundle icicle) {
        
        super.onActivityCreated(icicle);
        
        lv = getListView();
        
        // Only ever embedded in Recordings activity
        activity = (Recordings)getActivity();
        activity.addHereToFrontendChooser(VideoPlayer.class);
        lv.setOnItemLongClickListener(this);
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        
        TextView emptyView = new TextView(activity);
        emptyView.setText(Messages.getString("RecListFragment.0")); //$NON-NLS-1$
        emptyView.setTextSize(20);
        emptyView.setGravity(Gravity.CENTER);
        ((ViewGroup)lv.getParent()).addView(emptyView);
        lv.setEmptyView(emptyView);
        
        View detailsFrame = getActivity().findViewById(R.id.recdetails);
        dualPane = detailsFrame != null && 
                   detailsFrame.getVisibility() == View.VISIBLE;
       
        if (!activity.hasRecordings())
            activity.refresh();
        
    }
    
    @Override
    public void onResume() {
        super.onResume();
        lv.setSelection(activity.visibleIndex);
    }
    
    @Override
    public void onPause() {
        super.onPause();
        int vIdx = lv.getFirstVisiblePosition();
        if (vIdx != ListView.INVALID_POSITION && vIdx != 0)
            activity.visibleIndex = vIdx;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        setListAdapter(null);
    }
    
    @Override
    public void onListItemClick(ListView list, View v, int pos, long id) {
        Globals.curProg = (Program)list.getItemAtPosition(pos);
        activity.checkedIndex  = pos;
        showDetails();
    }

    @Override
    public boolean onItemLongClick(
        AdapterView<?> adapter, View item, int pos, long itemid
    ) {
        Globals.curProg       = (Program)adapter.getItemAtPosition(pos);
        activity.checkedIndex = pos;
        activity.nextActivity = TVRemote.class;
        activity.showDialog(Recordings.FRONTEND_CHOOSER);
        return true;
    }
    
    /**
     * Return our ListView's FirstVisiblePosition
     * @return the index of the first visible Program
     */
    public int getFirstVisiblePosition() {
        return lv.getFirstVisiblePosition();
    }
    
    /**
     * Populate the list 
     * @param recordings ArrayList of Programs
     */
    public void setAdapter(ArrayList<Program> recordings) {
        
        if (recordings.isEmpty()) {
            setListAdapter(null);
            return;
        }
            
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
        
        int maxIndex = lv.getAdapter().getCount() - 1;
        
        if (maxIndex >= 0) 
            activity.checkedIndex = Math.min(activity.checkedIndex, maxIndex);
        
        if (activity.checkedIndex > maxIndex) return;
        
        Program p = (Program)lv.getItemAtPosition(activity.checkedIndex);
        if (p == null) return;
        Globals.curProg = p;

        lv.setSelection(
            activity.checkedIndex >= 0 ? 
                activity.checkedIndex : activity.visibleIndex
        );
        
        if (!dualPane) return;
        
        lv.setItemChecked(activity.checkedIndex, true);
        
        // Do we need to add / replace the fragment in the details view slot?
        Fragment df = getFragmentManager().findFragmentById(R.id.recdetails);
        
        if (
            df == null || !df.getClass().equals(RecDetailFragment.class) ||
            !df.isVisible()
        ) {
            showDetails();
            return;
        }

        Program prog = ((RecDetailFragment)df).getProg();
        if (prog == null || !prog.equals(Globals.curProg))
            showDetails();    
        
    }
    
    private void showDetails() {
        
        Fragment rdf           = null;
        FragmentManager fm     = getFragmentManager();
        if (fm == null) return;
        
        FragmentTransaction ft = fm.beginTransaction();
  
        rdf = RecDetailFragment.newInstance(false, false);
        
        if (dualPane)
            ft.replace(R.id.recdetails, rdf);
        else {
            ft.replace(R.id.reclistframe, rdf);
            ft.addToBackStack(null);
        }
        
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commitAllowingStateLoss();
                
    }
    
}
