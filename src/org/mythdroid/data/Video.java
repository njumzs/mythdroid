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

import java.io.InputStream;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.mythdroid.Globals;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.BitmapFactory.Options;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** Represents a video or directory containing videos */
public class Video {

    @SuppressWarnings("all")
    public String title, subtitle, director, plot, homepage, filename;
    @SuppressWarnings("all")
    public float rating;
    @SuppressWarnings("all")
    public int year, length, dir = -1, id = -1;
    @SuppressWarnings("all")
    public Drawable poster = null;
    
    final private static Options opts = new BitmapFactory.Options();
    static { opts.inSampleSize = 8; }

    final private static int
        ID = 0, TITLE = 1, SUBTITLE = 2, DIRECTOR = 3, PLOT = 4, HOMEPAGE = 5,
        YEAR = 6, USERRATING = 7, LENGTH = 8, FILENAME = 9;

    /**
     * Constructor
     * @param line A String containing a DIRECTORY or VIDEO line from MDD
     */
    public Video(String line) {

        if (line.matches("^[0-9-]+ DIRECTORY .+")) { //$NON-NLS-1$
            dir   = Integer.valueOf(line.substring(0, line.indexOf(" "))); //$NON-NLS-1$
            title = line.substring(line.indexOf("DIRECTORY") + 10); //$NON-NLS-1$
            return;
        }

        String[] fields = line.split("\\|\\|"); //$NON-NLS-1$

        if (fields.length < FILENAME + 1)
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

    }


    /**
     * Fetch the poster for the video, scale it and store it as a Drawable
     *  in this.poster
     * @param x desired width of poster in pixels
     * @param y desired height of poster in pixels
     */
    public void getPoster(float x, float y) {

        if (id == -1)
            return;

        URL url = null;
        try {
            url = new URL(
                Globals.getBackend().getStatusURL() +
                "/Myth/GetVideoArt?" +  //$NON-NLS-1$
                "Id=" + id  //$NON-NLS-1$
            );
        } catch (Exception e) {}

        if (url == null)
            return;

        Bitmap bm = null;

        try {
            HttpClient client = new DefaultHttpClient();
            HttpResponse resp = client.execute(new HttpGet(url.toURI()));
            if (resp.getStatusLine().getStatusCode() == 404)
                return;
            InputStream is = new BufferedHttpEntity(resp.getEntity())
                .getContent();
            bm = BitmapFactory.decodeStream(is, null, opts);
            is.close();
        } catch (Exception e) {}

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
