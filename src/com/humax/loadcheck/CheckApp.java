package com.humax.loadcheck;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RecoverySystem;
import android.os.StatFs;
import android.text.format.Formatter;
import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.TimeZone;

class PInfo {
    private static final String TAG = "CheckApp";
    String appname = "";
    String pname = "";
    String versionName = "";
    String firstinstalltime = "";
    int versionCode = 0;
    void prettyPrint() {
        //Log.i(TAG + "\t" + pname + "\t" + versionName + "\t" + versionCode);
        Log.i(TAG, appname + "\t" + pname + "\t" + versionName + "\t" + versionCode + "\t" + firstinstalltime);
    }
    String prettyString(){

        StringBuilder sb = new StringBuilder(appname + "\t");
        sb.append(pname + "\t" );
        sb.append(versionName + "\t");
        sb.append(versionCode + "\t");
        sb.append(firstinstalltime);
        //Log.i(TAG, sb.toString());
        return sb.toString();
    }



}



public class CheckApp extends Service {
    private static final String TAG = "CheckApp";
    private Thread mThread;
    private boolean mContinue;
    private Notification mNotification;
    private String mServerAddress;
    private String mAppName;
    private String mAppVersion;
    private String mSerialNumber;
    private String mIPAddress;
    private String mGmsversion;
    private String mBuildid;
    private Long mFirstbootLong;
    private String mFirstboot;
    private Long mStartTimeLong;
    private String mStartTime;
    private String mAvailableSize;
    private String mTotalInstalledApp;
    private Date dt = new Date();
    private String mInstalledapp = "installedapp";
    private String mInstalledappContents = "";
    ArrayList<PInfo> mAppslist;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate()");

        {
            mIPAddress = getLocalIpAddress();
            Log.i(TAG, "My Ip Address is " + mIPAddress);
            mSerialNumber = getProp("ro.serialno");
            Log.i(TAG, "ro.serialno " + mSerialNumber);
            mGmsversion = getProp("ro.com.google.gmsversion");
            Log.i(TAG, "ro.com.google.gmsversion " + mGmsversion);
            mBuildid = getProp("ro.build.id");
            Log.i(TAG, "ro.build.id " + mBuildid);
            mFirstboot = getProp("ro.runtime.firstboot");
            mFirstbootLong = mFirstbootLong.valueOf(mFirstboot);
            mFirstboot = simpleDateForm(mFirstbootLong);
            Log.i(TAG, "ro.runtime.firstboot " + mFirstboot);
            mStartTime = dt.toString();
            Log.i(TAG, "mStartTime " + mStartTime);

        }

