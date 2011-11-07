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
	
	final private IOException connectionGone = 
        new IOException(Messages.getString("FrontendManager.0")); //$NON-NLS-1$
	
	private ConnMgr cmgr = null;
    
    /**
     * Constructor
     * @param name name of frontend
     * @param host hostname or IP address of frontend
     */
    public FrontendManager(String name, String host) throws IOException {

        cmgr = ConnMgr.connect(host, 6546, new onConnectListener() {
                @Override
                public void onConnect(ConnMgr cmgr) throws IOException {
                    getSingleLineResponse(cmgr);
                }
            }
        );

        if (cmgr == null)
        	throw new 
        		IOException(Messages.getString("FrontendManager.1") + name); //$NON-NLS-1$

        this.name = name;
        addr = host;

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
     * @param loc String describing location
     * @return true if we jumped ok, false otherwise
     */
    public synchronized boolean jumpTo(final String loc) throws IOException {
        if (cmgr == null) throw connectionGone;
        cmgr.writeLine("jump " + loc); //$NON-NLS-1$
        cmgr.setTimeout(ConnMgr.timeOut.EXTRALONG);
        if (getSingleLineResponse().equals("OK")) //$NON-NLS-1$
            return true;
        return false;
    }

    /**
     * Jump to a frontend location
     * @param loc a FrontendLocation to jump to
     * @return true if we jumped ok, false otherwise
     */
    public synchronized boolean jumpTo(FrontendLocation loc) throws IOException {
        if (cmgr == null || loc == null || loc.location == null) return false;
        cmgr.writeLine("jump " + loc.location.toLowerCase()); //$NON-NLS-1$
        cmgr.setTimeout(ConnMgr.timeOut.EXTRALONG);
        if (getSingleLineResponse().equals("OK")) //$NON-NLS-1$
            return true;
        return false;
    }

    /**
     * Send a key to the frontend
     * @param key Key to send
     * @return true if the frontend accepted the key, false otherwise
     */
    public synchronized boolean sendKey(final Key key) throws IOException {
        if (cmgr == null) throw connectionGone;
        if (key == null) return false;
        cmgr.writeLine("key " + key.str()); //$NON-NLS-1$
        if (getSingleLineResponse().equals("OK")) //$NON-NLS-1$
            return true;
        return false;
    }

    /**
     * Send a key to the frontend
     * @param key String containing key to send
     * @return true if the frontend accepted the key, false otherwise
     */
    public synchronized boolean sendKey(final String key) throws IOException {
        if (cmgr == null) throw connectionGone;
        cmgr.writeLine("key " + key); //$NON-NLS-1$
        if (getSingleLineResponse().equals("OK")) //$NON-NLS-1$
            return true;
        return false;
    }

    /**
     * Get the current frontend location
     * @return a FrontendLocation
     */
    public synchronized FrontendLocation getLoc() throws IOException {
        if (cmgr == null) throw connectionGone;
        cmgr.writeLine("query loc"); //$NON-NLS-1$
        cmgr.setTimeout(ConnMgr.timeOut.LONG);
        String loc = getSingleLineResponse();

        int i = 0;
        while (loc.startsWith("ERROR: Timed out") && i++ < 4) { //$NON-NLS-1$
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
            if (cmgr == null) throw connectionGone;
            cmgr.writeLine("query loc"); //$NON-NLS-1$
            cmgr.setTimeout(ConnMgr.timeOut.LONG);
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
        final HashMap<String,String> locs = new HashMap<String,String>(64);
        cmgr.writeLine("help jump"); //$NON-NLS-1$
        cmgr.setTimeout(ConnMgr.timeOut.LONG);
        final String[] lines = getResponse();
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
    public synchronized boolean playRec(final Program prog) throws IOException {
        if (cmgr == null) throw connectionGone;
        if (prog == null) return false;
        cmgr.writeLine("play prog " + prog.playbackID()); //$NON-NLS-1$
        cmgr.setTimeout(ConnMgr.timeOut.LONG);
        if (getSingleLineResponse().equals("OK")) //$NON-NLS-1$
            return true;
        return false;
    }

    /**
     * Play a video
     * @param file filename of video to play
     * @return true if starting playing ok, false otherwise
     */
    public synchronized boolean playFile(final String file) throws IOException {
        if (cmgr == null) throw connectionGone;
        cmgr.writeLine("play file " + file); //$NON-NLS-1$
        cmgr.setTimeout(ConnMgr.timeOut.LONG);
        if (getSingleLineResponse().equals("OK")) //$NON-NLS-1$
            return true;
        return false;
    }

    /**
     * Switch to a channel in livetv (must be in livetv to call)
     * @param chanid channel id to switch to
     * @return true if we switched ok, false otherwise
     */
    public synchronized boolean playChan(int chanid) throws IOException {
        if (cmgr == null) throw connectionGone;
        cmgr.writeLine("play chanid " + chanid); //$NON-NLS-1$
        cmgr.setTimeout(ConnMgr.timeOut.LONG);
        if (getSingleLineResponse().equals("OK")) //$NON-NLS-1$
            return true;
        return false;
    }
    
    /**
     * Seek to a specified position in the video
     * @param pos position in seconds
     * @return true if the seek was successful, false otherwise
     */
    public synchronized boolean seekTo(int pos) throws IOException {
        if (cmgr == null) throw connectionGone;
        int hours = pos / 3600;
        pos -= hours * 3600;
        int mins = pos / 60;
        pos -= mins * 60;
        int secs = pos;
        cmgr.writeLine(
            "play seek " + String.format("%02d:%02d:%02d", hours, mins, secs) //$NON-NLS-1$ //$NON-NLS-2$
        );
        if (getSingleLineResponse().equals("OK")) //$NON-NLS-1$
            return true;
        return false;
    }

    /** Disconnect from the frontend */
    public void disconnect() {
        if (cmgr == null) return;
        cmgr.disconnect();
        cmgr = null;
    }

    /**
     * Read lines from until we get a prompt
     * @return array of lines read
     */
    private synchronized String[] getResponse() throws IOException {
        final ArrayList<String> resp = new ArrayList<String>(64);
        String msg = ""; //$NON-NLS-1$
        while (cmgr != null) {
            msg = cmgr.readLine();
            if (msg.length() == 0) continue;
            if (msg.equals("#")) break; //$NON-NLS-1$
            resp.add(msg);
        }
        return resp.toArray(new String[resp.size()]);
    }

    /**
     * Read a single line response via the supplied ConnMgr
     * @param cmgr ConnMgr to read via
     * @return the first line read
     */
    @SuppressWarnings("null")
    private synchronized String getSingleLineResponse(ConnMgr cmgr)
        throws IOException {
        final String line = cmgr.readLine();
        while (cmgr != null)
            if (cmgr.readLine().equals("#")) break; //$NON-NLS-1$
        return line;
    }

    /**
     * Read a single line response
     * @return the first line read
     */
    private synchronized String getSingleLineResponse() throws IOException {
        if (cmgr == null) throw connectionGone;
        final String line = cmgr.readLine();
        while (cmgr != null)
            if (cmgr.readLine().equals("#")) break; //$NON-NLS-1$
        return line;
    }

}
