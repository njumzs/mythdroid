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

import org.mythdroid.Enums.ArtworkType;
import org.mythdroid.Enums.Extras;
import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.data.Video;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.Menu;
import android.view.MenuItem;

/**
 * MDActivity displays details of a Video.
 * Allows user to play the Video or view further details at TVDB
 */
public class VideoDetail extends MDActivity {

    private Video video     = null;
    private Context ctx     = this;
    private Handler handler = new Handler();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addHereToFrontendChooser(VideoPlayer.class);
        setContentView(R.layout.video_detail);
        setViews();
    }

    private void setViews() {

        video = Globals.curVid;
        
        if (video == null) {
            ErrUtil.err(this, Messages.getString("VideoDetail.2")); //$NON-NLS-1$
            finish();
            return;
        }
            
        ((TextView)findViewById(R.id.title)).setText(video.title);
        ((TextView)findViewById(R.id.director))
            .setText(Messages.getString("VideoDetail.0") + video.director); //$NON-NLS-1$
        ((TextView)findViewById(R.id.rating))
            .setText(
                Messages.getString("VideoDetail.1") + //$NON-NLS-1$
                String.format("%.2f", video.rating) //$NON-NLS-1$
            );
        ((TextView)findViewById(R.id.year))
            .setText(
                Messages.getString("VideoDetail.3") + //$NON-NLS-1$
                String.valueOf(video.year)
            );
        ((TextView)findViewById(R.id.length))
            .setText(
                Messages.getString("VideoDetail.4") + //$NON-NLS-1$
                String.valueOf(video.length) + " mins" //$NON-NLS-1$
            );
        ((TextView)findViewById(R.id.plot))
            .setText(video.plot);
        if (video.subtitle != null && video.subtitle.length() > 0)
            ((TextView)findViewById(R.id.subtitle)).setText(video.subtitle);
        else
            ((TextView)findViewById(R.id.subtitle)).setVisibility(View.GONE);

        if (video.poster == null)
            ((ImageView)findViewById(R.id.image))
                .setImageResource(R.drawable.video);
        else
            ((ImageView)findViewById(R.id.image))
                .setImageDrawable(video.poster);
        
        Button tvdb = ((Button)findViewById(R.id.TVDB));
        
        if (video.homepage == null || video.homepage.length() == 0)
            tvdb.setVisibility(View.GONE);
        else
            tvdb.setOnClickListener(
                new OnClickListener() {
                    @Override
                        public void onClick(View v) {
                        startActivity(
                            new Intent(Intent.ACTION_VIEW).setData(
                                Uri.parse(video.homepage)
                            )
                        );
                    }
                }
            );

        Button play = ((Button)findViewById(R.id.play));
        play.setOnClickListener(
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    
                    String filename = null;
                    try {
                        filename = video.getPath();
                    } catch (IOException e) {
                        ErrUtil.err(ctx, e);
                        return;
                    }
                    
                    Class<?> nextClass = 
                        Globals.isCurrentFrontendHere() 
                            ? VideoPlayer.class : TVRemote.class;
                    
                    startActivity(
                        new Intent()
                            .setClass(ctx, nextClass)
                            .putExtra(Extras.FILENAME.toString(), filename) 
                            .putExtra(Extras.TITLE.toString(), video.title)
                            .putExtra(Extras.VIDEOID.toString(), video.id)
                    );
                    
                }
            }
        );
        play.setOnLongClickListener(
            new OnLongClickListener() {
                @Override
                public boolean onLongClick(View arg0) {
                    try {
                        setExtra(Extras.FILENAME.toString(), video.getPath());
                    } catch (IOException e) {
                        ErrUtil.err(ctx, e);
                        return true;
                    }
                    setExtra(Extras.TITLE.toString(), video.title);
                    setExtra(Extras.VIDEOID.toString(), video.id);
                    nextActivity = TVRemote.class;
                    showDialog(FRONTEND_CHOOSER);
                    return true;
                }
            }
        );
        
        setBackground(video, findViewById(R.id.layout));

    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        setContentView(R.layout.video_detail);
        setViews();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        addFrontendChooser(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_FRONTEND:
                nextActivity=null;
                showDialog(FRONTEND_CHOOSER);
                return true;
        }

        return super.onOptionsItemSelected(item);

    }
    
    private void setBackground(final Video video, final View v) {
        
        if (!Globals.haveServices()) return;
        
        final DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        final int height = (int)(dm.heightPixels/ 1.5);
        final int width  = (int)(dm.widthPixels / 1.5);

        Globals.runOnThreadPool(
            new Runnable() {
                @Override
                public void run() {
                    Bitmap bm = null;
                    if (width > height)
                        bm = video.getArtwork(ArtworkType.fanart, width, 0);
                    else
                        bm = video.getArtwork(ArtworkType.fanart, 0, height);
                    if (bm == null) return;
                    final BitmapDrawable d = new BitmapDrawable(
                        getResources(), bm
                    );
                    if (height > width)
                        d.setGravity(Gravity.FILL_VERTICAL);
                    d.setAlpha(65);
                    handler.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                v.setBackgroundDrawable(d);
                            }
                        }
                    );
                    
                }
            }
        );
}


}
