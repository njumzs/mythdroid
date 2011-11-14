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

import java.util.Date;

import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.activities.MDFragmentActivity;
import org.mythdroid.activities.Status;
import org.mythdroid.resource.Messages;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Displays backend status */
public class StatusBackendFragment extends Fragment {

    private MDFragmentActivity activity = null;
    
    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle icicle
    ) {

        if (container == null) return null;
        activity = (MDFragmentActivity)getActivity();
        View view = inflater.inflate(R.layout.status_backend, null, false);

        if (Status.statusDoc == null && !Status.getStatus(activity)) {
            activity.finish();
            return view;
        }

        Document doc = Status.statusDoc;

        Node info = doc.getElementsByTagName("MachineInfo").item(0); //$NON-NLS-1$

        Node StorageNode = null, LoadNode = null, GuideNode = null;
        NodeList nodes = info.getChildNodes();
        int numNodes = nodes.getLength();
        String name = null;
        String stotal = Messages.getString("StatusBackend.1"), //$NON-NLS-1$
                sused = Messages.getString("StatusBackend.2"), //$NON-NLS-1$
                sfree = Messages.getString("StatusBackend.3"); //$NON-NLS-1$
        NamedNodeMap attr = null;

        for (int i = 0; i < numNodes; i++) {
            Node node = nodes.item(i);
            name = node.getNodeName();
            if (name == null) continue;
            if (name.equals("Storage")) //$NON-NLS-1$
                StorageNode = node;
            else if (name.equals("Load")) //$NON-NLS-1$
                LoadNode = node;
            else if (name.equals("Guide")) //$NON-NLS-1$
                GuideNode = node;
        }

        if (StorageNode != null) {
            if (Globals.protoVersion < 50) {
                attr = StorageNode.getAttributes();
                stotal = attr.getNamedItem("drive_total_total").getNodeValue(); //$NON-NLS-1$
                sused = attr.getNamedItem("drive_total_used").getNodeValue(); //$NON-NLS-1$
                sfree = attr.getNamedItem("drive_total_free").getNodeValue(); //$NON-NLS-1$
            }
            else {
                NodeList storageNodes = StorageNode.getChildNodes();
                int numSNodes = storageNodes.getLength();

                for (int i = 0; i < numSNodes; i++) {
                    Node node = storageNodes.item(i);
                    name = node.getNodeName();
                    if (name == null) continue;
                    if (name.equals("Group")) { //$NON-NLS-1$
                        attr = node.getAttributes();
                        if (
                            attr.getNamedItem("dir") //$NON-NLS-1$
                            .getNodeValue()
                            .equals("TotalDiskSpace") //$NON-NLS-1$
                        )
                            break;
                    }
                }

                if (attr != null) {
                    stotal = attr.getNamedItem("total").getNodeValue(); //$NON-NLS-1$
                    sused = attr.getNamedItem("used").getNodeValue(); //$NON-NLS-1$
                    sfree = attr.getNamedItem("free").getNodeValue(); //$NON-NLS-1$
                }
            }
        }

        ((TextView)view.findViewById(R.id.storageTotal)).setText(
            Messages.getString("StatusBackend.16") + stotal + " MB" //$NON-NLS-1$ //$NON-NLS-2$
        );
        ((TextView)view.findViewById(R.id.storageUsed)).setText(
            Messages.getString("StatusBackend.18") + sused + " MB" //$NON-NLS-1$ //$NON-NLS-2$
        );
        ((TextView)view.findViewById(R.id.storageFree)).setText(
            Messages.getString("StatusBackend.20") + sfree + " MB" //$NON-NLS-1$ //$NON-NLS-2$
        );

        if (LoadNode != null) {

            attr = LoadNode.getAttributes();

            ((TextView)view.findViewById(R.id.load1min)).setText(
                "1 min: \t\t" + attr.getNamedItem("avg1").getNodeValue() //$NON-NLS-1$ //$NON-NLS-2$
            );
            ((TextView)view.findViewById(R.id.load5min)).setText(
                "5 min: \t\t" + attr.getNamedItem("avg2").getNodeValue() //$NON-NLS-1$ //$NON-NLS-2$
            );
            ((TextView)view.findViewById(R.id.load15min)).setText(
                "15 min:\t\t" + attr.getNamedItem("avg3").getNodeValue() //$NON-NLS-1$ //$NON-NLS-2$
            );

        }

        if (GuideNode != null) {

            attr = GuideNode.getAttributes();

            Date when = null;

            try {
                when = Globals.dateFmt.parse(
                    attr.getNamedItem("guideThru").getNodeValue() //$NON-NLS-1$
                );
            } catch (Exception e) {}

            Node days, lastRun;

            if ((days = attr.getNamedItem("guideDays")) != null) //$NON-NLS-1$
                ((TextView)view.findViewById(R.id.guideLength)).setText(
                    String.format(
                        Messages.getString("StatusBackend.30"), //$NON-NLS-1$
                        days.getNodeValue(), Globals.dispFmt.format(when)
                    )
                );

            if ((lastRun = attr.getNamedItem("status")) != null) //$NON-NLS-1$
                ((TextView)view.findViewById(R.id.guideLast)).setText(
                    Messages.getString("StatusBackend.32") + // Last run:  //$NON-NLS-1$
                    lastRun.getNodeValue().toLowerCase()
                );

        }
        
        return view;
        
    }

}
