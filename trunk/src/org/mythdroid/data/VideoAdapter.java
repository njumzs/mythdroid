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

import java.util.ArrayList;

import org.mythdroid.R;
import org.mythdroid.resource.Messages;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/** Extended ArrayAdapter, adapts Videos for a ListView */
public class VideoAdapter extends ArrayAdapter<Object> {

    private class ViewHolder {
        public TextView    title;
        public TextView    subtitle;
        public TextView    director;
        public TextView    rating;
        public ImageView   poster;
    }

    private Context          ctx      = null;
    private ArrayList<Video> videos   = null;

    private Drawable folderDrawable   = null;
    private Drawable videoDrawable    = null;

    /**
     * Constructor
     * @param ctx context
     * @param resource layout resource for items
     * @param list ArrayList of Videos
     */
    public VideoAdapter(Context ctx, int resource, ArrayList<Video> list) {
        super(ctx, resource, list.toArray());
        this.ctx = ctx;
        this.videos = list;
        folderDrawable = ctx.getResources().getDrawable(R.drawable.folder);
        videoDrawable = ctx.getResources().getDrawable(R.drawable.video);
    }

    @Override
    public View getView(int pos, View old, ViewGroup parent) {

        ViewHolder vHolder = null;

        if (old == null) {
            old = ((Activity)ctx).getLayoutInflater().inflate(
                R.layout.video, null
            );
            vHolder = new ViewHolder();
            vHolder.title = (TextView)old.findViewById(R.id.videoTitle);
            vHolder.subtitle = (TextView)old.findViewById(R.id.videoSubtitle);
            vHolder.director = (TextView)old.findViewById(R.id.videoDirector);
            vHolder.rating = (TextView)old.findViewById(R.id.videoRating);
            vHolder.poster = (ImageView)old.findViewById(R.id.videoPoster);
            old.setTag(vHolder);
        }
        else
            vHolder = (ViewHolder)old.getTag();

        Video video = videos.get(pos);
        vHolder.title.setText(video.title);
        vHolder.subtitle.setText(video.subtitle);
        vHolder.director.setText(
            Messages.getString("VideoAdapter.0") + video.director // Directed by //$NON-NLS-1$
        );
        vHolder.rating.setText(
            Messages.getString("VideoAdapter.1") + // Rating: //$NON-NLS-1$
            String.format("%.2f", video.rating) //$NON-NLS-1$
        );

        if (video.id == -1) {
            vHolder.poster.setImageDrawable(folderDrawable);
            vHolder.subtitle.setVisibility(View.GONE);
            vHolder.director.setVisibility(View.GONE);
            vHolder.rating.setVisibility(View.GONE);
            RelativeLayout.LayoutParams params =
                (RelativeLayout.LayoutParams)vHolder.title.getLayoutParams();
            params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
            vHolder.title.setLayoutParams(params);
        }

        else {
            vHolder.subtitle.setVisibility(View.VISIBLE);
            vHolder.director.setVisibility(View.VISIBLE);
            vHolder.rating.setVisibility(View.VISIBLE);
            RelativeLayout.LayoutParams params =
                (RelativeLayout.LayoutParams)vHolder.title.getLayoutParams();
            params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
            vHolder.title.setLayoutParams(params);
            if (video.poster == null)
                vHolder.poster.setImageDrawable(videoDrawable);
            else
                vHolder.poster.setImageDrawable(video.poster);

        }

        return old;

    }

    @Override
    public Video getItem(int pos) {
        return videos.get(pos);
    }

}
