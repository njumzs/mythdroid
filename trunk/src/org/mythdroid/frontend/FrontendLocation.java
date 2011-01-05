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
import java.util.HashMap;

import org.mythdroid.Globals;

import android.util.Log;

/** Describes a location in the frontend */
public class FrontendLocation {

    private static HashMap<String, String> locations = null;
        
    public String  location  = null, niceLocation = null;
    public int     position, end;
    public float   rate;
    public String  filename;
    public boolean 
        video = false, livetv = false, music = false, musiceditor = false;

    /**
     * Constructor
     * @param loc String describing location
     */
    public FrontendLocation(FrontendManager feMgr, String loc) {

        location = loc;
        
        if (locations == null && !populateLocations(feMgr)) 
            return;

        loc = loc.toLowerCase();

        if (loc.startsWith("playback ")) //$NON-NLS-1$
            parsePlaybackLoc(loc);
        else if (loc.startsWith("error")) //$NON-NLS-1$
            location = niceLocation = "Error"; //$NON-NLS-1$
        else
            niceLocation = locations.get(loc);

        if (niceLocation == null)
            niceLocation = "Unknown"; //$NON-NLS-1$
        else if (loc.equals("playmusic"))  //$NON-NLS-1$
            music = true;
        else if (loc.equals("musicplaylists")) //$NON-NLS-1$
            musiceditor = true;
        
        if (Globals.debug) 
            Log.d(
                "FrontendLocation",  //$NON-NLS-1$
                "loc: " + loc +  //$NON-NLS-1$
                " location: " + location +  //$NON-NLS-1$
                " niceLocation: " + niceLocation //$NON-NLS-1$
            );

    }

    private Boolean populateLocations(FrontendManager feMgr) {

        if (feMgr == null || !feMgr.isConnected())
            return false;

        try {
            locations = feMgr.getLocs();
        } catch (IOException e) { return false; }
        
        return true;
        
    }

    private void parsePlaybackLoc(String loc) {
        
        String[] tok = loc.split(" "); //$NON-NLS-1$
        niceLocation = tok[0] + " " + tok[1]; //$NON-NLS-1$
        location = niceLocation;
        position = timeToInt(tok[2]);
        
        if (tok[1].equals("recorded") || tok[1].equals("livetv")) { //$NON-NLS-1$ //$NON-NLS-2$
            end = timeToInt(tok[4]);
            if (tok[5].equals("pause")) //$NON-NLS-1$
                rate = 0;
            else
                rate = Float.parseFloat(
                    tok[5].substring(0, tok[5].lastIndexOf('x'))
                );
            filename = tok[9];
            if (tok[1].equals("livetv")) livetv = true; //$NON-NLS-1$
        }
        else if (tok[1].equals("video")) { //$NON-NLS-1$
            end = -1;
            if (tok[3].equals("pause")) //$NON-NLS-1$
                rate = 0;
            else
                rate = Float.parseFloat(
                    tok[3].substring(0, tok[3].lastIndexOf('x'))
                );
            filename = "Video"; //$NON-NLS-1$
        }
        else {
            rate = loc.contains("pause") ? 0 : -1; //$NON-NLS-1$
            end = -1;
            filename = "Unknown"; //$NON-NLS-1$
        }
        
        video = true;
        
        if (Globals.debug) 
            Log.d(
                "FrontendLocation",  //$NON-NLS-1$
                "position: " + position +  //$NON-NLS-1$
                " end: " + end +  //$NON-NLS-1$
                " rate: " + rate +  //$NON-NLS-1$
                " filename: " + filename +  //$NON-NLS-1$
                " livetv: " + livetv //$NON-NLS-1$
            );
        
    }

    private int timeToInt(String time) {
        
        String tm[] = time.split(":"); //$NON-NLS-1$
        
        if (tm.length == 2)
            return Integer.parseInt(tm[0]) * 60 +
                     Integer.parseInt(tm[1]);
        
        return Integer.parseInt(tm[0]) * 3600 + 
                 Integer.parseInt(tm[1]) * 60 +
                   Integer.parseInt(tm[2]);
        
    }

}
