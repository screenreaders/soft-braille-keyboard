package com.dalton.braillekeyboard;

import android.content.Context;
import android.os.Vibrator;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import com.dalton.braillekeyboard.Options.KeyboardFeedback;
import com.dalton.braillekeyboard.Pad.Coords;

final class BrailleViewFeedbackUtils {
    private BrailleViewFeedbackUtils() {
    }

    static String getTalkBackKeyboardDescription(Context context,
            boolean passthroughActive) {
        return context.getString(passthroughActive
                ? R.string.braille_keyboard_talkback_ready
                : R.string.braille_keyboard_talkback_enable_service);
    }

    static boolean isTalkBackTouchModeActive(Context context,
            AccessibilityManager accessibilityManager) {
        return accessibilityManager != null
                && accessibilityManager.isTouchExplorationEnabled()
                && Options.getBooleanPreference(context,
                        R.string.pref_talkback_braille_mode_key,
                        Boolean.parseBoolean(context.getString(
                                R.string.pref_talkback_braille_mode_default)));
    }

    static boolean shouldUseEightDots(Context context, KeyboardListener listener) {
        if (listener != null) {
            return listener.getDots() == 8;
        }
        return Options.getBooleanPreference(context,
                R.string.pref_use_eight_dots_key,
                Boolean.parseBoolean(context.getString(
                        R.string.pref_use_eight_dots_default)));
    }

    static byte buildPressedDotString(Coords[] dotsDown, boolean dot7,
            boolean dot8) {
        byte mask = 1;
        byte value = 0;
        for (int i = 0; i < dotsDown.length - 2; i++) {
            if (dotsDown[i] != null) {
                value |= mask;
            }
            mask <<= 1;
        }
        if (dot7 || dotsDown[6] != null) {
            value |= mask;
        }
        mask <<= 1;
        if (dot8 || dotsDown[7] != null) {
            value |= mask;
        }
        return value;
    }

    static void sendNotification(Context context, Vibrator vibrator, View target,
            boolean vibrate, boolean playSound, long quickVibration) {
        int keyboardFeedback = Options.getIntPreference(context,
                R.string.pref_keyboard_feedback_key,
                KeyboardFeedback.ALL.getValue());
        if (vibrate
                && (KeyboardFeedback.VIBRATE.value & keyboardFeedback) != 0
                && vibrator != null) {
            vibrator.vibrate(quickVibration);
        }
        if (playSound
                && (KeyboardFeedback.SOUND.value & keyboardFeedback) != 0
                && target != null) {
            target.playSoundEffect(SoundEffectConstants.CLICK);
        }
    }

    static boolean readPrivacyEnabled(Context context) {
        return Options.getBooleanPreference(context,
                R.string.pref_privacy_key,
                Boolean.parseBoolean(context.getString(
                        R.string.pref_privacy_default)));
    }

    static long announceTalkBackHint(Context context, Speech speech,
            long lastTalkBackHintAt, String message, long debounceMs) {
        if (speech == null || message == null) {
            return lastTalkBackHintAt;
        }
        long now = System.currentTimeMillis();
        if (now - lastTalkBackHintAt < debounceMs) {
            return lastTalkBackHintAt;
        }
        speech.speak(context, message, Speech.QUEUE_FLUSH);
        return now;
    }
}
