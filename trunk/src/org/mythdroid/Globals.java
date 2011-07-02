package org.mythdroid;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.mythdroid.backend.BackendManager;
import org.mythdroid.data.Program;
import org.mythdroid.data.Video;
import org.mythdroid.frontend.FrontendDB;
import org.mythdroid.frontend.FrontendLocation;
import org.mythdroid.frontend.FrontendManager;
import org.mythdroid.resource.Messages;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

/** Contains all globals */
public class Globals {

     /** Debug? */
    final public static boolean debug = true;

    /** Backend protocol version */
    public static int protoVersion  = 0;
    /** Backend version - used only to workaround MythTV r25366 */
    public static int beVersion = 0;

    /** Application context */
    public static Context appContext = null;

    /** The name of the current default frontend */
    public static String defaultFrontend = null;

    /** Backend address from preferences */
    public static String backend = null;
    
    /** Mux connections to the backend host via MDD */
    public static boolean muxConns = false;

    /** To remember where we were */
    public static FrontendLocation lastLocation =
        new FrontendLocation(null, "MainMenu"); //$NON-NLS-1$

    /** A Program representing the currently selected recording */
    public static Program curProg = null;
    /** A Video representing the currently selected video */
    public static Video curVid = null;

    /** SimpleDateFormat of yyyy-MM-dd'T'HH:mm:ss */
    final public static SimpleDateFormat dateFmt =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); //$NON-NLS-1$
    /** SimpleDateFormat of HH:mm, EEE d MMM yy */
    final public static SimpleDateFormat dispFmt =
        new SimpleDateFormat("HH:mm, EEE d MMM yy"); //$NON-NLS-1$
    static {
        dispFmt.setTimeZone(TimeZone.getDefault());
        dateFmt.setTimeZone(TimeZone.getDefault());
    }

    /** A BackendManager representing a connected backend */
    private static BackendManager  beMgr  = null;
    /** A FrontendManager representing a connected frontend */
    private static FrontendManager feMgr  = null;
    /** A handler for the worker thread */
    private static Handler wHandler = null;

    /**
     * Connect to defaultFrontend or the first frontend in the FrontendDB
     * if defaultFrontend is null, returns quickly if the defaultFrontend if
     * already connected
     * @param ctx a Context
     * @return A FrontendManager connected to a frontend or null if there's a
     * problem
     */
    public static FrontendManager getFrontend(Context ctx) throws IOException {

        String name = defaultFrontend;

        // Are we already connected to the desired frontend?
        if (feMgr != null && feMgr.isConnected()) {
            if (name.equals(feMgr.name))
                return feMgr;
            // Wrong frontend, disconnect
            feMgr.disconnect();
            feMgr = null;
        }

        // If unspecified, connect to the first defined frontend
        if (name == null) {
            name = FrontendDB.getFirstFrontendName(ctx);
            if (name == null)
                throw new IOException(Messages.getString("MythDroid.26")); //$NON-NLS-1$
            feMgr = 
                new FrontendManager(name, FrontendDB.getFirstFrontendAddr(ctx));
        }

        // Connect to the specified frontend
        else
            feMgr = new FrontendManager(
                name, FrontendDB.getFrontendAddr(ctx, name)
            );

        
        // Set the default frontend to the newly connected frontend
        if (feMgr != null)
            defaultFrontend = feMgr.name;

        return feMgr;

    }

    /**
     * Connect to a backend.
     *
     * Connect to a specific backend if so configured or locate one otherwise
     * returns quickly if a backend is already connected
     * @return A BackendManager connected to a backend or null if there's a
     * problem
     * @throws IOException
     */
    public static BackendManager getBackend() throws IOException {

        // Check to see if we're already connected to a backend
        if (beMgr != null && beMgr.isConnected())
            return beMgr;
        
        // SSH port forwarding? Mux conns via MDD's CMux if possible
        if (
            backend != null &&
            (backend.equals("127.0.0.1") || backend.equals("localhost")) && //$NON-NLS-1$ //$NON-NLS-2$
            testMuxConn()
        )
            muxConns = true;

        // Connect to the specified backend
        if (backend != null && backend.length() > 0)
            try {
                beMgr = new BackendManager(backend);
            } catch(IOException e) {}
            
        if (beMgr == null) {
            // See if we can locate a backend via UPnP
            beMgr = BackendManager.locate();
            muxConns = false;
        }

        return beMgr;

    }

    /**
     * Get a Handler for the worker thread
     * @return a Handler for the worker thread
     */
    public static Handler getWorker() {

        if (wHandler != null)
            return wHandler;

        final HandlerThread hThread = new HandlerThread(
            "worker", Process.THREAD_PRIORITY_BACKGROUND //$NON-NLS-1$
        );

        hThread.setDaemon(true);
        hThread.start();
        // Wait for the thread to start
        while (!hThread.isAlive()) {}

        if (wHandler == null)
            wHandler = new Handler(hThread.getLooper());
        
        // Reap unused, cached ConnMgrs every 30s
        wHandler.postDelayed(
            new Runnable() {
                @Override
                public void run() {
                    ConnMgr.reapOld();
                    wHandler.postDelayed(this, 30000);
                }
            }, 30000
        );

        return wHandler;

    }

    /** Disconnect and dispose of the currently connected frontend */
    public static void destroyFrontend() {
         if (feMgr != null && feMgr.isConnected())
             feMgr.disconnect();
         feMgr = null;
    }

    /** Disconnect and dispose of the currently connected backend */
    public static void destroyBackend() {
        if (beMgr != null)
            beMgr.done();
        beMgr = null;
    }

    /** Dispose of the worker thread */
    public static void destroyWorker() {
        if (wHandler != null)
            wHandler.getLooper().quit();
        wHandler = null;
    }
    
    /** Test muxed conns, return true if they're available, false otherwise */
    private static boolean testMuxConn() {
        ConnMgr cmgr = null;        
        try {
            cmgr = new ConnMgr(backend, 6543, null, true);
            cmgr.dispose();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

}
