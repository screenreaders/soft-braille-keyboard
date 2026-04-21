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
import android.text.TextUtils;

import java.util.Map;

final class SpeechTextUtils {
    private SpeechTextUtils() {
    }

    static String buildSpokenText(Context context, String format, CharSequence text,
            Map<String, String> speechMap) {
        if (text == null) {
            return null;
        }
        CharSequence normalisedText = normalizeSpokenText(context, text);
        return String.format(format,
                extractPunctuation(normalisedText.toString(), speechMap));
    }

    static CharSequence normalizeSpokenText(Context context, CharSequence text) {
        if (text.equals(" ")) {
            return context.getString(R.string.space);
        }
        if (text.length() < 2 && text.length() > 0
                && Character.isUpperCase(text.charAt(0))) {
            return String.format(context.getString(R.string.capital), text);
        }
        if (text.equals("\n")) {
            return context.getString(R.string.newline);
        }
        if (text.toString().trim().equals("")) {
            return context.getString(R.string.blank);
        }
        return text;
    }

    static String maskPassword(String text) {
        String safeText = text == null ? "" : text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < safeText.length(); i++) {
            sb.append('*');
        }
        return sb.toString();
    }

    static int getBestEnd(String text, int start, int end) {
        String[] separators = { " ", "\n" };
        int bestEnd = end;
        if (text.length() != end) {
            bestEnd = -1;
            for (String separator : separators) {
                int temp = text.lastIndexOf(separator, end - 1);
                if (temp >= start && temp > bestEnd) {
                    bestEnd = temp;
                }
            }
        }
        return bestEnd < end && bestEnd > 0 ? bestEnd : end;
    }

    static String extractPunctuation(String text, Map<String, String> speechMap) {
        if (text == null) {
            return null;
        }
        String symbol = null;
        if (text.length() == 1) {
            symbol = speechMap.get(text.substring(0, 1));
        }
        return symbol == null ? text : symbol;
    }
}
