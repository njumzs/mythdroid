package org.mythdroid.util;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.provider.ContactsContract;

/**
 * Wrap classes that are only present in newer SDK versions
 *  so that we can test for their presence and use them if
 *  they're there
 */
public class Reflection {
    
    /**
     * Wrapped StrictMode
     */
    @TargetApi(9)
	public static class rStrictMode {
        
        static {
            try {
                Class.forName("android.os.StrictMode"); //$NON-NLS-1$
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        /**
         * Initialise rStrictMode to see if it's available
         */
        public static void checkAvailable() {}
        
        /**
         * Set a thread policy that allows sockets on the UI
         */
        public static void setThreadPolicy() {
            StrictMode.setThreadPolicy(
                new ThreadPolicy.Builder().permitDiskReads()
                                          .permitDiskWrites()
                                          .permitNetwork()
                                          .penaltyLog()
                                          .build()
            );
        }
        
    }
    
    /**
     * Set a ThreadPolicy that permits network activity if
     * we're on a sufficiently recent version of Android. 
     * Has no effect on pre-Honeycomb devices
     */
    public static void setThreadPolicy() {
        /* Allow network activity on UI thread */
        if (Build.VERSION.SDK_INT >= 11)
            try {
                rStrictMode.checkAvailable();
                rStrictMode.setThreadPolicy();
            } catch (Exception e) {}
    }
    
    /**
     * Wrapped ContactsContract
     */
    @TargetApi(5)
	public static class rContactsContract {
        
        static {
            try {
                Class.forName("android.provider.ContactsContract"); //$NON-NLS-1$
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        /**
         * Initialise rContactsContract to see if it's available
         */
        public static void checkAvailable() {}
        
        /**
         * Return ContactContract.PhoneLookup's CONTENT_FILTER_URI
         */
        public static Uri getContentFilterURI() {
            return ContactsContract.PhoneLookup.CONTENT_FILTER_URI;
        }
        
        /**
         *  Return ContactsContract.PhoneLookup's DISPLAY_NAME
         */
        public static String getDisplayName() {
            return ContactsContract.PhoneLookup.DISPLAY_NAME;
        }
        
    }

}
