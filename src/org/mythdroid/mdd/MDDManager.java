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
import org.mythdroid.data.Video;

/**
 * Manage a connection to mdd
 */
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
                    !line.equals("DONE")
                ) {
                    lineCache.add(line);
                    continue;
                }
                
                if (!lineCache.isEmpty()) {
                    for (String l : lineCache) {
                        handleData(l);
                    }
                    lineCache.clear();
                }
                
                handleData(line);
               
            }
            
        }
    };
    
    /**
     * Get a list of MDD commands from mdd
     * @param addr - String containing address of frontend
     * @return - ArrayList<String> containing names of MDD commands
     */
    public static ArrayList<String> getCommands(String addr) throws IOException {
        ConnMgr cmgr = new ConnMgr(addr, 16546);
        ArrayList<String> cmds = new ArrayList<String>();
        
        String line = cmgr.readLine();
        
        while (line != null && !line.equals("COMMANDS DONE")) {
            if (line.startsWith("COMMAND"))
                cmds.add(line.substring(line.indexOf("COMMAND") + 8));
            line = cmgr.readLine();
        }
        
        cmgr.disconnect();
        return cmds;
    }
    
    /**
     * Send a MDD command to mdd
     * @param addr - String containing address of frontend
     * @param cmd - String containing the name of the MDD command
     */
    public static void mddCommand(String addr, String cmd) throws IOException {
        ConnMgr cmgr = new ConnMgr(addr, 16546);
        cmgr.writeLine("COMMAND " + cmd);
        cmgr.disconnect();
    }
    
    public static ArrayList<Video> 
        getVideos(String addr, String dir) throws IOException {
        
        ArrayList<Video> videos = new ArrayList<Video>();
        ConnMgr cmgr = new ConnMgr(addr, 16546);
        cmgr.writeLine("VIDEOLIST " + (dir == null ? "ROOT" : dir));
        
        String line = null;
        
        while (line == null || (!line.equals("VIDEOLIST DONE"))) {
            line = cmgr.readLine();
            if (!(line.startsWith("VIDEO ")||line.startsWith("DIRECTORY")))
                continue;
            videos.add(new Video(line));
        }

        cmgr.disconnect();
        return videos;
        
    }
    
    public static void 
        streamFile(String addr, String file, int w, int h, int vb, int ab) 
            throws IOException {
        ConnMgr cmgr = new ConnMgr(addr, 16546);
        cmgr.writeLine(
            "STREAM " + w + "x" + h + " VB " + vb + " AB " + ab + " " + file
        );
        cmgr.disconnect();
    }
    
    public static void stopStream(String addr) throws IOException {
        ConnMgr cmgr = new ConnMgr(addr, 16546);
        cmgr.writeLine("STOPSTREAM");
        cmgr.disconnect();
    }
    
    /**
     * Construct a new MDDManager
     * @param addr - String containing address of frontend
     */
    public MDDManager(String addr) throws IOException {
        cmgr = new ConnMgr(addr, 16546);
        new Thread(recvTask).start();
    }
    
    /**
     * Add a new listener for MENU events
     * @param l - MenuListener
     */
    public void setMenuListener(MDDMenuListener l) {
        menuListeners.add(l);
    }
    
    /**
     * Add a new listener for MUSIC events
     * @param l - MusicListener
     */
    public void setMusicListener(MDDMusicListener l) {
        musicListeners.add(l);
    }
    
    /**
     * Add a new listener for CHANNEL events
     * @param l - ChannelListener
     */
    public void setChannelListener(MDDChannelListener l) {
        channelListeners.add(l);
    }
    
    /**
     * Disconnect from mdd
     */
    public void shutdown() throws IOException {
        cmgr.disconnect();
    }
  
    private void handleData(String line) {
        if (line.startsWith("MENU ")) 
            handleMenu(line);
        else if (line.startsWith("MUSIC ")) 
            handleMusic(line);
        else if (line.startsWith("MUSICPROGRESS "))
            handleMusicProgress(line);
        else if (line.startsWith("MUSICPLAYERPROP "))
            handleMusicPlayerProp(line);
        else if (line.startsWith("CHANNEL "))
            handleChannel(line);
        else if (line.startsWith("CHANNELPROGRESS "))
            handleChannelProgress(line);
        else if (line.equals("DONE"))
            handleDone();
    }
    
    private void handleMenu(String line) {

        int itemidx = line.indexOf("ITEM");
        String menu = line.substring(5, itemidx - 1);
        String item = line.substring(itemidx + 4);
        
        menu.replace('_', ' ');
        
        for (MDDMenuListener l : menuListeners)
            l.onMenuItem(menu, item);
        
    }
    
    private void handleMusic(String line) {
        
        String track = null;
        int artid = -1;
        int albumidx = line.indexOf("ALBUM");
        int trackidx = line.indexOf("TRACK");
        int artidx = line.indexOf("ARTID");
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
        String prog = line.replace("MUSICPROGRESS ", "");
        for (MDDMusicListener l : musicListeners)
            l.onProgress(Integer.valueOf(prog));
    }
    
    private void handleMusicPlayerProp(String line) {
        
        String prop = line.replace("MUSICPLAYERPROP ", "");
        String a[] = prop.split(" ");
        for (MDDMusicListener l : musicListeners)
            l.onPlayerProp(a[0], Integer.valueOf(a[1]));
        
    }
    
    private void handleChannel(String line) {
        
        String title = null, subtitle = null;
        int titleidx = line.indexOf("TITLE");
        int subtitleidx = line.indexOf("SUBTITLE");
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
        String prog = line.replace("CHANNELPROGRESS ", "");
        for (MDDChannelListener l : channelListeners)
            l.onProgress(Integer.valueOf(prog));
    }
    
    private void handleDone() {
        for (MDDChannelListener l : channelListeners)
            l.onExit();
    }

}
