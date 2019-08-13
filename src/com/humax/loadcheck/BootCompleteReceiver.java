package com.humax.loadcheck;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootCompleteReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompleteReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.d(TAG, "onReceive()");
            if (isServiceActive(context)) {
                restartService(context);
            }
        }
    }

    public void restartService(Context context) {
        Log.d(TAG, "startService()");
        Intent intent = new Intent(context, CheckService.class);
        intent.putExtra("serverAddress", loadServerAddress(context));
        context.startForegroundService(intent);
    }

    private boolean isServiceActive(Context context) {
        SharedPreferences pref = context.getSharedPreferences(
                context.getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getBoolean(context.getString(R.string.saved_service_active),
                false);
    }

    private String loadServerAddress(Context context) {
        SharedPreferences pref = context.getSharedPreferences(
                context.getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getString(context.getString(R.string.saved_server_address),
                context.getString(R.string.default_server));
    }
}
