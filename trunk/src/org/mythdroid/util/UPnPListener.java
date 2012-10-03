package org.mythdroid.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.mythdroid.Globals;
import org.mythdroid.data.XMLHandler;
import org.mythdroid.data.XMLHandler.Element;
import org.mythdroid.frontend.FrontendDB;
import org.mythdroid.frontend.FrontendManager;
import org.mythdroid.receivers.ConnectivityReceiver;
import org.mythdroid.resource.Messages;
import org.xml.sax.SAXException;

import android.content.Context;
import android.os.Process;
import android.sax.EndTextElementListener;
import android.util.Xml;

/** Utilities for finding devices via UPnP */
public class UPnPListener {
    
    static final private String BACKEND_UPNP_ID =
        "ST: urn:schemas-mythtv-org:device:MasterMediaServer:1\r\n"; //$NON-NLS-1$
    
    static final private String FRONTEND_UPNP_ID = 
        "ST: urn:schemas-mythtv-org:service:MythFrontend:1\r\n"; //$NON-NLS-1$

    static final private String UPNP_SEARCH     =
        "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" + //$NON-NLS-1$
        "MAN: \"ssdp:discover\"\r\n" + //$NON-NLS-1$
        "MX: 2\r\n"; //$NON-NLS-1$
       

    static final private String UPNP_LOCATION   = "LOCATION: "; //$NON-NLS-1$
    
    static final private DatagramPacket rpkt =
        new DatagramPacket(new byte[1024], 1024);
    
    final private ArrayList<String> frontends = new ArrayList<String>(4);
    
    private String backend = null;
    
    final private Runnable recvRunnable = new Runnable() {
        @Override
        public void run() {
            
            final InetSocketAddress isa = new InetSocketAddress(1900);
            DatagramSocket sock;
            try {
                sock = new DatagramSocket(null);
                sock.setReuseAddress(true);
                sock.bind(isa);
            } catch (SocketException e) {
                ErrUtil.logErr(e);
                return;
            }
            
            while(true) {
                
                try {
                    sock.receive(rpkt);
                } catch (IOException e) {
                    sock.close();
                    ErrUtil.logErr(e);
                    break;
                }
                
                final String msg =
                    new String(rpkt.getData(), 0, rpkt.getLength());
                
                if (backend == null && msg.contains(BACKEND_UPNP_ID)) {
                    int locIdx = 
                        msg.indexOf(UPNP_LOCATION) + 7 + UPNP_LOCATION.length();
                    backend = msg.substring(locIdx, msg.indexOf(":", locIdx)); //$NON-NLS-1$
                }
                
                else if (msg.contains(FRONTEND_UPNP_ID)) {
                    int locIdx =
                        msg.indexOf(UPNP_LOCATION) + UPNP_LOCATION.length();
                    String url =
                        msg.substring(locIdx, msg.indexOf("\r\n", locIdx)); //$NON-NLS-1$
                    if (frontends.contains(url))
                        continue;
                    frontends.add(url);
                    addFrontend(url);
                }
                
            }
            
        }
    };
    
    private final Runnable cleanDiscoveredFrontends = new Runnable() {
        @Override
        public void run() {
            Context ctx = Globals.appContext;
            ArrayList<String> fes = FrontendDB.getDiscoveredFrontends(ctx);
            FrontendManager feMgr = null;
            for (String fe : fes) {
                try {
                    feMgr = new FrontendManager(
                        fe, FrontendDB.getFrontendAddr(ctx, fe)
                    );
                    feMgr.disconnect();
                } catch (IOException e) {
                    LogUtil.debug(
                        "Frontend " + fe + " isn't reachable now, remove" //$NON-NLS-1$ //$NON-NLS-2$
                    );
                    FrontendDB.delete(ctx, fe);
                }
            }
        }
    };
     
    /**
     * Construct a new UPnPListener
     * @throws SocketException
     */
    public UPnPListener() throws SocketException {
        Thread recvThread = new Thread(recvRunnable, "UPnPListener"); //$NON-NLS-1$
        recvThread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        recvThread.setDaemon(true);
        recvThread.start();
    }
    
    /**
     * Find a master backend via UPnP
     * @param timeout timeout in milliseconds
     * @throws IOException
     */
    public String findMasterBackend(int timeout) throws IOException {
        
        LogUtil.debug("Start UPnP search for master backend"); //$NON-NLS-1$
        sendSearch(BACKEND_UPNP_ID);
        
        final Thread thisThread = Thread.currentThread();
        final Timer timer = new Timer();
        
        timer.schedule(
            new TimerTask() {
                @Override
                public void run() {
                    thisThread.interrupt();
                }
            }, timeout
        );
        
        try {
            while (backend == null) {
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            throw new IOException(Messages.getString("BackendManager.2")); //$NON-NLS-1$
        }
        
        return backend;
            
    }
    
    /**
     * Find frontends via UPnP
     * @throws IOException
     */
    public void findFrontends() throws IOException {
        LogUtil.debug("Start UPnP search for frontends"); //$NON-NLS-1$
        sendSearch(FRONTEND_UPNP_ID);
        Globals.scheduleOnWorker(cleanDiscoveredFrontends, 1000);
    }
    
    static private void sendSearch(String search) throws IOException {
        
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
    
        sock.setReuseAddress(true);
        sock.bind(isa);
        sock.setBroadcast(true);
        sock.send(pkt);
        sock.close();
     
    }
    
    
    /**
     * Add a frontend to the FrontendDB
     * @param url String containing url of the getDeviceDesc service of a
     *  frontend
     */
    static private void addFrontend(String url) {
        
        final String addr = url.substring(7, url.indexOf(':', 7));
        
        LogUtil.debug("Found frontend at " + addr); //$NON-NLS-1$
        
        if (FrontendDB.hasFrontendWithAddr(Globals.appContext, addr)) return;
        
        final StringBuilder name = new StringBuilder();
        final XMLHandler handler = new XMLHandler("root"); //$NON-NLS-1$
        final Element root  = handler.rootElement();
        final Element fname = root.getChild("device").getChild("friendlyName"); //$NON-NLS-1$ //$NON-NLS-2$

        fname.setTextElementListener(
            new EndTextElementListener() {
                @Override
                public void end(String text) {
                    name.append(text.substring(0, text.indexOf(':')));
                }
            }
        );
        
        final URL xmlurl;
        try {
            xmlurl = new URL(url);
        } catch (MalformedURLException e) {
            ErrUtil.logErr(e);
            return;
        }
        
        try {
            Xml.parse(xmlurl.openStream(), Xml.Encoding.UTF_8, handler);
        } catch (SocketException e) {
            ErrUtil.logErr(e);
            return;
        } catch (SAXException e) {
            ErrUtil.logErr(e);
            return;
        } catch (IOException e) {
            ErrUtil.logErr(e);
            return;
        }
        
        if (name.length() == 0) return;
        
        LogUtil.debug(
            "Frontend named " + name.toString() + " at " + addr + //$NON-NLS-1$ //$NON-NLS-2$
            " is new, adding to the FrontendDB" //$NON-NLS-1$
        );
        
        name.setCharAt(0, Character.toUpperCase(name.charAt(0)));
        FrontendDB.insert(
            Globals.appContext, name.toString(), addr, null, true
        );
        
    }
    
}
