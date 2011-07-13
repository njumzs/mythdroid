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

import org.mythdroid.R;
import org.mythdroid.resource.Messages;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.util.Log;
import android.widget.Toast;

/** General utility methods */
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
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * Inform user of a message - call from UI thread
     * @param c context
     * @param e message to display
     */
    static public void err(final Context c, final String e) {
        Toast.makeText(c, e, Toast.LENGTH_SHORT).show();
        Log.e("Error", e); //$NON-NLS-1$
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
                    Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(c, e, Toast.LENGTH_SHORT).show();
                    Log.e("Error", e); //$NON-NLS-1$
                }
            }
        );
    }

    /**
     * Put an error message and 'OK' button in a dialog
     * @param c context
     * @param dialog dialog to insert error message into
     * @param msgId ID for a String resource containing the error message
     */
    static public void errDialog(
        final Context c, final Dialog dialog, final int msgId
    ) {
        ((AlertDialog)dialog).setMessage(
            c.getResources().getString(msgId)
        );

        ((AlertDialog)dialog).setButton(
            AlertDialog.BUTTON_POSITIVE,
            c.getResources().getString(R.string.ok),
            new OnClickListener() {
                @Override
                public void onClick(
                    DialogInterface dialog, int which
                ) {
                    dialog.dismiss();
                }
            }
        );
    }

}
