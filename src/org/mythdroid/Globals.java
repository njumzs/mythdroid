package org.mythdroid;

import java.io.IOException;
import java.net.SocketException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.mythdroid.backend.BackendManager;
import org.mythdroid.data.Program;
import org.mythdroid.data.Video;
import org.mythdroid.frontend.FrontendLocation;
import org.mythdroid.frontend.FrontendManager;
import org.mythdroid.receivers.ConnectivityReceiver;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ConnMgr;
import org.mythdroid.util.DatabaseUtil;
import org.mythdroid.cache.ImageCache;
import org.mythdroid.util.UPnPListener;

import android.annotation.SuppressLint;
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
    // public static int protoVersion    = 0;
    /** Backend version - used only to workaround MythTV r25366 */
    // public static int beVersion       = 0;

    /** Application context */
    public static Context appContext  = null;
    /** The name of the current frontend */
    public static String curFe        = null;
    /** The name of the previous frontend (used by TVRemote's moveTo) */
    public static String prevFe       = null;
    /** Backend address from preferences */
    public static String backend      = null;
    /** Mux connections to the backend host via MDD */
    public static boolean muxConns    = false;
    /** A Program representing the currently selected recording */
    public static Program curProg     = null;
    /** A Video representing the currently selected video */
    public static Video curVid        = null;
    
    /** An ImageCache for artwork */
    public static ImageCache artCache = new ImageCache(
        "artwork", Runtime.getRuntime().maxMemory() / 4, //$NON-NLS-1$
        Runtime.getRuntime().maxMemory() / 16, 1024*1024*128
    );
    
    /** SimpleDateFormat of yyyy-MM-dd'T'HH:mm:ss */
    @SuppressLint("SimpleDateFormat")
    final private static SimpleDateFormat dateFmt =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); //$NON-NLS-1$
    /** SimpleDateFormat of HH:mm, EEE d MMM yy */
    @SuppressLint("SimpleDateFormat")
    final private static SimpleDateFormat dispFmt =
        new SimpleDateFormat("HH:mm, EEE d MMM yy"); //$NON-NLS-1$
    /** SimpleDataFormat like dateFmt but in UTC */
    @SuppressLint("SimpleDateFormat")
    final private static SimpleDateFormat utcFmt = 
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); //$NON-NLS-1$;
    static {
        dispFmt.setTimeZone(TimeZone.getDefault());
        dateFmt.setTimeZone(TimeZone.getDefault());
        utcFmt.setTimeZone(TimeZone.getTimeZone("UTC")); //$NON-NLS-1$
    }
    
    /** To remember where we were */
    private static FrontendLocation lastLocation =
        new FrontendLocation(null, "MainMenu"); //$NON-NLS-1$
    /** The global UPnPListener */
    private static UPnPListener upnp      = null;
    private static Object upnpLock        = new Object();
    /** A BackendManager representing a connected backend */
    private static BackendManager  beMgr  = null;
    private static Object beMgrLock       = new Object();
    /** A FrontendManager representing a connected frontend */
    private static FrontendManager feMgr  = null;
    private static Object feMgrLock       = new Object();
    /** A handler for the worker thread */
    private static Handler wHandler       = null;
    private static Object wHandlerLock    = new Object();
    /** A list of addresses that have been checked for updates */
    private static ArrayList<String> updateChecked = new ArrayList<String>(4);
    /** The queue for the thread pool */
    private static LinkedBlockingQueue<Runnable> threadQueue = null;
    private static Object threadQueueLock                    = new Object();
    /** An ExecutorService for accessing the thread pool */
    private static ThreadPoolExecutor threadPool             = null;
    private static Object threadPoolLock                     = new Object();
    /** A ThreadGroup for the pool threads */
    private static ThreadGroup poolGroup     = new ThreadGroup("GlobalPool"); //$NON-NLS-1$
    private static ThreadFactory poolFactory = new ThreadFactory() {
        int num = 0;
        @Override
        public Thread newThread(Runnable r) {
            Thread thr = new Thread(poolGroup, r, "poolThread-" + num++); //$NON-NLS-1$
            thr.setDaemon(true);
            thr.setPriority(Thread.NORM_PRIORITY - 1);
            return thr;
        }
    };
    
    /* We'll keep a reference to this in our Application class so that
       the GC doesn't get rid of unreferenced members occasionally */
    private static Globals instance = new Globals();
    
    private Globals() {}
    
    /** 
     * Get a reference to the singleton Globals object 
     * @return the singleton Globals object
     */
    public static Globals getInstance(Context ctx) {
        appContext = ctx;
        return instance;
    }

    /**
     * Is the currentFrontend set to 'Here'? 
     * @return true if so, false otherwise
     */
    public static boolean isCurrentFrontendHere() {
        if (curFe == null)
            return false;
        return curFe.equals(Messages.getString("MDActivity.0")); //$NON-NLS-1$
    }
    
    /**
     * Connect to defaultFrontend or the first frontend in the FrontendDB
     * if defaultFrontend is null, returns quickly if the defaultFrontend if
     * already connected
     * @param ctx a Context
     * @return A FrontendManager connected to a frontend or null if there's a
     * problem
     */
    public static FrontendManager getFrontend(final Context ctx)
        throws IOException {

        String name = curFe;

        synchronized (feMgrLock) {
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
                if ((name = DatabaseUtil.getFirstFrontendName(ctx)) == null)
                    throw new IOException(Messages.getString("Globals.1")); //$NON-NLS-1$
                feMgr = 
                    new FrontendManager(
                        name, DatabaseUtil.getFirstFrontendAddr(ctx)
                    );
            }
            // Check if the current frontend is set to 'Here'
            else if (name.equals(Messages.getString("MDActivity.0"))) //$NON-NLS-1$
                throw new IOException(Messages.getString("Globals.0")); //$NON-NLS-1$
    
            // Connect to the specified frontend
            else
                feMgr = new FrontendManager(
                    name, DatabaseUtil.getFrontendAddr(ctx, name)
                );

            curFe = feMgr.name;
            return feMgr;
        }

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

        synchronized (beMgrLock) {
        
            // Check to see if we're already connected to a backend
            if (beMgr != null && beMgr.isConnected())
                return beMgr;
            
            // Reset muxConns
            muxConns = false;
            
            boolean mobile =
                ConnectivityReceiver.networkType() ==
                    ConnectivityManager.TYPE_MOBILE;
            
            /* SSH port forwarding or not connected to wifi? 
               Mux conns via MDD's CMux if possible */
            if (
                backend != null &&
                (
                    backend.equals("127.0.0.1") || //$NON-NLS-1$
                    backend.equals("localhost") || //$NON-NLS-1$
                    mobile
                ) && 
                testMuxConn()
            )
                muxConns = true;
    
            // Connect to the specified backend
            if (backend != null && backend.length() > 0)
                try {
                    beMgr = new BackendManager(backend);
                } catch (IOException e) {}
            
            if (beMgr != null && beMgr.isConnected()) return beMgr;
            
            // If we're not on wifi, maybe there's a ssh tunnel we can use?
            if (mobile) {
                try {
                    beMgr = new BackendManager("localhost"); //$NON-NLS-1$
                } catch (IOException e) {}        
                if (beMgr != null && beMgr.isConnected()) return beMgr;
            }
            
            // See if we can locate a backend via UPnP
            try {
                beMgr = new BackendManager(
                    getUPnPListener().findMasterBackend(950)
                );
            } catch (IOException e) {}
            
            if (beMgr != null && beMgr.isConnected()) {
                muxConns = false;
                return beMgr;
            }
            
            // See if we can use a muxed connection to the specified backend        
            if (backend != null && testMuxConn()) {
                muxConns = true;
                beMgr = new BackendManager(backend);
                return beMgr;
            }
        
        }
        
        throw new IOException(Messages.getString("BackendManager.2")); //$NON-NLS-1$

    }
    
    /**
     * Get the protocol version of the currently connected backend
     * @return Protocol version of connected backend or 0 if no backend is 
     * connected
     */
    public static int protoVersion() {
        synchronized (beMgrLock) {
            if (beMgr != null)
                return beMgr.protoVersion;
        }
        return 0;
    }
    
    /**
     * Setup the global Date formatters with a given timezone, which would
     * usually be obtained from the backend we're connected to
     * @param zone String containing name of the timezone
     */
    public static void setBackendTimezone(String zone) {
        TimeZone tz = TimeZone.getTimeZone(zone);
        synchronized (dispFmt) { dispFmt.setTimeZone(tz); }
        synchronized (dateFmt) { dateFmt.setTimeZone(tz); }
    }
    
    /**
     * Returns true if the backend supports the Services API
     * @return true if the backend supports the Services API, false otherwise
     */
    public static boolean haveServices() {
        synchronized (beMgrLock) {
            if (beMgr != null && beMgr.protoVersion >= 72)
                return true;
            return false;
        }
    }
    
    /**
     * Format a Date like "yyyy-MM-dd'T'HH:mm:ss" in the local/backend timezone
     * @param date Date to format
     * @return String
     */
    public static String dateFormat(Date date) {
        synchronized (dateFmt) { return dateFmt.format(date); }
    }
    
    /**
     * Parse a string in the format "yyyy-MM-dd'T'HH:mm:ss" and local/backend 
     * timezone
     * @param date String to parse
     * @return a Date
     * @throws ParseException
     */
    public static Date dateParse(String date) throws ParseException {
        synchronized (dateFmt) { return dateFmt.parse(date); }
    }
    
    /**
     * Format a Date like "HH:mm, EEE d MMM yy" in the local/backend timezone
     * @param date Date to format
     * @return String
     */
    public static String dispFormat(Date date) {
        synchronized (dispFmt) { return dispFmt.format(date); }
    }
    
    /**
     * Parse a string in the format "HH:mm, EEE d MMM yy" and local/backend 
     * timezone
     * @param date String to parse
     * @return a Date
     * @throws ParseException
     */
    public static Date dispParse(String date) throws ParseException {
        synchronized (dispFmt) { return dispFmt.parse(date); }
    }

    /**
     * Format a Date like "yyyy-MM-dd'T'HH:mm:ss" in UTC
     * @param date Date to format
     * @return String
     */
    public static String utcFormat(Date date) {
        synchronized (utcFmt) { return utcFmt.format(date); }
    }
    
    /**
     * Parse a string in the format "yyyy-MM-dd'T'HH:mm:ss" and UTC 
     * @param date String to parse
     * @return a Date
     * @throws ParseException
     */
    public static Date utcParse(String date) throws ParseException {
        synchronized (utcFmt) { return utcFmt.parse(date); }
    }

    /**
     * Run a Runnable on the background worker thread
     * Runnable's will be run consecutively in the order they are submitted
     */
    public static void runOnWorker(final Runnable r) {
        synchronized (wHandlerLock) {
            if (wHandler == null)
                wHandler = createWorker();
            wHandler.post(r);
        }
    }
    
    /**
     * Schedule a Runnable to be run in the future on the worker thread
     * @param r Runnable
     * @param delay time in milliseconds before it will be run
     */
    public static void scheduleOnWorker(final Runnable r, int delay) {
        synchronized (wHandlerLock) {
            if (wHandler == null)
                wHandler = createWorker();
            wHandler.postDelayed(r, delay);
        }
    }
    
    /**
     * Run a Runnable on the global thread pool
     * Runnable's might be run concurrently but will be started in the order
     * they are submitted
     * @param r
     */
    public static void runOnThreadPool(final Runnable r) {
        
        synchronized (threadQueueLock) {
            if (threadQueue == null)
                threadQueue = new LinkedBlockingQueue<Runnable>();
        }
        synchronized (threadPoolLock) {
            if (threadPool == null)
                threadPool = new ThreadPoolExecutor(
                    2, 8, 30, TimeUnit.SECONDS, threadQueue, poolFactory
                );
            threadPool.execute(r);
        }
        
    }
    
    /**
     * Remove instances of the specified task from the global thread pool queue
     * @param r
     */
    public static void removeThreadPoolTask(final Runnable r) {
        synchronized (threadPoolLock) {
            if (threadPool == null) return;
            threadPool.remove(r);
        }
    }
    
    /** Clear the queue of tasks awaiting execution on the global thread pool */
    public static void removeAllThreadPoolTasks() {
        synchronized (threadQueueLock) {
            if (threadQueue == null) return;
            threadQueue.clear();
        }
    }
    
    /**
     * Get the global UPnPListener
     * @return the global UPnPListener
     * @throws SocketException
     */
    public static UPnPListener getUPnPListener() throws SocketException {
        synchronized (upnpLock) {
            if (upnp != null) return upnp;
            upnp = new UPnPListener();
            return upnp;
        }
    }

    /** Disconnect and dispose of the currently connected frontend */
    public static void destroyFrontend() {
        synchronized (feMgrLock) {
            if (feMgr != null && feMgr.isConnected())
                feMgr.disconnect();
            feMgr = null;
        }
    }

    /** Disconnect and dispose of the currently connected backend */
    public static void destroyBackend() {
        synchronized (beMgrLock) {
            if (beMgr != null)
                beMgr.done();
            beMgr = null;
        }
    }

    /** Dispose of the worker thread */
    public static void destroyWorker() {
        synchronized (wHandlerLock) {
            if (wHandler != null)
                wHandler.getLooper().quit();
            wHandler = null;
        }
    }
    
    /**
     * Update the global lastLocation field, used to remember the last
     * location of a frontend prior to a jump elsewhere
     * @param loc FrontendLocation representing the last location
     */
    public static void setLastLocation(final FrontendLocation loc) {
        /* We don't want to store locations that might result in a loop
           if we jump to them, or that we can't jump to anyway */
        if (
            loc == null || loc.video || loc.music ||
            loc.location.endsWith(".xml") //$NON-NLS-1$
        ) return;
        lastLocation = loc;
    }
    
    /** Get the global lastLocation */
    public static FrontendLocation getLastLocation() {
        return lastLocation;
    }
    
    /**
     * Check whether the supplied address has already been checked for updates
     * and mark it so if not
     * @param addr address to check
     * @return true if already checked, false otherwise
     */
    public static boolean checkedForUpdate(final String addr) {
        
        if (
            PreferenceManager.getDefaultSharedPreferences(appContext)
                .getBoolean("disableUpdateNotif", false) //$NON-NLS-1$
        )
            return true;
        
        if (updateChecked.contains(addr)) return true;
        updateChecked.add(addr);
        return false;
    }
    
    /** Create a new worker thread, return the Handler for it */
    private static Handler createWorker() {
        final HandlerThread hThread = new HandlerThread(
            "worker", Process.THREAD_PRIORITY_BACKGROUND //$NON-NLS-1$
        );
        hThread.setDaemon(true);
        hThread.start();
        // Wait for the thread to start
        while (!hThread.isAlive()) {}
        return new Handler(hThread.getLooper());
    }
    
    /** Test muxed conns, return true if they're available, false otherwise */
    private static boolean testMuxConn() {
        ConnMgr cmgr = null;        
        try {
            cmgr = new ConnMgr(backend, 6543, null, true);
            cmgr.dispose();
        } catch (IOException e) { return false; }
        return true;
    }

}
