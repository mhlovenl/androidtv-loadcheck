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

            if (isActionServiceActive(context)) {
                restartActionService(context);
            }

            if (isCheckAppServiceActive(context)) {
                restartCheckAppService(context);
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



    public void restartActionService(Context context) {
        Log.d(TAG, "restartActionService()");
        Intent intent = new Intent(context, ActionService.class);
        intent.putExtra("serverAddress", loadServerAddress(context));
        context.startForegroundService(intent);
    }

    private boolean isActionServiceActive(Context context) {
        SharedPreferences pref = context.getSharedPreferences(
                context.getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getBoolean(context.getString(R.string.saved_action_service_active),
                false);
    }
/*
    private String loadActionServerAddress(Context context) {
        SharedPreferences pref = context.getSharedPreferences(
                context.getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getString(context.getString(R.string.saved_action_server_address),
                context.getString(R.string.default_server));
    }
*/
    public void restartCheckAppService(Context context) {
        Log.d(TAG, "restartCheckAppService()");
        Intent intent = new Intent(context, CheckApp.class);
        intent.putExtra("serverAddress", loadServerAddress(context));
        context.startForegroundService(intent);
    }

    private boolean isCheckAppServiceActive(Context context) {
        SharedPreferences pref = context.getSharedPreferences(
                context.getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getBoolean(context.getString(R.string.saved_check_app_active),
                false);
    }
/*
    private String loadActionServerAddress(Context context) {
        SharedPreferences pref = context.getSharedPreferences(
                context.getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getString(context.getString(R.string.saved_check_app_server_address),
                context.getString(R.string.default_server));
    }
  */

}
