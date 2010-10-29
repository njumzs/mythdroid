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
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.mythdroid.activities.MythDroid;
import org.mythdroid.receivers.ConnectivityReceiver;
import org.mythdroid.resource.Messages;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.util.Log;

/**
 * A TCP connection manager
 * It's probably a bad idea to mix buffered and unbuffered reads 
 */
public class ConnMgr {
    
    public interface onConnectListener {
        public void onConnect(ConnMgr cmgr) throws IOException;
    }
    
    public  String                  addr             = null;
    
    final private static ArrayList<WeakReference<ConnMgr>> conns =
        new ArrayList<WeakReference<ConnMgr>>(8);
    
    final private static IOException disconnected =
        new IOException(Messages.getString("ConnMgr.0")); //$NON-NLS-1$

    final private static int        rbufSize         = 128;
    
    private WeakReference<ConnMgr>  weakThis         = null;
    private Socket                  sock             = null;
    private SocketAddress           sockAddr         = null;
    private OutputStream            os               = null;
    private InputStream             is               = null;
    private int                     rbufIdx          = -1;
    private byte[]                  rbuf             = null;
    private int                     timeout          = 1000;
    private String                  hostname         = null;
    private WifiLock                wifiLock         = null;
    private boolean                 connectedReady   = false;
    private boolean                 reconnectPending = false;
    private byte[]                  lastSent         = null;
    private onConnectListener       oCL              = null;

    /**
     * Constructor
     * @param host - String with hostname or dotted decimal IP address
     * @param port - integer port number
     */
    public ConnMgr(String host, int port, onConnectListener ocl)
        throws IOException {

        sockAddr = new InetSocketAddress(host, port);
        
        hostname = host;
        addr = host + ":" + port; //$NON-NLS-1$
        
        oCL = ocl;

        if (
            ConnectivityReceiver.networkType() == ConnectivityManager.TYPE_WIFI
        ) {
            wifiLock = ((WifiManager)MythDroid.appContext
                .getSystemService(Context.WIFI_SERVICE))
                .createWifiLock("MythDroid"); //$NON-NLS-1$
            wifiLock.acquire();
        }
        else 
            timeout *= 8;
        
        connect(timeout);
        
        weakThis = new WeakReference<ConnMgr>(this);
        synchronized(conns) { conns.add(weakThis); }
        
    }

    /**
     * Write a line of text to the socket
     * @param str - string to write, will have '\n' appended if necessary
     */
    public void writeLine(String str) throws IOException {

        if (str.endsWith("\n")) //$NON-NLS-1$
            write(str.getBytes());
        else {
            str += "\n"; //$NON-NLS-1$
            write(str.getBytes());
        }

        if (MythDroid.debug) Log.d("ConnMgr", "writeLine: " + str); //$NON-NLS-1$ //$NON-NLS-2$

    }

    /**
     * Write a string to the socket, prefixing with 8 chars of length
     * @param str - string to write
     */
    public void sendString(String str) throws IOException {

        str = String.format("%-8d", str.length()) + str; //$NON-NLS-1$

        if (MythDroid.debug) Log.d("ConnMgr", "sendString: " + str); //$NON-NLS-1$ //$NON-NLS-2$

        write(str.getBytes());

    }

