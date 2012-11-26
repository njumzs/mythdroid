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

import java.io.IOException;
import java.io.InputStream;
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
import org.mythdroid.util.HttpFetcher;
import org.mythdroid.util.LogUtil;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentTransaction;

/**
 * MDFragmentActivity, houses status fragments
 */
public class Status extends MDFragmentActivity {

    /** The status XML doc from the backend */
    public Document          statusDoc   = null;
    /** Are we embedding the child fragments? */ 
    public boolean           embed       = true;
    final private Context    ctx         = this;
    final private Handler    handler     = new Handler();

    final private Runnable getStatusTask = new Runnable() {
        @Override
        public void run() {
            final boolean ok = fetchStatus();
            handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        dismissLoadingDialog();
                        if (ok)
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
        showLoadingDialog();
        Globals.runOnThreadPool(getStatusTask);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Globals.removeThreadPoolTask(getStatusTask);
        statusDoc = null;
    }

    /**
     * Fetch a new XML status document from the backend
     */
    public boolean fetchStatus() {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        URL url = null;
        try {
            if (Globals.haveServices()) 
                url = new URL(
                    Globals.getBackend().getStatusURL() + "/Status/GetStatus" //$NON-NLS-1$
                );
            else
                url = new URL(Globals.getBackend().getStatusURL() + "/xml"); //$NON-NLS-1$
            LogUtil.debug("Fetching XML from " + url.toString()); //$NON-NLS-1$
            InputStream is = 
                new HttpFetcher(url.toString(), Globals.muxConns)
                    .getInputStream();
            if (is == null)
                throw new IOException(Messages.getString("Status.4")); //$NON-NLS-1$
            statusDoc = dbf.newDocumentBuilder().parse(is);
        } catch (SAXException e) {
            ErrUtil.postErr(ctx, Messages.getString("Status.10")); //$NON-NLS-1$
        } catch (Exception e) { ErrUtil.postErr(ctx, e); }
        
        return statusDoc != null;

    }
    
    /**
     * Get the status XML document
     * @return status XML document
     */
    public Document getStatus() {
        return statusDoc;
    }
    
    private void installFragments() {
        
        if (statusDoc == null) return;
        
        if (!embed) {
            FragmentTransaction ft = 
                getSupportFragmentManager().beginTransaction();
            ft.replace(android.R.id.content, new StatusFragment());
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.commitAllowingStateLoss();
        }
        else {
            FragmentTransaction ft =
                getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.recorderframe, new StatusRecordersFragment());
            ft.replace(R.id.jobframe, new StatusJobsFragment());
            ft.replace(R.id.scheduledframe, new StatusScheduledFragment());
            ft.replace(R.id.backendframe, new StatusBackendFragment());
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            ft.commitAllowingStateLoss();
        }
        
    }
    
    @Override
    protected void resetContentView() {
        if (embed) {
            setContentView(R.layout.status);
            installFragments();
        }
        else
            super.resetContentView();
    }
  
}
