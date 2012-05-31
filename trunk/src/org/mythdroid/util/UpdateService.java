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

package org.mythdroid.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;

import org.apache.http.client.ClientProtocolException;

import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.data.XMLHandler;
import org.mythdroid.data.XMLHandler.Element;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.resource.Messages;
import org.xml.sax.SAXException;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.sax.EndTextElementListener;
import android.util.Xml;

/** An update checker for MythDroid and MDD */
@TargetApi(8)
public class UpdateService extends Service {
    
    /** ACTION intent extra value to check for MythDroid updates */
    final public static int    CHECKMD = 0;
    /** ACTION intent extra value to check for MDD updates */
    final public static int    CHECKMDD = 1;
    /** ACTION intent extra, determines which package to check */
    final public static String ACTION = "ACTION"; //$NON-NLS-1$
    /** ADDR intent extra, value should be address of MDD */
    final public static String ADDR = "ADDR"; //$NON-NLS-1$
    
    final private static int    UPDATEMD = 2, UPDATEMDD = 3;
    final private static String VER = "VERSION",  URL = "URL"; //$NON-NLS-1$ //$NON-NLS-2$
    final private static String 
        urlString = "http://code.google.com/feeds/p/mythdroid/downloads/basic"; //$NON-NLS-1$
    
    final private static String MDACTION  = "org.mythdroid.action.MDUpdate"; //$NON-NLS-1$
    final private static String MDDACTION = "org.mythdroid.action.MDDUpdate"; //$NON-NLS-1$
    
    final private static IntentFilter filter = new IntentFilter();
    static {
        filter.addAction(MDACTION);
        filter.addAction(MDDACTION);
    }
    
    private XMLHandler               handler = null;
    private ArrayList<DownloadEntry> entries = null;
    private DownloadEntry            download = null;
    private Version                  MDVer = null, MDDVer = null;
    private Context                  ctx = this;
    
    private class DownloadEntry {
        
        public String url;
        public String title;
        public DownloadEntry() {}
        
    }
    
    private class Version implements Comparable<Version> {
        
        public int major, minor, inc;
        public Uri url;
        
        public Version(String ver, String url) throws NumberFormatException {
        
            int first = ver.indexOf('.');
            major = Integer.valueOf(ver.substring(0, first));
            int second = ver.indexOf('.', first + 1);
            minor = Integer.valueOf(ver.substring(first + 1, second));
            inc = Integer.valueOf(ver.substring(second + 1));
            
            if (url != null)
                this.url = Uri.parse(url);
            
        }

        @Override
        public int compareTo(Version another) {
            
            if (major > another.major) return 1;
            if (another.major > major) return -1;
            if (minor > another.minor) return 1;
            if (another.minor > minor) return -1;
            if (inc > another.inc)     return 1;
            if (another.inc > inc)     return -1;
            return 0;
            
        }
        
        @Override
        public String toString() {
            return major + "." + minor + "." + inc; //$NON-NLS-1$ //$NON-NLS-2$
        }
        
    }
    
    final private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            
            intent.setClass(ctx, ctx.getClass());
            
