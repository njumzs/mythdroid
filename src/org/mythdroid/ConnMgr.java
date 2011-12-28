/*
    MythDroid: Android MythTV Remote
    Copyright (C) 2009-2010 foobum@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.mythdroid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.mythdroid.receivers.ConnectivityReceiver;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.LogUtil;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;

/** A TCP connection manager */
public class ConnMgr {

    /**
     * Read timeout values for use with setTimeout()
     */
    public enum timeOut {
        /** The default read timeout */
        DEFAULT,
        /** 5x the default read timeout */
        LONG,
        /** 10x the default read timeout */
        EXTRALONG,
        /** never timeout */
        INFINITE
    }
    
    /** 
     * Implement this to create a callback that'll be called upon a 
     * successful connection 
     */
    public interface onConnectListener {
        /**
         * Called when the connection has been successfully established
         * @param cmgr the ConnMgr instance
         */
        public void onConnect(ConnMgr cmgr) throws IOException;
    }

    /** The address of the remote host in 'host:port' form */
    public  String                  addr             = null;

    /** An array of weak references to current connections */
    final private static ArrayList<WeakReference<ConnMgr>> conns =
        new ArrayList<WeakReference<ConnMgr>>(8);

    /** Receive buffer size */
    final private static int        rbufSize         = 128;
    /** Maximum age of unused connections in milliseconds */
    final private static int        maxAge           = 60000;
    
    /** List of onConnect callbacks */
    final private ArrayList<onConnectListener> oCLs = 
        new ArrayList<onConnectListener>();
        
    /** A lock for operations that modify sock, inUse or reconnectPending */
    final private Object            sockLock         = new Object();
    
    /** A weak reference to ourself */
    private WeakReference<ConnMgr>  weakThis         = null;
    /** Our socket */
    private Socket                  sock             = null;
    /** The sockaddr of the remote host */
    private InetSocketAddress       sockAddr         = null;
    /** Our outputstream */
    private OutputStream            os               = null;
    /** Our inputstream */
    private InputStream             is               = null;
    /** Current index into receive buffer */
    private int                     rbufIdx          = -1;
    /** Our receive buffer */
    private byte[]                  rbuf             = null;
    /** Default socket timeout for connect and read */
    private int                     timeout          = 2000;
    /** Hostname of the remote host */
    private String                  hostname         = null;
    private WifiLock                wifiLock         = null;
    /** Is this socket in use, connected and ready for IO? */
    private boolean                 inUse            = false;
    /** Date of last use */
    private long                    lastUsed         = -1;
    /** Is a reconnect pending due to connectivity changes? */
    private boolean                 reconnectPending = false;
    /** The most recently transmitted message */
    private byte[]                  lastSent         = null;
    /** The timeOut modifier for the next read */
    private timeOut                 timeOutModifier  = timeOut.DEFAULT;
    
    /**
     * Make a connection, reuse an existing connection if possible
     * @param host String with hostname or dotted decimal IP address
     * @param port integer port number
     */
    public static ConnMgr connect(String host, int port) throws IOException {
        ConnMgr cmgr = null;
        if ((cmgr = findExisting(host, port)) != null)
            return cmgr;
        return new ConnMgr(host, port, null, false);
    }
        
    /**
     * Make a connection, reuse an existing connection if possible
     * @param host String with hostname or dotted decimal IP address
     * @param port integer port number
     * @param ocl callback to call upon successful connection
     */
    public static ConnMgr connect(String host, int port, onConnectListener ocl)
        throws IOException {

        ConnMgr cmgr = null;
        if ((cmgr = findExisting(host, port)) != null)
            return cmgr;
        return new ConnMgr(host, port, ocl, false);
        
    }
    
    /**
     * Make a connection, reuse an existing connection if possible
     * @param host String with hostname or dotted decimal IP address
     * @param port integer port number
     * @param ocl callback to call upon successful connection
     * @param mux connection will be muxed via MDD if true
     */
    public static ConnMgr connect(
        final String host, final int port, onConnectListener ocl, boolean mux
    ) throws IOException {
        
        ConnMgr cmgr = null;
        if ((cmgr = findExisting(host, port)) != null)
            return cmgr;
        return new ConnMgr(host, port, ocl, mux);
        
    }
    
