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
import android.view.inputmethod.ExtractedText;

final class ActionHandlerTextUtils {
    private ActionHandlerTextUtils() {
    }

    static String cycleKeyboardFeedback(Context context) {
        Options.KeyboardFeedback feedback = Options.KeyboardFeedback.valueOf(
                Options.getIntPreference(context,
                        R.string.pref_keyboard_feedback_key,
                        Options.KeyboardFeedback.ALL.getValue()));
        feedback = Options.KeyboardFeedback.next(feedback);
        Options.writeStringPreference(context,
                R.string.pref_keyboard_feedback_key, feedback.getValue());
        return context.getString(feedback.resource);
    }

    static String cycleKeyboardEcho(Context context) {
        Options.KeyboardEcho echo = Options.KeyboardEcho.valueOf(
                Options.getIntPreference(context,
                R.string.pref_echo_feedback_key,
                Options.KeyboardEcho.CHARACTER.getValue()));
        echo = Options.KeyboardEcho.next(echo);
        Options.writeStringPreference(context, R.string.pref_echo_feedback_key,
                echo.getValue());
        return context.getString(echo.resource);
    }

    static String togglePasswordEcho(Context context) {
        boolean echoPassword = Options.switchBooleanPreference(context,
                R.string.pref_echo_passwords_key, false);
        return echoPassword ? context.getString(R.string.speak_passwords)
                : context.getString(R.string.no_password_echo);
    }

    static String toggleAutoCaps(Context context) {
        Options.switchBooleanPreference(context, R.string.pref_auto_caps_key,
                Boolean.parseBoolean(
                        context.getString(R.string.pref_auto_caps_default)));
        return Options.getBooleanPreference(context, R.string.pref_auto_caps_key,
                Boolean.parseBoolean(
                        context.getString(R.string.pref_auto_caps_default)))
                                ? context.getString(R.string.auto_caps_enabled)
                                : context.getString(R.string.auto_caps_disabled);
    }

    static String buildTextStatsMessage(Context context,
            KeyboardListener listener) {
        ExtractedText extractedText = listener.getAllText();
        CharSequence text = extractedText == null ? null : extractedText.text;
        if (text == null) {
            return context.getString(R.string.blank);
        }
        return String.format(context.getString(R.string.word_count),
                EditingUtilities.lineCount(text), EditingUtilities.wordCount(text),
                EditingUtilities.characterCount(text));
    }

    static String echoCharacter(Context context, String character) {
        if ((Options.getIntPreference(context, R.string.pref_echo_feedback_key,
                Options.KeyboardEcho.CHARACTER.getValue())
                & Options.KeyboardEcho.CHARACTER.value) != 0) {
            return character;
        }
        return null;
    }

    static String echoWord(Context context, String word) {
        if ((Options.getIntPreference(context, R.string.pref_echo_feedback_key,
                Options.KeyboardEcho.CHARACTER.getValue())
                & Options.KeyboardEcho.WORD.value) != 0) {
            return word;
        }
        return null;
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return TextUtils.isEmpty(trimmed) ? null : trimmed;
    }
}