            if (intent.getAction().equals(MDACTION)) {
                intent.putExtra(ACTION, UPDATEMD);
                startService(intent);
            }
            else if (intent.getAction().equals(MDDACTION)) {
                intent.putExtra(ACTION, UPDATEMDD);
                startService(intent);
            }
            
        }
    };
    
    @Override
    public void onStart(Intent intent, int startId) {
        handleCommand(intent);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        
        handleCommand(intent);
        return Service.START_NOT_STICKY;

    }
    
    @Override
    public void onCreate() {
        
        registerReceiver(receiver, filter);
        
        handler = new XMLHandler("feed"); //$NON-NLS-1$
        final Element root  = handler.rootElement();
        final Element entry = root.getChild("entry"); //$NON-NLS-1$

        entry.getChild("content").setTextElementListener( //$NON-NLS-1$
            new EndTextElementListener() {
                @Override
                public void end(String text) {
                    int start = text.indexOf("http:"); //$NON-NLS-1$
                    int end = text.indexOf("\">", start); //$NON-NLS-1$
                    download.url = text.substring(start, end);
                    entries.add(download);
                }
            }
        );
        
        entry.getChild("title").setTextElementListener( //$NON-NLS-1$
            new EndTextElementListener() {
                @Override
                public void end(String text) {
                    download = new DownloadEntry();
                    download.title = text;
                    
                }
            }
        );
        
        Globals.runOnWorker(
            new Runnable() {
                @Override
                public void run() {
                    getAvailableVersions();
                }
            }
        );
        
    }
    
    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);
    }
    
    private void handleCommand(final Intent intent) {
        
        switch (intent.getIntExtra(ACTION, CHECKMD)) {
            
            case CHECKMD:  
                Globals.runOnWorker(
                    new Runnable() {
                        @Override
                        public void run() { checkMythDroid(); }
                    }
                );
                break;
                
            case CHECKMDD: 
                Globals.runOnWorker(
                    new Runnable() {
                        @Override
                        public void run() {
                            checkMDD(intent.getStringExtra(ADDR));
                        }
                    }
                );
                break;
                
            case UPDATEMD: 
                Globals.runOnWorker(
                    new Runnable() {
                        @Override
                        public void run() {
                            updateMythDroid(
                                intent.getStringExtra(VER),
                                intent.getStringExtra(URL)
                            );
                        }
                    }
                );
                break;
                
            case UPDATEMDD:
                Globals.runOnWorker(
                    new Runnable() {
                        @Override
                        public void run() {
                            updateMDD(
                                intent.getStringExtra(ADDR),
                                intent.getStringExtra(VER),
                                intent.getStringExtra(URL)
                            );
                        }
                    }
                );
                break;
        }
        
    }
    
    private void checkMythDroid() {
        
        if (MDVer == null) return;
        
        Version runningVer;
        try {
            runningVer = new Version(
                getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName,
                null
            );
        } catch (NameNotFoundException e) { return; }

        if (runningVer.compareTo(MDVer) >= 0) {
            LogUtil.debug("Already running latest version of MythDroid"); //$NON-NLS-1$
            return;
        }
        
        LogUtil.debug(
            "Version " + MDVer.toString() + //$NON-NLS-1$
            " is available (current version is " + runningVer.toString() + ")" //$NON-NLS-1$ //$NON-NLS-2$
        );
        
        final NotificationManager nm = (NotificationManager)getSystemService(
            Context.NOTIFICATION_SERVICE
        );

        final Notification notification = new Notification(
            R.drawable.logo, 
            Messages.getString("UpdateService.0") + "MythDroid" + //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("UpdateService.1"),  //$NON-NLS-1$
            System.currentTimeMillis()
        );
        
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        
        final Intent intent = new Intent(MDACTION);
        intent.putExtra(VER, MDVer.toString());
        intent.putExtra(URL, MDVer.url.toString());

        notification.setLatestEventInfo(
            getApplicationContext(), 
            "MythDroid" + Messages.getString("UpdateService.2"),  //$NON-NLS-1$ //$NON-NLS-2$
            MDVer.toString() + Messages.getString("UpdateService.1") + //$NON-NLS-1$ 
                Messages.getString("UpdateService.3"), //$NON-NLS-1$
            PendingIntent.getBroadcast(this, 0, intent, 0)
        );

        nm.notify(CHECKMD, notification);
        
    }
    
    private void updateMythDroid(String ver, String url) {
        
        LogUtil.debug("Fetching APK from " + url); //$NON-NLS-1$
        
        HttpFetcher fetcher = null;
        try {
            fetcher = new HttpFetcher(url);
        } catch (ClientProtocolException e) {
            ErrUtil.logErr(e);
            notify(
                "MythDroid" + Messages.getString("UpdateService.2"), //$NON-NLS-1$ //$NON-NLS-2$
                e.getMessage()
            );
            return;
        } catch (IOException e) {
            ErrUtil.logErr(e);
            notify(
                "MythDroid" + Messages.getString("UpdateService.2"), //$NON-NLS-1$ //$NON-NLS-2$
                e.getMessage()
            );
            return;
        }
        
        final File storage = Environment.getExternalStorageDirectory();
        final File outputFile = new File(
            storage.getAbsolutePath() + '/' + "Download", //$NON-NLS-1$
            "MythDroid-" + ver + ".apk" //$NON-NLS-1$ //$NON-NLS-2$
        );
        
        LogUtil.debug("Saving to " + outputFile.getAbsolutePath()); //$NON-NLS-1$
        
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(outputFile);
        } catch (FileNotFoundException e) {
            ErrUtil.logErr("SDCard is unavailable"); //$NON-NLS-1$
            notify(
                "MythDroid" + Messages.getString("UpdateService.2"), //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("UpdateService.4") //$NON-NLS-1$
            );
            return;
        }

        try {
            fetcher.writeTo(outputStream);
        } catch (IOException e) {
            ErrUtil.logErr(e);
            notify(
                "MythDroid" + Messages.getString("UpdateService.2"), //$NON-NLS-1$ //$NON-NLS-2$
                e.getMessage()
            );
            return;
        }
        
        LogUtil.debug("Download successful, installing..."); //$NON-NLS-1$
        
        final Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(
            Uri.fromFile(outputFile), "application/vnd.android.package-archive" //$NON-NLS-1$
        );
        installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(installIntent);
        
    }

    private void checkMDD(String addr) {
        
        if (MDDVer == null) return;
        
        Version currentVer;
        try {
            currentVer = new Version(MDDManager.getVersion(addr), null);
        } catch (NumberFormatException e) {
            ErrUtil.logErr(e);
            return;
        } catch (IOException e) {
            ErrUtil.logErr(e);
            return;
        }
        
        if (currentVer.compareTo(MDDVer) >= 0) {
            LogUtil.debug(
                "MDD on " + addr + " is already the latest version" //$NON-NLS-1$ //$NON-NLS-2$
            );
            return;
        }
        
        LogUtil.debug(
            "MDD ver " + MDDVer.toString() + " is available (current ver on " + //$NON-NLS-1$ //$NON-NLS-2$
            addr + " is " + currentVer.toString() + ")" //$NON-NLS-1$ //$NON-NLS-2$
        );
        
        if (MDVer != null && MDVer.compareTo(MDDVer) != 0) {
            LogUtil.warn(
                "Version mismatch:" + " MythDroid is " + MDVer.toString() + //$NON-NLS-1$ //$NON-NLS-2$
                ", MDD on " + addr + " is " + MDDVer.toString() //$NON-NLS-1$ //$NON-NLS-2$ 
            );
        }
        
        final NotificationManager nm = (NotificationManager)getSystemService(
            Context.NOTIFICATION_SERVICE
        );

        final Notification notification = new Notification(
            R.drawable.logo, 
            Messages.getString("UpdateService.0") + "MDD" + //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("UpdateService.1"),  //$NON-NLS-1$
            System.currentTimeMillis()
        );
        
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        
        final Intent intent = new Intent(MDDACTION);
        intent.putExtra(ADDR, addr);
        intent.putExtra(VER, MDDVer.toString());
        intent.putExtra(URL, MDDVer.url.toString());
        
        notification.setLatestEventInfo(
            getApplicationContext(), 
            "MDD" + Messages.getString("UpdateService.2"),  //$NON-NLS-1$ //$NON-NLS-2$
            MDDVer.toString() + Messages.getString("UpdateService.1") + //$NON-NLS-1$ 
                Messages.getString("UpdateService.3"), //$NON-NLS-1$
            PendingIntent.getBroadcast(this, 0, intent, 0)
        );

        nm.notify(CHECKMDD, notification);
        
    }
    
    private void updateMDD(String addr, String ver, String url) {
        
        String file;
        try {
            file = MDDManager.downloadUpdate(addr, ver, url);
        } catch (IOException e) {
            ErrUtil.logErr(e);
            notify(
                "MDD" + Messages.getString("UpdateService.5") + addr, //$NON-NLS-1$ //$NON-NLS-2$
                e.getMessage()
            );
            return;
        }
        
        if (file.equals("FAILED")) { //$NON-NLS-1$
            notify(
                "MDD" + Messages.getString("UpdateService.5") + addr, //$NON-NLS-1$ //$NON-NLS-2$
                Messages.getString("UpdateService.7") //$NON-NLS-1$
            );
        }
        
        notify(
            "MDD" + Messages.getString("UpdateService.5") + addr, //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("UpdateService.6") + file //$NON-NLS-1$
        );
        
    }
    
    private void notify(String title, String message) {
        
        NotificationManager nm = (NotificationManager)getSystemService(
            Context.NOTIFICATION_SERVICE
        );

        Notification notification = new Notification(
            R.drawable.logo, title, System.currentTimeMillis()
        );
        
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        
        notification.setLatestEventInfo(
            getApplicationContext(), title,  message,
            PendingIntent.getBroadcast(Globals.appContext, 0, null, 0)
        );

        nm.notify(-1, notification);
    }
    
    private void getAvailableVersions() {
        
        final URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            ErrUtil.logErr(e);
            return;
        }
        
        entries = new ArrayList<DownloadEntry>(2);

        try {
            Xml.parse(url.openStream(), Xml.Encoding.UTF_8, handler);
        } catch (SocketException e) {
            ErrUtil.logErr(e);
        } catch (SAXException e) {
            ErrUtil.logErr(e);
        } catch (IOException e) {
            ErrUtil.logErr(e);
        }
        
        if (entries.size() != 2) return;
        
        for (int i = 0; i < 2; i++) {
            
            String version;
            final DownloadEntry entry = entries.get(i);
            int start = -1;
            if ((start = entry.title.indexOf("MythDroid")) != -1) { //$NON-NLS-1$
                start += 10;
                int end = entry.title.indexOf(".apk"); //$NON-NLS-1$
                if (end == -1) continue;
                version = entry.title.substring(start, end);
                try {
                    MDVer = new Version(version, entry.url);
                } catch (NumberFormatException e) {
                    MDVer = null;
                }
            }
            else if ((start = entry.title.indexOf("mdd")) != -1) { //$NON-NLS-1$
                start += 4;
                int end = entry.title.indexOf(".tgz"); //$NON-NLS-1$
                if (end == -1) continue;
                version = entry.title.substring(start, end);
                try {
                    MDDVer = new Version(version, entry.url);
                } catch (NumberFormatException e) {
                    MDDVer = null;
                }
            }
            else
                ErrUtil.logErr("Unexpected title: " + entry.title); //$NON-NLS-1$
            
        }
        
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
}
