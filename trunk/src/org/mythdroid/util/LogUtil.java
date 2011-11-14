package org.mythdroid.util;

import org.mythdroid.Globals;

import android.util.Log;

/**
 * Debug logging class
 */
/**
 * @author nick
 *
 */
public class LogUtil {
    
    /**
     * Send a message to the device log if debug is enabled
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
     * Send an error message to the device log
     * @param msg message content
     */
    public static void error(String msg) {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[4];
        
        String method = ste.getMethodName();
        Log.e(
            "Error ",  //$NON-NLS-1$
            msg + " in " + method + " at line " + ste.getLineNumber() + //$NON-NLS-1$ //$NON-NLS-2$
              " of " + ste.getFileName() //$NON-NLS-1$
        );
    }
    
    /**
     * Send a warning message to the device log
     * @param msg message content
     */
    public static void warn(String msg) {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[4];
        
        String method = ste.getMethodName();
        Log.w(
            "Warning ",  //$NON-NLS-1$
            msg + " in " + method + " at line " + ste.getLineNumber() + //$NON-NLS-1$ //$NON-NLS-2$
              " of " + ste.getFileName() //$NON-NLS-1$
        );
    }
    
    /**
     * Log a stack trace
     * @param msg String containing tag for log message
     */
    public static void trace(String msg) {
        String st = ""; //$NON-NLS-1$
        StackTraceElement[] elems = Thread.currentThread().getStackTrace();
        for (int i = 3; i < elems.length; i++) {
            String method = elems[i].getMethodName();
            if (method.contains("trace")) continue; //$NON-NLS-1$
            st += elems[i].getMethodName() + "(" + elems[i].getFileName() + //$NON-NLS-1$
                  ":" + elems[i].getLineNumber() + ")\n"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        Log.e(msg, st.trim());
    }
        
}
