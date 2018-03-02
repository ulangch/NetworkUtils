package com.mi.networkutils.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.os.Environment;
import android.os.SystemClock;

public final class Utils {
    private final static String TAG = "MINETWORK_UTILS_UTILS";
    
    private static int sIsRoot = -1;
    private static String sSdcardPath;
    
    public static boolean isRoot() {
        if (sIsRoot != -1) {
            return sIsRoot == 1;
        }

        StringBuilder sb = new StringBuilder();
        String command = "ls /";

        int exitcode = Command.runCommand(command, sb, 10 * 1000, true);
        if (exitcode == Command.TIME_OUT) {
            return false;
        }
        if (sb.toString().contains("system")) {
            sIsRoot = 1;
        }

        Log.i(TAG, "isRoot " + (sIsRoot == 1));
        return sIsRoot == 1;
    }
    
    public static void copyFile(InputStream in, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.flush();
    }
    
    public final static String generateFileName(String prefix, String suffix) {
        return prefix + new SimpleDateFormat("yyyy_MM_ddHHmmss").format(new Date()) + "." + suffix;
    }
    
    public static String getDataPath() {
        if (sSdcardPath == null) {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                // sSdcardPath = Environment.getExternalStorageDirectory().getAbsolutePath();
                sSdcardPath = "/sdcard";
            } else {
                sSdcardPath = "/sdcard";
            }
            Log.d(TAG, "sdcard path : " + sSdcardPath);
        }
        return sSdcardPath;
    }

    public static final long now() {
        return SystemClock.elapsedRealtime();
    }

    
}
