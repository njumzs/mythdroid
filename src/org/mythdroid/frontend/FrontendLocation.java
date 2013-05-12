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

import android.annotation.SuppressLint;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.mythdroid.Globals;
import org.mythdroid.Enums.PlaybackLocation;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.util.LogUtil;

/** Describes a location in the frontend */
public class FrontendLocation {

    /** True if the locations have been initialised, false otherwise */
    public static boolean hasLocations = false;
    /** A runnable that will populate the locations */
    final public static Runnable getLocations = new Runnable() {
        @Override
        public void run() {
            try {
                if (!hasLocations)
                    populateLocations(Globals.getFrontend(Globals.appContext));
            } catch (IOException e) { ErrUtil.logWarn(e); }
        }
    };
    
    private static Object locationsLock = new Object();
    
    private static HashMap<String, String> locations = null;
   
    @SuppressWarnings("javadoc")
    public int chanid, chapter, numchapters, volume, position, end;
    
    @SuppressWarnings("javadoc")
    public String playedtime, remainingtime, location, niceLocation, title,
                  subtitle, audiotrack, filename;
    
    @SuppressWarnings("javadoc")
    public Date starttime;
    
    @SuppressWarnings("javadoc")
    public float   fps, rate;
    @SuppressWarnings("javadoc")
    public boolean
        video = false, livetv = false, music = false, musiceditor = false;
    
    final private static String[] addLocs = {
         "library.xml",                 "Media Library",                //$NON-NLS-1$ //$NON-NLS-2$
         "info_menu.xml",               "Information Centre",           //$NON-NLS-1$ //$NON-NLS-2$
         "media_settings.xml",          "Media Settings",               //$NON-NLS-1$ //$NON-NLS-2$
         "tv_search.xml",               "TV Search",                    //$NON-NLS-1$ //$NON-NLS-2$
         "info_settings.xml",           "Information Centre Settings",  //$NON-NLS-1$ //$NON-NLS-2$
         "optical_menu.xml",            "Optical Disks",                //$NON-NLS-1$ //$NON-NLS-2$
         "tv_settings.xml",             "TV Settings",                  //$NON-NLS-1$ //$NON-NLS-2$
         "recpriorities_settings.xml",  "Recording Priorities",         //$NON-NLS-1$ //$NON-NLS-2$
         "tvmenu.xml",                  "TV Menu",                      //$NON-NLS-1$ //$NON-NLS-2$
         "main_settings.xml",           "Settings",                     //$NON-NLS-1$ //$NON-NLS-2$
         "util_menu.xml",               "Utilities",                    //$NON-NLS-1$ //$NON-NLS-2$
         "tv_lists.xml",                "TV Lists",                     //$NON-NLS-1$ //$NON-NLS-2$
         "manage_recordings.xml",       "Manage Recordings",            //$NON-NLS-1$ //$NON-NLS-2$
         "tv_schedule.xml",             "Schedule Recordings",          //$NON-NLS-1$ //$NON-NLS-2$
         "playlistview",                "Music Playlists",              //$NON-NLS-1$ //$NON-NLS-2$
         "playlisteditorview",          "Music Playlist Editor",        //$NON-NLS-1$ //$NON-NLS-2$
         "searchview",                  "Music Search",                 //$NON-NLS-1$ //$NON-NLS-2$
         "visualizerview",              "Music Visualizer"              //$NON-NLS-1$ //$NON-NLS-2$
    };

    /**
     * Constructor
     * @param loc String describing location
     */
	@SuppressLint("DefaultLocale")
	public FrontendLocation(FrontendManager feMgr, String loc)
        throws IllegalArgumentException {

        location = loc;

        if (!hasLocations && !populateLocations(feMgr))
            return;

        loc = loc.toLowerCase();

        if (loc.startsWith("playback ")) //$NON-NLS-1$
            parsePlaybackLoc(loc);
        else {
            niceLocation = getNiceLocation(loc);
            setMusicLocation(loc);
        }

        LogUtil.debug(
            "loc: " + loc +  //$NON-NLS-1$
            " location: " + location +  //$NON-NLS-1$
            " niceLocation: " + niceLocation //$NON-NLS-1$
        );

    }
    
    /**
     * Construct a FrontendStatus from a FrontendStatus JSONObject
     * @param jo FrontendStatus JSONObject
     * @throws JSONException
     * @throws ParseException
     */
    public FrontendLocation(JSONObject jo) throws JSONException, ParseException {
        
        JSONObject state     = jo.getJSONObject("State"); //$NON-NLS-1$
        location             = state.getString("state"); //$NON-NLS-1$
        
        if (location.equals("idle")) { //$NON-NLS-1$
            location = state.getString("currentlocation"); //$NON-NLS-1$
            niceLocation = getNiceLocation(location);
            setMusicLocation(location);            
            return;
        }
        
        switch (PlaybackLocation.valueOf(location)) {
            case WatchingLiveTV: livetv = true; break;
            case WatchingVideo:  video  = true; break;
        }
        
        parseJSONPlaybackLoc(state);
        
    }

