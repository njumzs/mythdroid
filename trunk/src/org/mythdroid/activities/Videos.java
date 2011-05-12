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
import java.util.WeakHashMap;

import org.mythdroid.Enums.Extras;
import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.data.Video;
import org.mythdroid.data.VideoAdapter;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;
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

/** MDActivity displays browsable list of Videos */
public class Videos extends MDActivity implements
    ListView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    final private WeakHashMap<Integer, Drawable> artCache =
        new WeakHashMap<Integer, Drawable>(32);

    final private Handler handler   = new Handler();
    private Thread artThread        = null;
    private ListView lv             = null;
    private ArrayList<Video> videos = null;
    private int viddir              = -1;
    private String path             = "ROOT"; //$NON-NLS-1$
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
                    Globals.getBackend().addr, viddir, path
                );
            } catch (IOException e) {
                ErrUtil.postErr(ctx, new Exception(Messages.getString("Videos.1"))); //$NON-NLS-1$
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
                        artThread = new Thread(fetchArt);
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
                if (vids[i].poster != null) continue;
                Drawable d = artCache.get(vids[i].id);
                if (d != null)
                    vids[i].poster = d;
                else {
                    vids[i].getPoster(70 * scale + 0.5f, 110 * scale + 0.5f);
                    artCache.put(vids[i].id, vids[i].poster);
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
        addHereToFrontendChooser(VideoPlayer.class);
        dirText = (TextView)findViewById(R.id.videoDir);
        lv = (ListView)findViewById(R.id.videoList);
        lv.setOnItemClickListener(this);
        lv.setOnItemLongClickListener(this);

        scale = getResources().getDisplayMetrics().density;

        showDialog(DIALOG_LOAD);
        Globals.getWorker().post(getVideos);
    }

    @Override
    public void onItemClick(
        AdapterView<?> adapter, View view, int pos, long id
    ) {

        Video video = videos.get(pos);

        // A directory?
        if (video.id == -1) {

            if (path.equals("ROOT")) //$NON-NLS-1$
                path = video.title;
            else
                path += "/" + video.title; //$NON-NLS-1$

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

            // At top level?
            if (path.equals("ROOT") && viddir == -1)  //$NON-NLS-1$
                return super.onKeyDown(code, event);
            // Going to top level?
            int slash = path.lastIndexOf('/');
            if (slash == -1) {
                // Check if we're going to list of videodirs (top top level)
                if (path.equals("ROOT")) //$NON-NLS-1$
                    viddir = -1;
                path = "ROOT"; //$NON-NLS-1$
                dirText.setText(Messages.getString("Videos.0")); // Videos //$NON-NLS-1$
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
