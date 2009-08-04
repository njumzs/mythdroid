/*
    MythDroid: Android MythTV Remote
    
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

package org.mythdroid;

import java.util.ArrayList;
import java.util.HashMap;

import android.R.drawable;
import android.R.id;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.database.Cursor;
import android.widget.SimpleCursorAdapter;

/** Base class for activities that display frontend choosers 
 * and/or loading dialogs - same as MDActivity */
public abstract class MDListActivity extends ListActivity {

    /** A frontend chooser dialog */
    final protected static int FRONTEND_CHOOSER = -1, DIALOG_LOAD = -2;
    final protected Context ctx = this;
    
    /** 
     * The activity we are on the way to when the frontend chooser dialog
     * finishes
     */
    protected Class<?> nextActivity = null;

    /** Extras to put in the intent passed to nextActivity */
    final private ArrayList<String> boolExtras = new ArrayList<String>();
    final private HashMap<String, Integer> intExtras = 
        new HashMap<String, Integer>();

    /** Start nextActivity when the frontend dialog chooser is finished */
    protected OnDismissListener dismissListener =
        new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (MythDroid.defaultFrontend == null || nextActivity == null)
                    return;
                FrontendDB.close();
                final Intent intent = new Intent().setClass(ctx, nextActivity);
                for (String extra : boolExtras)
                    intent.putExtra(extra,true);
                for (String extra : intExtras.keySet())
                    intent.putExtra(extra, intExtras.get(extra).intValue());
                nextActivity = null;
                startActivity(intent);
            }
        };

    /** Clear nextActivity if the frontend chooser is cancelled */ 
    protected OnCancelListener cancelListener =
        new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                nextActivity = null;
            }
        };

    @Override
    public Dialog onCreateDialog(int id) {

        switch (id) {

            case FRONTEND_CHOOSER:
                final Dialog d = frontendChooser(this);
                
                if (d == null)
                    return new AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage("No frontends are defined!")
                        .setPositiveButton("OK", 
                            new OnClickListener() {
                                @Override
                                public void onClick(
                                    DialogInterface dialog, int which
                                ) {
                                    dialog.dismiss();
                                }
                            }
                        )
                        .create();
                
                d.setOnDismissListener(dismissListener);
                d.setOnCancelListener(cancelListener);

                return d;

            case DIALOG_LOAD:
                final ProgressDialog prog = new ProgressDialog(this);
                prog.setIndeterminate(true);
                prog.setMessage("Loading");
                return prog;
        
        }

        return null;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeDialog(FRONTEND_CHOOSER);
    }

    /** Create a dialog allowing user to choose default frontend */
    public static Dialog frontendChooser(Context ctx) {

        final SimpleCursorAdapter ca = new SimpleCursorAdapter(
            ctx, R.layout.simple_list_item_1, FrontendDB.getFrontends(ctx),
            new String[] { "name" }, new int[] { id.text1 }
        );

        if (ca.getCount() < 1) return null;

        return new AlertDialog.Builder(ctx)
            .setAdapter(ca,
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Cursor c = ca.getCursor();
                        c.moveToPosition(which);
                        MythDroid.defaultFrontend = 
                            c.getString(FrontendDB.NAME);
                        dialog.dismiss();
                    }
                }
            )
            .setIcon(drawable.ic_menu_upload_you_tube)
            .setTitle("Choose frontend")
            .create();

    }

    protected void setExtra(String name) {
        boolExtras.add(name);
    }

    protected void setExtra(String name, int value) {
        intExtras.put(name, value);
    }
}

