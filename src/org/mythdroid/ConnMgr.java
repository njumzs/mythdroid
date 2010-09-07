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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.mythdroid.activities.MythDroid;
import org.mythdroid.resource.Messages;

import android.util.Log;

/**
 * A TCP connection manager
 * It's probably a bad idea to mix buffered and unbuffered reads 
 */
public class ConnMgr {
    
    public  String       addr    = null;
    
    static final private IOException disconnected =
        new IOException(Messages.getString("ConnMgr.0")); //$NON-NLS-1$

    static final private int rbufSize = 128;
    
    private Socket       sock    = null;
    private OutputStream os      = null;
    private InputStream  is      = null;
    private int          rbufIdx = -1;
    private byte[]       rbuf    = null;

    /**
     * Constructor
     * @param host - String with hostname or dotted decimal IP address
     * @param port - integer port number
     */
    public ConnMgr(String host, int port) throws IOException {

        sock = new Socket();
        sock.setTcpNoDelay(true);

        final SocketAddress sa = new InetSocketAddress(host, port);

        try {
            sock.connect(sa, 1000);
        } catch (UnknownHostException e) {
            throw (new IOException(Messages.getString("ConnMgr.1") + host)); //$NON-NLS-1$
        } catch (SocketTimeoutException e) {
            throw (
                new IOException(
                    Messages.getString("ConnMgr.2") + host + ":" + port +  //$NON-NLS-1$ //$NON-NLS-2$
                        Messages.getString("ConnMgr.4")) //$NON-NLS-1$
                );
        } catch (IOException e) {
            throw (
                new IOException(
                    Messages.getString("ConnMgr.5") + host + ":" + port +  //$NON-NLS-1$ //$NON-NLS-2$
                        Messages.getString("ConnMgr.7")) //$NON-NLS-1$
                );
        }
        
        addr = host;
        os = sock.getOutputStream();
        is = sock.getInputStream();

    }

    /**
     * Write a line of text to the socket
     * @param str - string to write, will have '\n' appended if necessary
     */
    public void writeLine(String str) throws IOException {

        if (str.endsWith("\n")) //$NON-NLS-1$
            os.write(str.getBytes());
        else {
            str += "\n"; //$NON-NLS-1$
            os.write(str.getBytes());
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

        os.write(str.getBytes());

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
            
            r = is.read(buf, 0, rbufSize);
        
            if (r == -1) {
                disconnect();
                throw disconnected;
            }
            
            String extra = new String(buf, 0 , r);
            line += extra;
            
            // If the buffer was empty and we got 2 bytes, check for a prompt
            if (
                line.length() == 2 && 
                extra.charAt(0) == '#' && extra.charAt(1) == ' '
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
        int read = is.read(bytes, 0, len);
        
        if (read == -1) {
            disconnect();
            throw disconnected;
        }
        
        while (read < len) 
            read += is.read(bytes, read, len - read);
        
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
        if (is.read(bytes, 0, 8) == -1) {
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

    /**
     * disconnect the socket
     */
    public void disconnect() throws IOException {
        sock.close();
    }

}
