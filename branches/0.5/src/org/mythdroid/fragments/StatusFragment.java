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

import org.mythdroid.activities.MDFragmentActivity;
import org.mythdroid.resource.Messages;

import android.R.layout;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * Lists other StatusFragment fragments.
 * Houses static method to fetch the status XML
 */
public class StatusFragment extends ListFragment {

    final private static String[] StatusItems =
    {
        Messages.getString("Status.0"),    // Recorders //$NON-NLS-1$
        Messages.getString("Status.1"),    // Scheduled //$NON-NLS-1$
        Messages.getString("Status.2"),    // Job Queue //$NON-NLS-1$
        Messages.getString("Status.3")     // Backend Info //$NON-NLS-1$
    };

    
    private MDFragmentActivity activity = null;

    @Override
    public void onActivityCreated(Bundle icicle) {
        super.onActivityCreated(icicle);
        activity = (MDFragmentActivity)getActivity();
        setListAdapter(
            new ArrayAdapter<String>(
                activity, layout.simple_list_item_1, StatusItems
            )
        );
    }

    /** When a status menu entry is selected */
    @Override
    public void onListItemClick(ListView list, View item, int pos, long id) {

        final String action = (String)list.getItemAtPosition(pos);
        Fragment f = null;

        if      (action.equals(Messages.getString("Status.0"))) //$NON-NLS-1$
            f = new StatusRecordersFragment();
        else if (action.equals(Messages.getString("Status.1"))) //$NON-NLS-1$
            f = new StatusScheduledFragment();
        else if (action.equals(Messages.getString("Status.2"))) //$NON-NLS-1$
            f = new StatusJobsFragment();
        else if (action.equals(Messages.getString("Status.3"))) //$NON-NLS-1$
            f = new StatusBackendFragment();

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.addToBackStack(null);
        ft.replace(android.R.id.content, f).commitAllowingStateLoss();

    }

}
