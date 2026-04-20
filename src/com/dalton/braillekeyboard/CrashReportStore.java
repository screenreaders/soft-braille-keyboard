package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class CrashReportStore {
    private static final String KEY_PENDING_CRASH = "PENDING_CRASH_REPORT";
    private static final String KEY_PENDING_CRASH_TIME = "PENDING_CRASH_TIME";
    private static boolean installed;

    public static final class PendingCrash {
        public final String details;
        public final long timeMillis;

        PendingCrash(String details, long timeMillis) {
            this.details = details;
            this.timeMillis = timeMillis;
        }
    }

    private CrashReportStore() {
    }

    public static synchronized void install(final Context context) {
        if (installed || context == null) {
            return;
        }
        installed = true;
        final Context appContext = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous
                = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, Throwable ex) {
                        if (Options.getBooleanPreference(appContext,
                                R.string.pref_prompt_crash_report_key,
                                Boolean.parseBoolean(appContext.getString(
                                        R.string.pref_prompt_crash_report_default)))) {
                            storePendingCrash(appContext, thread, ex);
                        }
                        if (previous != null) {
                            previous.uncaughtException(thread, ex);
                        } else {
                            System.exit(10);
                        }
                    }
                });
    }

    public static PendingCrash consumePendingCrash(Context context) {
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        String details = preferences.getString(KEY_PENDING_CRASH, "");
        long time = preferences.getLong(KEY_PENDING_CRASH_TIME, 0L);
        preferences.edit()
                .remove(KEY_PENDING_CRASH)
                .remove(KEY_PENDING_CRASH_TIME)
                .apply();
        return TextUtils.isEmpty(details) ? null : new PendingCrash(details, time);
    }

    private static void storePendingCrash(Context context, Thread thread,
            Throwable ex) {
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        printWriter.println("Crash captured by Soft Braille Keyboard");
        printWriter.println("App version: " + BuildConfig.VERSION_NAME + " ("
                + BuildConfig.VERSION_CODE + ")");
        printWriter.println("Android: " + Build.VERSION.RELEASE + " / SDK "
                + Build.VERSION.SDK_INT);
        printWriter.println("Device: " + Build.MANUFACTURER + " "
                + Build.MODEL);
        printWriter.println("Thread: " + (thread == null ? "unknown"
                : thread.getName()));
        printWriter.println();
        if (ex != null) {
            ex.printStackTrace(printWriter);
        }
        printWriter.flush();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(KEY_PENDING_CRASH, writer.toString())
                .putLong(KEY_PENDING_CRASH_TIME, System.currentTimeMillis())
                .apply();
    }
}