    /**
     * Constructor
     * @param host String with hostname or dotted decimal IP address
     * @param port integer port number
     * @param ocl callback to call upon successful connection
     * @param mux connection will be muxed via MDD if true
     */
    public ConnMgr(
        final String host, final int port, onConnectListener ocl, boolean mux
    ) throws IOException {

        if (host == null || host.length() < 1)
            throw new IOException(Messages.getString("ConnMgr.8")); //$NON-NLS-1$
        
        if (port < 0 || port > 65535)
            throw new IOException(Messages.getString("ConnMgr.9") + port); //$NON-NLS-1$
        
        if (mux)
            // Add a callback that sets up the muxed connection
            oCLs.add(
                new onConnectListener() {
                    @Override
                    public void onConnect(ConnMgr cmgr) throws IOException {
                        byte[] buf = new byte[512];
                        synchronized (cmgr.is) {
                            // Tell CMux which port we want to connect to
                            cmgr.write(String.valueOf(port).getBytes());
                            // Get the response
                            cmgr.read(buf, 0, 2);
                            if (buf[0] == 'O' && buf[1] == 'K')
                                return;
                            // There was a problem, read the rest of the err msg
                            cmgr.read(buf, 2, 510);
                        }
                        throw new IOException(new String(buf));
                    }
                }
            );
        
        sockAddr = new InetSocketAddress(host, mux ? 16550 : port);

        if (sockAddr == null || sockAddr.getAddress() == null)
            throw new IOException(Messages.getString("ConnMgr.6") + host); //$NON-NLS-1$
        
        hostname = host;
        addr = host + ":" + port; //$NON-NLS-1$

        if (ocl != null)
            oCLs.add(ocl);
        
        boolean wifi = 
                ConnectivityReceiver.networkType() == 
                    ConnectivityManager.TYPE_WIFI;

        // Increase default socket timeout if we're on a slow link
        if (wifi) {
            if (sockAddr.getAddress().isLoopbackAddress())
                // SSH port forward we guess
                timeout *= 5;
        }
        else
            timeout *= 5;

        doConnect(timeout);
        
        if (wifi) { 
            wifiLock = ((WifiManager)Globals.appContext
                .getSystemService(Context.WIFI_SERVICE))
                .createWifiLock("MythDroid"); //$NON-NLS-1$
            wifiLock.acquire();
        }

        // Add a weak reference to ourselves to the static connection list
        weakThis = new WeakReference<ConnMgr>(this);
        synchronized (conns) { conns.add(weakThis); }
        
    }

    /**
     * Set the read timeout for the next read
     * @param time a ConnMgr.timeOut value
     */
    public void setTimeout(final timeOut time) {
        timeOutModifier = time;
    }
    
    /**
     * Set the read timeout to infinity for all reads
     */
    public void setIndefiniteReads() {
        timeOutModifier = timeOut.INFINITE;
        try {
            sock.setSoTimeout(0);
        } catch (SocketException e) {}
    }

    /**
     * Write a line of text to the socket
     * @param str string to write, will have '\n' appended if necessary
     */
    public void writeLine(String str) throws IOException {

        if (str.endsWith("\n")) //$NON-NLS-1$
            write(str.getBytes());
        else {
            str += "\n"; //$NON-NLS-1$
            write(str.getBytes());
        }

        LogUtil.debug("writeLine: " + str); //$NON-NLS-1$

    }

    /**
     * Write a string to the socket, prefixing with 8 chars of length
     * @param str string to write
     */
    public void sendString(String str) throws IOException {

        str = String.format("%-8d", str.length()) + str; //$NON-NLS-1$

        LogUtil.debug("sendString: " + str); //$NON-NLS-1$

        write(str.getBytes());

    }

    /**
     * Separate and write a stringlist to the socket
     * @param list Array of strings to write
     */
    public void sendStringList(final String[] list) throws IOException {

        String str = list[0];

        for (int i = 1; i < list.length; i++) {
            str += "[]:[]" + list[i]; //$NON-NLS-1$
        }

        sendString(str);
    }

