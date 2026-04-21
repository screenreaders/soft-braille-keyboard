package com.dalton.braillekeyboard;

import android.graphics.Rect;

final class BrailleImePassthroughUtils {
    private BrailleImePassthroughUtils() {
    }

    static boolean isTalkBackBrailleModeActive(BrailleIME ime, BrailleView brailleView) {
        return brailleView != null
                && Options.getBooleanPreference(ime,
                        R.string.pref_talkback_braille_mode_key,
                        Boolean.parseBoolean(ime.getString(
                                R.string.pref_talkback_braille_mode_default)))
                && brailleView.isTalkBackTouchModeActive();
    }

    static void clearAccessibilityPassthroughRegion() {
        BrailleImePassthroughBridge.updateKeyboardRegion(new Rect(), false);
    }

    static void publishAccessibilityPassthroughRegion(final BrailleView brailleView,
            boolean talkBackModeActive) {
        if (brailleView == null || !talkBackModeActive) {
            clearAccessibilityPassthroughRegion();
            return;
        }
        brailleView.post(new Runnable() {
            @Override
            public void run() {
                if (!brailleView.isShown() || brailleView.getWidth() <= 0
                        || brailleView.getHeight() <= 0) {
                    clearAccessibilityPassthroughRegion();
                    return;
                }
                int[] location = new int[2];
                brailleView.getLocationOnScreen(location);
                Rect region = new Rect(location[0], location[1],
                        location[0] + brailleView.getWidth(),
                        location[1] + brailleView.getHeight());
                BrailleImePassthroughBridge.updateKeyboardRegion(region, true);
            }
        });
    }
}
