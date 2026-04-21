package com.dalton.braillekeyboard;

import android.content.Context;
import android.text.TextUtils;

final class UserProfilePresetUtils {
    static final int PRESET_CUSTOM = 0;
    static final int PRESET_POLISH = 1;
    static final int PRESET_ENGLISH = 2;

    private UserProfilePresetUtils() {
    }

    static void applyPreset(Context context, int preset) {
        if (context == null) {
            return;
        }
        if (preset == PRESET_POLISH) {
            Options.writeStringPreference(context, R.string.pref_braille_type_key, "1");
            Options.writeStringPreference(context,
                    R.string.pref_braille_literary_table_key, "pl-g1");
            Options.writeStringPreference(context,
                    R.string.pref_braille_computer_table_key, "pl-comp");
        } else if (preset == PRESET_ENGLISH) {
            Options.writeStringPreference(context, R.string.pref_braille_type_key, "0");
            Options.writeStringPreference(context,
                    R.string.pref_braille_literary_table_key, "en-US-g2");
            Options.writeStringPreference(context,
                    R.string.pref_braille_computer_table_key, "en-US-comp8");
        }
    }

    static String resolveProfileName(Context context, int preset, CharSequence customName) {
        if (context == null) {
            return "";
        }
        String name = customName == null ? "" : customName.toString().trim();
        if (!TextUtils.isEmpty(name)) {
            return name;
        }
        if (preset == PRESET_POLISH) {
            return context.getString(
                    R.string.user_profile_setup_default_profile_name_polish);
        }
        if (preset == PRESET_ENGLISH) {
            return context.getString(
                    R.string.user_profile_setup_default_profile_name_english);
        }
        return context.getString(
                R.string.user_profile_setup_default_profile_name_custom);
    }
}
