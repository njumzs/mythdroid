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

package org.mythdroid.frontend;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Manage a sqlite database of frontends */
public class FrontendDB {

    @SuppressWarnings("all")
    final public static int     ID = 0, ADDR = 1, NAME = 2, HWADDR = 3;

    final private static String DB_NAME        = "MythDroid.db"; //$NON-NLS-1$
    final private static int    DB_VERSION     = 3;
    final private static String FRONTEND_TABLE = "frontends"; //$NON-NLS-1$

    private static SQLiteDatabase db           = null;
    private static Cursor         cached       = null;
    private static ArrayList<String> namesList = null;
    
    private static class DBOpenHelper extends SQLiteOpenHelper {

        public DBOpenHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE " + FRONTEND_TABLE + //$NON-NLS-1$
                " (_id INTEGER PRIMARY KEY AUTOINCREMENT" + //$NON-NLS-1$
                ", addr TEXT, name TEXT, hwaddr TEXT);" //$NON-NLS-1$
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
            db.execSQL("DROP TABLE IF EXISTS " + FRONTEND_TABLE); //$NON-NLS-1$
            onCreate(db);
        }

    }

    /**
     * Get a cursor listing the frontends - columns are ID, ADDR and NAME
     * @return Cursor
     */
    public static Cursor getFrontends(Context ctx) {
        if (db == null || cached == null) initDB(ctx);
        return cached;
    }
    
    /**
     * Get an ArrayList of frontend names
     * @param ctx Context
     * @return list of frontend names
     */
    public static ArrayList<String> getFrontendNames(Context ctx) {
        
        if (db == null || cached == null) initDB(ctx);
        
        if (namesList != null)
            return new ArrayList<String>(namesList);
        
        namesList = new ArrayList<String>();
        if (cached.moveToFirst()) { 
            do  {
                namesList.add(cached.getString(NAME));
            } while(cached.moveToNext());
        }
        
        return new ArrayList<String>(namesList);
        
    }
    
    /**
     * Get the name of the first defined frontend
     * @param ctx Context
     * @return name of the first frontend
     */
    public static String getFirstFrontendName(Context ctx) {
        if (db == null || cached == null) initDB(ctx);
        if (cached.getCount() < 1) 
            return null;
        cached.moveToFirst();
        return cached.getString(NAME);
    }
    
    /**
     * Get the addr of the first defined frontend
     * @param ctx Context
     * @return addr of the first frontend
     */
    public static String getFirstFrontendAddr(Context ctx) {
        if (db == null || cached == null) initDB(ctx);
        if (cached.getCount() < 1) 
            return null;
        cached.moveToFirst();
        return cached.getString(ADDR);
    }
    
    /**
     * Get the addr of the frontend with the given name
     * @param ctx Context
     * @param name name of the frontend
     * @return addr of the frontend
     */
    public static String getFrontendAddr(Context ctx, String name) {
        if (db == null || cached == null) initDB(ctx);
        cached.moveToFirst();
        while (!cached.isAfterLast()) {
            if (cached.getString(NAME).equals(name))
                return cached.getString(ADDR);
            cached.moveToNext();
        }
        return null;
    }

    /**
     * Insert a new frontend
     * @param name name of the frontend
     * @param addr address of the frontend
     * @return true if successful, false if a frontend with that name already
     *  existed
     */
    public static boolean insert(
        Context ctx, String name, String addr, String hwaddr
    ) {

        if (db == null) initDB(ctx);

        final ContentValues cv = new ContentValues();
        cv.put("addr", addr.trim()); //$NON-NLS-1$
        cv.put("name", name.trim()); //$NON-NLS-1$
        cv.put("hwaddr", hwaddr != null ? hwaddr.trim() : null); //$NON-NLS-1$

        Cursor c = db.rawQuery(
            "SELECT _id from " + FRONTEND_TABLE + " WHERE name = ?",  //$NON-NLS-1$ //$NON-NLS-2$
            new String[] { name.trim() }
        );

        if (c.getCount() > 0) return false;

        db.insert(FRONTEND_TABLE, null, cv);

        c.close();
        cached.requery();
        namesList = null;
        return true;

    }

    /**
     * Update a frontend record
     * @param id id of frontend record
     * @param name new name of frontend
     * @param addr new address of frontend
     */
    public static void update(
        Context ctx, long id, String name, String addr, String hwaddr
    ) {

        if (db == null) initDB(ctx);

        final ContentValues cv = new ContentValues();
        cv.put("addr", addr.trim()); //$NON-NLS-1$
        cv.put("name", name.trim()); //$NON-NLS-1$
        cv.put("hwaddr", hwaddr != null ? hwaddr.trim() : null); //$NON-NLS-1$
        db.update(
            FRONTEND_TABLE, cv, "_id = ?", new String[] { String.valueOf(id) } //$NON-NLS-1$
        );
        cached.requery();
        namesList = null;
    }

    /**
     * Delete a frontend record
     * @param id id of frontend record
     */
    public static void delete(Context ctx, long id) {
        if (db == null) initDB(ctx);
        db.delete(FRONTEND_TABLE, "_id = ?", //$NON-NLS-1$
            new String[] { String.valueOf(id) });
        cached.requery();
        namesList = null;
    }

    /** Close the frontend database */
    public static void close() {
        if (cached != null) {
            cached.close();
            cached = null;
        }
        if (db != null) {
            if (db.isOpen())
                db.close();
            db = null;
        }
        namesList = null;
    }

    private static void initDB(Context ctx) {
        db = new DBOpenHelper(ctx).getWritableDatabase();
        cached = db.rawQuery(
            "SELECT _id, addr, name, hwaddr from " + FRONTEND_TABLE, null //$NON-NLS-1$
        );
    }

}
