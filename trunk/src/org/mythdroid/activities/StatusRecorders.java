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

import org.mythdroid.Enums.Extras;
import org.mythdroid.R;
import org.mythdroid.data.Program;
import org.mythdroid.activities.RecordingDetail;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.R.layout;
import android.app.Activity;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 *  ListActivity displays status of recorders
 */
public class StatusRecorders extends ListActivity {

    final private static int REFRESH_NEEDED = Activity.RESULT_FIRST_USER;
    final private static int DIALOG_LOAD    = 0;

    final private Context ctx = this;

    private Document doc = null;
    private ArrayList<Encoder> encoders = new ArrayList<Encoder>();

    final private Handler handler = new Handler();
    
    final private Runnable refreshEncoders = new Runnable() {
        @Override
        public void run() {

            doc = Status.statusDoc;

            encoders.clear();

            NodeList encoderItems = 
                doc.getElementsByTagName("Encoder"); //$NON-NLS-1$

            for (int i = 0; i < encoderItems.getLength(); i++)
                encoders.add(new Encoder(encoderItems.item(i)));

            dismissDialog(DIALOG_LOAD);

            setListAdapter(
                new EncoderAdapter(
                    ctx, layout.simple_list_item_1, encoders
                )
            );

        }
    };

    final private Runnable getStatusTask = new Runnable() {
        @Override
        public void run() {
            try {
                Status.getStatus();
            } catch (SAXException e) {
                ErrUtil.err(ctx, Messages.getString("Status.10")); //$NON-NLS-1$
            } catch (Exception e) { ErrUtil.err(ctx, e); }
            handler.post(refreshEncoders);
        }
    };

    private class Encoder {

        private String[] states = { 
        		Messages.getString("StatusRecorders.1"),  // Idle //$NON-NLS-1$
        		Messages.getString("StatusRecorders.2"),  // Live TV //$NON-NLS-1$
        		"?", "?",  //$NON-NLS-1$ //$NON-NLS-2$
        		Messages.getString("StatusRecorders.5")   // Recording //$NON-NLS-1$
        };

        public int     id;
        public boolean local;
        public String  hostname, state;
        public Program program;

        public Encoder(Node item) {

            NamedNodeMap attr = item.getAttributes();
            id = Integer.parseInt(attr.getNamedItem("id").getNodeValue()); //$NON-NLS-1$
            local = 
                attr.getNamedItem("local").getNodeValue().equals("1") //$NON-NLS-1$ //$NON-NLS-2$
                    ? true : false;
            hostname = attr.getNamedItem("hostname").getNodeValue(); //$NON-NLS-1$
            state = states[
                Integer.parseInt(attr.getNamedItem("state").getNodeValue()) //$NON-NLS-1$
            ];

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
    private class EncoderAdapter extends ArrayAdapter<Object> {

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
                old = getLayoutInflater().inflate(
                    R.layout.encoder_list_item, null
                );
                vHolder = new ViewHolder();
                vHolder.encoder = 
                    (TextView)old.findViewById(R.id.enclist_encoder);
                vHolder.state = (TextView)old.findViewById(R.id.enclist_state);
                vHolder.program = 
                    (TextView)old.findViewById(R.id.enclist_program);
                vHolder.endTime = 
                    (TextView)old.findViewById(R.id.enclist_endTime);
                vHolder.rec = (ProgressBar)old.findViewById(R.id.enclist_rec);
                old.setTag(vHolder);
            }
            else
                vHolder = (ViewHolder)old.getTag();

            Encoder enc = encoders.get(pos);

            vHolder.encoder.setText(
                Messages.getString("StatusRecorders.12") + enc.id + "    (" + //$NON-NLS-1$ //$NON-NLS-2$
                (enc.local ? Messages.getString("StatusRecorders.14") +  //$NON-NLS-1$
                enc.hostname : Messages.getString("StatusRecorders.15")) + ")" //$NON-NLS-1$ //$NON-NLS-2$
            );
            vHolder.state.setText(enc.state);
            if (enc.program != null) {
                vHolder.program.setText(
                    enc.program.Title + 
                    Messages.getString("StatusRecorders.0") + // on //$NON-NLS-1$ 
                    enc.program.Channel 
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
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        refresh();
    }

    @Override
    public Dialog onCreateDialog(int id) {
        ProgressDialog d = new ProgressDialog(this);
        d.setIndeterminate(true);
        d.setMessage(getResources().getString(R.string.loading));
        return d;
    }

    @Override
    public void onListItemClick(ListView list, View item, int pos, long id) {
        
        Encoder enc = (Encoder)list.getItemAtPosition(pos);

        if (enc.program == null) return;

        MythDroid.curProg = enc.program;
        startActivityForResult(
            new Intent()
            .putExtra(
                Extras.LIVETV.toString(), 
                enc.state.equals(Extras.LIVETV.toString()) ? true : false
            )
            .setClass(this, RecordingDetail.class), 
            0
        );

    }

    @Override
    public void onActivityResult(int reqCode, int resCode, Intent data) {
        if (resCode == REFRESH_NEEDED) 
            refresh();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
    }

    private void refresh() {
        showDialog(DIALOG_LOAD);
        MythDroid.wHandler.post(getStatusTask);
    }

}
