package org.mythdroid.receivers;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.mythdroid.ConnMgr;
import org.mythdroid.activities.MythDroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiManager;
import android.util.Log;

public class ConnectivityReceiver extends BroadcastReceiver {

    static private boolean connected = false;
    static private int netType = -1;
    
    static final private ConnectivityManager cm = 
        (ConnectivityManager)MythDroid.appContext.getSystemService(
            Context.CONNECTIVITY_SERVICE
        );
    
    static final private WifiManager wm = 
        (WifiManager)MythDroid.appContext.getSystemService(
            Context.WIFI_SERVICE
        );
    
    public ConnectivityReceiver() {
        
        final NetworkInfo info = cm.getActiveNetworkInfo();
        
        if (info != null) {
            connected = (info.getState() == NetworkInfo.State.CONNECTED);
            netType = info.getType();
        }
        
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        MythDroid.appContext.registerReceiver(this, filter);
        
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        
        if (!intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION))
            return;
        
        NetworkInfo info = (NetworkInfo)intent.getExtras().get(
            ConnectivityManager.EXTRA_NETWORK_INFO
        );
        
        int type = info.getType();
        State state = info.getState();
        
        if (MythDroid.debug)
            Log.d(
                "ConnectivityReceiver",   //$NON-NLS-1$
                "wasConnected " + connected + " type " + info.getTypeName() + //$NON-NLS-1$ //$NON-NLS-2$
                " state " + state //$NON-NLS-1$
            );
        
        if (
            connected &&
            netType == ConnectivityManager.TYPE_WIFI &&
            (
                type == ConnectivityManager.TYPE_MOBILE ||
                (
                    type == ConnectivityManager.TYPE_WIFI &&
                    state == State.CONNECTED
                )
             )
        )
            return;

        if (state == State.DISCONNECTED) {
           
            try {
                ConnMgr.disconnectAll();
            } catch (IOException e) {}
        
            connected = false;
            return;
        }
            
        if (state != State.CONNECTED)
            return;
        
        netType = type;
        connected = true;
            
        try {
            ConnMgr.reconnectAll();
        } catch (IOException e) {}
              
    }
    
    public static int networkType() {
        return netType;
    }
    
    public static void waitForWifi(int timeout) {
        
        NetworkInfo winfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        
        int wifiState = wm.getWifiState();
            
        if (
            wifiState == WifiManager.WIFI_STATE_ENABLED ||
            wifiState == WifiManager.WIFI_STATE_ENABLING
        ) {
            
            final Thread thisThread = Thread.currentThread();
            
            Timer timer = new Timer();
            timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        thisThread.interrupt();
                    }
                }, timeout
            );
            
            while (
                wifiState == WifiManager.WIFI_STATE_ENABLING     ||
                winfo.getState() == NetworkInfo.State.CONNECTING
            ) {
                
                if (MythDroid.debug)
                    Log.d(
                        "ConnectivityReceiver", "Waiting for WiFi link" //$NON-NLS-1$ //$NON-NLS-2$
                    );
                
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) { return; }
                
                winfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                wifiState = wm.getWifiState();

            }
            
            while (netType != ConnectivityManager.TYPE_WIFI || connected == false) {
                
                if (MythDroid.debug)
                    Log.d(
                        "ConnectivityReceiver", "Waiting for WiFi connection" //$NON-NLS-1$ //$NON-NLS-2$
                    );
                
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) { return; }
                
            }
            
        }
        
    }

}
