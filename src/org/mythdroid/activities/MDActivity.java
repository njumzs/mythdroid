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
import org.mythdroid.resource.Messages;
import org.mythdroid.util.DatabaseUtil;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.util.Reflection;
import org.mythdroid.views.ActionView;

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
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;
import android.view.View.OnClickListener;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;

/** 
 * Base class for activities that display frontend choosers
 * and/or loading dialogs - same as MDListActivity 
 */
public abstract class MDActivity extends Activity {

    final static int MENU_FRONTEND = 0;

    /** Frontend chooser and loading dialogs */
    final protected static int FRONTEND_CHOOSER = -1, DIALOG_LOAD = -2;
    final protected Context ctx = this;
    
    private Handler handler = new Handler();

    /**
     * The activity we are on the way to when the frontend chooser dialog
     * finishes
     */
    protected Class<?> nextActivity = null, hereActivity = null;

    /** Extras to put in the intent passed to nextActivity */
    private Bundle bundleExtras = null;
    final private ArrayList<String> boolExtras = new ArrayList<String>();
    final private HashMap<String, Integer> intExtras =
        new HashMap<String, Integer>();
    final private HashMap<String, String> stringExtras =
        new HashMap<String, String>();

    /** ActionBar Indicator for Frontend */
    private TextView frontendIndicator = null;
    
    private boolean onHere = false;

    /** Start nextActivity when the frontend dialog chooser is finished */
    protected OnDismissListener dismissListener =
        new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (
                    (
                        !onHere && (
                            Globals.curFe == null || nextActivity == null
                        )
                    ) ||
                    (onHere && (hereActivity == null || nextActivity == null))
                )
                    return;
                final Intent intent = new Intent().setClass(
                    ctx, onHere ? hereActivity : nextActivity
                );
                if (bundleExtras != null)
                    intent.putExtras(bundleExtras);
                for (String extra : boolExtras)
                    intent.putExtra(extra, true);
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
                prog.setCanceledOnTouchOutside(false);
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
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Reflection.setThreadPolicy();
        Reflection.setActionHomeEnabled(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateFrontendIndicator();
    }
    
    @Override
    public void onSaveInstanceState(Bundle icicle) {
        removeDialog(DIALOG_LOAD);
        super.onSaveInstanceState(icicle);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeDialog(FRONTEND_CHOOSER);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent intent = new Intent(this, Main.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return true;
        }
        return false;
    }

    /** Create a dialog allowing user to choose default frontend */
    private Dialog createFrontendDialog() {

        final AlertDialog d = new AlertDialog.Builder(ctx)
            .setItems(new String[] {}, null)
            .setIcon(drawable.ic_menu_upload_you_tube)
            .setTitle(R.string.chFe)
            .create();

        d.getListView().setOnItemClickListener(
            new OnItemClickListener() {
                @Override
                public void onItemClick(
                    AdapterView<?> av, View v, int pos, long id
                ) {
                    onHere = false;
                    String fe = (String)av.getAdapter().getItem(pos);
                    Globals.curFe = fe;
                    updateFrontendIndicator();
                    if (fe.equals(Messages.getString("MDActivity.0"))) // Here //$NON-NLS-1$
                        onHere = true;
                    d.dismiss();
                }
            }
        );

        return d;
    }

    private void prepareFrontendDialog(final Dialog dialog) {

        final ArrayList<String> list = DatabaseUtil.getFrontendNames(this);

        if (hereActivity != null || nextActivity == null)
            list.add(Messages.getString("MDActivity.0")); // Here //$NON-NLS-1$
        
        if (list.isEmpty()) {
            ErrUtil.errDialog(ctx, dialog, R.string.noFes, FRONTEND_CHOOSER);
            return;
        }

        ((AlertDialog)dialog).getListView().setAdapter(
            new ArrayAdapter<String>(
                ctx, R.layout.simple_list_item_1, id.text1, list
            )
        );

    }
    
    /** Show the loading dialog */
    protected void showLoadingDialog() {
        handler.post(
            new Runnable() {
                @Override
                public void run() {
                    showDialog(DIALOG_LOAD);
                }
            }
        );
    }

    /** Dismiss the loading dialog */
    protected void dismissLoadingDialog() {
        handler.post(
            new Runnable() {
                @Override
                public void run() {
                    try {
                        dismissDialog(DIALOG_LOAD);
                    } catch (IllegalArgumentException e) {}
                }
            }
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
    
    /**
     * Add an Bundle of Extras to the Intent used to start child
     * activities when the frontend chooser finishes
     * @param extras Bundle of extras
     */
    protected void setExtras(Bundle extras) {
        bundleExtras = extras;
    }

    /**
     * Add a frontend chooser to the options menu (or action bar on >= 3.0)
     * @param menu menu to add to
     */
    protected void addFrontendChooser(Menu menu) {
        
        final MenuItem item = menu.add(
            Menu.NONE, MENU_FRONTEND, Menu.NONE, R.string.setCurFe
        ).setIcon(drawable.ic_menu_upload_you_tube);
     
        if (
            MenuItemCompat.setShowAsAction(
                item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS
            )
        ) {
            
            ActionView vi = (ActionView) LayoutInflater.from(this).inflate(
                R.layout.frontend_indicator, null
            );
    
            vi.setFocusable(true);

            vi.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick( View v ) {
                       nextActivity = null;
                       showDialog(FRONTEND_CHOOSER);
                    }
                }
            );
            
            frontendIndicator = (TextView)vi.findViewById(R.id.actionItemText);
            updateFrontendIndicator();
    
            MenuItemCompat.setActionView(item, vi);
        
        }
          
    }
    
    private void updateFrontendIndicator() {
        if (frontendIndicator == null) return;
        if (Globals.curFe != null)
            frontendIndicator.setText(Globals.curFe);
        else
            frontendIndicator.setText(R.string.none);
    }
    
}
