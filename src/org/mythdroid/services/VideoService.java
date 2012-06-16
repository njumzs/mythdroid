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

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mythdroid.data.Video;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.util.LogUtil;
import org.mythdroid.util.MemCache;

/** An implementation of the Video service */
public class VideoService {
    
    private JSONClient jc = null;
    private JSONArray  ja = null;
    private MemCache<Integer, Video> videoCache = null;
    
    /**
     * Construct a client for the Content service
     * @param addr IP address or hostname of server
     */
    public VideoService(String addr) {
        jc = new JSONClient(addr, "Video"); //$NON-NLS-1$
        videoCache = new MemCache<Integer, Video>(20, 100);
    }
    
    /**
     * Get a list of videos / directories in a given subdirectory
     * @param subdir the desired subdirectory or "ROOT" for the top-level
     * @return an ArrayList of Videos
     */
    public ArrayList<Video> getVideos(String subdir) throws IOException {
        
        ArrayList<Video> videos = null;
        
        if (ja == null) {
        
            JSONObject jo = jc.Get("GetVideoList", null); //$NON-NLS-1$
        
            if (jo == null) return null;
        
            try {
                jo = jo.getJSONObject("VideoMetadataInfoList"); //$NON-NLS-1$
            } catch (JSONException e) {
                LogUtil.error(e.getMessage());
                return null;
            }
        
            try {
                videos = new ArrayList<Video>(jo.getInt("TotalAvailable")); //$NON-NLS-1$
            } catch (JSONException e) {
                LogUtil.error(e.getMessage());
                return null;
            }
        
            try {
                ja = jo.getJSONArray("VideoMetadataInfos"); //$NON-NLS-1$
            } catch (JSONException e) {
                LogUtil.debug(e.getMessage());
                return null;
            }
            
        }
        else
            videos = new ArrayList<Video>(ja.length());
        
        int num = ja.length();
        
        final ArrayList<String> subdirs = new ArrayList<String>(16);
        
        for (int i = 0; i < num; i++) {
            
            try {
                
                Video vid = null;
                
                if ((vid = videoCache.get(i)) == null) {
                    try {
                        vid = new Video(ja.getJSONObject(i));
                        videoCache.put(i, vid);
                    } catch (IllegalArgumentException e) {
                        ErrUtil.logWarn(e);
                        continue;
                    }
                }
                
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
                
            } catch (ParseException e) {
                LogUtil.error(e.getMessage());
                continue;
            } catch (JSONException e) {
                LogUtil.error(e.getMessage());
                continue;
            }
            
        }
        
        for (String name : subdirs) {
            try {
                videos.add(new Video("-1 DIRECTORY " + name)); //$NON-NLS-1$
            } catch (IllegalArgumentException e) { ErrUtil.logWarn(e); }            
        }
        
        return videos;
        
    }
    
}
    