        mContinue = false;

        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (mContinue) {
                    //check_app_status();
                    //mAppslist = getPackages();
                    mAppslist = getInstalledApps(false);
                    mAvailableSize = getAvailableSize();
                    send_app_info();

                    try {
                        Thread.sleep(10000);
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

    static String CHANNEL_ID = "CheckApp";
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "CheckApp",
                NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private void saveServiceStatus(boolean active, String address) {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(getString(R.string.saved_check_app_active), active);
        editor.putString(getString(R.string.saved_check_app_server_address), address);
        editor.commit();
    }

    private boolean isServiceActive() {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getBoolean(getString(R.string.saved_check_app_active),
                false);
    }

    private String loadServerAddress() {
        SharedPreferences pref = getSharedPreferences(
                getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        return pref.getString(getString(R.string.saved_check_app_server_address),
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

    private void send_app_info() {
        String url = "http://" + mServerAddress + "/api/log/app?deviceid=0x"+mSerialNumber;
        //String url = "http://222.121.66.23/api/log/app?deviceid=1111111111&packageName=com.test.humax";
        Log.i(TAG, "send() URL :" + url);
        try {
            HttpURLConnection conn = (HttpURLConnection) (new URL(url).openConnection());

            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            JSONObject jsonParam = new JSONObject();
            jsonParam.put("gmsversion", mGmsversion);
            jsonParam.put("buildid", mBuildid);
            jsonParam.put("starttime", mStartTime);
            jsonParam.put("ipaddress", mIPAddress);
            jsonParam.put("availablesize", mAvailableSize);
            jsonParam.put("totalinstalledapp", mTotalInstalledApp);

            Log.i(TAG, "JSON Data");
            Log.i(TAG, "gmsversion " + mGmsversion);
            Log.i(TAG, "buildid " + mBuildid);
            Log.i(TAG, "starttime " + mStartTime);
            Log.i(TAG, "ipaddress " + mIPAddress);
            Log.i(TAG, "availablesize " + mAvailableSize);
            Log.i(TAG, "totalinstalledapp " + mTotalInstalledApp);




            //ArrayList<PInfo> appslist = getInstalledApps(false); /* false = no system packages */
            final int mSize = mAppslist.size();
            for (int i=0; i<mSize; i++) {
                //mAppslist.get(i).prettyPrint();
                mInstalledappContents = mAppslist.get(i).prettyString();
                Log.i(TAG, mInstalledapp + "\t"+ mInstalledappContents);
                jsonParam.put(mInstalledapp, mInstalledappContents);
            }

            conn.connect();

            DataOutputStream os = new DataOutputStream(conn.getOutputStream());
            os.writeBytes(URLEncoder.encode(jsonParam.toString(), "UTF-8"));

            os.flush();
            os.close();

            int resp = conn.getResponseCode();
            Log.i(TAG, "Response code : " + resp);

            if (resp == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        conn.getInputStream()));
                String content = in.readLine();
                Log.i(TAG, "Content : " + content);
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


    }

    private void check_app_status(){

        mAppName = loadAappName();
        Log.i(TAG, "mAppName = " + mAppName);

        Log.i(TAG, "check_app_status end");
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

    private String loadAappName() {
        PackageManager pm = getPackageManager();
        Log.i(TAG, "loadAappName start");
        List<ApplicationInfo> list = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (int i=0; i<list.size(); i++) {
            Log.i(TAG, "Application = " + list.get(i).packageName);


        }

        return "";
    }


    private ArrayList<PInfo> getPackages() {
        ArrayList<PInfo> apps = getInstalledApps(false); /* false = no system packages */
        final int max = apps.size();
        for (int i=0; i<max; i++) {
            apps.get(i).prettyPrint();
        }
        return apps;
    }

    private ArrayList<PInfo> getInstalledApps(boolean getSysPackages) {
        ArrayList<PInfo> res = new ArrayList<PInfo>();
        List<PackageInfo> packs = getPackageManager().getInstalledPackages(0);
        mTotalInstalledApp = String.valueOf(packs.size());
        Log.i(TAG, "getInstalledApps packs size " + mTotalInstalledApp);
        for(int i=0;i<packs.size();i++) {
            PackageInfo p = packs.get(i);
            if ((!getSysPackages) && (p.versionName == null)) {
                continue ;
            }
            PInfo newInfo = new PInfo();
            newInfo.appname = p.applicationInfo.loadLabel(getPackageManager()).toString();
            newInfo.pname = p.packageName;
            newInfo.versionName = p.versionName;
            newInfo.firstinstalltime = simpleDateForm(p.firstInstallTime);
            //newInfo.versionCode = p.versionCode;
            res.add(newInfo);

        }
        return res;
    }

    private String getAvailableSize(){
        Log.i(TAG, "getAvailableSize ");
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long bytesAvailable = (long)stat.getBlockSize() *(long)stat.getBlockCount();
        long megAvailable = bytesAvailable / 1048576;
        Log.i(TAG, "Available MB : "+megAvailable);

        File path = Environment.getDataDirectory();
        StatFs stat2 = new StatFs(path.getPath());
        long blockSize = stat2.getBlockSize();
        long availableBlocks = stat2.getAvailableBlocks();
        String format =  Formatter.formatFileSize(this, availableBlocks * blockSize);
        Log.i(TAG,"Format : "+format);

        return format;

    }

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static String simpleDateForm (long millisec){
        SimpleDateFormat sdf = new SimpleDateFormat();
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(millisec));

    }

}
