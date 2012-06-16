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
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mythdroid.Globals;
import org.mythdroid.Enums.ArtworkType;
import org.mythdroid.data.StreamInfo;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.util.LogUtil;
import org.mythdroid.util.MemCache;

import android.graphics.Bitmap;

/** An implementation of the Guide service */
public class ContentService {
    
    static private MemCache<String, String> artUrlCache =
        new MemCache<String,String>(10, 40);
    
    private JSONClient  jc = null;
    
    /**
     * Construct a client for the Guide service
     * @param addr IP address or hostname of server
     */
    public ContentService(String addr) {
        jc = new JSONClient(addr, "Content"); //$NON-NLS-1$
    }
    
    /**
     * Fetch artwork for a recording
     * @param chanid recording chanid
     * @param start recording StartTime
     * @param type type of artwork desired
     * @return Bitmap or null if not found
     * @throws IOException
     * @throws JSONException
     */
    public Bitmap getRecordingArt(
        int chanid, Date start, ArtworkType type, int w, int h
    ) throws IOException, JSONException {
        
        final String key  = chanid + Globals.utcFmt.format(start) + type.name();
        String path = artUrlCache.get(key);
        
        if (path == null) {
        
            final JSONArray ja = getArtworkList(chanid, start);
            int size = ja.length();
        
            for (int i = 0; i < size; i++) {
                final JSONObject jo = ja.getJSONObject(i);
                if (!jo.getString("Type").equals(type.name())) //$NON-NLS-1$
                    continue;
                path = jo.getString("URL"); //$NON-NLS-1$
            }
            
        }
        
        if (path == null) return null;
        
        artUrlCache.put(key, path);
        
        path += "&Width=" + w + "&Height=" + h; //$NON-NLS-1$ //$NON-NLS-2$
        
        Bitmap bm = Globals.artCache.get(path);
        if (bm != null) return bm;
        
        bm = Globals.getBackend().getImage(path);
        
        if (bm != null) Globals.artCache.put(path, bm);
        
        return bm;
        
    }
    
   /**
     * Add a HTTP Live Stream
     * @param chanid channel id
     * @param startTime startTime in UTC ISO format
     * @param width desired width
     * @param height desired height
     * @param vb desired video bitrate
     * @param ab desired audio bitrate
     * @return a StreamInfo or null if there's a problem
     * @throws IOException
     */
    public StreamInfo StreamFile(
        int chanid, String startTime, int width, int height, int vb, int ab
    ) throws IOException {
        
        Params params = new Params();
        params.put("ChanId", chanid); //$NON-NLS-1$
        params.put("StartTime", startTime); //$NON-NLS-1$
        params.put("Width", width); //$NON-NLS-1$
        params.put("Height", height); //$NON-NLS-1$
        params.put("Bitrate", vb); //$NON-NLS-1$
        params.put("AudioBitrate", ab); //$NON-NLS-1$
        
        final JSONObject jo = jc.Get("AddRecordingLiveStream", params); //$NON-NLS-1$
        
        try {
            return new StreamInfo(jo.getJSONObject("LiveStreamInfo")); //$NON-NLS-1$
        } catch (JSONException e) {
            ErrUtil.logErr(e);
            return null;
        } 
   
    }
    
    /**
     * Add a HTTP Video Live Stream
     * @param id video id
     * @param width desired width
     * @param height desired height
     * @param vb desired video bitrate
     * @param ab desired audio bitrate
     * @return a StreamInfo or null if there's a problem
     * @throws IOException
     */
    public StreamInfo StreamFile(
        int id, int width, int height, int vb, int ab
    ) throws IOException {
        
        final Params params = new Params();
        params.put("Id", id); //$NON-NLS-1$
        params.put("Width", width); //$NON-NLS-1$
        params.put("Height", height); //$NON-NLS-1$
        params.put("Bitrate", vb); //$NON-NLS-1$
        params.put("AudioBitrate", ab); //$NON-NLS-1$
        
        final JSONObject jo = jc.Get("AddVideoLiveStream", params); //$NON-NLS-1$
        
        try {
            return new StreamInfo(jo.getJSONObject("LiveStreamInfo")); //$NON-NLS-1$
        } catch (JSONException e) {
            ErrUtil.logErr(e);
            return null;
        } 
   
    }
    
    /**
     * Remove a live stream
     * @param id stream id
     * @return true if successful, false otherwise
     */
    public boolean RemoveStream(int id) {
        final Params params = new Params("Id", String.valueOf(id)); //$NON-NLS-1$
        try {
            final JSONObject jo = jc.Get("RemoveLiveStream", params); //$NON-NLS-1$
            return jo.getBoolean("bool"); //$NON-NLS-1$
        } catch (IOException e) {
            ErrUtil.logErr(e);
            return false;
        } catch (JSONException e) {
            ErrUtil.logErr(e);
            return false;
        }
    }
    
    /**
     * Poll the stream status, return when it's started
     * @param id stream id
     */
    public void WaitForStream(int id) {
        
        int segments = 0;
        final Params params = new Params("Id", String.valueOf(id)); //$NON-NLS-1$
        
        while (segments < 2) {
            try {
                JSONObject jo = jc.Get("GetLiveStream", params); //$NON-NLS-1$
                jo = jo.getJSONObject("LiveStreamInfo"); //$NON-NLS-1$
                segments = jo.getInt("SegmentCount"); //$NON-NLS-1$
                LogUtil.debug("Live stream segments: " + segments); //$NON-NLS-1$
            } catch (IOException e) {
                ErrUtil.logErr(e);
                break;
            } catch (JSONException e) {
                ErrUtil.logErr(e);
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                continue;
            }
        }
        
    }
    
    private JSONArray getArtworkList(int chanid, Date start)
        throws IOException, JSONException {
        
        final Params params = new Params();
        params.put("ChanId", chanid); //$NON-NLS-1$
        params.put("StartTime", Globals.utcFmt.format(start)); //$NON-NLS-1$
        
        JSONObject jo = jc.Get("GetRecordingArtworkList", params); //$NON-NLS-1$
        jo = jo.getJSONObject("ArtworkInfoList"); //$NON-NLS-1$
        return jo.getJSONArray("ArtworkInfos"); //$NON-NLS-1$
        
    }
    
}
    