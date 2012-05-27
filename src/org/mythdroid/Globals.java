package org.mythdroid;

import java.io.IOException;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.mythdroid.backend.BackendManager;
import org.mythdroid.data.Program;
import org.mythdroid.data.Video;
import org.mythdroid.frontend.FrontendDB;
import org.mythdroid.frontend.FrontendLocation;
import org.mythdroid.frontend.FrontendManager;
import org.mythdroid.receivers.ConnectivityReceiver;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.UPnPListener;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.preference.PreferenceManager;

/** Global static variables and methods */
public class Globals {

     /** Debug? */
    final public static boolean debug = true;

    /** Backend protocol version */
    public static int protoVersion  = 0;
    /** Backend version - used only to workaround MythTV r25366 */
    public static int beVersion = 0;

    /** Application context */
    public static Context appContext = null;

    /** The name of the current frontend */
    public static String currentFrontend = null;

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
    /** SimpleDataFormat like dateFmt but in UTC */
    final public static SimpleDateFormat utcFmt = 
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); //$NON-NLS-1$;
    static {
        dispFmt.setTimeZone(TimeZone.getDefault());
        dateFmt.setTimeZone(TimeZone.getDefault());
        utcFmt.setTimeZone(TimeZone.getTimeZone("UTC")); //$NON-NLS-1$
    }
    
    private static UPnPListener upnp = null;

    /** A BackendManager representing a connected backend */
    private static BackendManager  beMgr  = null;
    /** A FrontendManager representing a connected frontend */
    private static FrontendManager feMgr  = null;
    /** A handler for the worker thread */
    private static Handler wHandler = null;
    /** The queue for the thread pool */
    private static LinkedBlockingQueue<Runnable> threadQueue = null;
    /** An ExecutorService for accessing the thread pool */
    private static ThreadPoolExecutor threadPool = null;
    /** A list of addresses that have been checked for updates */
    private static ArrayList<String> updateChecked = new ArrayList<String>(4);

    /**
     * Is the currentFrontend set to 'Here'? 
     * @return true if so, false otherwise
     */
    public static boolean isCurrentFrontendHere() {
        if (currentFrontend == null)
            return false;
        return currentFrontend.equals(Messages.getString("MDActivity.0")); //$NON-NLS-1$
    }
    
    /**
     * Connect to defaultFrontend or the first frontend in the FrontendDB
     * if defaultFrontend is null, returns quickly if the defaultFrontend if
     * already connected
     * @param ctx a Context
     * @return A FrontendManager connected to a frontend or null if there's a
     * problem
     */
    public static FrontendManager getFrontend(Context ctx) throws IOException {

        String name = currentFrontend;

        // Are we already connected to the desired frontend?
        if (feMgr != null && feMgr.isConnected()) {
            if (name != null && name.equals(feMgr.name))
                return feMgr;
            // Wrong frontend, disconnect
            feMgr.disconnect();
            feMgr = null;
        }

        // If unspecified, connect to the first defined frontend
        if (name == null) {
            if ((name = FrontendDB.getFirstFrontendName(ctx)) == null)
                throw new IOException(Messages.getString("Globals.1")); //$NON-NLS-1$
            feMgr = 
                new FrontendManager(name, FrontendDB.getFirstFrontendAddr(ctx));
        }
        
        else if (name.equals(Messages.getString("MDActivity.0"))) //$NON-NLS-1$
            throw new IOException(Messages.getString("Globals.0")); //$NON-NLS-1$

        // Connect to the specified frontend
        else
            feMgr = new FrontendManager(
                name, FrontendDB.getFrontendAddr(ctx, name)
            );

        currentFrontend = feMgr.name;
        return feMgr;

    }

    /**
     * Connect to a backend.
     *
     * Connect to a specific backend if so configured or locate one otherwise
     * returns quickly if a backend is already connected
     * @return A BackendManager connected to a backend
     * @throws IOException if we can't find or connect to a backend
     */
    public static BackendManager getBackend() throws IOException {

        // Check to see if we're already connected to a backend
        if (beMgr != null && beMgr.isConnected())
            return beMgr;
        
        /* SSH port forwarding or not connected to wifi? 
           Mux conns via MDD's CMux if possible */
        if (
            backend != null &&
            (
                backend.equals("127.0.0.1") || backend.equals("localhost") || //$NON-NLS-1$ //$NON-NLS-2$
                ConnectivityReceiver.networkType() ==
                    ConnectivityManager.TYPE_MOBILE
            ) && 
            testMuxConn()
        )
            muxConns = true;

        // Connect to the specified backend
        if (backend != null && backend.length() > 0)
            try {
                beMgr = new BackendManager(backend);
            } catch (IOException e) {}
            
        if (beMgr == null || !beMgr.isConnected()) {
            // See if we can locate a backend via UPnP
            beMgr = new BackendManager(
                Globals.getUPnPListener().findMasterBackend(550)
            );
            muxConns = false;
        }

        return beMgr;

    }
    
    /**
     * Returns true if the backend supports the Services API
     * @return true if the backend supports the Services API, false otherwise
     */
    public static boolean haveServices() {
        return protoVersion >= 72;
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
    
    /**
     * Run a Runnable on the global thread pool
     * @param r
     */
    public static void runOnThreadPool(Runnable r) {
        
        if (threadQueue == null)
            threadQueue = new LinkedBlockingQueue<Runnable>();
        if (threadPool == null) {
            threadPool = new ThreadPoolExecutor(
                8, 8, 30, TimeUnit.SECONDS, threadQueue
            );
            threadPool.allowCoreThreadTimeOut(true);
        }
        threadPool.execute(r);
        
    }
    
    /** Cancel tasks awaiting execution on the global thread pool */
    public static void cancelThreadPoolTasks() {
        
        if (threadQueue == null) return;
        threadQueue.clear();
        
    }
    
    /**
     * Get the global UPnPListener
     * @return the global UPnPListener
     * @throws SocketException
     */
    public static UPnPListener getUPnPListener() throws SocketException {
        
        if (upnp != null)
            return upnp;
        
        upnp = new UPnPListener();
        return upnp;
        
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
    
    /**
     * Check whether the supplied address has already been checked for updates
     * mark it so if not
     * @param addr address to check
     * @return true if already checked, false otherwise
     */
    public static boolean checkedForUpdate(String addr) {
        
        if (
            PreferenceManager.getDefaultSharedPreferences(appContext)
                .getBoolean("disableUpdateNotif", false) //$NON-NLS-1$
        )
            return true;
        
        if (updateChecked.contains(addr)) return true;
        updateChecked.add(addr);
        return false;
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
