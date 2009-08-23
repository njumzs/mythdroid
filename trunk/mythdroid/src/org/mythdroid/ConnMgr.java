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
import java.util.Arrays;
import java.util.List;

import android.util.Log;

/**
 * A TCP connection manager
 */
public class ConnMgr {
    
    static private IOException disconnected =
        new IOException("We've been disconnected");

    private Socket       sock = null;
    private OutputStream os   = null;
    private InputStream  is   = null;

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
            throw (new IOException("Unknown Host: " + host));
        } catch (SocketTimeoutException e) {
            throw (
                new IOException(
                    "Connection to " + host + ":" + port + " failed")
                );
        } catch (IOException e) {
            throw (
                new IOException(
                    "Connection to " + host + ":" + port + " refused")
                );
        }

        os = sock.getOutputStream();
        is = sock.getInputStream();

    }

    /**
     * Write a line of text to the socket
     * @param str - string to write, will have '\n' appended if necessary
     */
    public void writeLine(String str) throws IOException {

        if (str.endsWith("\n"))
            os.write(str.getBytes());
        else {
            str += "\n";
            os.write(str.getBytes());
        }

        if (MythDroid.debug) Log.d("ConnMgr", "writeLine: " + str);

    }

    /**
     * Write a string to the socket, prefixing with 8 chars of length
     * @param str - string to write
     */
    public void sendString(String str) throws IOException {

        str = String.format("%-8d", str.length()) + str;

        if (MythDroid.debug) Log.d("ConnMgr", "sendString: " + str);

        os.write(str.getBytes());

    }

    /**
     * Separate and write a stringlist to the socket
     * @param list - List of strings to write
     */
    public void sendStringList(List<String> list) throws IOException {

        String str = list.remove(0);

        for (String s : list) {
            str += "[]:[]" + s;
        }

        sendString(str);
    }

    /**
     * Read a line from the socket
     * @return A string containing the line we read
     */
    public String readLine() throws IOException {

        StringBuilder sb = new StringBuilder(32);

        char c;

        while (true) {
            c = readChar();
            sb.append(c);
            if (c == '\n') break;
            else if (sb.charAt(0) == '#' && c == ' ') break;
        }

        sb.trimToSize();
        String line = sb.toString().trim();

        if (MythDroid.debug) Log.d("ConnMgr", "readLine: " + line);

        return line;
    }

    /**
     * Read len bytes from the socket
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
            Log.d("ConnMgr", "readBytes read " + read + " bytes");
        
        return bytes;
    }

    /**
     * Read a stringlist from the socket
     * @return List of strings
     */
    public List<String> readStringList() throws IOException {

        byte[] bytes = new byte[8];
        if (is.read(bytes, 0, 8) == -1) {
            disconnect();
            throw disconnected;
        }

        String slen = new String(bytes);
        int len = Integer.parseInt(slen.trim());

        bytes = readBytes(len);
        return Arrays.asList(new String(bytes).split("\\[\\]:\\[\\]"));
    }
    
    /**
     * Get the IP address of the remote host
     * @return a String containing IP address
     */
    public String getAddress() {
        return sock.getInetAddress().getHostAddress();
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

    private char readChar() throws IOException {
        int i = is.read();
        if (i == -1) {
            disconnect();
            throw disconnected;
        }
        return (char) i;
    }

}
