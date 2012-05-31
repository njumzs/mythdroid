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
import org.mythdroid.Enums.RecStatus;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Extended ArrayAdapter, adapts Programs for a ListView */
public class ProgramAdapter extends ArrayAdapter<Object> {

    private class ViewHolder {
        public TextView    title, subtitle, channel, date;
        public ProgressBar rec;
    }

    private Context            ctx       = null;
    private ArrayList<Program> programs  = null;
    private int                resource  = 0;

    /**
     * Constructor
     * @param ctx context
     * @param resource layout resource for items
     * @param list ArrayList of Programs
     */
    public ProgramAdapter(Context ctx, int resource, ArrayList<Program> list) {
        super(ctx, resource, list.toArray());
        this.ctx = ctx;
        this.programs = list;
        this.resource = resource;
    }

    @Override
    public View getView(int pos, View old, ViewGroup parent) {

        ViewHolder vHolder = null;

        if (old == null) {
            old = ((Activity)ctx).getLayoutInflater().inflate(resource, null);
            vHolder = new ViewHolder();
            vHolder.title    = (TextView)old.findViewById(R.id.title);
            vHolder.subtitle = (TextView)old.findViewById(R.id.subtitle);
            vHolder.channel  = (TextView)old.findViewById(R.id.channel);
            vHolder.date     = (TextView)old.findViewById(R.id.date);
            vHolder.rec      = (ProgressBar)old.findViewById(R.id.progress);
            old.setTag(vHolder);
        }
        else
            vHolder = (ViewHolder)old.getTag();

        Program prog = programs.get(pos);

        vHolder.title.setText(prog.Title);
        vHolder.subtitle.setText(prog.SubTitle);
        vHolder.channel.setText(prog.Channel);
        vHolder.date.setText(prog.startString());
        vHolder.rec.setVisibility(
            prog.Status == RecStatus.RECORDING ? View.VISIBLE : View.GONE
        );

        return old;

    }

    @Override
    public Program getItem(int pos) {
        return programs.get(pos);
    }
    
    @Override
    public int getCount() {
       return programs.size(); 
    }

}
