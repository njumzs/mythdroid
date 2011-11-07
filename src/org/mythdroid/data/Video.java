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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.mythdroid.Globals;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;

/** Represents a video or directory containing videos */
public class Video {

    @SuppressWarnings("all")
    public String title, subtitle, director, plot, homepage, filename, coverfile;
    @SuppressWarnings("all")
    public float rating;
    @SuppressWarnings("all")
    public int year, length, id, dir = -1;
    @SuppressWarnings("all")
    public boolean directory = false;
    @SuppressWarnings("all")
    public BitmapDrawable poster = null;

    final private static int
        ID = 0, TITLE = 1, SUBTITLE = 2, DIRECTOR = 3, PLOT = 4, HOMEPAGE = 5,
        YEAR = 6, USERRATING = 7, LENGTH = 8, FILENAME = 9, COVER = 10;

    /**
     * Constructor
     * @param line A String containing a DIRECTORY or VIDEO line from MDD
     */
    public Video(String line) {

        if (line.matches("^[0-9-]+ DIRECTORY .+")) { //$NON-NLS-1$
            dir   = Integer.valueOf(line.substring(0, line.indexOf(" "))); //$NON-NLS-1$
            title = line.substring(line.indexOf("DIRECTORY") + 10); //$NON-NLS-1$
            directory = true;
            return;
        }

        String[] fields = line.split("\\|\\|"); //$NON-NLS-1$

        if (fields.length < COVER)
            return;
        
        fields[0] = fields[0].replaceFirst("VIDEO ", ""); //$NON-NLS-1$ //$NON-NLS-2$

        id          = Integer.valueOf(fields[ID]);
        title       = fields[TITLE];
        subtitle    = fields[SUBTITLE];
        director    = fields[DIRECTOR];
        plot        = fields[PLOT];
        homepage    = fields[HOMEPAGE];
        year        = fields[YEAR].matches("[0-9]+") ? //$NON-NLS-1$
                          Integer.valueOf(fields[YEAR]) : 0;
        rating      = fields[USERRATING].matches("[0-9.]+") ? //$NON-NLS-1$
                          Float.parseFloat(fields[USERRATING]) : 0;
        length      = fields[LENGTH].matches("[0-9]+") ? //$NON-NLS-1$
                          Integer.valueOf(fields[LENGTH]) : 0;
        filename    = fields[FILENAME];
        if (fields.length > COVER)
            coverfile   = fields[COVER];

    }
    
    /**
     * Get a full path to the video (a myth:// URL if it's in a storage group)
     * @return String containing path to the video
     */
    public String getPath() {
        if (filename.startsWith("/")) //$NON-NLS-1$
            return filename;
        try {
            return "myth://Videos@" + Globals.getBackend().addr + "/" + filename; //$NON-NLS-1$ //$NON-NLS-2$
        } catch (IOException e) {
            return null; 
        }
    }


    /**
     * Fetch the poster for the video, scale it and store it as a Drawable
     *  in this.poster
     * @param x desired width of poster in pixels
     * @param y desired height of poster in pixels
     */
    public void getPoster(float x, float y) {

        if (coverfile == null)
            return;
        
        int w = Math.round(x);
        int h = Math.round(y);
        
        URI uri = null;
        try {
            uri = new URI(
                "http", null, Globals.getBackend().addr, 16551, coverfile, //$NON-NLS-1$
                "width=" + w + "&height=" + h, null //$NON-NLS-1$ //$NON-NLS-2$
            ); 
        } catch (URISyntaxException e) { return; } 
          catch (IOException e) { return; }
        
       if (Globals.muxConns)
          try {
              uri = new URI(
                  "http", null, uri.getHost(), 16550, //$NON-NLS-1$
                  "/MDDHTTP" + uri.getPath(), uri.getQuery(), null //$NON-NLS-1$
              );
          } catch (URISyntaxException e) { return; }

        Bitmap bm = null;

        try {
            HttpClient client = new DefaultHttpClient();
            HttpResponse resp = client.execute(new HttpGet(uri));
            if (resp.getStatusLine().getStatusCode() == 404)
                return;
            InputStream is = new BufferedHttpEntity(resp.getEntity())
                .getContent();
            bm = BitmapFactory.decodeStream(is);
            is.close();
        } catch (Exception e) {}
          catch (OutOfMemoryError e) {}

        if (bm == null) return;

        poster = new BitmapDrawable(bm);

    }

}
