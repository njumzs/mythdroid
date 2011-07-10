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
import java.util.ArrayList;

import org.mythdroid.Enums.Extras;
import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.frontend.FrontendDB;
import org.mythdroid.frontend.FrontendManager;
import org.mythdroid.frontend.WakeOnLan;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.receivers.ConnectivityReceiver;
import org.mythdroid.remote.MusicRemote;
import org.mythdroid.remote.NavRemote;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.util.Reflection;

import android.R.drawable;
import android.R.id;
import android.R.layout;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * MDListActivity for the main menu
 * Holds a number of public static members used as 'globals'
 */
public class MythDroid extends MDListActivity implements
    AdapterView.OnItemLongClickListener {

    /** Menu IDs */
    final private static int
        MENU_SETTINGS = 1, MENU_NAV = 2,
        MENU_WAKE = 3,     MENU_MDD = 4;

    /** Dialog IDs */
    final private static int
        DIALOG_GUIDE  = 0, WAKE_FRONTEND = 1, MDD_COMMAND = 2;

    final private static int PREFS_CODE  = 0;

    /** Entries for the main menu list */
    final private static String[] MenuItems =
        {
            Messages.getString("MythDroid.7"),    // Watch TV   //$NON-NLS-1$
            Messages.getString("MythDroid.8"),    // Recordings //$NON-NLS-1$
            Messages.getString("MythDroid.12"),   // Videos     //$NON-NLS-1$
            Messages.getString("MythDroid.9"),    // Music      //$NON-NLS-1$
            Messages.getString("MythDroid.10"),   // Guide      //$NON-NLS-1$
            Messages.getString("MythDroid.11")    // Status     //$NON-NLS-1$
        };

    /** ListAdapter containing the main menu entries */
    private ArrayAdapter<String> menuAdapter = null;

    /** ConnectivityReceiver instance */
    ConnectivityReceiver crecv = null;

    @Override
    public void onCreate(Bundle icicle) {

        super.onCreate(icicle);

        Globals.appContext = getApplicationContext();
        
        if (Globals.currentFrontend == null) {
            Globals.currentFrontend = FrontendDB.getDefault(this);
            if (Globals.currentFrontend == null)
                Globals.currentFrontend = FrontendDB.getFirstFrontendName(this);
        }

        /* Allow network activity on UI thread - we only use it to connect to the
           backend, which we need to do before the UI is usable anyway */
        if (Integer.parseInt(Build.VERSION.SDK) >= 11)
            try {
                Reflection.rStrictMode.checkAvailable();
                Reflection.rStrictMode.setThreadPolicy();
            } catch (Exception e) {}
            
        setContentView(R.layout.mainmenu);

        menuAdapter = new ArrayAdapter<String>(
            this, layout.simple_list_item_1, MenuItems
        );

        getPreferences();

        crecv = new ConnectivityReceiver(Globals.appContext);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Globals.destroyBackend();
        Globals.destroyFrontend();
        FrontendDB.close();
    }

    @Override
    public void onResume() {

        super.onResume();
        getListView().setOnItemLongClickListener(this);

        try {
            if (Globals.getBackend() == null) {
                ((TextView)findViewById(R.id.emptyMsg))
                    .setText(R.string.no_be);
                ((TextView)findViewById(R.id.emptyDetail))
                    .setText(R.string.no_be_detail);
                setListAdapter(null);
            }
            else
                setListAdapter(menuAdapter);
        } catch (IOException e) {
            ErrUtil.err(this, e);
        }

    }

    @Override
    public void onListItemClick(ListView list, View item, int pos, long id) {

        final String action = (String)list.getItemAtPosition(pos);
        Class<?> activity = null;

        if      (action.equals(Messages.getString("MythDroid.7"))) //$NON-NLS-1$
            if (
                 Globals.currentFrontend != null && 
                !Globals.currentFrontend.equals("Here") //$NON-NLS-1$
             ) {
                activity = TVRemote.class;
            } else {
                onItemLongClick(list,item,pos,id);
                return;
            }
        else if (action.equals(Messages.getString("MythDroid.8"))) //$NON-NLS-1$
            activity = Recordings.class;
        else if (action.equals(Messages.getString("MythDroid.12"))) //$NON-NLS-1$
            activity = Videos.class;
        else if (action.equals(Messages.getString("MythDroid.9"))) //$NON-NLS-1$
            if (
                 Globals.currentFrontend != null && 
                !Globals.currentFrontend.equals("Here") //$NON-NLS-1$
            ) {
                activity = MusicRemote.class;
            } else {
                onItemLongClick(list,item,pos,id);
                return;
            }
        else if (action.equals(Messages.getString("MythDroid.10"))) //$NON-NLS-1$
            activity = Guide.class;
        else if (action.equals(Messages.getString("MythDroid.11"))) //$NON-NLS-1$
            activity = Status.class;

        startActivity(
            new Intent().putExtra(
                Extras.LIVETV.toString(), true
            ).setClass(this, activity)
        );

    }

    @Override
    public boolean onItemLongClick(
        AdapterView<?> adapter, View item, int pos, long itemid
    ) {

        final String action = (String) adapter.getItemAtPosition(pos);

        if (action.equals(Messages.getString("MythDroid.7"))) { //$NON-NLS-1$
            nextActivity = TVRemote.class;
            setExtra(Extras.LIVETV.toString());
            showDialog(FRONTEND_CHOOSER);
        }

        else if (action.equals(Messages.getString("MythDroid.9"))) { //$NON-NLS-1$
            nextActivity = MusicRemote.class;
            showDialog(FRONTEND_CHOOSER);
        }

        else if (action.equals(Messages.getString("MythDroid.10"))) { //$NON-NLS-1$
            showDialog(DIALOG_GUIDE);
        }

        return true;

    }

    @Override
    public Dialog onCreateDialog(int id) {

        switch (id) {

            case DIALOG_GUIDE:
                return createGuideDialog();
            case WAKE_FRONTEND:
                return createWakeDialog();
            case MDD_COMMAND:
                return createMddDialog();
            default:
                return super.onCreateDialog(id);

        }

    }

    @Override
    public void onPrepareDialog(int id, final Dialog dialog) {

        switch (id) {

            case DIALOG_GUIDE:
                prepareGuideDialog(dialog);
                return;
            case WAKE_FRONTEND:
                prepareWakeDialog(dialog);
                return;
            case MDD_COMMAND:
                prepareMddDialog(dialog);
                return;
            default:
                super.onPrepareDialog(id, dialog);
        }

    }

    /** Populate the pop-up menu */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        addFrontendChooser(menu);

        menu.add(Menu.NONE, MENU_SETTINGS, Menu.NONE, R.string.settings)
            .setIcon(drawable.ic_menu_preferences);
        menu.add(Menu.NONE, MENU_WAKE, Menu.NONE, R.string.wake_fe)
            .setIcon(drawable.ic_lock_power_off);
        menu.add(Menu.NONE, MENU_NAV, Menu.NONE, R.string.remote)
            .setIcon(drawable.ic_menu_compass);
        menu.add(Menu.NONE, MENU_MDD, Menu.NONE, R.string.mddCmds)
            .setIcon(drawable.ic_menu_agenda);
        return true;
    }

    /** Handle pop-up menu item selection */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case MENU_SETTINGS:
                startActivityForResult(
                    new Intent().setClass(this, Prefs.class), PREFS_CODE
                );
                return true;
            case MENU_FRONTEND:
                nextActivity=null;
                showDialog(FRONTEND_CHOOSER);
                return true;
            case MENU_WAKE:
                showDialog(WAKE_FRONTEND);
                return true;
            case MENU_MDD:
                showDialog(MDD_COMMAND);
                return true;
            case MENU_NAV:
                startActivity(new Intent().setClass(this, NavRemote.class));
                return true;
            default:
                return false;
        }

    }

    /** When the preferences are finished */
    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data) {
        super.onActivityResult(reqCode, resCode, data);
        getPreferences();
        try {
            Globals.getBackend();
        } catch (IOException e) {
            ErrUtil.err(this, e);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
    }

    private Dialog createGuideDialog() {

        final AlertDialog d = new AlertDialog.Builder(ctx)
            .setItems(new String[] {}, null)
            .setIcon(drawable.ic_menu_upload_you_tube)
            .setTitle(R.string.disp_guide)
            .create();

        d.getListView().setOnItemClickListener(
            new OnItemClickListener() {
                @Override
                public void onItemClick(
                    AdapterView<?> av, View v, int pos, long id
                ) {

                    String item = (String)av.getItemAtPosition(pos);
                    d.dismiss();

                    if (item.equals(Messages.getString("MythDroid.21"))) {   //$NON-NLS-1$
                        // Here
                        startActivity(new Intent().setClass(ctx, Guide.class));
                        return;
                    }

                    else if (item.equals(Messages.getString("MythDroid.23"))) {  //$NON-NLS-1$
                        // Choose frontend
                        nextActivity = NavRemote.class;
                        setExtra(Extras.GUIDE.toString());
                        showDialog(FRONTEND_CHOOSER);
                        return;
                    }

                    else {
                        startActivity(
                            new Intent()
                                .putExtra(Extras.GUIDE.toString(), true)
                                .setClass(ctx, NavRemote.class)
                        );
                        return;
                    }

                }
            }
        );

        return d;
    }

    private void prepareGuideDialog(final Dialog dialog) {

        final ArrayList<String> items = new ArrayList<String>(3);
        items.add(Messages.getString("MythDroid.21"));  // Here //$NON-NLS-1$
        if (Globals.currentFrontend != null)
            // On <defaultFrontend>
            items.add(
                Messages.getString("MythDroid.22") + Globals.currentFrontend //$NON-NLS-1$
            );
        items.add(Messages.getString("MythDroid.23")); // Choose frontend //$NON-NLS-1$

        final ListView lv = ((AlertDialog)dialog).getListView();
        lv.setAdapter(
            new ArrayAdapter<String>(
                ctx, R.layout.simple_list_item_1, items
            )
        );

        return;

    }

    private Dialog createWakeDialog() {

        final AlertDialog d = new AlertDialog.Builder(ctx)
            .setItems(new String[] {}, null)
            .setIcon(drawable.ic_lock_power_off)
            .setTitle(R.string.wake_fe)
            .create();

        d.getListView().setOnItemClickListener(
            new OnItemClickListener() {
                @Override
                public void onItemClick(
                    AdapterView<?> av, View v, int pos, long id
                ) {
                    Cursor c =
                        ((SimpleCursorAdapter)av.getAdapter()).getCursor();
                    c.moveToPosition(pos);
                    try {
                        WakeOnLan.Wake(c.getString(FrontendDB.HWADDR));
                    } catch (Exception e) { ErrUtil.err(ctx, e); }
                    Globals.currentFrontend = c.getString(FrontendDB.NAME);
                    c.close();
                    d.dismiss();
                }
            }
        );

        return d;

    }

    private void prepareWakeDialog(final Dialog dialog) {

        final SimpleCursorAdapter ca = new SimpleCursorAdapter(
            ctx, R.layout.simple_list_item_1, FrontendDB.getFrontends(this),
            new String[] { "name" }, new int[] { id.text1 } //$NON-NLS-1$
        );

        if (ca.getCount() < 1) {
            ErrUtil.errDialog(ctx, dialog, R.string.no_fes);
            return;
        }

        ((AlertDialog)dialog).getListView().setAdapter(ca);

    }

    private Dialog createMddDialog() {

        final AlertDialog d = new AlertDialog.Builder(ctx)
            .setItems(new String[] {}, null)
            .setIcon(drawable.ic_menu_agenda)
            .setTitle(R.string.mddCmd)
            .create();

        d.getListView().setOnItemClickListener(
            new OnItemClickListener() {
                @Override
                public void onItemClick(
                    AdapterView<?> av, View v, int pos, long id
                ) {
                    try {
                        MDDManager.mddCommand(
                            Globals.getFrontend(ctx).addr,
                            (String)av.getItemAtPosition(pos)
                        );
                    } catch (IOException e) { ErrUtil.postErr(ctx, e); }
                    d.dismiss();
                }
            }
        );

        return d;

    }

    private void prepareMddDialog(final Dialog dialog) {

        ArrayList<String> cmds = null;

        try {
            FrontendManager femgr = Globals.getFrontend(this);
            if (femgr == null) {
                ErrUtil.errDialog(ctx, dialog, R.string.no_fes);
                return;
            }
            cmds = MDDManager.getCommands(femgr.addr);
        } catch(IOException e) {
            ErrUtil.err(ctx, e);
        }

        if (cmds == null || cmds.isEmpty()) {
            ErrUtil.errDialog(ctx, dialog, R.string.no_mddCmds);
            return;
        }

        ((AlertDialog)dialog).getListView().setAdapter(
            new ArrayAdapter<String>(
                ctx, R.layout.simple_list_item_1, cmds
            )
         );

        return;

    }

    private void getPreferences() {
        Globals.backend =
            PreferenceManager.getDefaultSharedPreferences(this)
                .getString("backendAddr", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

}
