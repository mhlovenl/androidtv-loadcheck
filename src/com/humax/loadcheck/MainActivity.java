package com.humax.loadcheck;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.TrafficStats;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CpuUsageInfo;
import android.os.HardwarePropertiesManager;
import android.os.PowerManager;
import android.os.RecoverySystem;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        loadEditText();
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

    public void onCheckClicked(View v) {
        Log.i(TAG, "onCheckClicked()");

        checkHwProp();

        Log.i(TAG, "RxBytes : " + (TrafficStats.getTotalRxBytes() - totalRx));
        totalRx = TrafficStats.getTotalRxBytes();

        Log.i(TAG, "TxBytes : " + (TrafficStats.getTotalTxBytes() - totalTx));
        totalTx = TrafficStats.getTotalTxBytes();

        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        am.getMemoryInfo(mi);
        Log.i(TAG, "MemoryUsage : " + (float)(mi.totalMem - mi.availMem)/mi.totalMem);
    }

    private String getServerAddress() {
        EditText addressInput = findViewById(R.id.address);
        String address = addressInput.getText().toString();
        saveEditText(address);
        if (address.equals("")) {
            return getResources().getString(R.string.default_server);
        } else {
            return address;
        }
    }

    private void saveEditText(String address) {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(getString(R.string.saved_edittext), address);
        editor.commit();
    }

    private void loadEditText() {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        String address = pref.getString(getString(R.string.saved_edittext), "");
        EditText addressInput = findViewById(R.id.address);
        addressInput.setText(address);
    }

    public void onSendClicked(View v) {
        final String address = getServerAddress();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                String url = "http://" + address + "/api/log/RemoteControl?deviceid=0123456789";
                Log.i(TAG, "onSendClicked() URL :" + url);
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
                return null;
            }
        }.execute();
    }

    public void onRebootClicked(View v) {
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        pm.reboot(null);
    }

    public void onResetClicked(View v) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    RecoverySystem.rebootWipeUserData(getApplicationContext());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
    }

    public void onStartClicked(View v) {
        Intent intent = new Intent(this, CheckService.class);
        intent.putExtra("serverAddress", getServerAddress());
        startForegroundService(intent);

        Intent intent_app = new Intent(this, CheckApp.class);
        intent_app.putExtra("serverAddress", getServerAddress());
        startForegroundService(intent_app);
    }

    public void onStopClicked(View v) {

        stopService(new Intent(this, CheckService.class));
        stopService(new Intent(this, CheckApp.class));
    }


    public void onActionStartClicked(View v) {
        Intent intent = new Intent(this, ActionService.class);
        intent.putExtra("serverAddress", getServerAddress());
        startForegroundService(intent);
    }

    public void onActionStopClicked(View v) {
        stopService(new Intent(this, ActionService.class));
    }
}