    /**
     * Read a line from the socket (buffered)
     * @return A string containing the line we read
     */
    public String readLine() throws IOException {

        String line = ""; //$NON-NLS-1$
        int r = -1;

        // Have left over buffered data?
        if (rbufIdx > -1 && rbuf.length >= rbufIdx + 1)
            line = new String(rbuf, 0, rbufIdx + 1);

        rbufIdx = line.length() - 1;

        // Check for a prompt
        if (rbufIdx >= 1 && line.charAt(0) == '#' && line.charAt(1) == ' ') {
            // Did we consume the whole buffer?
            if (rbufIdx > 1) {
                // Nope
                rbuf = line.substring(2, rbufIdx).getBytes();
                rbufIdx -= 2;
            }
            else {
                // Yep
                rbuf = null;
                rbufIdx = -1;
            }
            LogUtil.debug("readLine: #");  //$NON-NLS-1$
            return "#"; //$NON-NLS-1$
        }

        // Is there a whole line in the buffer?
        r = line.indexOf('\n');

        if (r != -1) {
            // Yup, did we consume the whole buffer?
            if (r < rbufIdx) {
                // Nope
                rbuf = line.substring(r + 1, rbufIdx + 1).getBytes();
                rbufIdx -= r + 1;
                LogUtil.debug("readLine: " + line.substring(0,r)); //$NON-NLS-1$
                return line.substring(0, r).trim();
            }
            // Yup
            rbuf = null;
            rbufIdx = -1;
            LogUtil.debug("readLine: " + line); //$NON-NLS-1$
            return line.trim();
        }
        
        synchronized (is) {
            setReadTimeout();

            // We don't have a whole line buffered, read until we get one
            while (true) {
    
                final byte[] buf = new byte[rbufSize];
    
                r = read(buf, 0, rbufSize);
    
                String extra = new String(buf, 0 , r);
                line += extra;
    
                // If the buffer was empty and we got 2 bytes, check for a #
                if (
                    line.length() == 2 &&
                    line.charAt(0) == '#' && line.charAt(1) == ' '
                ) {
                    LogUtil.debug("readLine: #"); //$NON-NLS-1$
                    restoreReadTimeout();
                    return "#"; //$NON-NLS-1$
                }
    
                // Got a whole line yet?
                if (extra.indexOf('\n') != -1)
                    break;
    
            }
            
            restoreReadTimeout();
        }

        // We've got a whole line
        int tot = line.length() - 1;
        r = line.indexOf('\n');

        // Are we gonna consume the whole string?
        if (r < tot) {
            // Nope, buffer the rest
            rbuf = line.substring(r + 1, tot + 1).getBytes();
            rbufIdx = tot - (r + 1);
            LogUtil.debug("readLine: " + line.substring(0,r)); //$NON-NLS-1$
            return line.substring(0, r).trim();
        }
        // Yup
        rbuf = null;
        rbufIdx = -1;
        LogUtil.debug("readLine: " + line); //$NON-NLS-1$
        return line.trim();

    }

    /**
     * Read len bytes from the socket (unbuffered)
     * @param len number of bytes to read
     * @return a byte array of len bytes
     */
    public byte[] readBytes(final int len) throws IOException {

        final byte[] bytes = new byte[len];
        synchronized (is) {
            setReadTimeout();
            
            int read = read(bytes, 0, len);
            int got  = 0;

            while (read < len) {
                got = read(bytes, read, len - read);
                read += got;
            }

            LogUtil.debug("readBytes read " + read + " bytes"); //$NON-NLS-1$ //$NON-NLS-2$
            restoreReadTimeout();
        }
        return bytes;
    }

    /**
     * Read a MythTV style stringlist from the socket (unbuffered)
     * @return List of strings
     */
    public String[] readStringList() throws IOException {
        
        byte[] bytes = new byte[8];
        synchronized (is) {
            setReadTimeout();
            // First 8 bytes are the length
            read(bytes, 0, 8);
        }
        int len = Integer.parseInt(new String(bytes).trim());
        bytes = readBytes(len);
        return new String(bytes).split("\\[\\]:\\[\\]"); //$NON-NLS-1$
    }

    /**
     * Get the connection state
     * @return true if socket is connected, false otherwise
     */
    public boolean isConnected() {
        synchronized (sockLock) {
            return sock != null && sock.isConnected() && inUse;
        }
    }
    
    /**
     * Call when finished with the ConnMgr, doesn't actually disconnect the
     * socket since the ConnMgr will be cached in case it can be reused
     */
    public void disconnect() {
        synchronized (sockLock) {
            inUse = false;
            lastUsed = System.currentTimeMillis();
        }
        if (wifiLock != null && wifiLock.isHeld())
            wifiLock.release();
    }

    /** Disconnect the socket immediately and clean up internal resources */
    public void dispose() throws IOException {
        disconnect();
        doDisconnect();
        synchronized (conns) { conns.remove(weakThis); }
    }

