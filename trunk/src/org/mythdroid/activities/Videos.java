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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.mythdroid.Enums.Extras;
import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.backend.BackendManager;
import org.mythdroid.data.Video;
import org.mythdroid.data.VideoAdapter;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.util.ImageCache;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

/** MDActivity displays a list of Videos */
public class Videos extends MDActivity implements
    ListView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    final private ImageCache artCache =
        new ImageCache("videos", 20, 200, 1024*1024*10); //$NON-NLS-1$
    
    final private HashMap<Integer,String> viddirs = 
        new HashMap<Integer, String>();

    final private Handler handler   = new Handler();
    private Thread artThread        = null;
    private ListView lv             = null;
    private ArrayList<Video> videos = null;
    private int viddir              = -1;
    private String path             = "ROOT"; //$NON-NLS-1$
    private TextView dirText        = null;
    private boolean fetchingArt     = false;
    private boolean largeScreen     = false;
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
                BackendManager beMgr = Globals.getBackend();
                if (beMgr == null) {
                    ErrUtil.postErr(
                        ctx, new Exception(Messages.getString("Videos.2")) //$NON-NLS-1$
                    );
                    finish();
                    return;
                }
                
                /* We use an empty string to denote the root of a 
                   top-level directory */
                String tmppath = path.length() > 0 ? path : "ROOT"; //$NON-NLS-1$
                
                videos = MDDManager.getVideos(
                    Globals.getBackend().addr, viddir, tmppath
                );
            } catch (IOException e) {
                ErrUtil.postErr(
                    ctx, new Exception(Messages.getString("Videos.1")) //$NON-NLS-1$
                );
                finish();
                return;
            }

            handler.post(
                new Runnable() {
                    @Override
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
                        artThread = new Thread(fetchArt, "videoArtFetcher"); //$NON-NLS-1$
                        artThread.start();
                        try {
                            dismissDialog(DIALOG_LOAD);
                        } catch (IllegalArgumentException e1) {}
                    }
                }
            );
        }
    };

    /** Fetch posters for the current list of videos */
    final private Runnable fetchArt = new Runnable() {
        @Override
        public void run() {

            Video[] vids = videos.toArray(new Video[videos.size()]);
            int numvids = vids.length;
            
            for (int i = 0; i < numvids; i++) {
                if (!fetchingArt)
                    break;
                if (vids[i].poster != null || vids[i].directory) continue;
                Bitmap bm = artCache.get(vids[i].id);
                if (bm != null)
                    vids[i].poster = new BitmapDrawable(bm);
                else {
                    float w = (largeScreen ? 175 : 70) * scale + 0.5f;
                    float h = (largeScreen ? 275 : 110) * scale + 0.5f;
                    vids[i].getPoster(w, h); 
                    if (vids[i].poster != null)
                        artCache.put(vids[i].id, vids[i].poster.getBitmap());
                }
                if (vids[i].poster != null)
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
        addHereToFrontendChooser(VideoPlayer.class);
        dirText = (TextView)findViewById(R.id.videoDir);
        lv = (ListView)findViewById(R.id.videoList);
        lv.setOnItemClickListener(this);
        lv.setOnItemLongClickListener(this);

        scale = getResources().getDisplayMetrics().density;
        largeScreen = getResources().getDisplayMetrics().widthPixels > 1000;
        
        showDialog(DIALOG_LOAD);
        Globals.getWorker().post(getVideos);
        
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        artCache.shutdown();
    }

    @Override
    public void onItemClick(
        AdapterView<?> adapter, View view, int pos, long id
    ) {

        Video video = videos.get(pos);

        // A directory?
        if (video.directory) {

            if (path.equals("ROOT")) //$NON-NLS-1$
                // Top top level?
                if (video.dir == -1)
                    path = video.title;
                // The root of a top-level directory (multiple videodirs)
                else {
                    path = ""; //$NON-NLS-1$
                    viddirs.put(video.dir, video.title);
                }
            else {
                if (path.length() > 0)
                    path += "/";  //$NON-NLS-1$
                path += video.title;
            }
     
            if (path.equals("")) //$NON-NLS-1$
                dirText.setText(video.title);
            else
                dirText.setText(currentDir(path));
            viddir = video.dir;
            showDialog(DIALOG_LOAD);
            Globals.getWorker().post(getVideos);
            return;

        }

        Globals.curVid = video;
        startActivity(new Intent().setClass(this, VideoDetail.class));

    }

    @Override
    public boolean onItemLongClick(
        AdapterView<?> adapter, View item, int pos, long itemid
    ) {
        Video video = videos.get(pos);
        if (video.directory)
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

            // At top level?
            if (path.equals("ROOT") && viddir == -1)  //$NON-NLS-1$
                return super.onKeyDown(code, event);
            // Going to top level?
            int slash = path.lastIndexOf('/');
            if (slash == -1) {
                // Check if we're going to list of videodirs (top top level)
                if (path.equals("ROOT") || path.equals("")) //$NON-NLS-1$ //$NON-NLS-2$
                    viddir = -1;
                // Going to top top level
                if (viddir == -1) {
                    path = "ROOT"; //$NON-NLS-1$
                    dirText.setText(Messages.getString("Videos.0")); //$NON-NLS-1$
                }
                // Going to root of a toplevel directory
                else {
                    path = "";  //$NON-NLS-1$
                    dirText.setText(viddirs.get(viddir));
                }
            }
            else {
                path = path.substring(0, slash);
                dirText.setText(currentDir(path));
            }
            
            showDialog(DIALOG_LOAD);
            Globals.getWorker().post(getVideos);
            return true;

        }

        return super.onKeyDown(code, event);

    }

    private String currentDir(String path) {
        int slash = path.lastIndexOf('/');
        if (slash == -1)
            return path;
        return path.substring(slash + 1);
    }

}
