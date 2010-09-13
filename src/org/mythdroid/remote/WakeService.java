package org.mythdroid.remote;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;

public class WakeService extends Service implements SensorEventListener {

    final private static String tag   = "MythDroid";  //$NON-NLS-1$
    final private static int wakeTime = 10000; // ms
    private SensorManager sensorMgr;
    private Sensor sensor;

    public BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        
        @Override
        public void onReceive(Context context, Intent intent) {
            
            sensorMgr.unregisterListener(WakeService.this);
           
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF))  
                sensorMgr.registerListener(
                    WakeService.this, sensor, SensorManager.SENSOR_DELAY_NORMAL
                );
            
        }
        
    };

    @Override
    public void onCreate() {
        
        super.onCreate();
        
        sensorMgr = (SensorManager)getApplicationContext()
                        .getSystemService(Context.SENSOR_SERVICE);
        
        sensor = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        sensorMgr.registerListener(
            this, sensor, SensorManager.SENSOR_DELAY_NORMAL
        );

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenStateReceiver, filter);
    }


    @Override
    public void onDestroy() {
        
        super.onDestroy();
        unregisterReceiver(screenStateReceiver);
        sensorMgr.unregisterListener(this);
        
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        
        if (event.values[2] == 0) return;
        
        if (Math.abs(event.values[0]) > 2 ||
            Math.abs(event.values[1]) > 2 ||
            event.values[2] > 12 || event.values[2] < 7) 
            wakeUp();
                    
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    
    private void wakeUp() {
        
        KeyguardManager km = 
            (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        
        pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
            PowerManager.ACQUIRE_CAUSES_WAKEUP, 
            tag
        ).acquire(wakeTime);
        
        km.newKeyguardLock(tag).disableKeyguard();
        
    }

}
