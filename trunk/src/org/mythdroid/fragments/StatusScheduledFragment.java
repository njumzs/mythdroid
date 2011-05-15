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
import org.mythdroid.data.Program;
import org.mythdroid.data.ProgramAdapter;
import org.mythdroid.activities.MDFragmentActivity;
import org.mythdroid.activities.RecordingDetail;
import org.mythdroid.activities.Status;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

/** ListActivity displays scheduled recordings */
public class StatusScheduledFragment extends ListFragment {

    private ArrayList<Program> recordings = new ArrayList<Program>(10);
    
    private MDFragmentActivity activity = null;

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle icicle
    ) {
        if (container == null) return null;
        activity = (MDFragmentActivity)getActivity();
        View view = inflater.inflate(R.layout.status_scheduled, null, false);
        
        if (Status.statusDoc == null && !Status.getStatus(activity))
            activity.finish();

        Document doc = Status.statusDoc;

        Node scheduled = doc.getElementsByTagName("Scheduled").item(0); //$NON-NLS-1$

        NodeList programs = scheduled.getChildNodes();
        int progNodes = programs.getLength();

        for (int i = 0; i < progNodes; i++) {
            Node prog = programs.item(i);
            if (!prog.getNodeName().equals("Program")) continue; //$NON-NLS-1$
            recordings.add(new Program(prog));
        }
        
        ((TextView)view.findViewById(R.id.emptyMsg))
            .setText(R.string.no_scheduled);
       
        setListAdapter(
            new ProgramAdapter(
                activity, R.layout.recording_list_item, recordings
            )
        );
        
        return view;
    }

    @Override
    public void onListItemClick(ListView list, View item, int pos, long id) {
        Globals.curProg = (Program) list.getItemAtPosition(pos);
        startActivity(new Intent().setClass(activity, RecordingDetail.class));
    }
    
}
