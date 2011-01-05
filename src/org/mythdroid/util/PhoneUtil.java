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
import android.provider.Contacts;
import android.provider.Contacts.PeopleColumns;
import android.telephony.PhoneNumberUtils;

/** Utility methods for dealing with Contacts **/
public final class PhoneUtil {

    /**
     * Get a contact id from a phone number
     * @param ctx Context
     * @param number String containing the phone number
     */
    public static String idFromNumber(Context ctx, String number) {

        String id = null;

        Cursor c = ctx.getContentResolver().query(
            Uri.withAppendedPath(Contacts.Phones.CONTENT_FILTER_URL, number),
            new String[] { Contacts.Phones.PERSON_ID },
            null, null, null
        );

        if (c != null && c.getCount() > 0) {
            c.moveToFirst();
            id = String.valueOf(c.getLong(0));
        }

        if (c != null) c.close();

        return id;

    }

    /**
     * Get a contact name from a contact id
     * @param ctx Context
     * @param id String containing the contact id
     */
    public static String nameFromId(Context ctx, String id) {

        String name = null;

        Cursor c = ctx.getContentResolver().query(
            Uri.withAppendedPath(Contacts.People.CONTENT_FILTER_URI, id),
            new String[] { PeopleColumns.DISPLAY_NAME },
            null, null, null
        );

        if (c != null && c.getCount() > 0) {
            c.moveToFirst();
            name = c.getString(0);
        }

        if (c != null) c.close();

        return name;

    }

    /**
     * Get a contact name from a phone number
     * @param ctx Context
     * @param number String containing the phone number
     */
    public static String nameFromNumber(Context ctx, String number) {

        String id = idFromNumber(ctx, number);

        if (id == null)
            return PhoneNumberUtils.formatNumber(number);

        String name = nameFromId(ctx, id);

        if (name == null)
            return PhoneNumberUtils.formatNumber(number);

        return name;

    }

}
