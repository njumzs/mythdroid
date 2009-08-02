/*
    MythDroid: Android MythTV Remote
    
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
import java.util.HashMap;

import android.util.Log;

/** Describes a location in the frontend */
public class FrontendLocation {

    private static HashMap<String, String> locations = null;
        
    public String  location  = null, niceLocation = null;
    public int     position, end;
    public float   rate;
    public String  filename;
    public boolean video = false, livetv = false, music = false;

    /**
     * Constructor
     * @param loc - String describing location
     */
    public FrontendLocation(String loc) {

        location = loc;
        
        if (MythDroid.debug) Log.d("FrontendLocation", "loc: " + loc);
        
        if (locations == null && !populateLocations()) 
            return;

        loc = loc.toLowerCase();

        if (loc.startsWith("playback "))
            parsePlaybackLoc(loc);
        else if (loc.startsWith("error"))
            location = niceLocation = "Error";
        else
            niceLocation = locations.get(loc);

        if (niceLocation == null)
            niceLocation = "Unknown";

        else if (loc.equals("playmusic")) 
            music = true;
        
        if (MythDroid.debug) 
            Log.d(
                "FrontendLocation", 
                "loc: " + loc + 
                " location: " + location + 
                " niceLocation: " + niceLocation
            );

    }

    private Boolean populateLocations() {

        final FrontendManager feMgr = MythDroid.feMgr;

        if (feMgr == null || !feMgr.isConnected())
            return false;

        try {
            locations = feMgr.getLocs();
        } catch (IOException e) { return false; }
        
        return true;
        
    }

    private void parsePlaybackLoc(String loc) {
        String[] tok = loc.split(" ");
        niceLocation = tok[0] + " " + tok[1];
        location = niceLocation;
        position = timeToInt(tok[2]);
        end = timeToInt(tok[4]);
        rate = Float.parseFloat(tok[5].substring(0, tok[5].lastIndexOf('x')));
        filename = tok[9];
        video = true;
        if (tok[1].equals("livetv")) livetv = true;
        if (MythDroid.debug) 
            Log.d(
                "FrontendLocation", 
                "position: " + position + 
                " end: " + end + 
                " rate: " + rate + 
                " filename: " + filename + 
                " livetv: " + livetv
            );
    }

    private int timeToInt(String time) {
        String tm[] = time.split(":");
        return Integer.parseInt(tm[0]) * 3600 + 
                 Integer.parseInt(tm[1]) * 60 +
                   Integer.parseInt(tm[2]);
    }

}
