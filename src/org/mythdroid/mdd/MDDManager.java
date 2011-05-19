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

package org.mythdroid.mdd;

import java.io.IOException;
import java.util.ArrayList;

import org.mythdroid.ConnMgr;
import org.mythdroid.Globals;
import org.mythdroid.Enums.RecType;
import org.mythdroid.data.Program;
import org.mythdroid.data.Video;

/** Manage a connection to MDD */
public class MDDManager {

    private ConnMgr cmgr = null;
    private ArrayList<MDDMenuListener> menuListeners
        = new ArrayList<MDDMenuListener>();
    private ArrayList<MDDMusicListener> musicListeners
        = new ArrayList<MDDMusicListener>();
    private ArrayList<MDDChannelListener> channelListeners
        = new ArrayList<MDDChannelListener>();
    private ArrayList<String> lineCache = new ArrayList<String>(4);

    private Runnable recvTask = new Runnable() {
        @Override
        public void run() {

            String line;

            while (cmgr.isConnected()) {

                try {
                    line = cmgr.readLine();
                } catch (IOException e) { break; }

                if (
                    menuListeners.isEmpty()    &&
                    musicListeners.isEmpty()   &&
                    channelListeners.isEmpty() &&
                    !line.equals("DONE") //$NON-NLS-1$
                ) {
                    lineCache.add(line);
                    continue;
                }

                if (!lineCache.isEmpty()) {
                    for (String l : lineCache)
                        handleData(l);
                    lineCache.clear();
                }

                handleData(line);

            }

        }
    };

    /**
     * Get a list of MDD commands from mdd
     * @param addr String containing address of frontend
     * @return ArrayList<String> containing names of MDD commands
     */
    public static ArrayList<String> getCommands(String addr) throws IOException {
        final ConnMgr cmgr = ConnMgr.connect(addr, 16546);
        final ArrayList<String> cmds = new ArrayList<String>();

        String line = cmgr.readLine();

        while (line != null && !line.equals("COMMANDS DONE")) { //$NON-NLS-1$
            if (line.startsWith("COMMAND")) //$NON-NLS-1$
                cmds.add(line.substring(line.indexOf("COMMAND") + 8)); //$NON-NLS-1$
            line = cmgr.readLine();
        }

        cmgr.dispose();
        return cmds;
    }

    /**
     * Send a MDD command to mdd
     * @param addr String containing address of frontend
     * @param cmd String containing the name of the MDD command
     */
    public static void mddCommand(String addr, String cmd) throws IOException {
        final ConnMgr cmgr = sendMsgNoMux(addr, "COMMAND " + cmd); //$NON-NLS-1$
        cmgr.dispose();
    }

    /**
     * Static method, retrieves a list of Videos
     * @param addr String containing IP address of MDD to retrieve video list from
     * @param viddir which video dir to look in (could be a : separated list in mythtv config)
     * @param subdir String containing directory to enumerate, pass "ROOT" for the root video directory
     * @return ArrayList of Videos
     * @throws IOException
     */
    public static ArrayList<Video>
        getVideos(String addr, int viddir, String subdir) throws IOException {

        final ArrayList<Video> videos = new ArrayList<Video>(16);

        final ConnMgr cmgr =
            sendMsg(addr,
                    "VIDEOLIST " + viddir + " " + //$NON-NLS-1$ //$NON-NLS-2$
                    (subdir == null ? "ROOT" : subdir)); //$NON-NLS-1$

        while (true) {
            String line = cmgr.readLine();
            if (line.equals("VIDEOLIST DONE")) //$NON-NLS-1$
                break;
            videos.add(new Video(line));
        }

        videos.trimToSize();
        cmgr.dispose();
        return videos;

    }

    /**
     * Ask MDD to stream a file, SDP will be at http://addr:5554/stream
     * @param addr String containing IP address of MDD
     * @param file String containing path to the file
     * @param w int representing desired width of video in pixels
     * @param h int representing desired height of video in pixels
     * @param vb int representing desired bitrate of video in kb/s
     * @param ab int representing desired bitrate of audio in kb/s
     */
    public static void streamFile(
            String addr, String file, String sg, int w, int h, int vb, int ab
    ) throws IOException {
        final ConnMgr cmgr = sendMsg(
            addr,
            "STREAM " + w + "x" + h + " VB " + vb + " AB " + ab + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            " SG " + sg + " FILE " + file  //$NON-NLS-1$ //$NON-NLS-2$
        );
        cmgr.dispose();
    }

    /**
     * Ask MDD to stop streaming the currently streaming file
     * @param addr String containing IP address of MDD
     */
    public static void stopStream(String addr) throws IOException {
        final ConnMgr cmgr = sendMsg(addr, "STOPSTREAM"); //$NON-NLS-1$
        cmgr.dispose();
    }

