/*
    MythDroid: Android MythTV Remote
    
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

package org.mythdroid;

import java.util.Date;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.TextView;

public class StatusBackend extends Activity {

    @Override
    public void onCreate(Bundle icicle) {
        
        super.onCreate(icicle);
        setContentView(R.layout.status_backend);

        Document doc = Status.statusDoc;
        Node info = doc.getElementsByTagName("MachineInfo").item(0);

        Node StorageNode = null, LoadNode = null, GuideNode = null;
        NodeList nodes = info.getChildNodes();
        int numNodes = nodes.getLength();
        String name = null;

        for (int i = 0; i < numNodes; i++) {
            Node node = nodes.item(i);
            name = node.getNodeName();
            if (name == null) continue;
            if (name.equals("Storage"))
                StorageNode = node;
            else if (name.equals("Load"))
                LoadNode = node;
            else if (name.equals("Guide"))
                GuideNode = node;
        }

        NamedNodeMap attr = StorageNode.getAttributes();

        ((TextView)findViewById(R.id.storage_total)).setText(
           "Total: \t\t" + 
           attr.getNamedItem("drive_total_total").getNodeValue() + " MB"
        );
        ((TextView)findViewById(R.id.storage_used)).setText(
            "Used: \t\t" +
            attr.getNamedItem("drive_total_used").getNodeValue() + " MB"
        );
        ((TextView)findViewById(R.id.storage_free)).setText(
            "Free: \t\t" +
            attr.getNamedItem("drive_total_free").getNodeValue() + " MB"
        );

        attr = LoadNode.getAttributes();

        ((TextView)findViewById(R.id.load_1min)).setText(
            "1 min: \t\t" + attr.getNamedItem("avg1").getNodeValue()
        );
        ((TextView)findViewById(R.id.load_5min)).setText(
            "5 min: \t\t" + attr.getNamedItem("avg2").getNodeValue()
        );
        ((TextView)findViewById(R.id.load_15min)).setText(
            "15 min:\t\t" + attr.getNamedItem("avg3").getNodeValue()
        );

        attr = GuideNode.getAttributes();

        Date when = null;

        try {
            when = MythDroid.dateFmt.parse(
                attr.getNamedItem("guideThru").getNodeValue()
            );
        } catch (Exception e) {}

        ((TextView)findViewById(R.id.guide_length)).setText(
            attr.getNamedItem("guideDays").getNodeValue() + 
            " days (until " + MythDroid.dispFmt.format(when) + ")"
        );
        ((TextView)findViewById(R.id.guide_last)).setText(
            "Last run: " + 
            attr.getNamedItem("status").getNodeValue().toLowerCase()
        );
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
    }

}
