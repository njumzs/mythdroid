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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;

import android.R.drawable;
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
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class MythDroid extends MDListActivity implements
    AdapterView.OnItemLongClickListener {

    /** Debug? */
    final public static boolean debug = false;
    /** Backend protocol version */
    final public static int protoVersion  = 40;
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

    /**
     * Intent extras signifying LiveTV, a channel to jump to or to not jumpTo()
     * (for TVRemote) and to jump to guidegrid (for NavRemote)
     */
    final public static String 
        LIVETV = "LiveTV",      JUMPCHAN = "JUMPCHAN", 
        DONTJUMP = "DONTJUMP",  GUIDE = "GUIDE";

    final private static int 
        MENU_SETTINGS = 0, MENU_FRONTEND = 1, MENU_NAV = 2;

    final private static int DIALOG_GUIDE  = 0, PREFS_CODE    = 0;

    /** Entries for the main menu list */
    final private static String[] MenuItems =
        { "Watch TV", "Recordings", "Music", "Guide", "Status" };

    /** ListAdapter containing the main menu entries */
    private ArrayAdapter<String> menuAdapter   = null;
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
            } catch (IOException e) { Util.err(this, e); }
        beMgr = null;
        if (feMgr != null && feMgr.isConnected()) try {
            feMgr.disconnect();
        } catch (IOException e) { Util.err(this, e); }
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

        if      (action.equals("Watch TV"))    activity = TVRemote.class;
        else if (action.equals("Recordings"))  activity = Recordings.class;
        else if (action.equals("Music"))       activity = MusicRemote.class;
        else if (action.equals("Guide"))       activity = Guide.class;
        else if (action.equals("Status"))      activity = Status.class;

        startActivity(
            new Intent().putExtra(LIVETV, true).setClass(this, activity)
        );

    }

    @Override
    public boolean onItemLongClick(
        AdapterView<?> adapter, View item, int pos, long itemid
    ) {
        
        final String action = (String) adapter.getItemAtPosition(pos);

        if (action.equals("Watch TV")) {
            nextActivity = TVRemote.class;
            setExtra(LIVETV);
            showDialog(FRONTEND_CHOOSER);
        }
        
        else if (action.equals("Music")) {
            nextActivity = MusicRemote.class;
            showDialog(FRONTEND_CHOOSER);
        }

        else if (action.equals("Guide")) {
            showDialog(DIALOG_GUIDE);
        }

        return true;

    }

    @Override
    public Dialog onCreateDialog(int id) {

        switch (id) {
            case DIALOG_GUIDE:
                return new AlertDialog.Builder(ctx)
                    .setIcon(drawable.ic_menu_upload_you_tube)
                    .setTitle("Display Guide")
                    .setAdapter(
                        new ArrayAdapter<String>(
                            ctx, R.layout.simple_list_item_1, new String[] {}
                        ), null
                    )
                    .create();
            default:
                return super.onCreateDialog(id);

        }
        
    }

    @Override
    public void onPrepareDialog(int id, final Dialog dialog) {
        
        if (id != DIALOG_GUIDE) return;

        final ArrayList<String> items = new ArrayList<String>();
        items.add("Here");
        if (defaultFrontend != null) 
            items.add("On " + defaultFrontend);
        items.add("Choose frontend");

        final ListView lv = ((AlertDialog)dialog).getListView();
        lv.setAdapter(
            new ArrayAdapter<String>(
                ctx, R.layout.simple_list_item_1, items
            )
        );

        lv.setOnItemClickListener(
            new OnItemClickListener() {
                @Override
                public void onItemClick(
                    AdapterView<?> av, View v, int pos, long id
                ) {
                    
                    String item = (String)av.getItemAtPosition(pos);
                    dialog.dismiss();

                    if (item.equals("Here")) {
                        startActivity(new Intent().setClass(ctx, Guide.class));
                        return;
                    }

                    else if (item.equals("Choose frontend")) {
                        nextActivity = NavRemote.class;
                        setExtra(GUIDE);
                        showDialog(FRONTEND_CHOOSER);
                        return;
                    }

                    else {
                        startActivity(
                            new Intent()
                                .putExtra(GUIDE, true)
                                .setClass(ctx, NavRemote.class)
                        );
                        return;
                    }

                }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_SETTINGS, Menu.NONE, "Settings").setIcon(
            drawable.ic_menu_preferences);
        menu.add(Menu.NONE, MENU_FRONTEND, Menu.NONE, "Set Default Frontend")
            .setIcon(drawable.ic_menu_upload_you_tube);
        menu.add(Menu.NONE, MENU_NAV, Menu.NONE, "Remote").setIcon(
            drawable.ic_menu_compass);
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
     */
    public static FrontendManager connectFrontend(Context ctx) {

        String name = defaultFrontend;

        if (feMgr != null && feMgr.isConnected()) {
            if (name.equals(feMgr.name()))
                return feMgr;
            else
                try {
                    feMgr.disconnect();
                } catch (IOException e) {}
            feMgr = null;
        }

        Cursor c = FrontendDB.getFrontends(ctx);

        if (c.getCount() < 1) {
            Util.posterr(ctx, "No frontends are defined!");
            c.close();
            return null;
        }

        try {
            
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
            
        } catch (IOException e) {
            Util.err(ctx, e);
            feMgr = null;
        }

        if (feMgr != null) 
            defaultFrontend = feMgr.name();

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
                    } catch (IOException e) {
                        Util.posterr(ctx, e);
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
                    } catch (IOException e) { Util.posterr(ctx, e); }
                
                    handler.post(found);
                }
            }
       );

    }

    private void getPreferences() {
        final SharedPreferences prefs =
            PreferenceManager.getDefaultSharedPreferences(this);
        backend = prefs.getString("backend", "");
    }

}