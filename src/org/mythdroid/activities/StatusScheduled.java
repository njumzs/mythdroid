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

import org.mythdroid.R;
import org.mythdroid.data.Program;
import org.mythdroid.data.ProgramAdapter;
import org.mythdroid.activities.RecordingDetail;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.app.ListActivity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;

/**
 * ListActivity displays scheduled recordings
 */
public class StatusScheduled extends ListActivity {

    private ArrayList<Program> recordings = new ArrayList<Program>(10);

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Document doc = Status.statusDoc;

        Node scheduled = doc.getElementsByTagName("Scheduled").item(0); //$NON-NLS-1$

        NodeList programs = scheduled.getChildNodes();
        int progNodes = programs.getLength();

        for (int i = 0; i < progNodes; i++) {
            Node prog = programs.item(i);
            if (!prog.getNodeName().equals("Program")) continue; //$NON-NLS-1$
            recordings.add(new Program(prog));
        }

        setListAdapter(
            new ProgramAdapter(this, R.layout.recording_list_item, recordings)
        );
    }

    @Override
    public void onListItemClick(ListView list, View item, int pos, long id) {
        MythDroid.curProg = (Program) list.getItemAtPosition(pos);
        startActivity(new Intent().setClass(this, RecordingDetail.class));
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
    }
}
