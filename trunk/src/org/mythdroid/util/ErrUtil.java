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

package org.mythdroid.util;

import org.acra.ACRA;
import org.mythdroid.R;
import org.mythdroid.activities.FrontendList;
import org.mythdroid.resource.Messages;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/** Error reporting utility methods */
final public class ErrUtil {

    /**
     * Inform user of an exception - call from UI thread
     * @param c context
     * @param e exception whose message we will display
     */
    static public void err(final Context c, final Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            msg = e.getClass().getName();
            LogUtil.error(msg);
            msg = Messages.getString("ErrUtil.0"); //$NON-NLS-1$
        }
        Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
    }

    /**
     * Inform user of a message - call from UI thread
     * @param c context
     * @param e message to display
     */
    static public void err(final Context c, final String e) {
        Toast.makeText(c, e, Toast.LENGTH_LONG).show();
        Log.e("MythDroid", e); //$NON-NLS-1$
    }

    /**
     * Inform user of an exception - call from non-UI thread
     * @param c context
     * @param e exception whose message we will display
     */
    static public void postErr(final Context c, final Exception e) {
        ((Activity)c).runOnUiThread(
            new Runnable() {
                @Override
                public void run() {
                    String msg = e.getMessage();
                    if (msg == null) {
                        msg = e.getClass().getName();
                        LogUtil.error(msg);
                        msg = Messages.getString("ErrUtil.0"); //$NON-NLS-1$
                    }
                    Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
                }
            }
        );
    }

    /**
     * Inform user of a message - call from non-UI thread
     * @param c context
     * @param e message to display
     */
    static public void postErr(final Context c, final String e) {
        ((Activity)c).runOnUiThread(
            new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(c, e, Toast.LENGTH_LONG).show();
                    Log.e("MythDroid", e); //$NON-NLS-1$
                }
            }
        );
    }
    
    /**
     * Send an error message to the device log
     * @param e String containing error message
     */
    static public void logErr(final String e) {
        LogUtil.error(e);
    }
    
    /**
     * Log an exception to the device log
     * @param e Exception to log
     */
    static public void logErr(final Exception e) {
        String msg = e.getMessage();
        if (msg == null)
            msg = e.getClass().getName();
        LogUtil.error(msg);
    }
    
    /**
     * Send a warning message to the device log
     * @param e String containing message
     */
    static public void logWarn(final String e) {
        LogUtil.warn(e);
    }
    
    /**
     * Log an exception to the device log as a warning
     * @param e Exception to log
     */
    static public void logWarn(final Exception e) {
        String msg = e.getMessage();
        if (msg == null)
            msg = e.getClass().getName();
        LogUtil.warn(msg);
    }
    
    /**
     * Send an error report
     * @param e Exception to report
     */
    static public void report(Exception e) {
        Runtime r = Runtime.getRuntime();
        ACRA.getErrorReporter().putCustomData(
            "MemFree ", String.valueOf(r.freeMemory() / 1024) + "K" //$NON-NLS-1$ //$NON-NLS-2$
        );
        ACRA.getErrorReporter().putCustomData(
            "MemTotal", String.valueOf(r.totalMemory() / 1024) + "K" //$NON-NLS-1$ //$NON-NLS-2$
        );
        ACRA.getErrorReporter().putCustomData(
            "MemMax  ", String.valueOf(r.maxMemory() / 1024) + "K" //$NON-NLS-1$ //$NON-NLS-2$
        );
        ACRA.getErrorReporter().handleSilentException(e);
    }
    
    /**
     * Send an error report
     * @param msg message to report
     */
    static public void report(String msg) {
        Exception e = new Exception(msg);
        report(e);
    }
    
    /**
     * Inform user of an exception and report it - call from UI thread
     * @param c context
     * @param e exception whose message we will display
     */
    static public void reportErr(final Context c, final Exception e) {
        err(c, e);
        report(e);
    }
    
    /**
     * Inform user of a message and report it - call from UI thread
     * @param c context
     * @param e message to display
     */
    static public void reportErr(final Context c, final String e) {
        err(c, e);
        report(e);
    }

    /**
     * Inform user of an exception and report it - call from non-UI thread
     * @param c context
     * @param e exception whose message we will display
     */
    static public void postReportErr(final Context c, final Exception e) {
        postErr(c, e);
        report(e);
    }

    /**
     * Inform user of a message - call from non-UI thread
     * @param c context
     * @param e message to display
     */
    static public void postReportErr(final Context c, final String e) {
        postErr(c, e);
        report(e);
    }

    /**
     * Put an error message and 'OK' button in a dialog
     * @param c context
     * @param dialog dialog to insert error message into
     * @param msgId ID for a String resource containing the error message
     */
    static public void errDialog(
        final Context c, final Dialog dialog, final int msgId, final int id
    ) {
        errDialog(c, dialog, c.getResources().getString(msgId), id);
    }
    
    /**
     * Put an error message and 'OK' button in a dialog
     * @param c context
     * @param dialog dialog to insert error message into
     * @param msg String containing the error message
     */
    static public void errDialog(
        final Context c, final Dialog dialog, final String msg, final int id
    ) {
        
        AlertDialog ad = (AlertDialog)dialog;
        View v = LayoutInflater.from(c).inflate(R.layout.error_dialog, null);
        
        ((TextView)v.findViewById(R.id.message)).setText(msg);

        ((Button)v.findViewById(R.id.errButton)).setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((Activity)c).removeDialog(id);
                }
            }
        );
        
        if (msg.equals(c.getResources().getString(R.string.noFes))) {
            
            Button edit = ((Button)v.findViewById(R.id.editButton));
            edit.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((Activity)c).removeDialog(id);
                        c.startActivity(
                            new Intent().setClass(c, FrontendList.class)
                        );
                    }
                }
            );
            edit.setVisibility(View.VISIBLE);
        }
        
        ViewGroup vg = (ViewGroup)ad.findViewById(android.R.id.content);
        vg.removeAllViews();
        vg.addView(v);
        
    }

}
