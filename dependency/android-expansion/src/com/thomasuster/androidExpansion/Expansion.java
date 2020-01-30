package com.thomasuster.androidExpansion;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import org.haxe.extension.Extension;
import org.haxe.lime.HaxeObject;
import android.util.Log;
import java.io.File;
import android.os.Environment;
import android.content.Context;
import java.util.Vector;
import com.google.android.vending.expansion.downloader.Helpers;
import com.google.android.vending.expansion.downloader.IStub;
import com.google.android.vending.expansion.downloader.DownloaderClientMarshaller;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;
import pub.devrel.easypermissions.AppSettingsDialog;

import android.Manifest;

import java.util.List;
import java.util.Arrays;

public class Expansion extends Extension implements EasyPermissions.PermissionCallbacks {

    public static String BASE64_PUBLIC_KEY;
    public static byte[] SALT;

    private static final String[] EXTERNAL_STORAGE_PERMISSIONS =
        {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private static final int RC_EXTERNAL_STORAGE_PERM = 100;

    private static IStub mDownloaderClientStub;
    private static DownloaderClientImpl downloaderClient;
    private static int version;
    private static long bytes;

    private static HaxeObject cbObj;

    public static void init(final HaxeObject obj) {
        cbObj = obj;
    }

    public static void setKey(String v) {
        BASE64_PUBLIC_KEY = v;
    }

    public static void setSalt(byte[] v) {
        SALT = v;
    }

    public static void setVersion(int v) {
        version = v;
    }

    public static int getAPKVersion() {
        try {
            PackageInfo info = mainContext.getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static void setBytes(long v) {
        bytes = v;
    }

    public static int expansionFilesDelivered() {
        String fileName = Helpers.getExpansionAPKFileName(mainContext, true,version);
        System.out.println("Checking " + fileName);
        if (Helpers.doesFileExist(mainContext, fileName, bytes, false))
            return 1;
        return 0;
    }

    public static int startDownloadServiceIfRequired() {
        if(expansionFilesDelivered() == 1)
            return 0;
        // Build an Intent to start this activity from the Notification
        Intent notifierIntent = new Intent(mainContext, mainActivity.getClass());
        notifierIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(mainContext, 0,
                notifierIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        int startResult = 0;
        try {
            startResult = DownloaderClientMarshaller.startDownloadServiceIfRequired(mainContext,
                    pendingIntent, ExtensionDownloaderService.class);
            if (startResult != DownloaderClientMarshaller.NO_DOWNLOAD_REQUIRED) {
                Extension.mainActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        downloaderClient = new DownloaderClientImpl();
                        mDownloaderClientStub = DownloaderClientMarshaller.CreateStub(downloaderClient,
                                ExtensionDownloaderService.class);
                    }
                });
            }
        }
        catch (Exception e) {
            System.out.println("startDownloadServiceIfRequired error");
            e.printStackTrace();
        }
        return startResult;
    }

    @Override
    public void onResume() {
        System.out.println("onRESUME!!!!!!!");
        if (mDownloaderClientStub != null) {
            mDownloaderClientStub.connect(mainContext);
        }

        mainActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }


    @Override
    public void onStop() {
        if (mDownloaderClientStub != null) {
            mDownloaderClientStub.disconnect(mainContext);
        }
    }

    public static String getMainFile() {
        return getAPKExpansionFiles(mainContext, version, 0)[0];
    }

    private final static String EXP_PATH = "/Android/obb/";

    static String[] getAPKExpansionFiles(Context ctx, int mainVersion,
                                         int patchVersion) {
        String packageName = ctx.getPackageName();
        Vector<String> ret = new Vector<String>();
        if (Environment.getExternalStorageState()
                .equals(Environment.MEDIA_MOUNTED)) {
            File root = Environment.getExternalStorageDirectory();
            File expPath = new File(root.toString() + EXP_PATH + packageName);

            if (expPath.exists()) {
                if ( mainVersion > 0 ) {
                    String strMainPath = expPath + File.separator + "main." +
                            mainVersion + "." + packageName + ".obb";
                    File main = new File(strMainPath);
                    if ( main.isFile() ) {
                        ret.add(strMainPath);
                    }
                }
                if ( patchVersion > 0 ) {
                    String strPatchPath = expPath + File.separator + "patch." +
                            mainVersion + "." + packageName + ".obb";
                    File main = new File(strPatchPath);
                    if ( main.isFile() ) {
                        ret.add(strPatchPath);
                    }
                }
            }
        }
        String[] retArray = new String[ret.size()];
        ret.toArray(retArray);
        return retArray;
    }

    public static String getPackageName ()
    {
        return mainContext.getPackageName();
    }

    public static String getLocalStoragePath ()
    {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public static long overallTotal()
    {
        if(downloaderClient == null)
            return 0;
        if(downloaderClient.progress == null)
            return 0;
        return downloaderClient.progress.mOverallTotal;
    }

    @Override 
    public boolean LIMEonRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean r = super.LIMEonRequestPermissionsResult(requestCode, permissions, grantResults);

        // EasyPermissions handles the request result.
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);

        return r;
    }

    @Override 
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        LIMEonRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        Log.v("PERMISSIONS", "Permissions granted");
        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.v("PERMISSIONS", "Permissions granted on thread, calling haxe");
                cbObj.call0("onPermissionsGranted");
            }
        });
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        Log.v("PERMISSIONS", "Permissions denied");
        if (EasyPermissions.somePermissionPermanentlyDenied(mainActivity, Arrays.asList(EXTERNAL_STORAGE_PERMISSIONS))) {
            new AppSettingsDialog.Builder(mainActivity).build().show();
        } else {
            mainActivity.runOnUiThread(new Runnable() {
                public void run() {
                    Log.v("PERMISSIONS", "Permissions denied on thread, calling haxe");
                    cbObj.call0("onPermissionsDenied");
                }
            });
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
            mainActivity.runOnUiThread(new Runnable() {
                public void run() {
                    Log.v("PERMISSIONS", "Permissions denied on thread, calling haxe");
                    cbObj.call0("onPermissionsDenied");
                }
            });
        }
        return true;
    }

    public static int hasExternalStoragePermissions() {
        return EasyPermissions.hasPermissions(mainActivity, EXTERNAL_STORAGE_PERMISSIONS) ? 0 : 1;
    }
    
    @AfterPermissionGranted(RC_EXTERNAL_STORAGE_PERM)
    public static void askExternalStoragePermissions() {
        if (hasExternalStoragePermissions() == 1) {
            if (EasyPermissions.somePermissionPermanentlyDenied(mainActivity, Arrays.asList(EXTERNAL_STORAGE_PERMISSIONS))) {
                new AppSettingsDialog.Builder(mainActivity).build().show();
            } else {
                // Ask for one permission
                EasyPermissions.requestPermissions(
                        mainActivity,
                        //getString(R.string.rationale_read_write_storage),
                        "Requesting external storage permissions",
                        RC_EXTERNAL_STORAGE_PERM,
                        EXTERNAL_STORAGE_PERMISSIONS);
            }
        }
    }

}