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

package org.mythdroid.fragments;

import java.util.ArrayList;

import org.mythdroid.Enums.Extras;
import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.data.Program;
import org.mythdroid.activities.RecordingDetail;
import org.mythdroid.activities.Status;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.R.layout;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Displays the status of recorders */
public class StatusRecordersFragment extends ListFragment {

    final private static int REFRESH_NEEDED = Activity.RESULT_FIRST_USER;
    final private Handler handler           = new Handler();
    
    private Status activity                 = null;
    private Document doc                    = null;
    private ArrayList<Encoder> encoders     = new ArrayList<Encoder>();

    final private Runnable refreshEncoders = new Runnable() {
        @Override
        public void run() {

            doc = Status.statusDoc;
            
            if (doc == null) {
                activity.finish();
                return;
            }

            encoders.clear();

            NodeList encoderItems =
                doc.getElementsByTagName("Encoder"); //$NON-NLS-1$

            for (int i = 0; i < encoderItems.getLength(); i++)
                encoders.add(new Encoder(encoderItems.item(i)));

            activity.dismissLoadingDialog();
          
            setListAdapter(
                new EncoderAdapter(
                    activity, layout.simple_list_item_1, encoders
                )
            );

        }
    };

    final private Runnable getStatusTask = new Runnable() {
        @Override
        public void run() {
            
            if (!Status.getStatus(activity) && Status.statusDoc == null) {
                activity.dismissLoadingDialog();
                ErrUtil.postErr(
                    activity, Messages.getString("StatusRecorders.3") //$NON-NLS-1$
                );
                activity.finish();
                return;
            }

            handler.post(refreshEncoders);
        }
    };

    static final private class Encoder {

        // From enum TVState defined in libmythtv/tv.h
        private final static String[] states = {
            Messages.getString("StatusRecorders.1"),  // Idle //$NON-NLS-1$
            Messages.getString("StatusRecorders.2"),  // Live TV //$NON-NLS-1$
            "?", // Watching pre-recorded //$NON-NLS-1$
            "?", // Watching video //$NON-NLS-1$
            "?", // Watching DVD //$NON-NLS-1$
            "?", // Watching BD //$NON-NLS-1$
            Messages.getString("StatusRecorders.5"),  // Watching recording //$NON-NLS-1$
            Messages.getString("StatusRecorders.5"),  // Recording //$NON-NLS-1$
            Messages.getString("StatusRecorders.4")   // Changing state //$NON-NLS-1$
        };

        public int     id;
        public String  hostname, state;
        public Program program;

        public Encoder(Node item) {

            NamedNodeMap attr = item.getAttributes();
            id = Integer.parseInt(attr.getNamedItem("id").getNodeValue()); //$NON-NLS-1$
            hostname = attr.getNamedItem("hostname").getNodeValue(); //$NON-NLS-1$
            
            int s = Integer.parseInt(attr.getNamedItem("state").getNodeValue());  //$NON-NLS-1$
            
            if (s < 0) 
                state = Messages.getString("StatusRecorders.6"); //$NON-NLS-1$
            else
                state = states[s];

            if (!item.hasChildNodes()) return;

            Node ProgNode = null;
            NodeList nodes = item.getChildNodes();
            int numNodes = nodes.getLength();
            String name = null;

            for (int i = 0; i < numNodes; i++) {
                Node node = nodes.item(i);
                name = node.getNodeName();
                if (name != null && name.equals("Program")) { //$NON-NLS-1$
                    ProgNode = node;
                    break;
                }
            }

            if (ProgNode != null)
                program = new Program(ProgNode);

        }

    }

    /** Extended ArrayAdapter, adapts encoders for a ListView */
    final private class EncoderAdapter extends ArrayAdapter<Object> {

        private class ViewHolder {
            public TextView    encoder, state, program, endTime;
            public ProgressBar rec;
        }

        EncoderAdapter(Context ctx, int resource, ArrayList<Encoder> list) {
            super(ctx, resource, list.toArray());
        }

        @Override
        public View getView(int pos, View old, ViewGroup parent) {

            ViewHolder vHolder = null;

            if (old == null) {
                old = activity.getLayoutInflater().inflate(
                    R.layout.encoder_list_item, null
                );
                vHolder = new ViewHolder();
                vHolder.encoder = (TextView)old.findViewById(R.id.encoder);
                vHolder.state   = (TextView)old.findViewById(R.id.state);
                vHolder.program = (TextView)old.findViewById(R.id.program);
                vHolder.endTime = (TextView)old.findViewById(R.id.endTime);
                vHolder.rec = (ProgressBar)old.findViewById(R.id.progress);
                old.setTag(vHolder);
            }
            else
                vHolder = (ViewHolder)old.getTag();

            Encoder enc = encoders.get(pos);

            vHolder.encoder.setText(
                String.format(
                    Messages.getString("StatusRecorders.12"), //$NON-NLS-1$
                    enc.id, enc.hostname
                )
            );
            vHolder.state.setText(enc.state);
            if (enc.program != null) {
                vHolder.program.setText(
                    String.format(
                        Messages.getString("StatusRecorders.0"), //$NON-NLS-1$
                        enc.program.Title, enc.program.Channel
                    )
                );
                vHolder.endTime.setText(
                        Messages.getString("StatusRecorders.18") +  //$NON-NLS-1$
                        enc.program.endString()
                );
                vHolder.rec.setVisibility(View.VISIBLE);
            }
            else
                vHolder.rec.setVisibility(View.GONE);

            return old;

        }

        @Override
        public Encoder getItem(int pos) {
            return encoders.get(pos);
        }
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle icicle
    ) {
        if (container == null) return null;
        activity  = (Status)getActivity();
        View view = inflater.inflate(R.layout.status_recorders, null, false);
        ((TextView)view.findViewById(R.id.emptyMsg))
            .setText(R.string.noEncoders);
        if (activity.embed)
            handler.post(refreshEncoders);
        else
            refresh();
        return view;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Globals.removeThreadPoolTask(refreshEncoders);
    }

    @Override
    public void onListItemClick(ListView list, View item, int pos, long id) {

        Encoder enc = (Encoder)list.getItemAtPosition(pos);

        if (enc.program == null) return;

        Globals.curProg = enc.program;
        startActivityForResult(
            new Intent()
            .putExtra(
                Extras.LIVETV.toString(),
                enc.state.equals(Extras.LIVETV.toString()) ? true : false
            )
            .setClass(activity, RecordingDetail.class),
            0
        );

    }

    @Override
    public void onActivityResult(int reqCode, int resCode, Intent data) {
        if (resCode == REFRESH_NEEDED)
            refresh();
    }

    private void refresh() {
        Globals.removeThreadPoolTask(getStatusTask);
        activity.showLoadingDialog();
        Globals.runOnThreadPool(getStatusTask);
    }

}