    /**
     * Determine the RecType of a recording
     * @param addr String containing IP address of MDD
     * @param recid int representing RecID of recording
     * @return - RecType
     */
    public static RecType getRecType(String addr, int recid)
        throws IOException {
        final ConnMgr cmgr = sendMsg(addr, "RECTYPE " + recid); //$NON-NLS-1$
        final RecType rt = RecType.get(Integer.parseInt(cmgr.readLine()));
        cmgr.dispose();
        return rt;
    }

    /**
     * Determine the storage group of a recording
     * @param addr String containing IP address of MDD
     * @param recid int representing RecID of recording
     * @return String contaning recording's storage group
     */
    public static String getStorageGroup(String addr, int recid) throws IOException {
        final ConnMgr cmgr = sendMsg(addr, "STORGROUP " + recid); //$NON-NLS-1$
        String sg = cmgr.readLine();
        cmgr.dispose();
        return sg;
    }

    /**
     * Get a list of storage groups
     * @param addr String containing IP address of MDD
     * @return String[] of storage groups
     */
    public static String[] getStorageGroups(String addr) throws IOException {
        final ConnMgr cmgr = sendMsg(addr, "STORGROUPS"); //$NON-NLS-1$
        final ArrayList<String> groups = new ArrayList<String>();

        while (true) {
            String line = cmgr.readLine();
            if (line.equals("STORGROUPS DONE")) //$NON-NLS-1$
                break;
            groups.add(line);
        }

        cmgr.dispose();
        return groups.toArray(new String[groups.size()]);
    }

    /**
     * Get a list of recording groups
     * @param addr String containing IP address of MDD
     * @return String[] of recording groups
     */
    public static String[] getRecGroups(String addr) throws IOException {
        final ConnMgr cmgr = sendMsg(addr, "RECGROUPS"); //$NON-NLS-1$
        final ArrayList<String> groups = new ArrayList<String>();

        while (true) {
            String line = cmgr.readLine();
            if (line.equals("RECGROUPS DONE")) //$NON-NLS-1$
                break;
            groups.add(line);
        }

        cmgr.dispose();
        return groups.toArray(new String[groups.size()]);
    }

    /**
     * Create or update a recording rule
     * @param addr String containing IP address of MDD
     * @param prog Program the rule relates to
     * @param updates a list of modifications to an existing or default rule
     * @return int representing the RecID of the new/updated recording rule
     */
    public static int updateRecording(
        String addr, Program prog, String updates
    ) throws IOException {
        String msg;
        if (prog.RecID != -1)
            msg = "UPDATEREC " + prog.RecID + " " + updates; //$NON-NLS-1$ //$NON-NLS-2$
        else
            msg = "NEWREC " + prog.ChanID + " " + //$NON-NLS-1$ //$NON-NLS-2$
                      prog.StartTime.getTime() / 1000 + " " + updates; //$NON-NLS-1$

        final ConnMgr cmgr = sendMsg(addr, msg);
        int recid = Integer.parseInt(cmgr.readLine());
        cmgr.dispose();
        return recid;
    }

    /**
     * Delete a recording rule
     * @param addr String containing IP address of MDD
     * @param recid RecID of the rule to delete
     * @throws IOException
     */
    public static void deleteRecording(String addr, int recid)
        throws IOException {
        final ConnMgr cmgr = sendMsg(addr, "DELREC " + recid); //$NON-NLS-1$
        cmgr.dispose();
    }

    /**
     * Construct a new MDDManager
     * @param addr String containing address of frontend
     */
    public MDDManager(String addr) throws IOException {
        cmgr = ConnMgr.connect(addr, 16546);
        // Wait indefinitely for messages from MDD
        cmgr.setTimeout(0);
        new Thread(recvTask).start();
    }
    
    /**
     * Construct a new MDDManager
     * @param addr String containing address of frontend
     * @param mux mux MDD connection via MDD if true
     */
    public MDDManager(String addr, boolean mux) throws IOException {
        cmgr = ConnMgr.connect(addr, 16546, null, mux);
        // Wait indefinitely for messages from MDD
        cmgr.setTimeout(0);
        new Thread(recvTask).start();
    }


    /**
     * Add a new listener for MENU events
     * @param l MenuListener
     */
    public void setMenuListener(MDDMenuListener l) {
        menuListeners.add(l);
    }

    /**
     * Add a new listener for MUSIC events
     * @param l MusicListener
     */
    public void setMusicListener(MDDMusicListener l) {
        musicListeners.add(l);
    }

    /**
     * Add a new listener for CHANNEL events
     * @param l ChannelListener
     */
    public void setChannelListener(MDDChannelListener l) {
        channelListeners.add(l);
    }

