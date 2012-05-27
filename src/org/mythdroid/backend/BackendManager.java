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
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;


import org.mythdroid.ConnMgr;
import org.mythdroid.Globals;
import org.mythdroid.data.Program;
import org.mythdroid.resource.Messages;
import org.mythdroid.services.MythService;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.util.LogUtil;
import org.mythdroid.util.UpdateService;
import org.mythdroid.ConnMgr.onConnectListener;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * A BackendManager locates and manages a master backend, providing
 * methods to get, delete and stop recordings
 */
public class BackendManager {

    /** Hostname or IP address of the backend */
    public String addr = null;

    static final private String myAddr = "android"; //$NON-NLS-1$

    private String  statusURL = null;
    private ConnMgr cmgr      = null;

    /**
     * Constructor
     * @param host String containing the backend address
     */
    public BackendManager(final String host) throws IOException {

        statusURL = "http://" + host + ":6544"; //$NON-NLS-1$ //$NON-NLS-2$

        try {
            Globals.protoVersion = getVersion(statusURL);
        } catch (IOException e) {
            MythService myth = new MythService(host);
            Globals.protoVersion = myth.getVersion();
        }
        
        // Cope with odd protoVer resulting from mythtv r25366
        if (Globals.protoVersion > 1000) {
            Globals.beVersion = Globals.protoVersion / 1000;
            Globals.protoVersion = Globals.protoVersion % 1000;
        }

        LogUtil.debug(
            "Connecting to " + host +  //$NON-NLS-1$
            ":6543 (ProtoVer " + Globals.protoVersion +")" //$NON-NLS-1$ //$NON-NLS-2$
        );

        cmgr = ConnMgr.connect(host, 6543, new onConnectListener() {
                @Override
                public void onConnect(ConnMgr cmgr) throws IOException {
                    if (!announce(cmgr))
                        throw new IOException(
                            Messages.getString("BackendManager.0") //$NON-NLS-1$)
                        );
                }
            }, Globals.muxConns
        );
        
        addr = host;

        if (!Globals.checkedForUpdate(addr)) {
            Intent intent = new Intent();
            intent.setClass(Globals.appContext, UpdateService.class);
            intent.putExtra(UpdateService.ACTION, UpdateService.CHECKMDD);
            intent.putExtra(UpdateService.ADDR, addr);
            Globals.appContext.startService(intent);
        }

    }

    /**
     * Get the state of the connection to the backend
     * @return true if we are connected, false otherwise
     */
    public boolean isConnected() {
        return (cmgr != null && cmgr.isConnected());
    }

    /**
     * Get the URL of the status / XML service
     * @return String containing the URL
     */
    public String getStatusURL() {
        return statusURL;
    }

    /**
     * Get a Program from a recording filename
     * @param basename String containing the full path to the recording
     * @return A Program representing the recording
     */
    public Program getRecording(final String basename) throws IOException {

        Program prog = null;
        
        if (basename.equalsIgnoreCase("Unknown")) { //$NON-NLS-1$
            prog = new Program();
            prog.Title = "Unknown"; //$NON-NLS-1$
            return prog;
        }

        cmgr.sendString("QUERY_RECORDING BASENAME " + basename); //$NON-NLS-1$
        try {
            prog = new Program(cmgr.readStringList(), 1);
        } catch (IllegalArgumentException e) {
            LogUtil.warn(e.getMessage());
            prog = new Program();
            prog.Title = "Unknown";  //$NON-NLS-1$
        }
        
        return prog;

    }

    /**
     * Get a list of recordings
     * @return An ArrayList of Programs
     */
    public ArrayList<Program> getRecordings() throws IOException {

        // QUERY_RECORDINGS can take a few seconds..
        cmgr.setTimeout(ConnMgr.timeOut.LONG);
        
        String type = "Ascending"; //$NON-NLS-1$
        if (Globals.protoVersion < 65) 
            type = "Play"; //$NON-NLS-1$
        cmgr.sendString("QUERY_RECORDINGS " + type); //$NON-NLS-1$
        final String[] resp = cmgr.readStringList();

        int respSize = resp.length;
        int numFields = Program.numFields();
        int groupField = Program.recGroupField();

        ArrayList<Program> programs =
            new ArrayList<Program>(respSize / numFields);

        for (int i = respSize - numFields; i >= 0; i -= numFields) {
            if (!resp[i + groupField].equals("Default")) continue; //$NON-NLS-1$
            try {
                programs.add(new Program(resp, i));
            } catch (IllegalArgumentException e) { 
                LogUtil.warn(e.getMessage());
                continue; 
            }
        }

        if (Globals.protoVersion > 56)
            Collections.sort(programs, Collections.reverseOrder());

        return programs;

    }

    /**
     * Stop an in-progress recording
     * @param prog The Program to stop recording
     */
    public void stopRecording(final Program prog) throws IOException {
        final String[] list = prog.stringList();
        list[0] = "STOP_RECORDING"; //$NON-NLS-1$
        cmgr.sendStringList(list);
        cmgr.readStringList();
    }


