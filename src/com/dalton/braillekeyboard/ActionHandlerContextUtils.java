package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.Intent;

final class ActionHandlerContextUtils {
    private ActionHandlerContextUtils() {
    }

    static String togglePrivacy(Context context) {
        Options.switchBooleanPreference(context, R.string.pref_privacy_key,
                Boolean.parseBoolean(context
                        .getString(R.string.pref_privacy_default)));
        return Options.getBooleanPreference(context, R.string.pref_privacy_key,
                Boolean.parseBoolean(context
                        .getString(R.string.pref_privacy_default)))
                                ? context.getString(R.string.privacy_enabled)
                                : context.getString(R.string.privacy_disabled);
    }

    static String maybeShowInputSwitcher(Context context,
            boolean fastDoubleSwipe) {
        if (!fastDoubleSwipe) {
            return context.getString(R.string.swipe_confirm_input);
        }
        ActivityLaunchUtils.showInputMethodPicker(context);
        return context.getString(R.string.show_input_switcher);
    }

    static String maybeShowSettings(Context context, boolean fastDoubleSwipe) {
        if (!fastDoubleSwipe) {
            return context.getString(R.string.swipe_confirm_settings);
        }
        Intent intent = new Intent(context, PreferenceIME.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (ActivityLaunchUtils.canStartActivity(context, intent)) {
            context.startActivity(intent);
        }
        return context.getString(R.string.show_settings);
    }
}
