package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.Intent;
import android.view.inputmethod.InputMethodManager;

final class ActivityLaunchUtils {
    private ActivityLaunchUtils() {
    }

    static boolean canStartActivity(Context context, Intent intent) {
        return context != null && intent != null
                && context.getPackageManager() != null
                && intent.resolveActivity(context.getPackageManager()) != null;
    }

    static boolean showInputMethodPicker(Context context) {
        InputMethodManager inputManager = context == null ? null
                : (InputMethodManager) context.getSystemService(
                        Context.INPUT_METHOD_SERVICE);
        if (inputManager == null) {
            return false;
        }
        try {
            inputManager.showInputMethodPicker();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