    /**
     * Delete a recording
     * @param prog The Program to delete
     */
    public void deleteRecording(final Program prog) throws IOException {
        final String[] list = prog.stringList();
        list[0]= "DELETE_RECORDING"; //$NON-NLS-1$
        cmgr.sendStringList(list);
        cmgr.readStringList();
    }

    /**
     * Ask the backend to reschedule a recording
     * @param recid integer recording id
     */
    public void reschedule(int recid) throws IOException {
        if (Globals.protoVersion >= 73)
            cmgr.sendString(
                "RESCHEDULE_RECORDINGS MATCH " + recid + " 0 0 - MythDroid" //$NON-NLS-1$ //$NON-NLS-2$
            );
        else
            cmgr.sendString("RESCHEDULE_RECORDINGS " + recid); //$NON-NLS-1$
        cmgr.readStringList();
    }
    
    /**
     * Fetch an image file from the backend
     * @param path URL path for file
     * @return a Bitmap or null if there's a problem
     */
    public Bitmap getImage(String path) {
        
        URL url = null;
        try {
            url = new URL(statusURL + path);
        } catch (MalformedURLException e) { 
            ErrUtil.logWarn(e);
            return null; 
        }
        
        if (Globals.muxConns) 
            try {
                url = new URL(
                    url.getProtocol() + "://" + url.getHost() + ":16550" + //$NON-NLS-1$ //$NON-NLS-2$
                    url.getFile()
                );
            } catch (MalformedURLException e) {
                ErrUtil.logWarn(e);
                return null;
            }
        
        LogUtil.debug("Fetching image from " + url.toString()); //$NON-NLS-1$

        try {
            return BitmapFactory.decodeStream(url.openStream());
        } catch (IOException e) {
            ErrUtil.logWarn(e);
            return null;
        }
        
    }

    /** Disconnect from the backend */
    public void done() {
        cmgr.disconnect();
    }

    /**
     * Get the protocol version from the backend
     * @param sURL string containing backend status URL
     * @return integer containing backend protocol version
     */
    private int getVersion(String sURL) throws IOException {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        URL url = new URL(sURL + "/xml"); //$NON-NLS-1$
        Document doc;
        
        if (Globals.muxConns)
            url = new URL(
                url.getProtocol() + "://" + url.getHost() +  ":16550/xml"  //$NON-NLS-1$ //$NON-NLS-2$
            );
        
        LogUtil.debug("Fetching XML from " + url.toString()); //$NON-NLS-1$
        
        URLConnection urlConn = url.openConnection();
        urlConn.setConnectTimeout(3000);
        urlConn.setReadTimeout(4000);

        try {
            doc = dbf.newDocumentBuilder().parse(urlConn.getInputStream());
        } catch (SocketTimeoutException e) {
            throw new IOException(Messages.getString("BackendManager.1")); //$NON-NLS-1$
        } catch (SAXException e) {
            throw new IOException(Messages.getString("Status.10")); //$NON-NLS-1$
        } catch (ParserConfigurationException e) {
            throw new IOException(Messages.getString("Status.10")); //$NON-NLS-1$
        }

        Node status = doc.getElementsByTagName("Status").item(0); //$NON-NLS-1$
        NamedNodeMap attr = status.getAttributes();
        return Integer.parseInt(attr.getNamedItem("protoVer").getNodeValue()); //$NON-NLS-1$

    }

    /**
     * Announce ourselves to the backend
     * @return true if backend accepts us, false otherwise
     */
    private boolean announce(ConnMgr cmgr) throws IOException {

        // Cope with odd protoVer resulting from mythtv r25366
        int protoVer = Globals.beVersion * 1000 + Globals.protoVersion;

        String protoToken = getToken(protoVer);
        // prefix a space for the actual request
        if (protoToken.length() > 0)
            protoToken = " " + protoToken; //$NON-NLS-1$

        cmgr.sendString("MYTH_PROTO_VERSION " + protoVer + protoToken); //$NON-NLS-1$

        if (!cmgr.readStringList()[0].equals("ACCEPT")) return false; //$NON-NLS-1$

        cmgr.sendString("ANN Playback " + myAddr + " 0"); //$NON-NLS-1$ //$NON-NLS-2$

        if (!cmgr.readStringList()[0].equals("OK")) return false; //$NON-NLS-1$

        return true;

    }

    /**
     * Returns the protocol token for a given protocol version.
     * Tokens are defined in the messages.properties as:
     * ProtoToken.protoVer = token
     * @param protoVer protocol version to retrieve token for
     */
    private String getToken(int protoVer) {

        String key = "ProtoToken." + protoVer; //$NON-NLS-1$
        String token = Messages.getString(key);

        if (token.equals("!" + key + "!")) { //$NON-NLS-1$ //$NON-NLS-2$
            token = ""; //$NON-NLS-1$
        }

        return token.trim();

    }

}
