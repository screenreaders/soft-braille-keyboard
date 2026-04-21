/*
 * Copyright (C) 2026 The Soft Braille Keyboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dalton.braillekeyboard;

import android.content.Context;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

final class TtsSettingsUiUtils {
    private TtsSettingsUiUtils() {
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

    static String buildParameterLabel(Context context, int titleRes, int value,
            int minPercent, int maxPercent) {
        return context.getString(R.string.pref_text_to_speech_slider_label,
                context.getString(titleRes),
                Integer.valueOf(clampPercent(value, minPercent, maxPercent)));
    }

    static void updateParameterLabel(TextView view, Context context, int titleRes,
            int value, int minPercent, int maxPercent) {
        if (view != null) {
            view.setText(buildParameterLabel(context, titleRes, value,
                    minPercent, maxPercent));
        }
    }

    static String getCheckedTag(RadioGroup group, int checkedId) {
        View checkedView = group.findViewById(checkedId);
        if (!(checkedView instanceof RadioButton)) {
            return "";
        }
        Object tag = checkedView.getTag();
        return tag == null ? "" : tag.toString();
    }
}
