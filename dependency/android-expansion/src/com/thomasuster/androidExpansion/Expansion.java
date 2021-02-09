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
import android.os.StatFs;

import java.util.regex.*; 

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.PermissionRequest;

import android.Manifest;

import java.util.List;
import java.util.Arrays;

public class Expansion extends Extension implements EasyPermissions.PermissionCallbacks,EasyPermissions.RationaleCallbacks {

    public static String BASE64_PUBLIC_KEY;
    public static byte[] SALT;

    private static final String[] EXTERNAL_STORAGE_PERMISSIONS =
        {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private static final int RC_EXTERNAL_STORAGE_PERM = 100;

    private static IStub mDownloaderClientStub;
    private static DownloaderClientImpl downloaderClient;
    private static int version;
    private static int spaceNeeded;
    private static long bytes;

    private static HaxeObject cbObj;

    private static Pattern obbnamePattern = Pattern.compile("^([main|patch]+).([0-9]+).([\\w.]+).obb$");

    public static void init(final HaxeObject obj) {
        cbObj = obj;

        Vector<OBBFileInfo> obbs = findObbFiles();
        for(OBBFileInfo obb : obbs) {
            System.out.println("OBB found in " + obb.fullpath);
        }
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

    public static void setSpaceNeeded(int v) {
        spaceNeeded = v;
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

    // Checks if a volume containing external storage is available
    // for read and write.
    private static boolean isExternalStorageWritable() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    // Checks if a volume containing external storage is available to at least read.
    private static boolean isExternalStorageReadable() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ||
            Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY);
    }

    static Vector<OBBFileInfo> findObbFiles() {
        String packageName = mainContext.getPackageName();
        Vector<OBBFileInfo> result = new Vector<OBBFileInfo>();
        File[] dirs = mainContext.getObbDirs();
        for(File dir : dirs) {
            if(dir.exists()) {
                for(File file : dir.listFiles()) {
                    Matcher matcher = obbnamePattern.matcher(file.getName());
                    if(!matcher.matches()) continue;
                    
                    if(matcher.group(3).equals(packageName)) {
                        OBBFileInfo info = new OBBFileInfo();
                        info.isMain = matcher.group(1).equals("main");
                        info.version = Integer.parseInt(matcher.group(2));
                        info.packageName = packageName;
                        info.fullpath = dir + File.separator + matcher.group(0);
                        result.add(info);
                    }
                }
            }
        }

        return result;
    }

    public static String findBestPathToUnzip() {
        File[] dirs = mainContext.getExternalFilesDirs(null);
        long candidate_size = 0;
        String candidate = null;
        for(File dir : dirs) {
            // Skip removable storage or read-only storage
            if(Environment.isExternalStorageRemovable(dir) || Environment.getExternalStorageState(dir).equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
                continue;
            }
            // if the directory is emulated, we use the internal private files dir because it's in the same partition
            if(Environment.isExternalStorageEmulated(dir)) {
                dir = mainContext.getFilesDir();
            }

            // if the folder has an assets folder then we can expect that this is the correct folder
            File assetsFolder = new File(dir, "assets");
            if(assetsFolder.isDirectory()) {
                candidate = dir.toString();
                break;
            }
            
            // if we haven't found a candidate yet, check if there's enough space to unzip the whole file in the current directory
            StatFs stat = new StatFs(dir.toString());
            long available = stat.getAvailableBytes();
            if(available > spaceNeeded && available > candidate_size) {
                candidate = dir.toString();
                candidate_size = available;
                System.out.println("Found candidate to unzip here: " + candidate + " with an available space of " + available);
            }
        }

        return candidate;
    }


    static String[] getAPKExpansionFiles(Context ctx, int mainVersion,
                                         int patchVersion) {
        String packageName = ctx.getPackageName();
        Vector<String> ret = new Vector<String>();
        if (isExternalStorageWritable()) {
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
        //return Environment.getExternalFilesDir().getAbsolutePath();
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
    public void onRationaleAccepted(int requestCode) {
        
    }
    
    @Override
    public void onRationaleDenied(int requestCode) {
        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.v("PERMISSIONS", "Permissions denied on thread, calling haxe");
                cbObj.call0("onPermissionsDenied");
            }
        });
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
    public static void askExternalStoragePermissionsCB() {
        askExternalStoragePermissions("Allow the following permissions to be able to install the game.");
    }

    public static void askExternalStoragePermissions(String rationale) {
        if (hasExternalStoragePermissions() == 1) {

            /*

            // Ask for one permission
            EasyPermissions.requestPermissions(
                    mainActivity,
                    rationale,
                    RC_EXTERNAL_STORAGE_PERM,
                    EXTERNAL_STORAGE_PERMISSIONS);

            */

            int theme = android.R.style.Theme_DeviceDefault_Dialog;
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1){
                theme = android.R.style.Theme_DeviceDefault_Dialog_Alert;
            }

            EasyPermissions.requestPermissions(
                new PermissionRequest.Builder(mainActivity, RC_EXTERNAL_STORAGE_PERM, EXTERNAL_STORAGE_PERMISSIONS)
                        .setRationale(rationale)
                        .setTheme(theme)
                        .build());

        }
    }

}

class OBBFileInfo {
    public Boolean isMain;
    public int version;
    public String packageName;
    public String fullpath;
}