    /** Disconnect all currently connected connections */
    static public void disconnectAll() throws IOException {

        /* 
         * Local array of connections to dispose of once we've finished
         * iterating over conns (and dropped the lock on it)
         */
        final ArrayList<ConnMgr> dispose = new ArrayList<ConnMgr>();
        
        synchronized(conns) {

            boolean iU = false;
            
            for (WeakReference<ConnMgr> r : conns) {

                if (r == null) continue;
                ConnMgr c = r.get();
                if (c == null) continue;
                synchronized (c.sockLock) {
                    c.reconnectPending = true;
                    iU = c.inUse;
                }
                if (iU)
                    c.doDisconnect();
                else
                    dispose.add(c);

            }

        }
        
        // Dispose of the cached conns that weren't in use
        for (ConnMgr r : dispose)
            try {
                r.dispose();
            } catch (IOException e) {}

    }

    /** Reconnect all disconnected connections */
    static public void reconnectAll() {

        final ArrayList<ConnMgr> dispose = new ArrayList<ConnMgr>();
        
        synchronized(conns) {

            for (WeakReference<ConnMgr> r : conns) {

                if (r == null) continue;
                ConnMgr c = r.get();
                if (c == null) continue;
                try {
                    c.doConnect(c.timeout);
                } catch (IOException e) {
                    LogUtil.warn("Failed to reconnect to " + c.addr); //$NON-NLS-1$
                    dispose.add(c);
                }

            }

        }
        
        for (ConnMgr r : dispose)
            try {
                r.dispose();
            } catch (IOException e) {}

    }
    
    /** Dispose of cached connections that haven't been used recently */
    static public void reapOld() {
        
        if (conns == null || conns.isEmpty()) return;
        
        long now = System.currentTimeMillis();
        
        /* 
         * Local array of connections to dispose of once we've finished
         * iterating over conns (and dropped the lock on it)
         */
        final ArrayList<ConnMgr> dispose = new ArrayList<ConnMgr>();
        
        synchronized (conns) {
            
            for (WeakReference<ConnMgr> r : conns) {

                if (r == null) continue;
                ConnMgr c = r.get();
                if (c == null) continue;
                synchronized (c.sockLock) {
                    if (c.inUse == false && c.lastUsed + maxAge < now)
                        dispose.add(c);
                }
                    
            }
            
        }
        
        // Dispose of conns that hadn't been used during the last maxAge ms
        for (ConnMgr r : dispose)
            try {
                r.dispose();
            } catch (IOException e) {}
        
    }
    
    /**
     * Find and return an existing, unused connection 
     * @param host desired hostname or IP address
     * @param port desired port number
     * @return existing ConnMgr or null if none was found
     */
    static private ConnMgr findExisting(final String host, final int port) {
        
        synchronized (conns) {
            for (WeakReference<ConnMgr> r : conns) {

                if (r == null) continue;
                ConnMgr c = r.get();
                if (c == null) continue;
                synchronized (c.sockLock) {
                    if (
                        c.sock != null                   &&
                        c.addr.equals(host + ":" + port) && //$NON-NLS-1$
                        c.sock.isConnected()             &&
                        c.inUse == false
                    ) {
                        c.inUse = true;
                        if (c.wifiLock != null)
                            c.wifiLock.acquire();
                        LogUtil.debug(
                            "Reusing an existing connection to " + //$NON-NLS-1$
                            host + ":" + port //$NON-NLS-1$
                        );
                        return c;
                    }
                }
                    
            }
        }
        
        return null;
        
    }

