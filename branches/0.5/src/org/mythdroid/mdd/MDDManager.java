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
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;

/** Manage a connection to MDD */
public class MDDManager {

    private ConnMgr cmgr = null;
    private Thread recvThread = null;
    
    final private ArrayList<MDDMenuListener> menuListeners
        = new ArrayList<MDDMenuListener>();
    final private ArrayList<MDDMusicListener> musicListeners
        = new ArrayList<MDDMusicListener>();
    final private ArrayList<MDDChannelListener> channelListeners
        = new ArrayList<MDDChannelListener>();
    
    final private ArrayList<String> lineCache = new ArrayList<String>(4);
    
    /*
     *  Listen for events from MDD and send them to the appropriate listener
     *  Run in its own thread
     */
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
     * @param addr String containing address of MDD
     * @return ArrayList<String> containing names of MDD commands
     */
    public static ArrayList<String> getCommands(String addr) throws IOException
    {
        
        final ConnMgr cmgr = sendMsgNoMux(addr, "COMMANDS"); //$NON-NLS-1$
        final ArrayList<String> cmds = new ArrayList<String>();
        
        String line = cmgr.readLine();
        while (line != null && !line.equals("COMMANDS DONE")) { //$NON-NLS-1$
            if (line.startsWith("COMMAND")) //$NON-NLS-1$
                cmds.add(line.substring(line.indexOf("COMMAND") + 8)); //$NON-NLS-1$
            line = cmgr.readLine();
        }

        cmgr.disconnect();
        return cmds;
        
    }

    /**
     * Send a MDD command to mdd
     * @param addr String containing address of frontend
     * @param cmd String containing the name of the MDD command
     */
    public static void mddCommand(String addr, String cmd) throws IOException {
        sendMsgNoMux(addr, "COMMAND " + cmd).disconnect(); //$NON-NLS-1$
    }

    /**
     * Static method, retrieves a list of Videos
     * @param addr String containing address of MDD
     * @param viddir which video dir(s) to look in (could be a : separated list)
     * @param subdir String containing directory to enumerate, 
     * pass "ROOT" for the root video directory
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

        // Uncached SQL queries can take some time with lots of videos
        cmgr.setTimeout(ConnMgr.timeOut.LONG);
        
        String line = cmgr.readLine();
        while (line != null && !line.equals("VIDEOLIST DONE")) { //$NON-NLS-1$
            if (!line.equals("KEEPALIVE")) //$NON-NLS-1$
                try {
                    videos.add(new Video(line));
                } catch (IllegalArgumentException e) { ErrUtil.logWarn(e); }
            line = cmgr.readLine();
        }

        videos.trimToSize();
        cmgr.disconnect();
        return videos;

    }

    /**
     * Ask MDD to stream a file, SDP will be at http://addr:5554/stream
     * @param addr String containing address of MDD
     * @param file String containing path to the file
     * @param w int representing desired width of video in pixels
     * @param h int representing desired height of video in pixels
     * @param enc int representing the desired encoding complexity (0-2)
     * @param vb int representing desired bitrate of video in kb/s
     * @param ab int representing desired bitrate of audio in kb/s
     */
    public static void streamFile(
            String addr, String file, String sg, int w, int h, int enc,
            int vb, int ab
    ) throws IOException {
        sendMsg(
            addr,
            "STREAM " + w + "x" + h + " ENC " + enc + " VB " + vb + " AB " + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ 
            ab + " SG " + sg + " FILE " + file  //$NON-NLS-1$ //$NON-NLS-2$
        ).disconnect();
    }

    /**
     * Ask MDD to stop streaming the currently streaming file
     * @param addr String containing IP address of MDD
     */
    public static void stopStream(String addr) throws IOException {
        sendMsg(addr, "STOPSTREAM").disconnect(); //$NON-NLS-1$
    }

    /**
     * Determine the RecType of a recording
     * @param addr String containing address of MDD
     * @param recid int representing RecID of recording
     * @return RecType
     */
    public static RecType getRecType(String addr, int recid)
        throws IOException {
        final ConnMgr cmgr = sendMsg(addr, "RECTYPE " + recid); //$NON-NLS-1$
        final RecType rt = RecType.get(Integer.parseInt(cmgr.readLine()));
        cmgr.disconnect();
        return rt;
    }
    
    /**
     * Get a cutlist for a recording
     * @param addr String containing address of MDD
     * @param prog Program to get cutlist for
     * @return Array of cuts represented as 2 element arrays. 
     * The first element is the start frame, the second is the end frame
     */
    public static int[][] getCutList(String addr, Program prog) 
        throws IOException {
        
        final ConnMgr cmgr = sendMsg(
            addr, "CUTLIST " + prog.ChanID + " " + //$NON-NLS-1$ //$NON-NLS-2$ 
            prog.RecStartTime.getTime() / 1000 
        ); 
        
        ArrayList<int[]> cuts = new ArrayList<int[]>(8);
        String line = cmgr.readLine();
        while (line != null && !line.equals("CUTLIST DONE")) { //$NON-NLS-1$
            int[] cut = new int[2]; 
            cut[0] = Integer.parseInt(
                line.substring(0, line.indexOf('-')).trim()
            );
            cut[1] = Integer.parseInt(
                line.substring(line.indexOf('-') + 1).trim()
            );
            cuts.add(cut);
            line = cmgr.readLine();
        }

        cmgr.disconnect();
        return cuts.toArray(new int[cuts.size()][2]);
        
    }

