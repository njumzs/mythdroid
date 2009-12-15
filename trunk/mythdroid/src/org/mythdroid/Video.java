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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.BitmapFactory.Options;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Class representing a video
 */
public class Video {
    
    public String title, subtitle, director, plot, homepage, filename;
    public float rating;
    public int year, length, id = -1;
      
    public Drawable poster = null;
    
    final private static int 
        ID = 0, TITLE = 1, SUBTITLE = 2, DIRECTOR = 3, PLOT = 4, HOMEPAGE = 5,
        YEAR = 6, USERRATING = 7, LENGTH = 8, FILENAME = 9;
    
    /**
     * Constructor
     * @param line - A String containing a DIRECTORY or VIDEO line from MDD 
     */
    public Video(String line) {
        
        if (line.startsWith("DIRECTORY")) {
            title = line.substring(line.indexOf("DIRECTORY") + 10);
            return;
        }
        
        List<String> fields = Arrays.asList(line.split("\\|\\|"));
        fields.set(0, fields.get(0).replaceFirst("VIDEO ", ""));
                       
        id = Integer.valueOf(fields.get(ID));
        title = fields.get(TITLE);
        subtitle = fields.get(SUBTITLE);
        director = fields.get(DIRECTOR);
        plot = fields.get(PLOT);
        homepage = fields.get(HOMEPAGE);
        year = fields.get(YEAR).matches("[0-9]+") ?
                   Integer.valueOf(fields.get(YEAR)) : 0;
        rating = fields.get(USERRATING).matches("[0-9.]+") ?
                   Float.parseFloat(fields.get(USERRATING)) : 0;
        length = fields.get(LENGTH).matches("[0-9]+") ?
                   Integer.valueOf(fields.get(LENGTH)) : 0;
        filename = fields.get(FILENAME);
        
    }
    
    
    /**
     * Fetch the poster for the video, scale it and store it as a Drawable
     *  in this.poster
     * @param x - desired width of poster in pixels
     * @param y - desired height of poster in pixels
     */
    public void getPoster(float x, float y) {
        
        if (id == -1) 
            return;
       
        URL url = null;
        try {
            url = new URL(
                MythDroid.beMgr.getStatusURL() +
                "/Myth/GetVideoArt?" + 
                "Id=" + id 
            );
        } catch (MalformedURLException e) {}

        Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 8;
        
        Bitmap bm = null;
        try {
            URLConnection conn = url.openConnection();
            conn.connect();
            bm = BitmapFactory.decodeStream(
                conn.getInputStream(), null, opts
            );
        } catch(IOException e) {}
        if (bm == null)
            return;
        
        int width = bm.getWidth();
        int height = bm.getHeight();
        float wf = x / width;
        float hf = y / height;
        float factor = (wf < hf ? wf : hf);
        Matrix matrix = new Matrix();
        matrix.postScale(factor, factor);
        poster = new BitmapDrawable(
            Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true)
        );
        
    }

}
