package org.mythdroid;

import org.acra.*;
import org.acra.annotation.*;
import static org.acra.ReportField.*;

import android.app.Application;

/**
 * ACRA error reporting class
 */
@ReportsCrashes(
    
    formKey = "dElnQWVCdnF0U3JIZFMxc2lPZ05OM1E6MQ",
    customReportContent = { 
        REPORT_ID, APP_VERSION_CODE, PACKAGE_NAME, PHONE_MODEL, BRAND,
        ANDROID_VERSION, STACK_TRACE, INITIAL_CONFIGURATION,
        CRASH_CONFIGURATION, DISPLAY, LOGCAT, DEVICE_FEATURES, ENVIRONMENT,
        SHARED_PREFERENCES, SETTINGS_SYSTEM, SETTINGS_SECURE
    }, 
    mode = ReportingInteractionMode.NOTIFICATION,    
    resNotifTickerText = R.string.crash_notif_ticker_text,
    resNotifTitle = R.string.crash_notif_title,
    resNotifText = R.string.crash_notif_text,
    resNotifIcon = android.R.drawable.stat_notify_error,
    resDialogText = R.string.crash_dialog_text,
    resDialogIcon = android.R.drawable.ic_dialog_info,
    logcatArguments = { 
        "-s", "-t", "100", "MythDroid", "ConnMgr", "Guide", "WakeService", 
        "BackendManager", "FrontendLocation", "WakeOnLan", 
        "ConnectivityReceiver", "ImageCache", "ImageDiskCache"
    }
)
public class MythDroid extends Application {

    @SuppressWarnings("unused")
    private Globals globalsInstance = null;
    
    @Override 
    public void onCreate() {
        ACRA.init(this);
        globalsInstance = Globals.getInstance(this); 
        super.onCreate();
    }
    
}
