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

package org.mythdroid.receivers;

import org.mythdroid.frontend.OSDMessage;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.PhoneUtil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;

/**
 * A BroadcastReceiver that looks for incoming calls / SMS
 * and displays them on the MythTV OSD
 */
public class PhoneStateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {

        final SharedPreferences prefs =
            PreferenceManager.getDefaultSharedPreferences(ctx);
        final boolean showCalls = prefs.getBoolean("osdCalls",  true); //$NON-NLS-1$
        final boolean showSMS   = prefs.getBoolean("osdSMS",    true); //$NON-NLS-1$
        final boolean scrollSMS = prefs.getBoolean("scrollSMS", true); //$NON-NLS-1$
        final boolean altOSD    = prefs.getBoolean("altOSD",    false); //$NON-NLS-1$
        final String action     = intent.getAction();
        String number, name     = null;


        if (
            showCalls &&
            action.equals(
                android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED
            ) &&
            intent.getStringExtra(
                android.telephony.TelephonyManager.EXTRA_STATE
            ).equals(android.telephony.TelephonyManager.EXTRA_STATE_RINGING)
        ) {
            number = intent.getStringExtra(
                android.telephony.TelephonyManager.EXTRA_INCOMING_NUMBER
            );

            if (number != null) {
                name = PhoneUtil.nameFromNumber(ctx, number);

                if (altOSD)
                    OSDMessage.XOSD(
                        ctx, 
                        Messages.getString("PhoneStateReceiver.0") +  //$NON-NLS-1$
                            name + " (" + number + ")"   //$NON-NLS-1$//$NON-NLS-2$
                    );
                else
                    try {
                        OSDMessage.Caller(name, number);
                    } catch (Exception e) {}
            }

        }

        else if (
            showSMS &&
            action.equals("android.provider.Telephony.SMS_RECEIVED") //$NON-NLS-1$
        ) {
            final Bundle bundle = intent.getExtras();
            final Object[] pdus = (Object[])bundle.get("pdus"); //$NON-NLS-1$
            
            SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdus[0]);
            final String from = msg.getDisplayOriginatingAddress();

            String m = Messages.getString("BCastReceiver.5") + from + ": " + //$NON-NLS-1$ //$NON-NLS-2$
                       msg.getDisplayMessageBody();
        
            if (pdus.length > 1) {
                for (int i = 1; i < pdus.length; i++){
                    msg = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    String f = msg.getDisplayOriginatingAddress();
                    if (f == null || !f.equals(from))
                        break;
                    m += msg.getDisplayMessageBody();
                }
            }
            
            if (altOSD)
                OSDMessage.XOSD(ctx, m);
            else
                try {
                    if (scrollSMS)
                        OSDMessage.Scroller(m, m.length() / 9 + 8);
                    else
                        OSDMessage.Alert(m);
                } catch (Exception e){}
        }

    }

}

