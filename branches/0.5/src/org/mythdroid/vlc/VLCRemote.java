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


package org.mythdroid.vlc;

import java.io.IOException;

import org.mythdroid.ConnMgr;
import org.mythdroid.Globals;
import org.mythdroid.resource.Messages;

/**
 * Manage a connection to VLC's remote control interface
 */
public class VLCRemote {
    
    final static private int port = 16547;
    
    private ConnMgr cmgr = null;

    /**
     * Constructor
     * @param host - hostname or IP address of host running VLC
     */
    public VLCRemote(String host) throws IOException {

        cmgr = ConnMgr.connect(host, port, null, Globals.muxConns);

        if (cmgr == null)
            throw new 
                IOException(
                    Messages.getString("VLCRemote.0") + host + ":" + port //$NON-NLS-1$ //$NON-NLS-2$
                );
    }
    
    /**
     * Get the length of the video
     * @return length of the video in milliseconds
     * @throws IOException 
     */
    public long getLength() throws IOException {
        cmgr.writeLine("get_length"); //$NON-NLS-1$
        return getIntegerResult() * 1000;
    }
    
    /**
     * Get the current position 
     * @return number of milliseconds into the video
     * @throws IOException 
     */
    public long getTime() throws IOException {
        cmgr.writeLine("get_time"); //$NON-NLS-1$
        return getIntegerResult() * 1000;
    }
    
    /**
     * Toggle pause state
     * @throws IOException
     */
    public void togglePause() throws IOException {
        cmgr.writeLine("pause"); //$NON-NLS-1$
    }
    
    /**
     * Seek to the given position
     * @param msecs position in seconds
     * @throws IOException
     */
    public void seek(int msecs) throws IOException {
        cmgr.writeLine("seek " + msecs / 1000); //$NON-NLS-1$
    }
    
    /** Disconnect and cleanup */
    public void disconnect() throws IOException {
        if (cmgr == null) return;
        cmgr.dispose();
        cmgr = null;
    }
    
    private synchronized int getIntegerResult() throws IOException {
        
        String result = ""; //$NON-NLS-1$
        
        while (!result.matches("[0-9]+")) //$NON-NLS-1$
            result = cmgr.readLine();
        
        return Integer.parseInt(result);
        
    }
    

}
