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

package org.mythdroid.frontend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.mythdroid.ConnMgr;
import org.mythdroid.ConnMgr.onConnectListener;
import org.mythdroid.Enums.Key;
import org.mythdroid.data.Program;
import org.mythdroid.resource.Messages;

/** Manages a frontend */
public class FrontendManager {

    /** String containing the name of the frontend */
    public  String  name = null;
    /** String containing the hostname or IP address of the frontend */
    public  String  addr = null;
    
    final private IllegalArgumentException invalidParam =
        new IllegalArgumentException(Messages.getString("FrontendManager.2")); //$NON-NLS-1$
    
    final private Object cmgrLock = new Object(); 
    
    private ConnMgr cmgr          = null;
    
    /**
     * Constructor
     * @param name name of frontend
     * @param host hostname or IP address of frontend
     */
    public FrontendManager(String name, String host) throws IOException {

        synchronized (cmgrLock) {
            cmgr = ConnMgr.connect(
                host, 6546,
                new onConnectListener() {
                    @Override
                    public void onConnect(ConnMgr cmgr) throws IOException {
                        readToPrompt(cmgr);
                    }
                }
            );
        }

        this.name = name;
        addr = host;

    }

    /**
     * Get state of frontend
     * @return true if we are connected, false otherwise
     */
    public boolean isConnected() {
        synchronized (cmgrLock) {
            return (cmgr != null && cmgr.isConnected());
        }
    }

    /**
     * Jump to a frontend location
     * @param loc String describing location
     * @return true if we jumped ok, false otherwise
     */
    public boolean jumpTo(final String loc) throws IOException {
        if (loc == null) throw invalidParam;
        return sendCommand("jump " + loc, ConnMgr.timeOut.EXTRALONG); //$NON-NLS-1$
    }

    /**
     * Jump to a frontend location
     * @param loc a FrontendLocation to jump to
     * @return true if we jumped ok, false otherwise
     */
    public boolean jumpTo(FrontendLocation loc) throws IOException
    {
        if (loc == null || loc.location == null) throw invalidParam;
        return jumpTo(loc.location.toLowerCase());
    }

    /**
     * Send a key to the frontend
     * @param key String containing key to send
     * @return true if the frontend accepted the key, false otherwise
     */
    public boolean sendKey(final String key) throws IOException {
        if (key == null) throw invalidParam;
        return sendCommand("key " + key); //$NON-NLS-1$
    }
    
    /**
     * Send a key to the frontend
     * @param key Key to send
     * @return true if the frontend accepted the key, false otherwise
     */
    public boolean sendKey(final Key key) throws IOException {
        if (key == null) throw invalidParam;
        return sendKey(key.str());
    }

    /**
     * Get the current frontend location
     * @return a FrontendLocation
     */
    public FrontendLocation getLoc() throws IOException {
        
        String loc = getSingleLineResponse("query loc", ConnMgr.timeOut.LONG); //$NON-NLS-1$

        int i = 0;
        while (loc.startsWith("ERROR: Timed out") && i++ < 4) { //$NON-NLS-1$
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
            
            loc = getSingleLineResponse("query loc", ConnMgr.timeOut.LONG); //$NON-NLS-1$
            
        }

        return new FrontendLocation(this, loc);
    }

    /**
     * Get a HashMap of frontend locations and their descriptions from the
     * frontend
     * @return HashMap<String,String> of locations -> descriptions
     */
    public HashMap<String,String> getLocs() throws IOException {
        final HashMap<String,String> locs = new HashMap<String,String>(64);
        final String[] lines = getResponse("help jump", ConnMgr.timeOut.LONG); //$NON-NLS-1$
        int numlines = lines.length;
        for (int i = 0; i < numlines; i++) {
            if (!lines[i].matches(".*\\s+-\\s+.*")) continue; //$NON-NLS-1$
            String[] l = lines[i].split(" - "); //$NON-NLS-1$
            locs.put(l[0].trim(), l[1].trim());
        }
        return locs;
    }

