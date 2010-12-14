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

import java.net.URL;

import javax.xml.parsers.DocumentBuilderFactory;

import org.mythdroid.R;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import android.R.layout;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * ListActivity lists other Status activities
 * Houses static method to fetch the status XML
 */
public class Status extends ListActivity {

    /** The status XML doc from the backend */
    public static Document        statusDoc   = null;
    
    final static private int      DIALOG_LOAD = 0;
    final private Context         ctx         = this;
    final private static String[] StatusItems =
    { 
    	Messages.getString("Status.0"),    // Recorders //$NON-NLS-1$
    	Messages.getString("Status.1"),    // Scheduled //$NON-NLS-1$
    	Messages.getString("Status.2"),    // Job Queue //$NON-NLS-1$
    	Messages.getString("Status.3")     // Backend Info //$NON-NLS-1$
    };

    final private Handler handler = new Handler();
            
    final private Runnable getStatusTask = new Runnable() {
        @Override
        public void run() {
            try {
                getStatus();
            } catch (SAXException e) {
                ErrUtil.err(ctx, Messages.getString("Status.10")); //$NON-NLS-1$
            } catch (Exception e) { ErrUtil.err(ctx, e); }

            handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        dismissDialog(DIALOG_LOAD);
                        setListAdapter(
                            new ArrayAdapter<String>(
                                ctx, layout.simple_list_item_1, StatusItems
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
        showDialog(DIALOG_LOAD);
        MythDroid.getWorker().post(getStatusTask);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        statusDoc = null;
    }

    /** When a status menu entry is selected */
    @Override
    public void onListItemClick(ListView list, View item, int pos, long id) {
        
        final String action = (String)list.getItemAtPosition(pos);
        Class<?> activity = null;

        if      (action.equals(Messages.getString("Status.0"))) //$NON-NLS-1$
        	activity = StatusRecorders.class;
        else if (action.equals(Messages.getString("Status.1"))) //$NON-NLS-1$
        	activity = StatusScheduled.class;
        else if (action.equals(Messages.getString("Status.2"))) //$NON-NLS-1$
        	activity = StatusJobs.class;
        else if (action.equals(Messages.getString("Status.3"))) //$NON-NLS-1$
        	activity = StatusBackend.class;

        startActivity(new Intent().setClass(this, activity));

    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
    }

    @Override
    public Dialog onCreateDialog(int id) {
        ProgressDialog d = new ProgressDialog(this);
        d.setIndeterminate(true);
        d.setMessage(getResources().getString(R.string.loading));
        return d;
    }

    /**
     * Get new statusDoc from the backend
     */
    public static void getStatus() throws Exception {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        URL url = new URL(MythDroid.getBackend().getStatusURL() + "/xml"); //$NON-NLS-1$
        statusDoc = dbf.newDocumentBuilder().parse(
            url.openConnection().getInputStream()
        );

    }

}
