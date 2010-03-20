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

package org.mythdroid.activities;

import java.util.ArrayList;
import java.util.WeakHashMap;

import org.mythdroid.Extras;
import org.mythdroid.R;
import org.mythdroid.data.Video;
import org.mythdroid.data.VideoAdapter;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.util.ErrUtil;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

public class Videos extends MDActivity implements 
    ListView.OnItemClickListener, AdapterView.OnItemLongClickListener {
    
    final private WeakHashMap<Integer, Drawable> artCache = 
        new WeakHashMap<Integer, Drawable>(32);
    
    final private Handler handler   = new Handler();
    private Thread artThread        = null;
    private ListView lv             = null;
    private ArrayList<Video> videos = null;
    private String path             = "ROOT";
    private TextView dirText        = null;
    private boolean fetchingArt     = false;
    /** Scale factor for pixel values for different display densities */
    private float scale              = 1; 
    
    /**
     * Fetch a list of videos from MDD and then start a Thread to fetch 
     * the posters
     */
    final private Runnable getVideos  = new Runnable() {
        @Override
        public void run() {
            
            try {
                videos = MDDManager.getVideos(
                    MythDroid.beMgr.getAddress(), path
                );
            } catch (Exception e) {
                ErrUtil.postErr(ctx, new Exception("Failed to connect to MDD"));
                finish();
                return;
            }
        
            handler.post(
                new Runnable() {
                    public void run() {
                        lv.setAdapter(
                            new VideoAdapter(
                                ctx, R.layout.video, videos
                            )
                        );
                        if (artThread != null) {
                            fetchingArt = false;
                            artThread.interrupt();
                            try {
                                artThread.join();
                            } catch (InterruptedException e) {}
                        }
                        fetchingArt = true;
                        artThread = new Thread(fetchArt);
                        artThread.start();
                        dismissDialog(DIALOG_LOAD);
                    }
                }
            );
        }
    };
    
    /**
     * Fetch posters for the current list of videos 
     */
    final private Runnable fetchArt = new Runnable() {
        @Override
        public void run() {

            for (Video video : videos) {
                if (!fetchingArt)
                    break;
                if (video.poster != null) continue;
                Drawable d = artCache.get(video.id);
                if (d != null) 
                    video.poster = d;
                else { 
                    video.getPoster(70 * scale + 0.5f, 110 * scale + 0.5f);
                    artCache.put(video.id, video.poster);
                }
                handler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            ((VideoAdapter)lv.getAdapter())
                                .notifyDataSetChanged();
                        }
                    }
                );
            }
            
        }
    };
       
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.videos);
        
        dirText = (TextView)findViewById(R.id.videoDir);
        lv = (ListView)findViewById(R.id.videoList);
        lv.setOnItemClickListener(this);
        lv.setOnItemLongClickListener(this);
        
        scale = getResources().getDisplayMetrics().density;
        
        showDialog(DIALOG_LOAD);
        MythDroid.wHandler.post(getVideos);
    }
    
    @Override
    public void onItemClick(
        AdapterView<?> adapter, View view, int pos, long id
    ) {
    
        Video video = videos.get(pos);
        
        if (video.id == -1) {
            if (path.equals("ROOT"))
                path = video.title;
            else
                path += "/" + video.title;
            dirText.setText(currentDir(path));
            showDialog(DIALOG_LOAD);
            MythDroid.wHandler.post(getVideos);
            return;
        }
        
        MythDroid.curVid = video;
        startActivity(new Intent().setClass(this, VideoDetail.class));
        
    }
    
    @Override
    public boolean onItemLongClick(
        AdapterView<?> adapter, View item, int pos, long itemid
    ) {
        Video video = videos.get(pos);
        if (video.id == -1)
            return true;
        setExtra(Extras.FILENAME.toString(), video.filename);
        setExtra(Extras.TITLE.toString(), video.title);
        nextActivity = TVRemote.class;
        showDialog(FRONTEND_CHOOSER);
        return true;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent event) {
        
        if (code == KeyEvent.KEYCODE_BACK) {
            
            if (path.equals("ROOT")) 
                return super.onKeyDown(code, event);
            int slash = path.lastIndexOf('/');
            if (slash == -1) {
                path = "ROOT";
                dirText.setText("Videos");
            }
            else {
                path = path.substring(0, slash);
                dirText.setText(currentDir(path));
            }
            showDialog(DIALOG_LOAD);
            MythDroid.wHandler.post(getVideos);
            return true;
            
        }

        return super.onKeyDown(code, event);
        
    }
    
    private String currentDir(String path) {
        int slash = path.lastIndexOf('/');
        if (slash == -1)
            return path;
        else
            return path.substring(slash + 1);
    }
  
    
    

}