    /** Disconnect from MDD and clean up internal resources */
    public void shutdown() throws IOException {
        cmgr.dispose();
    }

    private static ConnMgr sendMsg(String addr, String msg) throws IOException {
        final ConnMgr cmgr = ConnMgr.connect(addr, 16546, null, Globals.muxConns);
        getMsgResponse(cmgr, msg);
        return cmgr;
    }
    
    private static ConnMgr sendMsgNoMux(String addr, String msg)
        throws IOException {
        final ConnMgr cmgr = ConnMgr.connect(addr, 16546);
        getMsgResponse(cmgr, msg);
        return cmgr;
    }
    
    private static void getMsgResponse(ConnMgr cmgr, String msg)
        throws IOException {
        
        String resp = null;
        cmgr.writeLine(msg);
        while (true) {
            resp = cmgr.readLine();
            if (resp.equals("OK")) //$NON-NLS-1$
                return;
            if (resp.equals("UNKNOWN")) //$NON-NLS-1$
                throw new IOException(
                    "MDD doesn't understand " + //$NON-NLS-1$
                    msg.substring(0, msg.indexOf(' '))
                );
        }
        
    }

    private void handleData(String line) {
        if (line.startsWith("MENU "))  //$NON-NLS-1$
            handleMenu(line);
        else if (line.startsWith("MUSIC "))  //$NON-NLS-1$
            handleMusic(line);
        else if (line.startsWith("MUSICPROGRESS ")) //$NON-NLS-1$
            handleMusicProgress(line);
        else if (line.startsWith("MUSICPLAYERPROP ")) //$NON-NLS-1$
            handleMusicPlayerProp(line);
        else if (line.startsWith("CHANNEL ")) //$NON-NLS-1$
            handleChannel(line);
        else if (line.startsWith("CHANNELPROGRESS ")) //$NON-NLS-1$
            handleChannelProgress(line);
        else if (line.equals("DONE")) //$NON-NLS-1$
            handleDone();
    }

    private void handleMenu(String line) {

        int itemidx = line.indexOf("ITEM"); //$NON-NLS-1$
        String menu = line.substring(5, itemidx - 1);
        String item = line.substring(itemidx + 4);

        menu.replace('_', ' ');

        for (MDDMenuListener l : menuListeners)
            l.onMenuItem(menu, item);

    }

    private void handleMusic(String line) {

        String track = null;
        int artid = -1;
        int albumidx = line.indexOf("ALBUM"); //$NON-NLS-1$
        int trackidx = line.indexOf("TRACK"); //$NON-NLS-1$
        int artidx = line.indexOf("ARTID"); //$NON-NLS-1$
        String artist = line.substring(6, albumidx - 1);
        String album = line.substring(albumidx + 6, trackidx - 1);
        if (artidx != -1) {
            track = line.substring(trackidx + 6, artidx - 1);
            artid = Integer.valueOf(line.substring(artidx + 6));
        }
        else
            track = line.substring(trackidx + 6);

        for (MDDMusicListener l : musicListeners)
            l.onMusic(artist, album, track, artid);

    }

    private void handleMusicProgress(String line) {
        String prog = line.replace("MUSICPROGRESS ", ""); //$NON-NLS-1$ //$NON-NLS-2$
        for (MDDMusicListener l : musicListeners)
            l.onProgress(Integer.valueOf(prog));
    }

    private void handleMusicPlayerProp(String line) {

        String prop = line.replace("MUSICPLAYERPROP ", ""); //$NON-NLS-1$ //$NON-NLS-2$
        String a[] = prop.split(" "); //$NON-NLS-1$
        for (MDDMusicListener l : musicListeners)
            l.onPlayerProp(a[0], Integer.valueOf(a[1]));

    }

    private void handleChannel(String line) {

        String title = null, subtitle = null;
        int titleidx = line.indexOf("TITLE"); //$NON-NLS-1$
        int subtitleidx = line.indexOf("SUBTITLE"); //$NON-NLS-1$
        String channel = line.substring(8, titleidx - 1);
        if (subtitleidx != -1) {
            title = line.substring(titleidx + 6, subtitleidx - 1);
            subtitle = line.substring(subtitleidx + 9);
        }
        else
            title = line.substring(titleidx + 6);

        for (MDDChannelListener l : channelListeners)
            l.onChannel(channel, title, subtitle);

    }

    private void handleChannelProgress(String line) {
        String prog = line.replace("CHANNELPROGRESS ", ""); //$NON-NLS-1$ //$NON-NLS-2$
        for (MDDChannelListener l : channelListeners)
            l.onProgress(Integer.valueOf(prog));
    }

    private void handleDone() {
        for (MDDChannelListener l : channelListeners)
            l.onExit();
    }

}
