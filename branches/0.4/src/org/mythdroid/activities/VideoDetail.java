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

import org.mythdroid.Enums.Extras;
import org.mythdroid.R;
import org.mythdroid.data.Video;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * MDActivity displays details of a Video
 * Allows user to play the Video or view further details at TVDB
 */
public class VideoDetail extends MDActivity {

    private Video video = null;
    private Context ctx = this;
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addHereToFrontendChooser(VideoPlayer.class);
        setContentView(R.layout.video_detail);
        setViews();
    }
    
    private void setViews() {
        
        video = MythDroid.curVid;
        ((TextView)findViewById(R.id.videoDTitle))
            .setText(video.title);
        ((TextView)findViewById(R.id.videoDDirector))
            .setText(Messages.getString("VideoDetail.0") + video.director); //$NON-NLS-1$
        ((TextView)findViewById(R.id.videoDRating))
            .setText(
                Messages.getString("VideoDetail.1") + //$NON-NLS-1$ 
                String.format("%.2f", video.rating) //$NON-NLS-1$
            ); 
        ((TextView)findViewById(R.id.videoDYear))
            .setText(
                Messages.getString("VideoDetail.3") + //$NON-NLS-1$ 
                String.valueOf(video.year)
            ); 
        ((TextView)findViewById(R.id.videoDLength))
            .setText(
                Messages.getString("VideoDetail.4") + //$NON-NLS-1$
                String.valueOf(video.length) + " mins" //$NON-NLS-1$
            ); 
        ((TextView)findViewById(R.id.videoDPlot))
            .setText(video.plot);
        if (video.subtitle.length() > 0)
            ((TextView)findViewById(R.id.videoDSubtitle))
                .setText(video.subtitle);
        else
            ((TextView)findViewById(R.id.videoDSubtitle))
                .setVisibility(View.GONE);
        
        if (video.poster == null)
            ((ImageView)findViewById(R.id.videoDPoster))
                .setImageResource(R.drawable.video);
        else
            ((ImageView)findViewById(R.id.videoDPoster))
                .setImageDrawable(video.poster);
        
        Button play = ((Button)findViewById(R.id.videoDPlay)); 
        
        play.setOnClickListener(
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(
                        new Intent()
                            .setClass(ctx, TVRemote.class)
                            .putExtra(Extras.FILENAME.toString(), video.filename)
                            .putExtra(Extras.TITLE.toString(), video.title)
                    );
                }
            }
        );
        play.setOnLongClickListener(
            new OnLongClickListener() {
                @Override
                public boolean onLongClick(View arg0) {
                    setExtra(Extras.FILENAME.toString(), video.filename);
                    setExtra(Extras.TITLE.toString(), video.title);
                    nextActivity = TVRemote.class;
                    showDialog(FRONTEND_CHOOSER);
                    return true;
                }
            }
        );
        
        ((Button)findViewById(R.id.videoDTVDB))
            .setOnClickListener(
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
 
    }
    
    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        setContentView(R.layout.video_detail);
        setViews();
    }
    
}
