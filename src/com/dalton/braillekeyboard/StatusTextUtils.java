package com.dalton.braillekeyboard;

import android.content.Context;

final class StatusTextUtils {
    private StatusTextUtils() {
    }

    static String yesNo(Context context, boolean value) {
        return context.getString(value ? R.string.main_status_yes
                : R.string.main_status_no);
    }
}
