package com.gamecenter.app.util;

public final class Log {
    private static final String TAG = "GameCenter";
    
    private Log() {}
    
    public static void d(String message) {
        android.util.Log.d(TAG, message);
    }
    
    public static void d(String tag, String message) {
        android.util.Log.d(tag, message);
    }
    
    public static void i(String message) {
        android.util.Log.i(TAG, message);
    }
    
    public static void i(String tag, String message) {
        android.util.Log.i(tag, message);
    }
    
    public static void w(String message) {
        android.util.Log.w(TAG, message);
    }
    
    public static void w(String tag, String message) {
        android.util.Log.w(tag, message);
    }
    
    public static void e(String message) {
        android.util.Log.e(TAG, message);
    }
    
    public static void e(String tag, String message) {
        android.util.Log.e(tag, message);
    }
    
    public static void e(String message, Throwable throwable) {
        android.util.Log.e(TAG, message, throwable);
    }
    
    public static void e(String tag, String message, Throwable throwable) {
        android.util.Log.e(tag, message, throwable);
    }
    
    public static void v(String message) {
        android.util.Log.v(TAG, message);
    }
    
    public static void v(String tag, String message) {
        android.util.Log.v(tag, message);
    }
}
