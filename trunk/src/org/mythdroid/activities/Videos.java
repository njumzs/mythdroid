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

import org.mythdroid.Enums.ArtworkType;
import org.mythdroid.Enums.Extras;
import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.data.Video;
import org.mythdroid.data.VideoAdapter;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;
import org.mythdroid.services.VideoService;
import org.mythdroid.util.ErrUtil;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

/** MDActivity displays a list of Videos */
public class Videos extends MDActivity implements
    ListView.OnItemClickListener, AdapterView.OnItemLongClickListener {
    
    final private SparseArray<String> viddirs = new SparseArray<String>();

    final private Handler handler   = new Handler();
    private ListView lv             = null;
    private ArrayList<Video> videos = null;
    private int viddir              = -1;
    private String path             = "ROOT"; //$NON-NLS-1$
    private TextView dirText        = null;
    private boolean largeScreen     = false;
    /** Scale factor for pixel values for different display densities */
    private float scale             = 1;
    
    private VideoService videoService = null;

    /**
     * Fetch a list of videos from MDD and then start threads to fetch
     * the posters
     */
    final private Runnable getVideos  = new Runnable() {
        @Override
        public void run() {

            /* We use an empty string to denote the root of a 
               top-level directory */
            String tmppath = path.length() > 0 ? path : "ROOT"; //$NON-NLS-1$
            
            /* Yikes.. Should be safe since this runnable is already running
               and there shouldn't be anything else in the queue (we hope!) */
            Globals.removeAllThreadPoolTasks();

            if (!Globals.haveServices()) {
                String addr = null;
                try {
                    addr = Globals.getBackend().addr;
                } catch (IOException e) {
                    ErrUtil.postErr(ctx, e);
                    finish();
                    return;
                }
                try {
                    videos = MDDManager.getVideos(addr, viddir, tmppath);
                } catch (IOException e) {
                    ErrUtil.postErr(ctx, e);
                    finish();
                    return;
                }
            }
            else
                try {
                    videos = videoService.getVideos(tmppath);
                } catch (IOException e) {
                    ErrUtil.postErr(ctx, e);
                    finish();
                    return;
                }
            
            final Video[] vids = videos.toArray(new Video[videos.size()]);
            int numvids = vids.length;
            
            for (int i = 0; i < numvids; i++) {
                
                final Video vid = vids[i];
                if (vid.poster != null || vid.directory)  continue;
                
                Globals.runOnThreadPool(
                    new Runnable() {
                        @Override
                        public void run() {
                            vid.getArtwork(
                                ArtworkType.coverart,
                                (largeScreen ? 175 : 70)  * scale + 0.5f,
                                (largeScreen ? 275 : 110) * scale + 0.5f
                            );
                            if (vid.poster != null)
                                handler.post(notifyChanged);
                        }
                    }
               );
                
            }

            handler.post(gotVideos);
                
        }
    };
    
    final private Runnable notifyChanged = new Runnable() {
        @Override
        public void run() {
            ((VideoAdapter)lv.getAdapter()).notifyDataSetChanged();
        }
    };
    
    final private Runnable gotVideos = new Runnable() {
        @Override
        public void run() {
            lv.setAdapter(new VideoAdapter(ctx, R.layout.video, videos));
            dismissLoadingDialog();
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
        
        if (Globals.haveServices()) 
            try {
                videoService = new VideoService(Globals.getBackend().addr);
            } catch (IOException e) {
                ErrUtil.err(this, e.getMessage());
                finish();
            }
        
        refresh();
        
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Globals.removeThreadPoolTask(getVideos);
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
            refresh();
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
        try {
            setExtra(Extras.FILENAME.toString(), video.getPath());
        } catch(IOException e) {
            ErrUtil.err(this, e);
            return true;
        }
        setExtra(Extras.TITLE.toString(), video.title);
        setExtra(Extras.VIDEOID.toString(), video.id);
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
            
            refresh();
            return true;

        }

        return super.onKeyDown(code, event);

    }
    
    private void refresh() {
        Globals.removeThreadPoolTask(getVideos);
        showDialog(DIALOG_LOAD);
        Globals.runOnThreadPool(getVideos);
    }

    private String currentDir(String path) {
        int slash = path.lastIndexOf('/');
        if (slash == -1)
            return path;
        return path.substring(slash + 1);
    }

}
