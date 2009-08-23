package org.mythdroid;

import java.io.IOException;
import java.util.ArrayList;

interface MenuListener {
    public void onMenuItem(String menu, String item);
}

interface MusicListener {
    public void onMusic(
        String artist, String album, String track, int artid
    );
    public void onProgress(int pos);
    public void onPlayerProp(String prop, int value);
}

interface ChannelListener {
    public void onChannel(String channel, String title, String subtitle);
    public void onProgress(int pos);
    public void onExit();
}

public class MDDManager {
    
    private ConnMgr cmgr = null;
    private ArrayList<MenuListener> menuListeners 
        = new ArrayList<MenuListener>();
    private ArrayList<MusicListener> musicListeners
        = new ArrayList<MusicListener>();
    private ArrayList<ChannelListener> channelListeners
        = new ArrayList<ChannelListener>();
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
                    menuListeners.isEmpty() && 
                    musicListeners.isEmpty() &&
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
    
    public MDDManager(String addr) throws IOException {
        cmgr = new ConnMgr(addr, 16546);
        new Thread(recvTask).start();
    }
    
    public void setMenuListener(MenuListener l) {
        menuListeners.add(l);
    }
    
    public void setMusicListener(MusicListener l) {
        musicListeners.add(l);
    }
    
    public void setChannelListener(ChannelListener l) {
        channelListeners.add(l);
    }
    
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
        
        for (MenuListener l : menuListeners)
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
        
        for (MusicListener l : musicListeners)
            l.onMusic(artist, album, track, artid);
        
    }
    
    private void handleMusicProgress(String line) {
        String prog = line.replace("MUSICPROGRESS ", "");
        for (MusicListener l : musicListeners)
            l.onProgress(Integer.valueOf(prog));
    }
    
    private void handleMusicPlayerProp(String line) {
        
        String prop = line.replace("MUSICPLAYERPROP ", "");
        String a[] = prop.split(" ");
        for (MusicListener l : musicListeners)
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
        
        for (ChannelListener l : channelListeners)
            l.onChannel(channel, title, subtitle);
            
    }
    
    private void handleChannelProgress(String line) {
        String prog = line.replace("CHANNELPROGRESS ", "");
        for (ChannelListener l : channelListeners)
            l.onProgress(Integer.valueOf(prog));
    }
    
    private void handleDone() {
        for (ChannelListener l : channelListeners)
            l.onExit();
    }

}
