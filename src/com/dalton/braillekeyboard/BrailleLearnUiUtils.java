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

import com.googlecode.eyesfree.braille.translate.TableInfo;

import java.util.Locale;

final class BrailleLearnUiUtils {
    private BrailleLearnUiUtils() {
    }

    static String formatDots(Context context, int dotsMask) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if ((dotsMask & (1 << i)) != 0) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(i + 1);
            }
        }
        return sb.length() == 0
                ? context.getString(R.string.braille_learn_no_dots)
                : sb.toString();
    }

    static String formatDotsSequence(Context context, String symbol, byte[] cells) {
        if (cells == null || cells.length == 0) {
            return context.getString(R.string.braille_learn_no_dots);
        }
        if (cells.length == 1) {
            return formatDots(context, cells[0] & 0xFF);
        }
        String prefixDescription = describeSequencePrefix(context, symbol, cells);
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(prefixDescription)) {
            builder.append(prefixDescription);
            builder.append(" -> ");
        }
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(i + 1);
            builder.append(": ");
            builder.append(formatDots(context, cells[i] & 0xFF));
        }
        return builder.toString();
    }

    static String describeSequencePrefix(Context context, String symbol, byte[] cells) {
        if (symbol == null || symbol.length() == 0 || cells == null
                || cells.length <= 1) {
            return "";
        }
        if (Character.isUpperCase(symbol.charAt(0))) {
            return context.getString(R.string.braille_learn_prefix_capital);
        }
        if (Character.isDigit(symbol.charAt(0))) {
            return context.getString(R.string.braille_learn_prefix_number);
        }
        return context.getString(R.string.braille_learn_prefix_multi_cell);
    }

    static String buildPromptLabel(Context context, boolean dotsToSymbol,
            String symbol, int dotsMask) {
        if (TextUtils.isEmpty(symbol)) {
            return context.getString(R.string.braille_learn_prompt_empty);
        }
        if (dotsToSymbol) {
            return context.getString(R.string.braille_learn_prompt_from_dots,
                    formatDots(context, dotsMask));
        }
        return context.getString(R.string.braille_learn_prompt,
                symbol.toUpperCase(Locale.getDefault()));
    }

    static String getCurrentTableLabel(Context context, BrailleParser parser) {
        if (parser == null) {
            return context.getString(R.string.braille_learn_table_waiting);
        }
        TableInfo table = parser.getTable(context);
        if (table == null || table.getLocale() == null) {
            return context.getString(R.string.braille_learn_table_waiting);
        }
        String label = table.getLocale().getDisplayLanguage();
        String country = table.getLocale().getDisplayCountry();
        if (!TextUtils.isEmpty(country)) {
            label += " (" + country + ")";
        }
        return context.getString(R.string.braille_learn_table_value, label,
                table.isEightDot()
                        ? context.getString(R.string.grade_computer)
                        : context.getString(R.string.grade_table,
                                table.getGrade()));
    }
}
