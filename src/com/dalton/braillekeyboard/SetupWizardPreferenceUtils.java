package com.dalton.braillekeyboard;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

final class SetupWizardPreferenceUtils {
    private SetupWizardPreferenceUtils() {
    }

    static int getBrailleTypeSelection(BrailleParser.BrailleType brailleType) {
        return brailleType == BrailleParser.BrailleType.COMPUTER ? 1 : 0;
    }

    static void setCheckedFromPreference(Context context, CheckBox checkBox,
            int keyRes, int defaultRes) {
        if (checkBox == null) {
            return;
        }
        checkBox.setChecked(Options.getBooleanPreference(context, keyRes,
                Boolean.parseBoolean(context.getString(defaultRes))));
    }

    static int getKeyboardStyleSelection(Context context) {
        String keyboardStyle = Options.getStringPreference(context,
                R.string.pref_keyboard_style_key,
                context.getString(R.string.pref_keyboard_style_normal_value));
        if (keyboardStyle.equals(
                context.getString(R.string.pref_keyboard_style_slate_value))) {
            return 1;
        }
        if (keyboardStyle.equals(
                context.getString(R.string.pref_keyboard_style_top_bottom_value))) {
            return 2;
        }
        return 0;
    }

    static String getKeyboardStyleValue(Context context, int selection) {
        return context.getString(selection == 1
                ? R.string.pref_keyboard_style_slate_value
                : selection == 2
                        ? R.string.pref_keyboard_style_top_bottom_value
                        : R.string.pref_keyboard_style_normal_value);
    }

    static boolean isChecked(CheckBox checkBox) {
        return checkBox != null && checkBox.isChecked();
    }

    static void persistProfilePreferences(Context context, boolean misspellings,
            boolean doubleSpace, boolean autoCaps, boolean voiceShortcut,
            boolean talkBackMode, boolean autoUpdates, boolean crashPrompt,
            boolean usesDisplay) {
        Options.writeBooleanPreference(context,
                R.string.pref_echo_misspellings_key, misspellings);
        Options.writeBooleanPreference(context,
                R.string.pref_double_space_period_key, doubleSpace);
        Options.writeBooleanPreference(context, R.string.pref_auto_caps_key,
                autoCaps);
        Options.writeBooleanPreference(context,
                R.string.pref_voice_shortcut_key, voiceShortcut);
        Options.writeBooleanPreference(context,
                R.string.pref_talkback_braille_mode_key, talkBackMode);
        Options.writeBooleanPreference(context,
                R.string.pref_auto_check_updates_key, autoUpdates);
        Options.writeBooleanPreference(context,
                R.string.pref_prompt_crash_report_key, crashPrompt);
        Options.writeBooleanPreference(context,
                R.string.pref_user_uses_braille_display_key, usesDisplay);
    }

    static int getSeekPercent(SeekBar seekBar, int minPercent, int maxPercent) {
        return clampPercent((seekBar == null ? 0 : seekBar.getProgress())
                + minPercent, minPercent, maxPercent);
    }

    static int clampPercent(int value, int minPercent, int maxPercent) {
        if (value < minPercent) {
            return minPercent;
        }
        if (value > maxPercent) {
            return maxPercent;
        }
        return value;
    }

    static void updatePercentLabel(Context context, TextView view, int value,
            int minPercent, int maxPercent) {
        if (view == null) {
            return;
        }
        view.setText(context.getString(R.string.pref_text_to_speech_percent_value,
                clampPercent(value, minPercent, maxPercent)));
    }
}
