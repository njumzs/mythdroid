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
import java.lang.reflect.InvocationTargetException;

import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.frontend.FrontendDB;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;

import android.R.drawable;
import android.R.id;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.view.Menu;

/** 
 * Base class for activities that display frontend choosers
 * and/or loading dialogs - same as MDActivity except for some sym visibility
 */
public abstract class MDFragmentActivity extends FragmentActivity {
 
    final static int MENU_FRONTEND = 0;

    /** Frontend chooser and loading dialogs */
    final public static int FRONTEND_CHOOSER = -1;
    final protected static int DIALOG_LOAD = -2;
    final protected Context ctx = this;

    /**
     * The activity we are on the way to when the frontend chooser dialog
     * finishes
     */
    public Class<?> nextActivity = null;
    protected Class<?> hereActivity = null;
    
    protected boolean isPaused = false, configChanged = false;

    /** Extras to put in the intent passed to nextActivity */
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
                            Globals.currentFrontend == null ||
                            nextActivity == null
                        )
                    ) ||
                    (onHere && (hereActivity == null || nextActivity == null))
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
                //Reinitialize onHere to False. Incase we have called this function more than once
                //For example by first calling the "Set Current Frontend" menu item and selecting here
                //and then by long-pressing on Watch-TV
                onHere=false;

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
        
        // Reset Frontend Indicator
        if (frontendIndicator != null)
            frontendIndicator.setText(Globals.currentFrontend);
        
        if (Globals.appContext == null)
            Globals.appContext = getApplicationContext();
        
        isPaused = false;
        if (configChanged)
            resetContentView();
        configChanged = false;
    }
    
    @Override
    public void onPause() {
        super.onPause();
        isPaused = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeDialog(FRONTEND_CHOOSER);
    }
    
    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (isPaused)
            configChanged = true;
        else
            resetContentView();
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
                    Globals.currentFrontend = fe;
                    if (frontendIndicator != null) frontendIndicator.setText(fe);
                    if (fe.equals(Messages.getString("MDActivity.0")))  // Here //$NON-NLS-1$
                        onHere = true;
                    d.dismiss();
                }
            }
        );

        return d;
    }

    private void prepareFrontendDialog(final Dialog dialog) {

        ArrayList<String> list = FrontendDB.getFrontendNames(this);

        if (hereActivity != null || nextActivity == null)
            list.add(Messages.getString("MDActivity.0")); // Here //$NON-NLS-1$
        
        if (list.isEmpty()) {
            ErrUtil.errDialog(ctx, dialog, R.string.no_fes);
            return;
        }

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
    public void addHereToFrontendChooser(Class<?> activity) {
        hereActivity = activity;
    }
    
    /**
     * Reset fragments - 
     * Called when configuration (orientation) changes - 
     * Default implementation replaces the fragment occupying the 'content' id
     * with a new instance of the same class
     */
    protected void resetContentView() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment f = null;
        
        try {
            f = fm.findFragmentById(android.R.id.content);
        } catch (Exception e) { 
            ErrUtil.err(this, e);
            return;
        }
        
        if (f == null) {
            ErrUtil.err(this, Messages.getString("MDFragmentActivity.0")); //$NON-NLS-1$
            return;
        }
        
        Bundle args = f.getArguments();
        try {
            f = f.getClass().newInstance();
        } catch (Exception e) {
            ErrUtil.err(this, e);
            return;
        }
        f.setArguments(args);
        
        fm.popBackStackImmediate();
        fm.beginTransaction().replace(android.R.id.content, f)
            .addToBackStack(null).commit();
        fm.executePendingTransactions();
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
     * Add a frontend chooser to the options menu (or action bar on >= 3.0)
     * @param menu menu to add to
     */
    protected void addFrontendChooser(Menu menu) {
        
        MenuItem item = menu.add(
            Menu.NONE, MENU_FRONTEND, Menu.NONE, R.string.set_def_fe
        ).setIcon(drawable.ic_menu_upload_you_tube);
     
        //If we can, add this to the action bar
        try {
            
            MenuItem.class.getMethod(
                "setShowAsAction",  int.class //$NON-NLS-1$
            ).invoke(item, 2);

            View vi = LayoutInflater.from(this).inflate(
                R.layout.frontend_indicator, null
            );

            frontendIndicator = (TextView)vi.findViewById(R.id.text);
            frontendIndicator.setText(Globals.currentFrontend);
            
            LinearLayout indicatorLL =
                (LinearLayout)vi.findViewById(R.id.layout);
            indicatorLL.setFocusable(true);
            indicatorLL.setBackgroundResource(
                android.R.drawable.list_selector_background
            );
            indicatorLL.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick( View v ) {
                       nextActivity = null;
                       showDialog(FRONTEND_CHOOSER);
                    }
                }
            );

            MenuItem.class.getMethod(
                "setActionView", View.class //$NON-NLS-1$
            ).invoke(item, vi);
            
        } catch (NoSuchMethodException e)     {}
          catch (IllegalAccessException e)    {}
          catch (InvocationTargetException e) {}
          
    }
    
}
