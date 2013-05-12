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

package org.mythdroid.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.mythdroid.data.Video;
import org.mythdroid.util.ErrUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/** An implementation of the Video service */
public class VideoService {
    
    static private GsonBuilder gsonBuilder = new GsonBuilder();
    static {
        gsonBuilder.registerTypeAdapter(
            Video.class, new Video.VideoJsonAdapter()
        ); 
    }
    static private Gson gson = gsonBuilder.create();
    
    private JSONClient jc = null;
    
    /**
     * Construct a client for the Video service
     * @param addr IP address or hostname of server
     */
    public VideoService(String addr) {
        jc = new JSONClient(addr, "Video"); //$NON-NLS-1$
    }
    
    /**
     * Get a list of videos / directories in a given subdirectory
     * @param subdir the desired subdirectory or "ROOT" for the top-level
     * @return an ArrayList of Videos
     */
    public ArrayList<Video> getVideos(String subdir) throws IOException {
        
        ArrayList<Video> videos = new ArrayList<Video>(128);
        
        InputStream is = jc.GetStream("GetVideoList", null); //$NON-NLS-1$
        
        if (is == null) return null;
        
        JsonReader jreader = new JsonReader(
            new BufferedReader(new InputStreamReader(is, "UTF-8")) //$NON-NLS-1$
        );
        
        Video vid;
        final ArrayList<String> subdirs = new ArrayList<String>(16);
        
        jreader.beginObject();
        skipTo(jreader, JsonToken.BEGIN_OBJECT);
        jreader.beginObject();
        skipTo(jreader, JsonToken.BEGIN_ARRAY);
        jreader.beginArray();
        while (jreader.hasNext()) {
            jreader.beginObject();
            vid = gson.fromJson(jreader, Video.class);
            jreader.endObject();
            
            if (!subdir.equals("ROOT") && !vid.filename.startsWith(subdir)) //$NON-NLS-1$
                continue;
            
            String name = vid.filename;
            
            if (!subdir.equals("ROOT")) //$NON-NLS-1$
                name = vid.filename.substring(subdir.length() + 1);
            
            int slash;
            if ((slash = name.indexOf('/')) > 0) {
                String dir = name.substring(0, slash);
                if (!subdirs.contains(dir))
                    subdirs.add(dir);
            }
            else
                videos.add(vid);  
        }
        jreader.endArray();
        jreader.endObject();
        jreader.endObject();
        jreader.close();
        jc.endStream();
        
        for (String name : subdirs) {
            try {
                videos.add(new Video("-1 DIRECTORY " + name)); //$NON-NLS-1$
            } catch (IllegalArgumentException e) { ErrUtil.logWarn(e); }            
        }
        
        videos.trimToSize();
        
        return videos;
        
    }
    
    private void skipTo(JsonReader jr, JsonToken token) throws IOException {
        while (jr.hasNext() && jr.peek() != token)
                jr.skipValue();
    }
    
}
    