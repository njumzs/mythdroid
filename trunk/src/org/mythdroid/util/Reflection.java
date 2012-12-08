package org.mythdroid.util;

import java.lang.reflect.Method;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.provider.ContactsContract;

/**
 * Wrap classes that are only present in newer SDK versions
 * so that we can test for their presence and use them if
 * they're there
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
    
    /**
     * Wrapped ActionBar
     */
    public static class rActionBar {
        
        private static Method setHomeEnabled = null;
        
        static {
            try {
                Class.forName("android.app.ActionBar"); //$NON-NLS-1$
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
		/**
		 * Enable the clicking of the ActionBar app icon taking us home
		 * @param ctx Context of Activity
		 */
		@TargetApi(11)
		public static void setHomeEnabled(Context ctx) {
            
            if (Build.VERSION.SDK_INT < 14) return;
            try {
                if (setHomeEnabled == null)
                    setHomeEnabled =
                        Class.forName("android.app.ActionBar") //$NON-NLS-1$
                            .getMethod("setHomeButtonEnabled", boolean.class); //$NON-NLS-1$
                setHomeEnabled.invoke(((Activity)ctx).getActionBar(), true);
            } catch (Exception e) {}
            
        }
        
    }
      
    /**
     * Enable the clicking of the ActionBar app icon taking us home
     * Has no effect on anything less than ICS
     * @param ctx Context of Activity
     */
    public static void setActionHomeEnabled(Context ctx) {
        if (Build.VERSION.SDK_INT < 14) return;
        try {
            rActionBar.setHomeEnabled(ctx);
        } catch (Exception e) {}
    }
    
    /**
     * Wrapped PowerManager
     */
    public static class rPowerManager {
        
        private static Method isScreenOn = null;
        
        /**
         * Find out whether the screen is on
         * @param pm PowerManager object
         * @return true if it is, false otherwise
         */
		public static boolean isScreenOn(PowerManager pm) {
            
            if (Build.VERSION.SDK_INT < 7) return false;

            try  {
                if (isScreenOn == null)
                    isScreenOn = PowerManager.class.getMethod("isScreenOn"); //$NON-NLS-1$
                return (Boolean) isScreenOn.invoke(pm);
            } catch (Exception e) { return false; }
            
        }
        
    }

}
