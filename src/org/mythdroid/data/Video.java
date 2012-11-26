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

package org.mythdroid.data;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;
import org.mythdroid.Enums.ArtworkType;
import org.mythdroid.Globals;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.util.HttpFetcher;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

/** Represents a video or directory containing videos */
public class Video {

    final private static int
        ID = 0, TITLE = 1, SUBTITLE = 2, DIRECTOR = 3, PLOT = 4, HOMEPAGE = 5,
        YEAR = 6, USERRATING = 7, LENGTH = 8, FILENAME = 9, COVER = 10;
    
    final private static Pattern
        splitPat  = Pattern.compile("\\|\\|"), //$NON-NLS-1$
        dirPat    = Pattern.compile("^[0-9-]+ DIRECTORY .+"), //$NON-NLS-1$
        digitsPat = Pattern.compile("[0-9]+"); //$NON-NLS-1$
    
    @SuppressWarnings("all")
    public String 
        title, subtitle, director, plot, homepage, filename, coverfile;
    @SuppressWarnings("all")
    public float rating;
    @SuppressWarnings("all")
    public int year, length, id, dir = -1;
    @SuppressWarnings("all")
    public boolean directory = false;
    @SuppressWarnings("all")
    public BitmapDrawable poster = null;

    /**
     * Constructor
     * @param line A String containing a DIRECTORY or VIDEO line from MDD
     */
    public Video(String line) throws IllegalArgumentException {

        if (dirPat.matcher(line).matches()) {
            dir = Integer.valueOf(line.
                substring(0, line.indexOf(" "))); //$NON-NLS-1$
            title = line.substring(line.indexOf("DIRECTORY") + 10); //$NON-NLS-1$
            directory = true;
            return;
        }

        String[] fields = splitPat.split(line);

        if (fields.length < COVER) {
            IllegalArgumentException e = new IllegalArgumentException(
                String.format(
                    Messages.getString("Video.0"), fields.length, COVER //$NON-NLS-1$
                )
            );
            ErrUtil.report(e);
            throw e;
        }
        
        fields[0] = fields[0].substring(line.indexOf("VIDEO") + 6); //$NON-NLS-1$

        try {
            id       = Integer.valueOf(fields[ID]);
            title    = fields[TITLE];
            subtitle = fields[SUBTITLE];
            director = fields[DIRECTOR];
            plot     = fields[PLOT];
            homepage = fields[HOMEPAGE];
            filename = fields[FILENAME];
            year     = digitsPat.matcher(fields[YEAR]).matches() ?
                           Integer.valueOf(fields[YEAR]) : 0;
            rating   = digitsPat.matcher(fields[USERRATING]).matches() ?
                           Float.parseFloat(fields[USERRATING]) : 0;
            length   = digitsPat.matcher(fields[LENGTH]).matches() ?
                           Integer.valueOf(fields[LENGTH]) : 0;
            if (fields.length > COVER)
                coverfile = fields[COVER];
        } catch (NumberFormatException e) {
            ErrUtil.report(e);
            throw new IllegalArgumentException(
                Messages.getString("Video.1") + e.getMessage() //$NON-NLS-1$
            );
        }
        
    }
    
    /**
     * Constructor
     * @param jo VideoMetadataInfo JSONObject
     * @throws JSONException 
     * @throws ParseException 
     */
    public Video(JSONObject jo) throws ParseException, JSONException {
        id             = jo.getInt("Id"); //$NON-NLS-1$
        title          = jo.getString("Title"); //$NON-NLS-1$
        subtitle       = jo.getString("SubTitle"); //$NON-NLS-1$
        director       = jo.getString("Director"); //$NON-NLS-1$
        plot           = jo.getString("Description"); //$NON-NLS-1$
        homepage       = jo.getString("HomePage"); //$NON-NLS-1$
        rating         = jo.getInt("UserRating"); //$NON-NLS-1$
        length         = jo.getInt("Length"); //$NON-NLS-1$
        filename       = jo.getString("FileName"); //$NON-NLS-1$
        coverfile      = jo.getString("Coverart"); //$NON-NLS-1$
        String release = jo.getString("ReleaseDate"); //$NON-NLS-1$
        if (release.length() > 0)
            year = Globals.dateFmt.parse(release).getYear();
    }
    
    /**
     * Get a full path to the video (a myth:// URL if it's in a storage group)
     * @return String containing path to the video
     */
    public String getPath() throws IOException {
        if (filename.startsWith("/")) return filename; //$NON-NLS-1$
        return "myth://Videos@" + Globals.getBackend().addr + "/" + filename; //$NON-NLS-1$ //$NON-NLS-2$
    }


    /**
     * Fetch the poster for the video, scale it and store it as a Drawable
     *  in this.poster
     * @param x desired width of poster in pixels
     * @param y desired height of poster in pixels
     */
    public Bitmap getArtwork(ArtworkType type, float x, float y) {
       
        Bitmap bm = Video.getArtwork(id, type, x, y, coverfile);
        if (bm == null) return null;
        
        if (type == ArtworkType.coverart)
            poster = new BitmapDrawable(bm);
        
        return bm;

    }
    
    /**
     * Fetch artwork for a video
     * @param id id of video
     * @param type type of artwork
     * @param x desired width
     * @param y desired height
     * @param cover filename of coverfile, null if using services api
     * @return Bitmap or null if it's not found
     */
    public static Bitmap getArtwork(
        int id, ArtworkType type, float x, float y, String cover
    ) {
        
        if (!Globals.haveServices() && !(type == ArtworkType.coverart))
            return null;
        
        if (!Globals.haveServices() && cover == null)
            return null;
        
        int w = Math.round(x);
        int h = Math.round(y);
        
        URI uri = null;
        try {
            uri = new URI(
                "http", null, Globals.getBackend().addr, //$NON-NLS-1$
                (Globals.haveServices() ? 6544 : 16551), 
                (Globals.haveServices() ? 
                    "/Content/GetVideoArtwork" : cover //$NON-NLS-1$
                ),
                (Globals.haveServices() ?
                    "Id=" + id + "&Type=" + type.name() + "&" : "") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                "Width=" + w + "&Height=" + h, null //$NON-NLS-1$ //$NON-NLS-2$
            ); 
        } catch (URISyntaxException e)  { ErrUtil.logWarn(e); return null; } 
          catch (IOException e)         { ErrUtil.logWarn(e); return null; }

       return HttpFetcher.getImage(uri, Globals.muxConns);

    }

}