    /**
     * Determine the storage group of a recording
     * @param addr String containing address of MDD
     * @param recid int representing RecID of recording
     * @return String contaning recording's storage group
     */
    public static String getStorageGroup(String addr, int recid)
        throws IOException {
        final ConnMgr cmgr = sendMsg(addr, "STORGROUP " + recid); //$NON-NLS-1$
        String sg = cmgr.readLine();
        cmgr.disconnect();
        return sg;
    }

    /**
     * Get a list of storage groups
     * @param addr String containing address of MDD
     * @return String[] of storage groups
     */
    public static String[] getStorageGroups(String addr) throws IOException {
        final ConnMgr cmgr = sendMsg(addr, "STORGROUPS"); //$NON-NLS-1$
        final ArrayList<String> groups = new ArrayList<String>();

        String line = cmgr.readLine();
        while (line != null && !line.equals("STORGROUPS DONE")) { //$NON-NLS-1$
            groups.add(line);
            line = cmgr.readLine();
        }

        cmgr.disconnect();
        return groups.toArray(new String[groups.size()]);
    }

    /**
     * Get a list of recording groups
     * @param addr String containing address of MDD
     * @return String[] of recording groups
     */
    public static String[] getRecGroups(String addr) throws IOException {
        final ConnMgr cmgr = sendMsg(addr, "RECGROUPS"); //$NON-NLS-1$
        final ArrayList<String> groups = new ArrayList<String>();
        
        String line = cmgr.readLine();
        while (line != null && !line.equals("RECGROUPS DONE")) { //$NON-NLS-1$
            groups.add(line);
            line = cmgr.readLine();
        }

        cmgr.disconnect();
        return groups.toArray(new String[groups.size()]);
    }

    /**
     * Create or update a recording rule
     * @param addr String containing address of MDD
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
        cmgr.disconnect();
        return recid;
    }

    /**
     * Delete a recording rule
     * @param addr String containing address of MDD
     * @param recid RecID of the rule to delete
     */
    public static void deleteRecording(String addr, int recid)
        throws IOException {
        sendMsg(addr, "DELREC " + recid).disconnect(); //$NON-NLS-1$
    }
    
    /**
     * Display a message via XOSD
     * @param addr String containing address of MDD
     * @param msg Message to display
     */
    public static void osdMsg(String addr, String msg) throws IOException {
        sendMsgNoMux(addr, "OSD " + msg).disconnect(); //$NON-NLS-1$
    }

    /**
     * Construct a new MDDManager
     * @param addr String containing address of host running MDD
     */
    public MDDManager(String addr) throws IOException {
        /* 
         * Don't use persistent connections for 'listening' mdd connections
         * We'll end up with an immortal recvThread since we can't interrupt
         * the read therein
         */
        cmgr = new ConnMgr(addr, 16546, null, false);
        // Wait indefinitely for messages from MDD
        cmgr.setIndefiniteReads();
        recvThread = new Thread(recvTask, "MDDListener"); //$NON-NLS-1$
        recvThread.start();
    }
    
    /**
     * Construct a new MDDManager
     * @param addr String containing address of host running MDD
     * @param mux mux MDD connection via MDD if true
     */
    public MDDManager(String addr, boolean mux) throws IOException {
        /* 
         * Don't use persistent connections for 'listening' mdd connections
         * We'll end up with an immortal recvThread since we can't interrupt
         * the read therein
         */
        cmgr = new ConnMgr(addr, 16546, null, mux);
        // Wait indefinitely for messages from MDD
        cmgr.setIndefiniteReads();
        recvThread = new Thread(recvTask, "MDDListener"); //$NON-NLS-1$
        recvThread.start();
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
    public void shutdown() {
        try {
            cmgr.dispose();
        } catch (IOException e) {}
    }

    private static ConnMgr sendMsg(String addr, String msg) throws IOException {
        final ConnMgr cmgr = ConnMgr.connect(
            addr, 16546, null, Globals.muxConns
        );
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
        
        cmgr.writeLine(msg);
        String resp = null;
        do {
            resp = cmgr.readLine();
            if (resp.equals("UNKNOWN")) { //$NON-NLS-1$
                int idx = msg.indexOf(' ');
                if (idx == -1) idx = msg.length();
                throw new IOException(
                    Messages.getString("MDDManager.0") + //$NON-NLS-1$
                    msg.substring(0, idx)
                );
            }
        } while (!resp.equals("OK")); //$NON-NLS-1$
        
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