    /**
     * Separate and write a stringlist to the socket
     * @param list - List of strings to write
     */
    public void sendStringList(String[] list) throws IOException {

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
        if (rbufIdx > -1) 
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
            if (MythDroid.debug) Log.d("ConnMgr", "readLine: #");  //$NON-NLS-1$ //$NON-NLS-2$
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
                if (MythDroid.debug) 
                    Log.d("ConnMgr", "readLine: " + line.substring(0,r)); //$NON-NLS-1$ //$NON-NLS-2$
                return line.substring(0, r).trim();
            }
            // Yup
            rbuf = null;
            rbufIdx = -1;
            if (MythDroid.debug) Log.d("ConnMgr", "readLine: " + line); //$NON-NLS-1$ //$NON-NLS-2$
            return line.trim();    
        }
        
        // We don't have a whole line buffered, read until we get one
        while (true) {
        
            final byte[] buf = new byte[rbufSize];
            
            r = read(buf, 0, rbufSize);
        
            if (r == -1) {
                disconnect();
                throw disconnected;
            }
            
            String extra = new String(buf, 0 , r);
            line += extra;
            
            // If the buffer was empty and we got 2 bytes, check for a prompt
            if (
                line.length() == 2 && 
                line.charAt(0) == '#' && line.charAt(1) == ' '
            ) {
                if (MythDroid.debug) Log.d("ConnMgr", "readLine: #"); //$NON-NLS-1$ //$NON-NLS-2$
                return "#"; //$NON-NLS-1$
            }
            
            // Got a whole line yet?
            if (extra.indexOf('\n') != -1)
                break;

        }
        
        // We've got a whole line
        int tot = line.length() - 1;
        r = line.indexOf('\n');
        
        // Are we gonna consume the whole string?
        if (r < tot) {
            // Nope, buffer the rest
            rbuf = line.substring(r + 1, tot + 1).getBytes();
            rbufIdx = tot - (r + 1);
            if (MythDroid.debug) 
                Log.d("ConnMgr", "readLine: " + line.substring(0,r)); //$NON-NLS-1$ //$NON-NLS-2$
            return line.substring(0, r).trim();
        }
        // Yup
        rbuf = null;
        rbufIdx = -1;
        if (MythDroid.debug) Log.d("ConnMgr", "readLine: " + line); //$NON-NLS-1$ //$NON-NLS-2$
        return line.trim();
        
    }

    /**
     * Read len bytes from the socket (unbuffered)
     * @param len - number of bytes to read
     * @return a byte array of len bytes
     */
    public byte[] readBytes(int len) throws IOException {

        final byte[] bytes = new byte[len];
        int read = read(bytes, 0, len);
        
        if (read == -1) {
            disconnect();
            throw disconnected;
        }
        
        int got = 0;
        
        while (read < len) {
            
            if ((got = read(bytes, read, len - read)) == -1) {
                disconnect();
                throw disconnected;
            }
            
            read += got;
            
        }
        
        if (MythDroid.debug) 
            Log.d("ConnMgr", "readBytes read " + read + " bytes"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        
        return bytes;
    }

    /**
     * Read a stringlist from the socket (unbuffered)
     * @return List of strings
     */
    public String[] readStringList() throws IOException {

        byte[] bytes = new byte[8];
        if (read(bytes, 0, 8) == -1) {
            if (MythDroid.debug) 
                Log.d("ConnMgr", "readStringList from " + addr + " failed"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            disconnect();
            throw disconnected;
        }

        int len = Integer.parseInt(new String(bytes).trim());

        bytes = readBytes(len);
        return new String(bytes).split("\\[\\]:\\[\\]"); //$NON-NLS-1$
    }
    
    /**
     * Get state of socket
     * @return true if socket is connected, false otherwise
     */
    public boolean isConnected() {
        return sock.isConnected();
    }
    
    public void dispose() throws IOException {
        disconnect();
        conns.remove(weakThis);
    }

    static public void disconnectAll() throws IOException {
        
        synchronized(conns) {
        
            for (WeakReference<ConnMgr> r : conns) {
            
                if (r == null) 
                    continue;
                
                ConnMgr c = r.get();
                    
                if (c == null) 
                    continue;
                
                c.disconnect();
                c.reconnectPending = true;
                
            }
        
        }
        
    }
    
    static public void reconnectAll() throws IOException {
        
        synchronized(conns) {
            
            for (WeakReference<ConnMgr> r : conns) {
            
                if (r == null)
                    continue;
                
                ConnMgr c = r.get();
                    
                if (c == null)
                    continue;
     
                c.connect(1000);
                
            }
        
        }
        
    }
    
    private void connect(int timeout) throws IOException {
        
        ConnectivityReceiver.waitForWifi(5000);
        
        if (MythDroid.debug)
            Log.d("ConnMgr", "Connecting to " + addr); //$NON-NLS-1$ //$NON-NLS-2$
        
        if (sock != null && sock.isConnected() && connectedReady) {
            if (MythDroid.debug)
                Log.d("ConnMgr", addr + " is already connected"); //$NON-NLS-1$ //$NON-NLS-2$
            return;
        }
        
        sock = new Socket();
        sock.setTcpNoDelay(true);
        sock.setSoTimeout(timeout);
          
        try {
            sock.connect(sockAddr, timeout / 2);
        } catch (UnknownHostException e) {
            throw new IOException(Messages.getString("ConnMgr.1") + hostname); //$NON-NLS-1$
        } catch (SocketTimeoutException e) {
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
        
        reconnectPending = false;
                
        os = sock.getOutputStream();
        is = sock.getInputStream();
        
        if (MythDroid.debug)
            Log.d("ConnMgr", "Connection to " + addr + " successful"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        
        connectedReady = true;
        
        if (oCL != null)
            oCL.onConnect(this);
        
    }
    
    private void disconnect() throws IOException {
        
        connectedReady = false;
        
        if (MythDroid.debug)
            Log.d("ConnMgr", "Disconnecting from " + addr); //$NON-NLS-1$ //$NON-NLS-2$
        
        if (!sock.isClosed())
            sock.close();
        
        if (wifiLock != null && wifiLock.isHeld())
            wifiLock.release();
        
    }
    
    private int read(byte[] buf, int off, int len) throws IOException {
        
        int ret = -1;
        
        try {
            ret = is.read(buf, off, len);
        } catch (SocketTimeoutException e) {
            
            if (MythDroid.debug)
                Log.d("ConnMgr", "Read from " + addr + " timed out"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            
            if (!sock.isConnected() || !connectedReady) {
                waitForConnection(timeout * 4);
                write(lastSent);
                return read(buf, off, len);
            }
            
            throw e;
            
        }
        
        if (ret == -1 && MythDroid.debug)
            Log.d("ConnMgr", "read from " + addr + " failed"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        
        return ret;
        
    }
    
    private void write(byte[] buf) throws IOException {
        
        if (!sock.isConnected() || !connectedReady)
            waitForConnection(timeout * 4);
        
        os.write(buf);
        lastSent = buf;
        
    }
    
    private void waitForConnection(int timeout) throws IOException {
        
        if (!reconnectPending) {
            connect(timeout);
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
        
        if (MythDroid.debug)
            Log.d("ConnMgr", "Waiting for a connection to " + addr); //$NON-NLS-1$ //$NON-NLS-2$
        
        while (!sock.isConnected() || !connectedReady)
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                if (MythDroid.debug)
                    Log.d("ConnMgr", "Timed out waiting for connection to " + addr); //$NON-NLS-1$ //$NON-NLS-2$
                break;
            }
    }

}
