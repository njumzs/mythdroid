package org.mythdroid.util;

import org.mythdroid.Globals;

import android.util.Log;

/**
 * Debug logging class
 */
public class LogUtil {
    
    /**
     * Print a message to the device log if debug is enabled
     * @param msg message content
     */
    public static void debug(String msg) {
        if (!Globals.debug)
            return;
        String caller = 
            Thread.currentThread().getStackTrace()[3].getClassName();
        Log.d(caller.substring(caller.lastIndexOf('.') + 1), msg);
    }
    
    /**
     * Print an error message to the device log
     * @param msg message content
     */
    public static void error(String msg) {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[4];
        
        String method = ste.getMethodName();
        Log.e(
            "Error",  //$NON-NLS-1$
            msg + " in " + method + " at line " + ste.getLineNumber() + //$NON-NLS-1$ //$NON-NLS-2$
              " of " + ste.getFileName() //$NON-NLS-1$
        );
    }
        
}
