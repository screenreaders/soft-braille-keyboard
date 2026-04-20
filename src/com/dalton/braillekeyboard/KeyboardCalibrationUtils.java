package com.dalton.braillekeyboard;

import java.util.Set;

public final class KeyboardCalibrationUtils {
    private static volatile boolean pendingCalibrationRequest;
    private static final int[] CALIBRATION_KEYS = {
            R.string.pref_keyboard_save_horizontal_portrait_key,
            R.string.pref_keyboard_save_horizontal_landscape_key,
            R.string.pref_keyboard_save_horizontal_portrait_invert_key,
            R.string.pref_keyboard_save_horizontal_landscape_invert_key,
            R.string.pref_keyboard_save_vertical_portrait_key,
            R.string.pref_keyboard_save_vertical_landscape_key,
            R.string.pref_keyboard_save_vertical_portrait_invert_key,
            R.string.pref_keyboard_save_vertical_landscape_invert_key
    };

    private KeyboardCalibrationUtils() {
    }

    public static boolean hasSavedCalibration(android.content.Context context) {
        return countSavedCalibrations(context) > 0;
    }

    public static int countSavedCalibrations(android.content.Context context) {
        int count = 0;
        for (int key : CALIBRATION_KEYS) {
            Set<String> points = Options.getStringSetPreference(context, key, null);
            if (points != null && !points.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public static void requestCalibrationMode() {
        pendingCalibrationRequest = true;
    }

    public static boolean isCalibrationModeRequested() {
        return pendingCalibrationRequest;
    }

    public static void clearCalibrationMode() {
        pendingCalibrationRequest = false;
    }
}
