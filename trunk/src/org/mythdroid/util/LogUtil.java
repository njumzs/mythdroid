package org.mythdroid.util;

import org.mythdroid.Globals;

import android.util.Log;

/**
 * Debug logging class
 */
public class LogUtil {
    
    final private static int maxMsgLen = 300;    
    
    /**
     * Send a message to the device log if debug is enabled
     * @param msg message content
     */
    public static void debug(String msg) {
        if (!Globals.debug)
            return;
        String caller = 
            Thread.currentThread().getStackTrace()[3].getClassName();
        Log.d(caller.substring(caller.lastIndexOf('.') + 1), limit(msg));
    }
    
    /**
     * Send an error message to the device log
     * @param msg message content
     */
    public static void error(String msg) {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[4];
        
        Log.e(
            "MythDroid",  //$NON-NLS-1$
            limit(msg) + " in " + ste.getMethodName() + " at line " +  //$NON-NLS-1$ //$NON-NLS-2$
                ste.getLineNumber() + " of " + ste.getFileName() //$NON-NLS-1$
        );
    }
    
    /**
     * Send a warning message to the device log
     * @param msg message content
     */
    public static void warn(String msg) {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[4];
        
        Log.w(
            "MythDroid",  //$NON-NLS-1$
            limit(msg) + " in " + ste.getMethodName() + " at line " +  //$NON-NLS-1$ //$NON-NLS-2$
                ste.getLineNumber() + " of " + ste.getFileName() //$NON-NLS-1$
        );
    }
    
    /**
     * Log a stack trace
     * @param msg String containing tag for log message
     */
    public static void trace(String msg) {
        StringBuilder st = new StringBuilder(16);
        StackTraceElement[] elems = Thread.currentThread().getStackTrace();
        for (int i = 3; i < elems.length; i++) {
            String method = elems[i].getMethodName();
            if (method.contains("trace")) continue; //$NON-NLS-1$
            st.append(elems[i].getMethodName()).append("(") //$NON-NLS-1$
                .append(elems[i].getFileName()).append(':')
                    .append(elems[i].getLineNumber()).append(")\n"); //$NON-NLS-1$
        }
        Log.e(msg, st.toString().trim());
    }
    
    private static String limit(String msg) {
        if (msg.length() <= maxMsgLen)
            return msg;
        return msg.substring(0, maxMsgLen) + " ..."; //$NON-NLS-1$
    }
        
}
