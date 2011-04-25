package org.mythdroid.util;

import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;

/**
 * Wrap classes that are only present in newer SDK versions
 *  so that we can test for their presence and use them if
 *  they're there..
 */
public class Reflection {
    
    /**
     * Wrapped StrictMode
     */
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
                new ThreadPolicy.Builder().detectDiskReads()
                                          .detectDiskWrites()
                                          .detectNetwork()
                                          .permitNetwork()
                                          .penaltyLog()
                                          .build()
            );
        }
        
    }

}