    /**
     * Play a recording
     * @param prog Program to play
     * @return true if starting playing ok, false otherwise
     */
    public boolean playRec(final Program prog) throws IOException {
        if (prog == null) throw invalidParam;
        return 
            sendCommand("play prog " + prog.playbackID(), ConnMgr.timeOut.LONG); //$NON-NLS-1$
    }

    /**
     * Play a video
     * @param file filename of video to play
     * @return true if starting playing ok, false otherwise
     */
    public boolean playFile(final String file) throws IOException {
        if (file == null) throw invalidParam;
        return sendCommand("play file " + file, ConnMgr.timeOut.LONG); //$NON-NLS-1$
    }

    /**
     * Switch to a channel in livetv (must be in livetv to call)
     * @param chanid channel id to switch to
     * @return true if we switched ok, false otherwise
     */
    public boolean playChan(int chanid) throws IOException {
        return sendCommand("play chanid " + chanid, ConnMgr.timeOut.LONG); //$NON-NLS-1$
    }
    
    /**
     * Seek to a specified position in the video
     * @param pos position in seconds
     * @return true if the seek was successful, false otherwise
     */
    public boolean seekTo(int pos) throws IOException {
        int hours = pos / 3600;
        pos -= hours * 3600;
        int mins = pos / 60;
        pos -= mins * 60;
        int secs = pos;
        return sendCommand(
            "play seek " + String.format("%02d:%02d:%02d", hours, mins, secs) //$NON-NLS-1$ //$NON-NLS-2$
        );
    }

    /** Disconnect from the frontend */
    public void disconnect() {
        if (!isConnected()) return;
        synchronized (cmgrLock) {
            cmgr.disconnect();
            cmgr = null;
        }
    }
    
    private boolean sendCommand(String cmd) throws IOException {
        if (getSingleLineResponse(cmd).equals("OK")) //$NON-NLS-1$
            return true;
        return false;
    }
    
    private boolean sendCommand(String cmd, ConnMgr.timeOut time)
        throws IOException {
        if (getSingleLineResponse(cmd, time).equals("OK")) //$NON-NLS-1$
            return true;
        return false;
    }

    /**
     * Read lines from until we get a prompt
     * @return array of lines read
     */
    private String[] getResponse(String cmd, ConnMgr.timeOut time)
        throws IOException {
        
        checkConnection();
        final ArrayList<String> resp = new ArrayList<String>(64);
        String msg = ""; //$NON-NLS-1$
        synchronized (cmgrLock) {
            cmgr.writeLine(cmd);
            cmgr.setTimeout(time);
            while (cmgr != null) {
                msg = cmgr.readLine();
                if (msg.length() == 0) continue;
                if (msg.equals("#")) break; //$NON-NLS-1$
                resp.add(msg);
            }
        }
        return resp.toArray(new String[resp.size()]);
        
    }

    /** Read until we see a prompt */
    private void readToPrompt(ConnMgr cmgr) throws IOException {
        synchronized (cmgrLock) {
            while (cmgr != null)
                if (cmgr.readLine().equals("#")) break; //$NON-NLS-1$
        }
    }

    private String getSingleLineResponse(String cmd) throws IOException {
        checkConnection();
        synchronized (cmgrLock) {
            cmgr.writeLine(cmd);
            final String line = cmgr.readLine();
            while (cmgr != null)
                if (cmgr.readLine().equals("#")) break; //$NON-NLS-1$
            return line;
        }
    }
    
    private String getSingleLineResponse(String cmd, ConnMgr.timeOut time)
        throws IOException {
        
        checkConnection();
        synchronized (cmgrLock) {
            cmgr.writeLine(cmd);
            cmgr.setTimeout(time);
            final String line = cmgr.readLine();
            while (cmgr != null)
                if (cmgr.readLine().equals("#")) break; //$NON-NLS-1$
            return line;
        }
        
    }
    
    private void checkConnection() throws IOException {
        
        if (cmgr.isConnected()) return;
        
        synchronized (cmgrLock) {
            
            if (cmgr != null) cmgr.dispose();
            
            cmgr = ConnMgr.connect(
                addr, 6546, 
                new onConnectListener() {
                    @Override
                    public void onConnect(ConnMgr cmgr) throws IOException {
                        readToPrompt(cmgr);
                    }
                }
            );
            
        }
        
    }

}
