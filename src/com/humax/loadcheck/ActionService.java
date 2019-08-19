package com.humax.loadcheck;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RecoverySystem;
import android.util.Log;
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
    private String mActionID;
    private String mSerialNumber;

    private String gAction;
    private String gActionID;
    private String gActionStatus;

    private static String Reboot = "reboot";
    private static String Reset = "reset";

    private String mActionStatus = null;
// ActionStatus is START or COMPLETE

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate()");

        mContinue = false;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (mContinue) {
                    send_action_status();
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
        Log.i(TAG, "getStringExtra mServerAddress = " + mServerAddress);
        if (mServerAddress == null) {
            // service recovered with null intent
            if (isServiceActive()) {
                mServerAddress = loadServerAddress();
                Log.i(TAG, "loadServerAddress mServerAddress = " + mServerAddress);
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
            Log.i(TAG, "saveServiceStatus mServerAddress = " + mServerAddress);
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
                Log.i(TAG, "mAction : " + mAction);
                mActionID = json.getString("actionid");
                Log.i(TAG, "mActionID : " + mActionID);
                saveActionID(mActionID);

                conn.disconnect();
            }
            else
            {
                conn.disconnect();
                Log.e(TAG, "HTTP Not Ok !!!");
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Exception connection");
            return;
        }

        Log.i(TAG, "server status start !! ");
//server status start
        url = "http://" + mServerAddress +"/api/log/RemoteControlStatus?actionid="+mActionID+"&status=start";
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
            Log.e(TAG, "Exception server status start");
            return;
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.i(TAG, "Thread interrupted");
        }

        if(mAction.equals(Reboot)) {
            Log.i(TAG, "reboot start !! ");
            mActionStatus = "COMPLETE";
            saveActionStatus(mActionStatus);
            onReboot();
            Log.i(TAG, "reboot complete. It will be not printed !! ");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Log.i(TAG, "Thread interrupted");
            }
        }

        if(mAction.equals(Reset)) {
            Log.i(TAG, "reset start !! ");
            mActionStatus = "COMPLETE";
            saveActionStatus(mActionStatus);
            onReset();

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Log.i(TAG, "Thread interrupted");
            }
        }

    }

    private void send_action_status(){
        gActionStatus = loadActionStatus();
        Log.i(TAG, "gActionStatus = " + gActionStatus);
        gActionID = loadActionID();
        Log.i(TAG, "gActionID = " + gActionID);
        gAction = loadAction();
        Log.i(TAG, "gAction = " + gAction);
        if(gActionStatus.equals("COMPLETE")) {

            String url = "http://" + mServerAddress + "/api/log/RemoteControlStatus?actionid=" + gActionID + "&status=complete";
            Log.i(TAG, "send_action_status() URL :" + url);
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
                Log.e(TAG, "Exception server status complete");
                return;
            }
        }

        mActionStatus = "START";
        saveActionStatus(mActionStatus);
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

    private void saveActionStatus(String status) {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(getString(R.string.saved_action_server_status), status);
        editor.commit();
    }

    private String loadActionStatus() {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getString(getString(R.string.saved_action_server_status), null);
    }

    private void saveActionID(String actionid) {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(getString(R.string.saved_action_server_actionid), actionid);
        editor.commit();
    }

    private String loadActionID() {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getString(getString(R.string.saved_action_server_actionid), null);
    }

    private void saveAction(String action) {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(getString(R.string.saved_action_server_action), action);
        editor.commit();
    }

    private String loadAction() {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getString(getString(R.string.saved_action_server_action), null);
    }

}
