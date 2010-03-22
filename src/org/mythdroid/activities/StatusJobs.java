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
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.mythdroid.R;
import org.mythdroid.data.Program;
import org.mythdroid.resource.Messages;
import org.mythdroid.activities.RecordingDetail;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.R.layout;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * ListActivity displays upcoming/recent jobs
 */
public class StatusJobs extends ListActivity {

    private ArrayList<Job> jobs = new ArrayList<Job>(8);

    private enum JobStatus {
        QUEUED  (1),     PENDING  (2),     STARTING (3),     RUNNING  (4),     
        STOPPING(5),     PAUSED   (6),     RETRYING (7),     ERRORING (8), 
        ABORTING(9),     DONE     (0x100), FINISHED (0x110), ABORTED  (0x120), 
        ERRORED (0x130), CANCELLED(0x140);

        private int value;

        static final private Map<Integer, JobStatus> revMap =
            new HashMap<Integer, JobStatus>(14);

        static {
            for (JobStatus j : EnumSet.allOf(JobStatus.class))
                revMap.put(j.value(), j);
        }

        private JobStatus(int code) {
            value = code;
        }

        public int value() {
            return value;
        }

        public static JobStatus get(int code) {

            if ((code & CANCELLED.value()) == 1) return CANCELLED;
            if ((code & ERRORED.value())   == 1) return ERRORED;
            if ((code & ABORTED.value())   == 1) return ABORTED;
            if ((code & FINISHED.value())  == 1) return FINISHED;

            return revMap.get(code);
        }
    }

    private enum JobType {
        NONE        (0,     Messages.getString("StatusJobs.0")), //$NON-NLS-1$
        TRANSCODE   (1,     Messages.getString("StatusJobs.1")), //$NON-NLS-1$
        COMMFLAG    (2,     Messages.getString("StatusJobs.2")), //$NON-NLS-1$
        SYSTEM      (0xff,  Messages.getString("StatusJobs.3")), //$NON-NLS-1$
        USERJOB1    (0x100, Messages.getString("StatusJobs.4")), //$NON-NLS-1$
        USERJOB2    (0x200, Messages.getString("StatusJobs.5")), //$NON-NLS-1$
        USERJOB3    (0x400, Messages.getString("StatusJobs.6")), //$NON-NLS-1$
        USERJOB4    (0x800, Messages.getString("StatusJobs.7")), //$NON-NLS-1$
        USERJOB     (0xff00,Messages.getString("StatusJobs.8")); //$NON-NLS-1$

        private int     value;
        private String  msg;

        static final private Map<Integer, JobType> revMap =
            new HashMap<Integer, JobType>(10);

        static {
            for (JobType j : EnumSet.allOf(JobType.class))
                revMap.put(j.value(), j);
        }

        private JobType(int code, String str) {
            value = code;
            msg = str;
        }

        public int value() {
            return value;
        }

        public String msg() {
            return msg;
        }

        public static JobType get(int code) {
            if ((code & SYSTEM.value())   == 1) return SYSTEM;
            if ((code & USERJOB1.value()) == 1) return USERJOB1;
            if ((code & USERJOB2.value()) == 1) return USERJOB2;
            if ((code & USERJOB3.value()) == 1) return USERJOB3;
            if ((code & USERJOB4.value()) == 1) return USERJOB4;
            if ((code & USERJOB.value())  == 1) return USERJOB;

            return revMap.get(code);
        }
    }

    private class Job {
        public Date      startTime;
        public JobStatus status;
        public JobType   type;
        public String    hostname;
        public String    comments;
        public Program   program;

        public Job(Node item) {
            try {

                NamedNodeMap attr = item.getAttributes();
                startTime = MythDroid.dateFmt.parse(
                    attr.getNamedItem("startTime").getNodeValue() //$NON-NLS-1$
                );
                status = JobStatus.get(
                    Integer.valueOf(attr.getNamedItem("status").getNodeValue()) //$NON-NLS-1$
                );
                type = JobType.get(
                    Integer.valueOf(attr.getNamedItem("type").getNodeValue()) //$NON-NLS-1$
                );
                hostname = attr.getNamedItem("hostname").getNodeValue(); //$NON-NLS-1$

                NodeList nodes = item.getChildNodes();
                int numNodes = nodes.getLength();
                String name = null;

                for (int i = 0; i < numNodes; i++) {
                    Node node = nodes.item(i);
                    name = node.getNodeName();
                    if (name != null) {
                        if (name.equals("Program")) //$NON-NLS-1$
                            program = new Program(node);
                        else if (node.getNodeType() == Node.TEXT_NODE) {
                            name = node.getNodeValue();
                            if (!name.startsWith("\n")) //$NON-NLS-1$
                                comments = name;
                        }
                    }
                }

            } catch (Exception e) {}
        }
    }

    /** Extended ArrayAdapter, adapts jobs for a ListView */
    private class JobAdapter extends ArrayAdapter<Object> {

        /*
         * A store for views so we don't have to call findViewById() too often
         */
        private class ViewHolder {
            public TextView    title, type, details, comments;
            public ProgressBar pBar;
        }

        JobAdapter(Context ctx, int resource, ArrayList<Job> list) {
            super(ctx, resource, list.toArray());
        }

        @Override
        public View getView(int pos, View old, ViewGroup parent) {

            ViewHolder vHolder = null;

            if (old == null) {
                old = getLayoutInflater().inflate(R.layout.job_list_item, null);
                vHolder = new ViewHolder();
                vHolder.title = (TextView)old.findViewById(R.id.joblist_title);
                vHolder.type = (TextView)old.findViewById(R.id.joblist_type);
                vHolder.details = 
                    (TextView)old.findViewById(R.id.joblist_details);
                vHolder.comments = 
                    (TextView)old.findViewById(R.id.joblist_comments);
                vHolder.pBar = 
                    (ProgressBar)old.findViewById(R.id.joblist_prog);
                old.setTag(vHolder);
            }
            else
                vHolder = (ViewHolder) old.getTag();

            Job j = jobs.get(pos);

            vHolder.title.setText(j.program.Title);
            vHolder.type.setText(j.type.msg());
            vHolder.details.setText(
                Messages.getString("StatusJobs.9") + // Started //$NON-NLS-1$
                MythDroid.dispFmt.format(j.startTime) + 
                Messages.getString("StatusJobs.25") + j.hostname // on //$NON-NLS-1$
            );
            vHolder.comments.setText(j.comments);
            
            if (j.status == JobStatus.RUNNING)
                vHolder.pBar.setVisibility(View.VISIBLE);
            else
                vHolder.pBar.setVisibility(View.GONE);

            return old;

        }

        @Override
        public Job getItem(int pos) {
            return jobs.get(pos);
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Document doc = Status.statusDoc;
        NodeList jobNodes = doc.getElementsByTagName("Job"); //$NON-NLS-1$

        for (int i = 0; i < jobNodes.getLength(); i++)
            jobs.add(new Job(jobNodes.item(i)));

        setListAdapter(new JobAdapter(this, layout.simple_list_item_1, jobs));

    }

    @Override
    public void onListItemClick(ListView list, View item, int pos, long id) {
        MythDroid.curProg = ((Job)list.getItemAtPosition(pos)).program;
        startActivity(new Intent().setClass(this, RecordingDetail.class));
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
    }

}
