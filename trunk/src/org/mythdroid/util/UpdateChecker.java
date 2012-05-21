package org.mythdroid.util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.mythdroid.R;
import org.mythdroid.data.XMLHandler;
import org.mythdroid.data.XMLHandler.Element;
import org.mythdroid.resource.Messages;
import org.xml.sax.SAXException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.sax.EndTextElementListener;
import android.util.Xml;

/** An update checker for MythDroid and MDD */
public class UpdateChecker {
    
    private static String 
        urlString = "http://code.google.com/feeds/p/mythdroid/downloads/basic"; //$NON-NLS-1$
    
    private XMLHandler handler = null;
    private ArrayList<DownloadEntry> entries = null;
    private DownloadEntry download = null;
    
    private class DownloadEntry {
        
        public String url;
        public String title;
        public DownloadEntry() {}
        
    }
    
    private class Version implements Comparable<Version> {
        
        public int major, minor, inc;
        public Uri url;
        
        public Version(String ver, String url) {
        
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
    
    /** Checks whether an updated version of MythDroid is available */
    public UpdateChecker() {
        
        handler = new XMLHandler("feed"); //$NON-NLS-1$
        Element root = handler.rootElement();
        Element entry = root.getChild("entry"); //$NON-NLS-1$

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

    }
    
    /** Perform the check */
    public void check(Context ctx) {
        
        @SuppressWarnings("unused")
        Version MDVer = null, MDDVer = null;
        
        URL url;
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
            DownloadEntry entry = entries.get(i);
            int start = -1;
            if ((start = entry.title.indexOf("MythDroid")) != -1) { //$NON-NLS-1$
                start += 10;
                int end = entry.title.indexOf(".apk"); //$NON-NLS-1$
                version = entry.title.substring(start, end);
                MDVer = new Version(version, entry.url);
            }
            else if ((start = entry.title.indexOf("mdd")) != -1) { //$NON-NLS-1$
                start += 4;
                int end = entry.title.indexOf(".tgz"); //$NON-NLS-1$
                version = entry.title.substring(start, end);
                MDDVer = new Version(version, entry.url);
            }
            else
                ErrUtil.logErr(
                    "UpdateChecker - unexpected title: " + entry.title //$NON-NLS-1$
                );
            
        }
        
        if (MDVer == null) return;
        
        Version runningVer;
        try {
            runningVer = new Version(
                ctx.getPackageManager().getPackageInfo(
                    ctx.getPackageName(), 0
                ).versionName, null
            );
        } catch (NameNotFoundException e) { return; }

        if (runningVer.compareTo(MDVer) >= 0) {
            LogUtil.debug("UpdateChecker: Already running latest version"); //$NON-NLS-1$
            return;
        }
        
        LogUtil.debug(
            "UpdateChecker: Version " + MDVer.toString() + //$NON-NLS-1$
            " is available (current version is " + runningVer.toString() + ")" //$NON-NLS-1$ //$NON-NLS-2$
        );
        
        NotificationManager nm = (NotificationManager)ctx.getSystemService(
            Context.NOTIFICATION_SERVICE
        );

        Notification notification = new Notification(
            R.drawable.logo, Messages.getString("UpdateChecker.0"), //$NON-NLS-1$
            System.currentTimeMillis()
        );
        
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        
        Intent intent = new Intent(Intent.ACTION_VIEW, MDVer.url);
        
        final PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> activityList = pm.queryIntentActivities(intent, 0);
        for (int i = 0; i < activityList.size(); i++) {
            ResolveInfo app = activityList.get(i);
            if (app.activityInfo.name.contains("com.android.browser")) //$NON-NLS-1$
              intent.setClassName(
                  app.activityInfo.packageName, app.activityInfo.name
              );
        }

        notification.setLatestEventInfo(
            ctx.getApplicationContext(), Messages.getString("UpdateChecker.1"),  //$NON-NLS-1$
            MDVer.toString() + Messages.getString("UpdateChecker.2"), //$NON-NLS-1$
            PendingIntent.getActivity(ctx, 0, intent, 0)
        );

        nm.notify(1, notification);
        
    }
    
}
