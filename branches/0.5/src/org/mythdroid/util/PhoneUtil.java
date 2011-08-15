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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Contacts;

/** Utility methods for dealing with Contacts */
@SuppressWarnings("deprecation")
public final class PhoneUtil {

    /**
     * Get a contact name from a phone number
     * @param ctx Context
     * @param number String containing the phone number
     */
    public static String nameFromNumber(Context ctx, String number) {
        
        String name = null;
        
        /* Use ContractsContract via reflection on Froyo and above */        
        if (Integer.parseInt(Build.VERSION.SDK) >= 5)
            try {
                Reflection.rContactsContract.checkAvailable();
                Uri uri = Uri.withAppendedPath(
                    Reflection.rContactsContract.getContentFilterURI(),
                    Uri.encode(number)
                );
                Cursor c = ctx.getContentResolver().query(
                    uri, 
                    new String[]{Reflection.rContactsContract.getDisplayName()},
                    null,null,null
                );
                if (c != null && c.moveToFirst()) name = c.getString(0);
                if (c != null) c.close();
                return name;
            } catch (Exception e) {}

        /* Old method for Donut and below */
        Cursor c = ctx.getContentResolver().query(
            Uri.withAppendedPath(
                Contacts.Phones.CONTENT_FILTER_URL, Uri.encode(number)
            ),
            new String[] { Contacts.Phones.DISPLAY_NAME },
            null, null, null
        );
        if (c != null && c.moveToFirst()) name = c.getString(0);
        if (c != null) c.close();
        return name;

    }

}
