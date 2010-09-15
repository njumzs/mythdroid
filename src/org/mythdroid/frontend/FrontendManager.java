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
import org.mythdroid.data.Key;
import org.mythdroid.data.Program;
import org.mythdroid.activities.MythDroid;

import android.content.Context;
import android.util.Log;

/** Manages a frontend */
public class FrontendManager {
    
    public  String  name = null, addr = null;
    private ConnMgr cmgr = null;
   
    /**
     * Constructor
     * @param name - name of frontend
     * @param host - hostname or IP address of frontend
     */
    public FrontendManager(String name, String host) throws IOException {
        if (MythDroid.debug) 
            Log.d("FrontendManager", "Connecting to " + host + ":6546");
        cmgr = new ConnMgr(host, 6546);
        if (cmgr == null) return;
        this.name = name;
        addr = host;
        getSingleLineResponse();
    }

    /**
     * Get state of frontend
     * @return true if we are connected, false otherwise
     */
    public boolean isConnected() {
        return (cmgr != null && cmgr.isConnected());
    }

    /**
     * Jump to a frontend location
     * @param loc - String describing location
     * @return true if we jumped ok, false otherwise
     */
    public synchronized boolean jumpTo(final String loc) throws IOException {
        cmgr.writeLine("jump " + loc);
        if (getSingleLineResponse().equals("OK")) 
            return true;
        else
            return false;
    }

    /**
     * Jump to a frontend location
     * @param loc - a FrontendLocation to jump to
     * @return true if we jumped ok, false otherwise
     */
    public synchronized boolean jumpTo(FrontendLocation loc) throws IOException {
        if (cmgr == null || loc == null || loc.location == null) return false;
        cmgr.writeLine("jump " + loc.location.toLowerCase());
        if (getSingleLineResponse().equals("OK"))
            return true;
        else
            return false;
    }

    /**
     * Send a key to the frontend
     * @param key - Key to send
     * @return true if the frontend accepted the key, false otherwise
     */
    public synchronized boolean sendKey(final Key key) throws IOException {
        cmgr.writeLine("key " + key.str());
        if (getSingleLineResponse().equals("OK"))
            return true;
        else
            return false;
    }

    /**
     * Send a key to the frontend
     * @param key - String containing key to send
     * @return true if the frontend accepted the key, false otherwise
     */
    public synchronized boolean sendKey(final String key) throws IOException {
        cmgr.writeLine("key " + key);
        if (getSingleLineResponse().equals("OK"))
            return true;
        else
            return false;
    }

    /**
     * Get the current frontend location
     * @return a FrontendLocation
     */
    public synchronized FrontendLocation getLoc() throws IOException {
        cmgr.writeLine("query loc");
        String loc = getSingleLineResponse();

        int i = 0;
        while (loc.startsWith("ERROR: Timed out") && i++ < 4) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
            cmgr.writeLine("query loc");
            loc = getSingleLineResponse();
        }

        return new FrontendLocation(this, loc);
    }

    /**
     * Get a HashMap of frontend locations and their descriptions from the 
     * frontend 
     * @return HashMap<String,String> of locations -> descriptions
     */
    public synchronized HashMap<String,String> getLocs() throws IOException {
        HashMap<String,String> locs = new HashMap<String,String>(44); 
        cmgr.writeLine("help jump");
        ArrayList<String> lines = getResponse();
        for (String line : lines) {
            if (!line.matches(".*\\s+-\\s+.*")) continue;
            String[] l = line.split(" - ");
            locs.put(l[0].trim(), l[1].trim());
        }
        return locs;
    }

    /**
     * Play a recording
     * @param prog - Program to play
     * @return true if starting playing ok, false otherwise
     */
    public synchronized boolean playRec(final Program prog) throws IOException {
        cmgr.writeLine("play prog " + prog.playbackID());
        if (getSingleLineResponse().equals("OK"))
            return true;
        else
            return false;
    }
    
    /**
     * Play a video
     * @param file - filename of video to play
     * @return true if starting playing ok, false otherwise
     */
    public synchronized boolean playFile(final String file) throws IOException {
        cmgr.writeLine("play file " + file);
        if (getSingleLineResponse().equals("OK"))
            return true;
        else
            return false;
    }

    /**
     * Switch to a channel in livetv (must be in livetv to call)
     * @param chanid - channel id to switch to
     * @return boolean if we switched ok, false otherwise
     */
    public synchronized boolean playChan(int chanid) throws IOException {
        cmgr.writeLine("play chanid " + chanid);
        if (getSingleLineResponse().equals("OK"))
            return true;
        else
            return false;
    }

    /** Disconnect from the frontend */
    public void disconnect() throws IOException {
        if (cmgr == null) return;
        cmgr.disconnect();
        cmgr = null;
    }
    
    private synchronized ArrayList<String> getResponse() throws IOException {
        final ArrayList<String> resp = new ArrayList<String>(32);
        String msg = "";
        while (cmgr != null) {
            msg = cmgr.readLine();
            if (msg.length() == 0) continue;
            if (msg.equals("#")) break;
            resp.add(msg);
        }
        return resp;
    }
    
    private synchronized String getSingleLineResponse() throws IOException {
        String line = cmgr.readLine();
        while (cmgr != null) 
            if (cmgr.readLine().equals("#")) break;
        return line;
    }

}
