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

package org.mythdroid.backend;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilderFactory;


import org.mythdroid.ConnMgr;
import org.mythdroid.data.Program;
import org.mythdroid.resource.Messages;
import org.mythdroid.activities.MythDroid;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import android.util.Log;

/**
 * A BackendManager locates and manages a master backend, providing
 * methods to get, delete and stop recordings 
 */
public class BackendManager {

    public String addr = null;
    
    static final private String BACKEND_UPNP_ID =
        "ST: urn:schemas-mythtv-org:device:MasterMediaServer:1\r\n"; //$NON-NLS-1$

    static final private String UPNP_SEARCH     =
        "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" + //$NON-NLS-1$
        "MAN: \"ssdp:discover\"\r\n" + //$NON-NLS-1$
        "MX: 2\r\n" + //$NON-NLS-1$
        BACKEND_UPNP_ID + "\r\n"; //$NON-NLS-1$

    static final private String UPNP_LOCATION   = "LOCATION: http://"; //$NON-NLS-1$
    static final private String myAddr          = "android"; //$NON-NLS-1$
    
    private String  statusURL = null;
    private ConnMgr cmgr      = null;
  
    /**
     * Constructor
     * @param host - the backend address
     */
    public BackendManager(final String host) throws Exception {

        statusURL = "http://" + host + ":6544"; //$NON-NLS-1$ //$NON-NLS-2$
       

        MythDroid.protoVersion = getVersion(statusURL);

        if (MythDroid.debug)
            Log.d(
                "BackendManager",  //$NON-NLS-1$
                "Connecting to " + host +  //$NON-NLS-1$
                ":6543 (ProtoVer " + MythDroid.protoVersion +")" //$NON-NLS-1$ //$NON-NLS-2$
             );
        
        cmgr = new ConnMgr(host, 6543);
               
        if (!announce()) 
            throw (new IOException(Messages.getString("BackendManager.0"))); //$NON-NLS-1$

        addr = host;
        
    }

    /**
     * Find a nearby master backend
     * @return An initialised BackendManager or null if we couldn't find one
     */
    static public BackendManager locate() throws Exception {

        final InetSocketAddress isa = new InetSocketAddress(1900);
        final DatagramSocket sock = new DatagramSocket(null);
        InetAddress addr = null;

        try {
            addr = InetAddress.getByName("239.255.255.250"); //$NON-NLS-1$
        } catch (UnknownHostException e) {}

        final DatagramPacket pkt = new DatagramPacket(
            UPNP_SEARCH.getBytes(), UPNP_SEARCH.length(), addr, 1900
        );

        final DatagramPacket rpkt = new DatagramPacket(new byte[1024], 1024);

        if (MythDroid.debug)
            Log.d(
                "BackendManager",  //$NON-NLS-1$
                "Sending UPNP M-SEARCH to 239.255.255.250:1900" //$NON-NLS-1$
            );
        
        sock.setReuseAddress(true);
        sock.bind(isa);
        sock.setBroadcast(true);
        sock.setSoTimeout(800);
        sock.send(pkt);

        try {
            sock.receive(rpkt);
        } catch (SocketTimeoutException e) {
            if (MythDroid.debug)
                Log.d("BackendManager", "Timeout waiting for UPNP response"); //$NON-NLS-1$ //$NON-NLS-2$
            return null; 
        }
        
        sock.close();

        final String msg = new String(rpkt.getData(), 0, rpkt.getLength());
        if (MythDroid.debug)
            Log.d("BackendManager", "UPNP Response received: " + msg); //$NON-NLS-1$ //$NON-NLS-2$
        
        if (!msg.contains(BACKEND_UPNP_ID)) return null;

        int locIdx = msg.indexOf(UPNP_LOCATION);
        int portIdx = msg.indexOf(":", locIdx + UPNP_LOCATION.length()); //$NON-NLS-1$

        return new BackendManager(
            msg.substring(locIdx + UPNP_LOCATION.length(), portIdx)
        );

    }
    
    /**
     * Get the URL of the status / XML service
     * @return A String containing the URL
     */
    public String getStatusURL() {
        if (MythDroid.debug)
            Log.d("BackendManager", "statusURL is " + statusURL); //$NON-NLS-1$ //$NON-NLS-2$
        return statusURL;
    }

    /**
     * Get a Program from a recording filename
     * @param basename - the full path to the recording
     * @return A Program representing the recording
     */
    public Program getRecording(final String basename) throws IOException {

        cmgr.sendString("QUERY_RECORDING BASENAME " + basename); //$NON-NLS-1$
        final String[] resp = cmgr.readStringList();
        return new Program(resp, 1);

    }

    
    /**
     * Get a list of recordings
     * @return An ArrayList of Programs
     */
    public ArrayList<Program> getRecordings() throws IOException {

        cmgr.sendString("QUERY_RECORDINGS Play"); //$NON-NLS-1$
        final String[] resp = cmgr.readStringList();

        int respSize = resp.length;
        int numFields = Program.numFields();
        int typeField = Program.typeField();

        ArrayList<Program> programs =
            new ArrayList<Program>(respSize / numFields);

        for (int i = respSize - numFields; i >= 0; i -= numFields) {
            if (!resp[i + typeField].equals("Default")) continue; //$NON-NLS-1$
            programs.add(new Program(resp, i));
        }

        return programs;

    }

    /**
     * Stop an in-progress recording
     * @param prog - The Program to stop recording 
     */    
    public void stopRecording(final Program prog) throws IOException {
        final String[] list = prog.stringList();
        list[0] = "STOP_RECORDING"; //$NON-NLS-1$
        cmgr.sendStringList(list);
        cmgr.readStringList();
    }

    
    /**
     * Delete a recording
     * @param prog - The Program to delete 
     */
    public void deleteRecording(final Program prog) throws IOException {
        final String[] list = prog.stringList();
        list[0]= "DELETE_RECORDING"; //$NON-NLS-1$
        cmgr.sendStringList(list);
        cmgr.readStringList();
    }

    /** Disconnect from the backend */
    public void done() throws IOException {
        cmgr.sendString("DONE"); //$NON-NLS-1$
        cmgr.disconnect();
    }
    
    /**
     * Get the protocol version from the backend
     * @param sURL - string containing backend status URL
     * @return - integer containing backend protocol version
     */
    private int getVersion(String sURL) throws Exception {
        
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        URL url = new URL(sURL + "/xml"); //$NON-NLS-1$
        Document doc;
        
        try {
            doc = dbf.newDocumentBuilder().parse(
                url.openConnection().getInputStream()
            );
        } catch (SAXException e) {
            throw new Exception(Messages.getString("Status.10")); //$NON-NLS-1$
        }
        
        Node status = doc.getElementsByTagName("Status").item(0); //$NON-NLS-1$
        NamedNodeMap attr = status.getAttributes();
        return Integer.parseInt(attr.getNamedItem("protoVer").getNodeValue()); //$NON-NLS-1$
        
    }

  
    /**
     * Announce ourselves to the backend
     * @return - true if backend accepts us, false otherwise
     */
    private boolean announce() throws IOException {

        cmgr.sendString("MYTH_PROTO_VERSION " + MythDroid.protoVersion); //$NON-NLS-1$

        if (!cmgr.readStringList()[0].equals("ACCEPT")) return false; //$NON-NLS-1$
        
        cmgr.sendString("ANN Playback " + myAddr + " 0"); //$NON-NLS-1$ //$NON-NLS-2$

        if (!cmgr.readStringList()[0].equals("OK")) return false; //$NON-NLS-1$

        return true;

    }

}
