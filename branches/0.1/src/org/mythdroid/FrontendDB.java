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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Manage a sqlite database of frontends */
public class FrontendDB {

    /** ints representing the columns of ID, ADDR and NAME */
    final public static int     ID = 0, ADDR = 1, NAME = 2;

    final private static String DB_NAME        = "MythDroid.db";
    final private static int    DB_VERSION     = 2;
    final private static String FRONTEND_TABLE = "frontends";

    private static SQLiteDatabase db             = null;

    private static class DBOpenHelper extends SQLiteOpenHelper {

        public DBOpenHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE " + FRONTEND_TABLE +
                " (_id INTEGER PRIMARY KEY AUTOINCREMENT" +
                ", addr TEXT, name TEXT);"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
            db.execSQL("DROP TABLE IF EXISTS " + FRONTEND_TABLE);
            onCreate(db);
        }

    }

    /**
     * Get a cursor listing the frontends - columns are ID, ADDR and NAME
     * @param ctx - context
     * @return Cursor 
     */
    public static Cursor getFrontends(Context ctx) {
        if (db == null) initDB(ctx);
        return db.rawQuery(
            "SELECT _id, addr, name from " + FRONTEND_TABLE, null
        );

    }

    /**
     * Insert a new frontend
     * @param ctx - context
     * @param name - name of the frontend
     * @param addr - address of the frontend
     * @return true if successful, 
     * false if a frontend with that name already existed
     */
    public static boolean insert(Context ctx, String name, String addr) {

        if (db == null) initDB(ctx);

        final ContentValues cv = new ContentValues();
        cv.put("addr", addr.trim());
        cv.put("name", name.trim());

        Cursor c = db.rawQuery(
            "SELECT _id from " + FRONTEND_TABLE + " WHERE name = ?", 
            new String[] { name.trim() }
        );

        if (c.getCount() > 0) return false;

        db.insert(FRONTEND_TABLE, null, cv);

        c.close();
        return true;
        
    }

    /**
     * Update a frontend record
     * @param ctx - context
     * @param id - id of frontend record
     * @param name - new name of frontend
     * @param addr - new address of frontend
     */
    public static void update(Context ctx, long id, String name, String addr) {

        if (db == null) initDB(ctx);

        final ContentValues cv = new ContentValues();
        cv.put("addr", addr.trim());
        cv.put("name", name.trim());
        db.update(
            FRONTEND_TABLE, cv, "_id = ?", new String[] { String.valueOf(id) }
        );
    }

    /**
     * Delete a frontend record
     * @param ctx - context
     * @param id - id of frontend record
     */
    public static void delete(Context ctx, long id) {
        if (db == null) initDB(ctx);
        db.delete(FRONTEND_TABLE, "_id = ?",
            new String[] { String.valueOf(id) });
    }

    /** Close the frontend database */
    public static void close() {
        if (db == null) return;
        db.close();
        db = null;
    }

    private static void initDB(Context ctx) {
        db = new DBOpenHelper(ctx).getWritableDatabase();
    }

}