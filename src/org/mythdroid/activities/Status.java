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

import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.fragments.StatusBackendFragment;
import org.mythdroid.fragments.StatusFragment;
import org.mythdroid.fragments.StatusJobsFragment;
import org.mythdroid.fragments.StatusRecordersFragment;
import org.mythdroid.fragments.StatusScheduledFragment;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentTransaction;


/**
 * ListActivity lists other Status activities
 * Houses static method to fetch the status XML
 */
public class Status extends MDFragmentActivity {

    /** The status XML doc from the backend */
    public static Document        statusDoc   = null;
    /** Are we embedding the child fragments? */ 
    public boolean                embed       = true;

    final static private int      DIALOG_LOAD = 0;
    
    final private Context ctx     = this;
    final private Handler handler = new Handler();

    final private Runnable getStatusTask = new Runnable() {
        @Override
        public void run() {
            getStatus(ctx);
            handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            dismissDialog(DIALOG_LOAD);
                        } catch (IllegalArgumentException e) {}
                        installFragments();
                    }
                }
            );
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.status);
        if (findViewById(R.id.statuslistframe) != null)
            embed = false;
        showDialog(DIALOG_LOAD);
        Globals.getWorker().post(getStatusTask);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        statusDoc = null;
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
     * @param ctx Context
     */
    public static boolean getStatus(Context ctx) {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            URL url = new URL(Globals.getBackend().getStatusURL() + "/xml"); //$NON-NLS-1$
            statusDoc = dbf.newDocumentBuilder().parse(
                url.openConnection().getInputStream()
            );
        } catch (SAXException e) {
            ErrUtil.err(ctx, Messages.getString("Status.10")); //$NON-NLS-1$
        } catch (Exception e) { ErrUtil.err(ctx, e); }
        
        return statusDoc != null;

    }
    
    private void installFragments() {
        if (!embed) {
            FragmentTransaction ft = 
            	getSupportFragmentManager().beginTransaction();
            ft.replace(android.R.id.content, new StatusFragment());
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.commit();
        }
        else {
            FragmentTransaction ft =
                getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.recorderframe, new StatusRecordersFragment());
            ft.replace(R.id.jobframe, new StatusJobsFragment());
            ft.replace(R.id.scheduledframe, new StatusScheduledFragment());
            ft.replace(R.id.backendframe, new StatusBackendFragment());
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.commit();
        }

    }
    
    @Override
    protected void resetContentView() {
        if (embed) {
        	setContentView(R.layout.status);
        	installFragments();
        }
    }

}
