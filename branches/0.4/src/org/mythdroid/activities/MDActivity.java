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
import java.util.HashMap;

import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.frontend.FrontendDB;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;

import android.R.drawable;
import android.R.id;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.database.Cursor;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView.OnItemClickListener;

/** Base class for activities that display frontend choosers
 * and/or loading dialogs - same as MDListActivity */
public abstract class MDActivity extends Activity {

    /** Frontend chooser and loading dialogs */
    final protected static int
        FRONTEND_CHOOSER = -1, DIALOG_LOAD = -2;
    final protected Context ctx = this;

    /**
     * The activity we are on the way to when the frontend chooser dialog
     * finishes
     */
    protected Class<?> nextActivity = null, hereActivity = null;

    /** Extras to put in the intent passed to nextActivity */
    final private ArrayList<String> boolExtras = new ArrayList<String>();
    final private HashMap<String, Integer> intExtras =
        new HashMap<String, Integer>();
    final private HashMap<String, String> stringExtras =
        new HashMap<String, String>();

    private boolean onHere = false;

    /** Start nextActivity when the frontend dialog chooser is finished */
    protected OnDismissListener dismissListener =
        new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (
                    (
                        !onHere && (
                            Globals.defaultFrontend == null ||
                            nextActivity == null
                        )
                    ) ||
                    (onHere && hereActivity == null)
                )
                    return;
                final Intent intent = new Intent().setClass(
                    ctx, onHere ? hereActivity : nextActivity
                );
                for (String extra : boolExtras)
                    intent.putExtra(extra,true);
                for (String extra : intExtras.keySet())
                    intent.putExtra(extra, intExtras.get(extra).intValue());
                for (String extra : stringExtras.keySet())
                    intent.putExtra(extra, stringExtras.get(extra));
                nextActivity = null;
                onHere = false;
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
                final Dialog d = createFrontendDialog();
                d.setOnDismissListener(dismissListener);
                d.setOnCancelListener(cancelListener);
                return d;

            case DIALOG_LOAD:
                final ProgressDialog prog = new ProgressDialog(this);
                prog.setIndeterminate(true);
                prog.setMessage(getResources().getText(R.string.loading));
                prog.setOnCancelListener(
                    new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface arg0) {
                            finish();
                        }
                    }
                );
                return prog;

        }

        return null;

    }

    @Override
    public void onPrepareDialog(int id, final Dialog dialog) {
        switch (id) {
            case FRONTEND_CHOOSER:
                prepareFrontendDialog(dialog);
                return;
            default:
                super.onPrepareDialog(id, dialog);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Globals.appContext == null)
            Globals.appContext = getApplicationContext();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeDialog(FRONTEND_CHOOSER);
    }

    /** Create a dialog allowing user to choose default frontend */
    private Dialog createFrontendDialog() {

        final AlertDialog d = new AlertDialog.Builder(ctx)
            .setItems(new String[] {}, null)
            .setIcon(drawable.ic_menu_upload_you_tube)
            .setTitle(R.string.ch_fe)
            .create();

        d.getListView().setOnItemClickListener(
            new OnItemClickListener() {
                @Override
                public void onItemClick(
                    AdapterView<?> av, View v, int pos, long id
                ) {
                    String fe = (String)av.getAdapter().getItem(pos);
                    if (fe.equals(Messages.getString("MDActivity.0")))  // Here //$NON-NLS-1$
                        onHere = true;
                    else
                        Globals.defaultFrontend = fe;
                    d.dismiss();
                }
            }
        );

        return d;
    }

    private void prepareFrontendDialog(final Dialog dialog) {

        Cursor c = FrontendDB.getFrontends(this);
        ArrayList<String> list = new ArrayList<String>();

        int num = c.getCount();

        if (hereActivity != null && num < 1) {
            ErrUtil.errDialog(ctx, dialog, R.string.no_fes);
            return;
        }

        if (num > 0) {
            c.moveToFirst();
            do  {
                list.add(c.getString(FrontendDB.NAME));
            } while(c.moveToNext());
        }

        c.close();
        FrontendDB.close();

        if (hereActivity != null)
            list.add(Messages.getString("MDActivity.1")); // Here //$NON-NLS-1$

        ((AlertDialog)dialog).getListView().setAdapter(
            new ArrayAdapter<String>(
                ctx, R.layout.simple_list_item_1, id.text1, list
            )
        );

    }

    /**
     * Add "Here" to the frontend chooser and start the provided
     * activity if it's selected
     * @param activity the activity to start
     */
    protected void addHereToFrontendChooser(Class<?> activity) {
        hereActivity = activity;
    }

    /**
     * Add a valueless Extra to the Intent used to start child activities
     * when the frontend chooser finishes
     * @param name String containing the name of the Extra
     */
    protected void setExtra(String name) {
        boolExtras.add(name);
    }

    /**
     * Add an Extra with an integer value to the Intent used to start child
     * activities when the frontend chooser finishes
     * @param name String containing the name of the Extra
     * @param value integer value for the Extra
     */
    protected void setExtra(String name, int value) {
        intExtras.put(name, value);
    }

    /**
     * Add an Extra with an String value to the Intent used to start child
     * activities when the frontend chooser finishes
     * @param name String containing the name of the Extra
     * @param value String value for the Extra
     */
    protected void setExtra(String name, String value) {
        stringExtras.put(name, value);
    }
}
