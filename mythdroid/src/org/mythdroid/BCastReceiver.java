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

package org.mythdroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.gsm.SmsMessage;

public class BCastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {

        final SharedPreferences prefs =
            PreferenceManager.getDefaultSharedPreferences(ctx);
        final boolean showCalls = prefs.getBoolean("osdCalls", true);
        final boolean showSMS = prefs.getBoolean("osdSMS", true);
        final String action = intent.getAction();
        String number, name = null;
        
        
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
            
                try {
                    OSDMessage.Caller(name, number);
                } catch (Exception e) {}       
            }
            
        }
        
        else if (
            showSMS &&
            action.equals("android.provider.Telephony.SMS_RECEIVED")
        ) {
            Bundle bundle = intent.getExtras();
            Object[] pdus = (Object[])bundle.get("pdus");
            
            try {
                for (int i = 0; i <pdus.length; i++){
                    SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    String m = "SMS from " + msg.getDisplayOriginatingAddress() +
                        ": " + msg.getDisplayMessageBody();
                    OSDMessage.Scroller(m, m.length() / 9 + 8);
                }
            } catch (Exception e){}
                
        }
    
    }

}
