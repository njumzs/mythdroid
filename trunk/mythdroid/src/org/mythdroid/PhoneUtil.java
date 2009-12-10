package org.mythdroid;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Contacts;
import android.provider.Contacts.PeopleColumns;
import android.telephony.PhoneNumberUtils;

public final class PhoneUtil {
    
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
