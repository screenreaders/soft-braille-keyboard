package com.dalton.braillekeyboard;

import android.app.Application;

public class SoftBrailleKeyboardApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReportStore.install(this);
    }
}
