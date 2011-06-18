package org.mythdroid;

import org.acra.CrashReportingApplication;

import android.os.Bundle;

/**
 * ACRA error reporting class
 */
public class MythDroid extends CrashReportingApplication {

    @Override
    public String getFormId() {
        boolean m = getPackageName().startsWith("org.mythdroid"); //$NON-NLS-1$
        return m ? "dFVIWUVVSjBubHlzSUQyeHc4dEpIWHc6MQ" : null;   //$NON-NLS-1$
    }

    @Override
    public Bundle getCrashResources() {
        boolean m = getPackageName().startsWith("org.mythdroid"); //$NON-NLS-1$
        Bundle result = new Bundle();
        result.putInt(RES_NOTIF_TICKER_TEXT, R.string.crash_notif_ticker_text);
        result.putInt(RES_NOTIF_TITLE, R.string.crash_notif_title);
        result.putInt(RES_NOTIF_TEXT, R.string.crash_notif_text);
        result.putInt(RES_NOTIF_ICON, android.R.drawable.stat_notify_error);
        result.putInt(RES_DIALOG_TEXT, m ? R.string.crash_dialog_text : R.string.mcrash_dialog_text);
        result.putInt(RES_DIALOG_ICON, android.R.drawable.ic_dialog_info);
        return result;
    }

}