    /**
     * Connect to the remote host
     * @param timeout connect timeout in milliseconds
     */
    private void doConnect(int timeout) throws IOException {

        // Wait for a maximum of 5s if a WiFi link is being established
        ConnectivityReceiver.waitForWifi(Globals.appContext, 5000);

        if (isConnected()) {
            LogUtil.debug(addr + " is already connected"); //$NON-NLS-1$
            return;
        }
        
        synchronized (sockLock) {

            sock = new Socket();
            sock.setTcpNoDelay(true);
            sock.setSoTimeout(timeout);
                
            for (int i = 0; i < 3; i++) {
                LogUtil.debug("Connecting to " + addr); //$NON-NLS-1$
                try {
                    sock.connect(sockAddr, timeout / 2);
                } catch (UnknownHostException e) {
                    throw 
                        new IOException(
                            Messages.getString("ConnMgr.1") + hostname //$NON-NLS-1$
                        );
                } catch (SocketTimeoutException e) {
                    if (i < 3)
                        continue;
                    throw
                        new IOException(
                            Messages.getString("ConnMgr.2") + addr +  //$NON-NLS-1$
                                Messages.getString("ConnMgr.4") //$NON-NLS-1$
                        );
                } catch (IOException e) {
                    throw
                        new IOException(
                            Messages.getString("ConnMgr.2") + addr +  //$NON-NLS-1$
                                Messages.getString("ConnMgr.7") //$NON-NLS-1$
                        );
                }
                if (sock.isConnected())
                    break;
            }
            
            reconnectPending = false;
            os = sock.getOutputStream();
            is = sock.getInputStream();
            LogUtil.debug("Connection to " + addr + " successful"); //$NON-NLS-1$ //$NON-NLS-2$
            inUse = true;
        }

        // Execute onConnectListeners
        for (onConnectListener oCL : oCLs)
            oCL.onConnect(this);

    }

    /** Actually disconnect the socket */
    private void doDisconnect() throws IOException {
        if (sock == null) return;
        synchronized (sockLock) {
            LogUtil.debug("Disconnecting from " + addr); //$NON-NLS-1$
            if (!sock.isClosed())
                sock.close();
            sock = null;
        }
    }

    private int read(byte[] buf, final int off, final int len) 
    	throws IOException {

        int ret = -1;
        
        try {
            ret = is.read(buf, off, len);
        } catch (SocketTimeoutException e) {
            
            final String msg = 
                String.format(Messages.getString("ConnMgr.5"), addr); //$NON-NLS-1$ 

            LogUtil.debug(msg);

            if (!isConnected()) {
                LogUtil.warn(
                    "Disconnected from " + addr + ", wait for reconnect"  //$NON-NLS-1$ //$NON-NLS-2$
                );
                return retryRead(buf, off, len);
            }
            
            dispose();
            throw new SocketTimeoutException(msg);

        }

        if (ret == -1) {
            LogUtil.warn("Read from " + addr + " failed, wait for reconnect"); //$NON-NLS-1$ //$NON-NLS-2$
            doDisconnect();
            return retryRead(buf, off, len);
        }
       
        return ret;

    }

    private void write(final byte[] buf) throws IOException {

        if (!isConnected())
            waitForConnection(timeout * 4);

        os.write(buf);
        lastSent = buf;

    }

    /**
     * Wait for a connection to be established
     * @param timeout - maximum wait time in milliseconds
     */
    private void waitForConnection(int timeout) throws IOException
    {

        if (!reconnectPending) {
            doConnect(timeout);
            return;
        }

        final Thread thisThread = Thread.currentThread();
        final Timer timer = new Timer();

        timer.schedule(
            new TimerTask() {
                @Override
                public void run() {
                    thisThread.interrupt();
                }
            }, timeout
        );

        LogUtil.debug("Waiting for a connection to " + addr); //$NON-NLS-1$

        while (sock == null || !sock.isConnected() || !inUse)
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                String msg = Messages.getString("ConnMgr.3") + addr; //$NON-NLS-1$
                LogUtil.debug(msg);
                synchronized (sockLock) { inUse = false; }
                timer.cancel();
                throw new IOException(msg);
            }
        
        timer.cancel();
        
    }
    
    private int retryRead(byte[] buf, final int off, final int len)
    	throws IOException {
    	
    	waitForConnection(timeout * 4);
        if (lastSent != null) write(lastSent);
        return read(buf, off, len);
        
    }
    
    private void setReadTimeout() throws SocketException {
        
        int localtimeout = timeout;
        
        /* 
         * See if a longer read timeout has been requested
         * but don't increase it too much if we already have a long timeout due 
         * to the use of a slow link
         */
        switch (timeOutModifier) {            
            case LONG:
                localtimeout *= (localtimeout > 5000 ? 2 : 5);
                break;
            case EXTRALONG:
                localtimeout *= (localtimeout > 5000 ? 3 : 10);
                break;
            default:
                return;
        }
        
        sock.setSoTimeout(localtimeout);

    }
    
    private void restoreReadTimeout() throws SocketException { 
        
        if (sock == null || timeOutModifier == timeOut.INFINITE) return;
        
        sock.setSoTimeout(timeout);
        timeOutModifier = timeOut.DEFAULT;
        
    }

}
