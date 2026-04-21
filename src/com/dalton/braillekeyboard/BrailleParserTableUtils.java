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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.googlecode.eyesfree.braille.service.translate.TableList;

final class BrailleParserTableUtils {
    private BrailleParserTableUtils() {
    }

    static List<TableInfo> loadFallbackTables(Context context) {
        try {
            TableList tableList = new TableList(context.getResources());
            return new ArrayList<TableInfo>(tableList.getTables());
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String describeTable(Context context, TableInfo table) {
        String result = String.format(table.getLocale(), "%s %s %s",
                table.getLocale().getDisplayLanguage(),
                table.getLocale().getDisplayCountry(),
                table.isEightDot() ? "" : String.format(
                        context.getString(R.string.grade_table),
                        table.getGrade()));
        return result.trim();
    }

    static byte[] buildPaddedCells(Byte[] cellBytes) {
        byte[] cells = new byte[cellBytes.length + 2];
        cells[0] = 0;
        cells[cells.length - 1] = 0;
        for (int i = 0; i < cellBytes.length; i++) {
            cells[i + 1] = cellBytes[i] != null ? cellBytes[i].byteValue() : 0;
        }
        return cells;
    }

    static String handleUnknownPatterns(String text, byte[] cells) {
        if (TextUtils.isEmpty(text) || cells == null || cells.length == 0) {
            return text;
        }
        String cleaned = text;
        for (byte cell : cells) {
            String value = "\\" + computeCellValue(cell) + "/";
            if (cleaned.contains(value)) {
                cleaned = cleaned.replace(value, "");
            }
        }
        return cleaned;
    }

    static boolean betterTable(TableInfo first, TableInfo second) {
        if (first == null) {
            return false;
        }
        Locale firstLocale = first.getLocale();
        Locale secondLocale = second != null ? second.getLocale() : Locale.ROOT;
        return matchRank(firstLocale, Locale.getDefault())
                > matchRank(secondLocale, Locale.getDefault());
    }

    static int matchRank(Locale first, Locale second) {
        Locale safeFirst = first != null ? first : Locale.ROOT;
        Locale safeSecond = second != null ? second : Locale.ROOT;
        int ret = safeFirst.getLanguage().equals(safeSecond.getLanguage()) ? 1 : 0;
        if (ret > 0) {
            ret += safeFirst.getCountry().equals(safeSecond.getCountry()) ? 1 : 0;
            if (ret > 1) {
                ret += safeFirst.getVariant().equals(safeSecond.getVariant())
                        ? 1 : 0;
            }
        }
        return ret;
    }

    private static String computeCellValue(byte value) {
        StringBuilder sb = new StringBuilder();
        int mask = 1;
        for (int i = 1; i <= 8; i++) {
            if ((mask & value) != 0) {
                sb.append(String.valueOf(i));
            }
            mask <<= 1;
        }
        return sb.length() > 0 ? sb.toString() : "";
    }
}
