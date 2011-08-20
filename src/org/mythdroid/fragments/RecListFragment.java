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
import org.mythdroid.util.ErrUtil;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Displays a list of recordings
 */
public class RecListFragment extends ListFragment
    implements AdapterView.OnItemLongClickListener {

    private Recordings activity = null;
    private ListView lv = null;
    private String detailsFragClass = null;
    private Bundle detailsFragBundle = null;
    private boolean dualPane = false;

    @Override
    public void onActivityCreated(Bundle icicle) {
        
        super.onActivityCreated(icicle);
        
        lv = getListView();
        
        // Only ever embedded in Recordings activity
        activity = (Recordings)getActivity();
        activity.addHereToFrontendChooser(VideoPlayer.class);
        lv.setOnItemLongClickListener(this);
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        
        setEmptyText(Messages.getString("RecListFragment.0")); //$NON-NLS-1$
        
        View detailsFrame = getActivity().findViewById(R.id.recdetails);
        dualPane = detailsFrame != null && 
                   detailsFrame.getVisibility() == View.VISIBLE;
        
        if (!activity.hasRecordings())
            activity.refresh();
        
        /* 
         * Find out if we should push another fragment on the stack during
         * showDetails() to restore state post configuration change 
         */
        Bundle args = getArguments();
        if (args != null) {
            detailsFragClass  = args.getString("detailsFragClass");      //$NON-NLS-1$
            detailsFragBundle = args.getParcelable("detailsFragBundle"); //$NON-NLS-1$
            if (detailsFragClass.equals(getClass().getName()))
                detailsFragClass = null;
        }
        
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        setListAdapter(null);
    }
    
    @Override
    public void onListItemClick(ListView list, View v, int pos, long id) {
        Globals.curProg = (Program)list.getItemAtPosition(pos);
        activity.index = pos;
        showDetails();
    }

    @Override
    public boolean onItemLongClick(
        AdapterView<?> adapter, View item, int pos, long itemid
    ) {
        Globals.curProg = (Program)adapter.getItemAtPosition(pos);
        activity.index = pos;
        activity.nextActivity = TVRemote.class;
        activity.showDialog(Recordings.FRONTEND_CHOOSER);
        return true;
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
        Globals.curProg = (Program)lv.getItemAtPosition(activity.index);
        lv.setItemChecked(activity.index, true);
        lv.setSelection(activity.index);
        if (dualPane || detailsFragClass != null) 
            showDetails();
    }
    
    private void showDetails() {
        
        Fragment rdf = null;
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        
        rdf = RecDetailFragment.newInstance(false, false);
        
        if (dualPane)
            ft.replace(R.id.recdetails, rdf);
        else {
            ft.replace(R.id.reclistframe, rdf);
            ft.addToBackStack(null);
        }
        
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
        
        if (detailsFragClass == null) return;
        
        if (detailsFragClass.endsWith("RecDetailFragment")) { //$NON-NLS-1$
            detailsFragClass = null;
            return;
        }
        
        /* We need to push another fragment on the stack to restore
           state post config change */
        ft = getFragmentManager().beginTransaction();
        
        try {
            rdf = (Fragment)Class.forName(detailsFragClass).newInstance();
        } catch (Exception e) {
            ErrUtil.err(activity, e);
            return;
        }
        rdf.setArguments(detailsFragBundle);
        
        ft.replace(dualPane ? R.id.recdetails : R.id.reclistframe, rdf);
        ft.addToBackStack(null);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
        detailsFragClass = null;
        
    }
    
}
