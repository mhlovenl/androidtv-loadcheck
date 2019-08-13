package com.humax.loadcheck;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.TrafficStats;
import android.os.CpuUsageInfo;
import android.os.HardwarePropertiesManager;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class CheckService extends Service {
    private static final String TAG = "CheckService";
    private Thread mThread;
    private boolean mContinue;
    private Notification mNotification;
    private String mServerAddress;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate()");

        mContinue = false;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (mContinue) {
                    checkLoad();
                    send();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Log.i(TAG, "Thread interrupted");
                    }
                }
            }
        });

        createNotificationChannel();
        mNotification = new Notification.Builder(this, CHANNEL_ID)
                .build();
    }

    String CHANNEL_ID = "LoadCheck";
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "System Load Checker",
                NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private void saveServiceStatus(boolean active, String address) {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(getString(R.string.saved_service_active), active);
        editor.putString(getString(R.string.saved_server_address), address);
        editor.commit();
    }

    private boolean isServiceActive() {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getBoolean(getString(R.string.saved_service_active),
                false);
    }

    private String loadServerAddress() {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getString(getString(R.string.saved_server_address),
                    getString(R.string.default_server));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand()");

        startForeground(1, mNotification);

        mServerAddress = intent.getStringExtra("serverAddress");
        if (mServerAddress == null) {
            // service recovered with null intent
            if (isServiceActive()) {
                mServerAddress = loadServerAddress();
            } else {
                stopForeground(0);
                return START_NOT_STICKY;
            }
        }

        if (!mContinue) {
            saveServiceStatus(true, mServerAddress);
            mContinue = true;
            mThread.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(0);
        mContinue = false;
        try {
            saveServiceStatus(false, mServerAddress);
            mThread.interrupt();
            mThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "onDestroy()");
    }


    private long total[] = {0, 0, 0, 0};
    private long active[] = {0, 0, 0, 0};

    private void checkHwProp() {
        HardwarePropertiesManager hm = (HardwarePropertiesManager) getSystemService(
                Context.HARDWARE_PROPERTIES_SERVICE);

        CpuUsageInfo[] infos = hm.getCpuUsages();
        for(int i = 0; i < infos.length ; i++) {
            long t = infos[i].getTotal() - total[i];
            long a = infos[i].getActive() - active[i];
            Log.i(TAG, "CpuUsage[" + i + "] = " + ((float)a)/t);
            total[i]= infos[i].getTotal();
            active[i]= infos[i].getActive();
        }

        float[] temps = hm.getDeviceTemperatures(
                HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                HardwarePropertiesManager.TEMPERATURE_CURRENT);
        for (int i = 0; i < temps.length; i++) {
            Log.i(TAG, "CpuTemp[" + i + "] = " + temps[i]);
        }
    }

    long totalRx = 0;
    long totalTx = 0;

    private void checkLoad() {
        //checkHwProp();

        Log.i(TAG, "RxBytes : " + (TrafficStats.getTotalRxBytes() - totalRx));
        totalRx = TrafficStats.getTotalRxBytes();

        Log.i(TAG, "TxBytes : " + (TrafficStats.getTotalTxBytes() - totalTx));
        totalTx = TrafficStats.getTotalTxBytes();

        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        am.getMemoryInfo(mi);
        Log.i(TAG, "MemoryUsage : " + (float)(mi.totalMem - mi.availMem)/mi.totalMem);
    }


    private void send() {
        String url = "http://" + mServerAddress + "/api/log/RemoteControl?deviceid=0123456789";
        Log.i(TAG, "send() URL :" + url);
        try {
            HttpURLConnection conn = (HttpURLConnection) (new URL(url).openConnection());
            conn.setConnectTimeout(1000);
            int resp = conn.getResponseCode();
            Log.i(TAG, "Response code : " + resp);

            if (resp == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        conn.getInputStream()));
                String content = in.readLine();
                Log.i(TAG, "Content : " + content);

                JSONObject json = new JSONObject(content);
                Log.i(TAG, "actionid : " + json.getString("actionid"));
            }

            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
