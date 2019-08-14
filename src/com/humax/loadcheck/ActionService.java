package com.humax.loadcheck;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RecoverySystem;
import android.util.Log;
import android.view.View;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ActionService extends Service {
    private static final String TAG = "ActionService";
    private Thread mThread;
    private boolean mContinue;
    private Notification mNotification;
    private String mServerAddress;
    private String mAction;
    private String mSerialNumber;
    private static String Reboot = "reboot";
    private static String Reset = "reset";


    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate()");

        mContinue = false;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (mContinue) {
                    send();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Log.i(TAG, "Thread interrupted");
                    }
                }
            }
        });


        {
            mSerialNumber = getProp("ro.serialno");
            Log.i(TAG, "ro.serialno " + mSerialNumber);
        }

        createNotificationChannel();
        mNotification = new Notification.Builder(this, CHANNEL_ID)
                .build();
    }

    static String CHANNEL_ID = "ActionService";
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Action Service",
                NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private void saveServiceStatus(boolean active, String address) {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(getString(R.string.saved_action_service_active), active);
        editor.putString(getString(R.string.saved_action_server_address), address);
        editor.commit();
    }

    private boolean isServiceActive() {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getBoolean(getString(R.string.saved_action_service_active),
                false);
    }

    private String loadServerAddress() {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getString(getString(R.string.saved_action_server_address),
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

    private void send() {
        //String url = "http://" + mServerAddress + "/api/log/RemoteControl?deviceid=0123456789";
        String url = "http://" + mServerAddress + "/api/log/RemoteControl?deviceid=0x"+mSerialNumber;
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
                mAction = json.getString("action");
                Log.i(TAG, "action : " + mAction);

                /*
                if(mAction.equals(Reboot)) {
                    Log.i(TAG, "reboot start !! ");
                    url = "http://" + mServerAddress +"/api/log/RemoteControlStatus?actionid=a01367fc-7233-415a-bb8f-5862e4d63903&status=start";
                    conn.getOutputStream();
                    conn.
                    Log.i(TAG, "send() URL :" + url);
                    Thread.sleep(1000);
                    //onReboot();
                }

                if(mAction.equals(Reset)) {
                    Log.i(TAG, "reset start !! ");
                    //onReset();
                }
               */


            }

            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }


        url = "http://" + mServerAddress +"/api/log/RemoteControlStatus?actionid=a01367fc-7233-415a-bb8f-5862e4d63903&status=start";
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
            }

            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.i(TAG, "Thread interrupted");
        }

//server status complete
        url = "http://" + mServerAddress + "/api/log/RemoteControlStatus?actionid=a01367fc-7233-415a-bb8f-5862e4d63903&status=complete";
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
            }

            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }

//server status reset
        url = "http://" + mServerAddress + "/api/log/RemoteControlReset?actionid=a01367fc-7233-415a-bb8f-5862e4d63903";
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
            }

            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }


        if(mAction.equals(Reboot)) {
            Log.i(TAG, "reboot start !! ");
            //onReboot();
        }

        if(mAction.equals(Reset)) {
            Log.i(TAG, "reset start !! ");
            onReset();
        }




    }


    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }


    private String getProp(String property) {
        try {
            //String buf = "/system/bin/getprop ro.serialno" + property;
            final Process ps = Runtime.getRuntime().exec("/system/bin/getprop "+property);
            final InputStream is = ps.getInputStream();
            final BufferedReader br = new BufferedReader(new InputStreamReader(is));
            final String strParam = br.readLine();

            return strParam;

        } catch (Exception ex) { } // for now eat exceptions
        return "";
    }


    private void onReboot() {
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        pm.reboot(null);
    }

    private void onReset() {
        try {
            RecoverySystem.rebootWipeUserData(getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
