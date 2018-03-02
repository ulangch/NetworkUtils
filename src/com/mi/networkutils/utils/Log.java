package com.mi.networkutils.utils;

public final class Log {

    private Log() {
    }

    public static int v(String tag, String msg) {
        return android.util.Log.v(tag, msg);
    }
    
    public static int d(String tag, String msg) {
        return android.util.Log.d(tag, msg);
    }
    
    public static int e(String tag, String msg) {
        return android.util.Log.e(tag, msg);
    }
    
    public static int i(String tag, String msg) {
        return android.util.Log.i(tag, msg);
    }
}
