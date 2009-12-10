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