    /**
     * Retrieve and parse the list of valid frontend locations
     * @param feMgr a FrontendManager to use
     * @return true if successful, false otherwise
     */
    public static Boolean populateLocations(FrontendManager feMgr) {

        if (feMgr == null || !feMgr.isConnected())
            return false;
        
        synchronized(locationsLock) {
        
            if (hasLocations)
                return false;
        
            try {
                locations = feMgr.getLocs();
            } catch (IOException e) { return false; }
        
            int l = addLocs.length;
        
            for (int i = 0; i < l; i += 2)
                locations.put(addLocs[i], addLocs[i+1]);

            hasLocations = true;
            
        }
        
        return true;

    }
    
    private static String getNiceLocation(String loc) {
        if (loc.startsWith("error")) return "Error"; //$NON-NLS-1$ //$NON-NLS-2$
        if (locations.containsKey(loc)) return locations.get(loc);
        return "Unknown"; //$NON-NLS-1$
    }
    
    private void setMusicLocation(String loc) {
        if (
            location.equals("playmusic") || //$NON-NLS-1$
            location.equals("playlistview") || //$NON-NLS-1$
            location.equals("searchview") || //$NON-NLS-1$
            location.equals("visualizerview") //$NON-NLS-1$
        )
            music = true;
        
        if (
            location.equals("musicplaylists") || //$NON-NLS-1$
            location.equals("playlisteditorview") //$NON-NLS-1$
        )
            musiceditor = true;
    }

    private void parsePlaybackLoc(String loc) throws IllegalArgumentException {

        final String[] tok = loc.split(" "); //$NON-NLS-1$
        niceLocation = tok[0] + " " + tok[1]; //$NON-NLS-1$
        location = niceLocation;
        position = timeToInt(tok[2]);

        if (tok[1].equals("recorded") || tok[1].equals("livetv")) { //$NON-NLS-1$ //$NON-NLS-2$
            if (tok.length < 11)
                throw new IllegalArgumentException("Invalid FE loc: " + loc); //$NON-NLS-1$
            end = timeToInt(tok[4]);
            if (tok[5].equals("pause")) //$NON-NLS-1$
                rate = 0;
            else
                rate = Float.parseFloat(
                    tok[5].substring(0, tok[5].lastIndexOf('x'))
                );
            chanid = Integer.parseInt(tok[6]);
            try {
                starttime = Globals.dateFmt.parse(tok[7]);
            } catch (ParseException e) {}
            filename = tok[9];
            try {
                fps = Float.parseFloat(tok[tok.length - 1]);
            } catch (NumberFormatException e) {
                fps = 0;
            }
            
            if (tok[1].equals("livetv")) livetv = true; //$NON-NLS-1$
        }
        else if (tok[1].equals("video")) { //$NON-NLS-1$
            if (tok.length < 7)
                throw new IllegalArgumentException("Invalid FE loc: " + loc); //$NON-NLS-1$
            end = -1;
            if (tok[3].equals("pause")) //$NON-NLS-1$
                rate = 0;
            else
                rate = Float.parseFloat(
                    tok[3].substring(0, tok[3].lastIndexOf('x'))
                );
            filename = "Video"; //$NON-NLS-1$
            try {
                fps = Float.parseFloat(tok[tok.length - 1]);
            } catch (NumberFormatException e) {
                fps = 0;
            }
        }
        else {
            rate = loc.contains("pause") ? 0 : -1; //$NON-NLS-1$
            end = -1;
            filename = "Unknown"; //$NON-NLS-1$
        }

        video = true;

        LogUtil.debug(
            "position: " + position +  //$NON-NLS-1$
            " end: " + end +  //$NON-NLS-1$
            " rate: " + rate +  //$NON-NLS-1$
            " chanid: " + chanid + //$NON-NLS-1$
            " filename: " + filename +  //$NON-NLS-1$
            " livetv: " + livetv //$NON-NLS-1$
        );

    }
    
    private void parseJSONPlaybackLoc(JSONObject state) 
        throws NumberFormatException, JSONException, ParseException {
        
        chanid          = Integer.valueOf(state.getString("chanid")); //$NON-NLS-1$
        chapter         = Integer.valueOf(state.getString("chapteridx")); //$NON-NLS-1$
        numchapters     = Integer.valueOf(state.getString("totalchapters")); //$NON-NLS-1$
        volume          = Integer.valueOf(state.getString("volume")); //$NON-NLS-1$
        rate            = Float.parseFloat(state.getString("playspeed")); //$NON-NLS-1$
        chapter         = Integer.valueOf(state.getString("chapteridx")); //$NON-NLS-1$
        position        = Integer.valueOf(state.getString("secondsplayed")); //$NON-NLS-1$
        end             = Integer.valueOf(state.getString("totalseconds")); //$NON-NLS-1$
        playedtime      = state.getString("playedtime"); //$NON-NLS-1$
        remainingtime   = state.getString("remainingtime"); //$NON-NLS-1$
        title           = state.getString("title"); //$NON-NLS-1$
        subtitle        = state.getString("subtitle"); //$NON-NLS-1$
        audiotrack      = state.getString("audiotrack"); //$NON-NLS-1$
        starttime       = Globals.utcFmt.parse(state.getString("starttime")); //$NON-NLS-1$
        
    }

    private static int timeToInt(String time) throws NumberFormatException {

        final String tm[] = time.split(":"); //$NON-NLS-1$

        if (tm.length == 2)
            return Integer.parseInt(tm[0]) * 60 +
                     Integer.parseInt(tm[1]);

        return Integer.parseInt(tm[0]) * 3600 +
                 Integer.parseInt(tm[1]) * 60 +
                   Integer.parseInt(tm[2]);

    }

}
