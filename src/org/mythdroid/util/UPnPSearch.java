package org.mythdroid.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import org.mythdroid.Globals;
import org.mythdroid.receivers.ConnectivityReceiver;
import org.mythdroid.resource.Messages;

/** Utilities for finding devices via UPnP */
public class UPnPSearch {
    
    static final private String BACKEND_UPNP_ID =
        "ST: urn:schemas-mythtv-org:device:MasterMediaServer:1\r\n"; //$NON-NLS-1$
    
    static final private String FRONTEND_UPNP_ID = 
        "ST: urn:schemas-mythtv-org:service:MythFrontend:1\r\n"; //$NON-NLS-1$

    static final private String UPNP_SEARCH     =
        "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" + //$NON-NLS-1$
        "MAN: \"ssdp:discover\"\r\n" + //$NON-NLS-1$
        "MX: 2\r\n"; //$NON-NLS-1$
       

    static final private String UPNP_LOCATION   = "LOCATION: "; //$NON-NLS-1$
    
    /**
     * Find a master backend via UPnP
     * @return String containing the address of the discovered backend
     * @throws IOException
     */
    static public String findMasterBackend() throws IOException {
        
        final DatagramSocket sock = sendSearch(BACKEND_UPNP_ID);
        final DatagramPacket rpkt = new DatagramPacket(new byte[1024], 1024);
        
        try {
            sock.receive(rpkt);
        } catch (SocketTimeoutException e) {
            sock.close();
            LogUtil.debug("Timeout waiting for UPNP response"); //$NON-NLS-1$
            throw new IOException(Messages.getString("BackendManager.2")); //$NON-NLS-1$
        }
    
        sock.close();
    
        final String msg = new String(rpkt.getData(), 0, rpkt.getLength());
        LogUtil.debug("UPNP Response received: " + msg); //$NON-NLS-1$
    
        if (!msg.contains(BACKEND_UPNP_ID)) 
            throw new IOException(Messages.getString("BackendManager.2")); //$NON-NLS-1$
    
        int locIdx = msg.indexOf(UPNP_LOCATION) + 7;
        int portIdx = msg.indexOf(":", locIdx + UPNP_LOCATION.length()); //$NON-NLS-1$
        
        return msg.substring(locIdx + UPNP_LOCATION.length(), portIdx);
        
    }
    
    /**
     * Find frontends via UPnP
     * @return an ArrayList of Strings containing the address of discovered
     *  frontends
     * @throws IOException
     */
    static public ArrayList<String> findFrontends() throws IOException {
        
        final DatagramSocket sock = sendSearch(FRONTEND_UPNP_ID);
        final DatagramPacket rpkt = new DatagramPacket(new byte[1024], 1024);
        final ArrayList<String> frontends = new ArrayList<String>(4);
        
        sock.setSoTimeout(500);
        
        while (true) {
            
            try {
                sock.receive(rpkt);
            } catch (SocketTimeoutException e) {
                break;
            }
            
            final String msg = new String(rpkt.getData(), 0, rpkt.getLength());

            if (!msg.contains(FRONTEND_UPNP_ID)) continue; 
        
            int locIdx = msg.indexOf(UPNP_LOCATION);
            int eolIdx = msg.indexOf("\r\n", locIdx + UPNP_LOCATION.length()); //$NON-NLS-1$
            
            String url = msg.substring(locIdx + UPNP_LOCATION.length(), eolIdx);
            
            if (!frontends.contains(url))
                frontends.add(
                    msg.substring(locIdx + UPNP_LOCATION.length(), eolIdx)
                );
            
        }
        
        sock.close();
        
        return frontends;
        
    }
    
    static private DatagramSocket sendSearch(String search) throws IOException {
        
        final InetSocketAddress isa = new InetSocketAddress(1900);
        final DatagramSocket sock = new DatagramSocket(null);
        InetAddress addr = null;

        try {
            addr = InetAddress.getByName("239.255.255.250"); //$NON-NLS-1$
        } catch (UnknownHostException e) {}
        
        String msearch = UPNP_SEARCH + search + "\r\n"; //$NON-NLS-1$

        final DatagramPacket pkt = new DatagramPacket(
            msearch.getBytes(), msearch.length(), addr, 1900
        );
    
        ConnectivityReceiver.waitForWifi(Globals.appContext, 5000);
    
        LogUtil.debug("Sending UPNP M-SEARCH to 239.255.255.250:1900"); //$NON-NLS-1$
    
        sock.setReuseAddress(true);
        sock.bind(isa);
        sock.setBroadcast(true);
        sock.setSoTimeout(800);
        sock.send(pkt);
        
        return sock;
     
    }
    
}
