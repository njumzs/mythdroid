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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;

import org.mythdroid.Extras;
import org.mythdroid.R;
import org.mythdroid.backend.BackendManager;
import org.mythdroid.data.Program;
import org.mythdroid.data.Video;
import org.mythdroid.frontend.FrontendDB;
import org.mythdroid.frontend.FrontendLocation;
import org.mythdroid.frontend.FrontendManager;
import org.mythdroid.frontend.WakeOnLan;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.remote.MusicRemote;
import org.mythdroid.remote.NavRemote;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;

import android.R.drawable;
import android.R.id;
import android.R.layout;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
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

public class MythDroid extends MDListActivity implements
    AdapterView.OnItemLongClickListener {

    /** Debug? */
    final public static boolean debug = false;
    /** Backend protocol version */
    public static int protoVersion  = 0;
    /** A BackendManager representing a connected backend */
    public static BackendManager  beMgr  = null;
    /** A FrontendManager representing a connected frontend */
    public static FrontendManager feMgr  = null;
    /** A handler for the worker thread */
    public static Handler wHandler = null;
    /** The name of the current default frontend */
    public static String defaultFrontend = null;

    /** To remember where we were */
    public static FrontendLocation lastLocation  =
        new FrontendLocation("MainMenu");

    /** A Program representing the currently selected recording */
    public static Program curProg = null;
    /** A Video representing the currently selected video */
    public static Video curVid = null;

    /** SimpleDateFormat of yyyy-MM-dd'T'HH:mm:ss */
    final public static SimpleDateFormat dateFmt =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    /** SimpleDateFormat of HH:mm, EEE d MMM yy */
    final public static SimpleDateFormat dispFmt =
        new SimpleDateFormat("HH:mm, EEE d MMM yy");

    static {
        dispFmt.setTimeZone(TimeZone.getDefault());
        dateFmt.setTimeZone(TimeZone.getDefault());
    }

    final private static int 
        MENU_SETTINGS = 0, MENU_FRONTEND = 1, MENU_NAV = 2,
        MENU_WAKE = 3,     MENU_MDD = 4;

    final private static int 
        DIALOG_GUIDE  = 0, WAKE_FRONTEND = 1, MDD_COMMAND = 2;
    
    final private static int PREFS_CODE  = 0;

    /** Entries for the main menu list */
    final private static String[] MenuItems =
        { 
    		Messages.getString("MythDroid.7"),    // Watch TV
    		Messages.getString("MythDroid.8"),    // Recordings
    		Messages.getString("MythDroid.12"),   // Videos
    		Messages.getString("MythDroid.9"),    // Music
    		Messages.getString("MythDroid.10"),   // Guide
    		Messages.getString("MythDroid.11")    // Status
    		
    	};

    /** ListAdapter containing the main menu entries */
    private ArrayAdapter<String> menuAdapter = null;
    private String backend = null;
    
    @Override
    public void onCreate(Bundle icicle) {
        
        super.onCreate(icicle);

        setContentView(R.layout.mainmenu);

        menuAdapter = new ArrayAdapter<String>(
            this, layout.simple_list_item_1, MenuItems
        );

        getPreferences();

        final HandlerThread hThread = new HandlerThread(
            "worker", Process.THREAD_PRIORITY_BACKGROUND
        );

        hThread.setDaemon(true);
        hThread.start();

        while (!hThread.isAlive()) {}

        if (wHandler == null) 
            wHandler = new Handler(hThread.getLooper());

    }

    @Override
    public void onDestroy() {
        
        super.onDestroy();
        if (beMgr != null) 
            try {
                beMgr.done();
            } catch (IOException e) { ErrUtil.err(this, e); }
        beMgr = null;
        if (feMgr != null && feMgr.isConnected()) try {
            feMgr.disconnect();
        } catch (IOException e) { ErrUtil.err(this, e); }
        feMgr = null;
        if (wHandler != null) 
            wHandler.getLooper().quit();
        wHandler = null;
        
    }
    
    @Override 
    public void onResume() {
        
        super.onResume();
        getListView().setOnItemLongClickListener(this);
        
        if (beMgr != null) 
            setListAdapter(menuAdapter);
        else
            findBackend();
        
    }

    @Override
    public void onListItemClick(ListView list, View item, int pos, long id) {
        
        final String action = (String)list.getItemAtPosition(pos);
        Class<?> activity = null;

        if      (action.equals(Messages.getString("MythDroid.7")))
        	activity = TVRemote.class;
        else if (action.equals(Messages.getString("MythDroid.8")))
        	activity = Recordings.class;
        else if (action.equals(Messages.getString("MythDroid.12")))
            activity = Videos.class;
        else if (action.equals(Messages.getString("MythDroid.9")))
        	activity = MusicRemote.class;
        else if (action.equals(Messages.getString("MythDroid.10")))
        	activity = Guide.class;
        else if (action.equals(Messages.getString("MythDroid.11")))
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

        if (action.equals(Messages.getString("MythDroid.7"))) {
            nextActivity = TVRemote.class;
            setExtra(Extras.LIVETV.toString());
            showDialog(FRONTEND_CHOOSER);
        }
        
        else if (action.equals(Messages.getString("MythDroid.9"))) {
            nextActivity = MusicRemote.class;
            showDialog(FRONTEND_CHOOSER);
        }

        else if (action.equals(Messages.getString("MythDroid.10"))) {
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_SETTINGS, Menu.NONE, R.string.settings)
        	.setIcon(drawable.ic_menu_preferences);
        menu.add(Menu.NONE, MENU_FRONTEND, Menu.NONE, R.string.set_def_fe)
            .setIcon(drawable.ic_menu_upload_you_tube);
        menu.add(Menu.NONE, MENU_WAKE, Menu.NONE, R.string.wake_fe)
            .setIcon(drawable.ic_lock_power_off);
        menu.add(Menu.NONE, MENU_NAV, Menu.NONE, R.string.remote)
        	.setIcon(drawable.ic_menu_compass);
        menu.add(Menu.NONE, MENU_MDD, Menu.NONE, R.string.mddCmds)
            .setIcon(drawable.ic_menu_agenda);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case MENU_SETTINGS:
                startActivityForResult(
                    new Intent().setClass(this, Prefs.class), PREFS_CODE
                );
                return true;
            case MENU_FRONTEND:
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
        if (beMgr == null) 
            findBackend();
    }
 
     /**
     * Connect to defaultFrontend or the first frontend in the FrontendDB
     * if defaultFrontend is null, returns quickly if the defaultFrontend if
     * already connected
     * @param ctx - context
     * @return A FrontendManager connected to a frontend or null if there's a 
     * problem
     * @throws IOException 
     */
    public static FrontendManager connectFrontend(Context ctx) throws IOException {

        String name = defaultFrontend;

        if (feMgr != null && feMgr.isConnected()) {
            if (name.equals(feMgr.name))
                return feMgr;
            else
                try {
                    feMgr.disconnect();
                } catch (IOException e) {}
            feMgr = null;
        }

        Cursor c = FrontendDB.getFrontends(ctx);

        if (c.getCount() < 1) {
            ErrUtil.postErr(ctx, Messages.getString("MythDroid.26"));
            c.close();
            return null;
        }

        c.moveToFirst();

        if (name == null) {
            name = c.getString(FrontendDB.NAME);
            feMgr = new FrontendManager(name, c.getString(FrontendDB.ADDR));
        }
        
        else {
            while (!c.isAfterLast()) {
                String n = c.getString(FrontendDB.NAME);
                if (n.equals(name)) {
                    feMgr = new FrontendManager(
                        name, c.getString(FrontendDB.ADDR)
                    );
                    break;
                }
                c.moveToNext();
            }
        }
        
        if (feMgr != null) 
            defaultFrontend = feMgr.name;

        c.close();
        FrontendDB.close();

        return feMgr;

    }
    
    /** Locate and connect to a backend */
    private void findBackend() {

        if (beMgr != null) return;

        final Handler handler = new Handler();
        
        final Runnable found = new Runnable() {
            @Override
            public void run() {

                if (beMgr == null && backend.length() > 0) 
                    try {
                        beMgr = new BackendManager(backend);
                    } catch (Exception e) {
                        ErrUtil.postErr(ctx, e);
                        beMgr = null;
                    } 

                    if (beMgr == null) {
                        ((TextView)findViewById(R.id.emptyMsg))
                        .setText(R.string.no_be);
                        ((TextView)findViewById(R.id.emptyDetail))
                        .setText(R.string.no_be_detail);
                        setListAdapter(
                            new ArrayAdapter<String>(
                                ctx, layout.simple_list_item_1
                            )
                        );
                    }
                    else
                        setListAdapter(menuAdapter);

            }
        };

        wHandler.post(
            new Runnable() {
                @Override
                public void run() {
                    // Auto locate a master backend
                    try {
                        beMgr = BackendManager.locate();
                    } catch (Exception e) { ErrUtil.postErr(ctx, e); }
                
                    handler.post(found);
                }
            }
       );

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

                    if (item.equals(Messages.getString("MythDroid.21"))) {  
                        // Here
                        startActivity(new Intent().setClass(ctx, Guide.class));
                        return;
                    }

                    else if (item.equals(Messages.getString("MythDroid.23"))) { 
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
        });
        
        return d;
    }
    
    private void prepareGuideDialog(final Dialog dialog) {
        
        final ArrayList<String> items = new ArrayList<String>(3);
        items.add(Messages.getString("MythDroid.21"));  // Here
        if (defaultFrontend != null) 
            // On <defaultFrontend>
            items.add(Messages.getString("MythDroid.22") + defaultFrontend); 
        items.add(Messages.getString("MythDroid.23")); // Choose frontend

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
                    c.close();
                    d.dismiss();
                }
            }
        );
        
        return d;
        
    }
    
    private void prepareWakeDialog(final Dialog dialog) {
        
        final SimpleCursorAdapter ca = new SimpleCursorAdapter(
            ctx, R.layout.simple_list_item_1, FrontendDB.getFrontends(ctx),
            new String[] { "name" }, new int[] { id.text1 }
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
                            feMgr.addr,
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
            cmds = MDDManager.getCommands(
                connectFrontend(ctx).addr
            );
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
        final SharedPreferences prefs =
            PreferenceManager.getDefaultSharedPreferences(this);
        backend = prefs.getString("backend", "");
    }

}