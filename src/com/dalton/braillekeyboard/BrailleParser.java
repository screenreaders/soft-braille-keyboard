/*
 * Copyright (C) 2016 The Soft Braille Keyboard Authors
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

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.googlecode.eyesfree.braille.translate.BrailleTranslator;
import com.googlecode.eyesfree.braille.translate.TableInfo;
import com.googlecode.eyesfree.braille.translate.TranslationResult;
import com.googlecode.eyesfree.braille.translate.TranslatorClient;
import com.googlecode.eyesfree.braille.translate.TranslatorClient.OnInitListener;
import com.googlecode.eyesfree.braille.service.translate.TableList;

/**
 * Acts as a layer of abstraction between the Android BrailleTranslator service
 * and the Braille IME itself.
 * 
 * You should instantiate this class in the InputMethodService implementation
 * and use it to back translate Braille patterns to text.
 * 
 * You should implement the BrailleParserListener callback as a method of
 * verifying when the service is ready for input.
 * 
 * This class provides methods to backtranslate Braille to text, switch Braille
 * tables and grades and list installed Braille tables.
 * 
 * You should always call the destroy() method when you are finished with the
 * instance to release system resources.
 */
public class BrailleParser {
    private static final Comparator<TableInfo> TABLE_LOCALE_COMPARATOR = new Comparator<TableInfo>() {
        @Override
        public int compare(TableInfo o1, TableInfo o2) {
            String table1 = o1.getLocale().getDisplayLanguage() + " "
                    + o1.getLocale().getDisplayCountry().trim();
            String table2 = o2.getLocale().getDisplayLanguage() + " "
                    + o2.getLocale().getDisplayCountry().trim();
            return table1.compareTo(table2);
        }
    };

    // The BrailleTranslator can currently accept back translation requests.
    public static final int STATUS_OK = 1;
    // The back translator is in the preparing state and can not yet receive
    // requests.
    public static final int STATUS_PREPARING = 0;
    // The BackTranslator is in the error state and can't accept requests.
    public static final int STATUS_ERROR = -1;
    // This instance has been shutdown and can not be used.
    public static final int STATUS_TABLE_ERROR = 2;

    /**
     * A callback which is invoked with a status flag when the BrailleTranslator
     * responds on initial setup.
     */
    public interface BrailleParserListener {

        /**
         * Invoked when the translator has responded and transfered from the
         * preparing state to some other state.
         * 
         * @param status
         *            The status of the BrailleTranslator. This can be STATUS_OK
         *            or STATUS_ERROR currently.
         */
        void onTranslatorReady(int status);
    }

    /**
     * Used to filter Braille tables by their varying types.
     * 
     * LITERARY refers to all 6 dot tables (grades 1 and 2). COMPUTER refers to
     * all 8 dot tables. ALL refers to both computer and literary tables.
     */
    public enum BrailleType {
        ALL(0), COMPUTER(8), LITERARY(6);

        public final int dots;

        BrailleType(int dots) {
            this.dots = dots;
        }

        /**
         * For backwards compatibility BrailleType is stored as an integer where
         * 0 = COMPUTER and all other values return LITERARY.
         * 
         * @param value
         *            The preference value for the BrailleType.
         * @return COMPUTER for value 0, LITERARY for all other values.
         */
        public static BrailleType valueOf(int value) {
            switch (value) {
            case 0:
                return COMPUTER;
            default:
                return LITERARY;
            }
        }

        /**
         * Toggle the BrailleType and return the new type. This makes COMPUTER
         * LITERARY and LITERARY COMPUTER. It does not make sense to toggle ALL
         * so ALL is returned.
         * 
         * @return LITERARY if the current state is COMPUTER, COMPUTER if the
         *         current state is LITERARY or ALL if the current state is ALL.
         */
        public BrailleType switchType() {
            switch (this) {
            case LITERARY:
                return COMPUTER;
            case COMPUTER:
                return LITERARY;
            default:
                return this;
            }
        }

        /**
         * Convert a BrailleType to an integer. This is most useful for
         * backwards compatibility with preferences.
         * 
         * @return 0 for COMPUTER Braille, 1 for LITERARY and 2 for ALL.
         */
        public int prefValue() {
            switch (this) {
            case COMPUTER:
                return 0;
            case LITERARY:
                return 1;
            default:
                return 2;
            }
        }
    }

    private final TranslatorClient client;
    private final SharedPreferences sharedPref;
    private final BrailleParserListener listener;
    private final List<String> tableIds;

    private BrailleTranslator translator;
    private List<TableInfo> tables;
    private int status = STATUS_PREPARING;

    /**
     * Construct a BrailleParser instance.
     * 
     * @param context
     *            The application context.
     * @param listener
     *            An implementation of BrailleParserListener which will be
     *            invoked when the BrailleTranslator has left the preparing
     *            state.
     */
    public BrailleParser(final Context context, BrailleParserListener listener) {
        this.listener = listener;
        sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        String[] ids = context.getResources().getStringArray(
                R.array.braille_tables);
        tableIds = Arrays.asList(ids);

        client = new MyTranslatorClient(context, new OnInitListener() {

            @Override
            public void onInit(int status) {
                ready(context, status);
            }
        });
    }

    /**
     * Release system resources held by this instance.
     */
    public void destroy() {
        if (client != null) {
            client.destroy();
        }
        status = STATUS_ERROR;
    }

    /**
     * Get the currently active BrailleType according to the BrailleType
     * preference and return it.
     * 
     * @param context
     *            The application context.
     * @return The active BrailleType.
     */
    public BrailleType getBrailleType(Context context) {
        int value = Options.getIntPreference(context,
                R.string.pref_braille_type_key,
                context.getString(R.string.pref_braille_type_default));
        return BrailleType.valueOf(value);
    }

    /**
     * Get the active Braille table.
     * 
     * @param context
     *            The application context.
     * @return The active Braille table as determined by the application
     *         preferences.
     */
    public TableInfo getTable(Context context) {
        BrailleType brailleType = getBrailleType(context);
        String id = getSelectedTableId(context, brailleType);

        List<TableInfo> tables = getTables(brailleType);
        if (tables == null || tables.isEmpty()) {
            return null;
        }

        for (TableInfo table : tables) {
            if (table.getId().equals(id)) {
                return table;
            }
        }
        return null;
    }

    /**
     * Return a list of supported Braille tables for the given BrailleType by
     * the BrailleTranslator.
     * 
     * @param context
     *            The application context.
     * @param brailleType
     *            The type of tables to be returned.
     * @return The list of tables.
     */
    public List<TableInfo> getTables(BrailleType brailleType) {
        if (tables != null) {
            List<TableInfo> filteredTables = filterTables(tables, brailleType);
            Collections.sort(filteredTables, TABLE_LOCALE_COMPARATOR);
            return filteredTables;
        }
        return null;
    }

    /**
     * Toggle the active Braille type. If the current type is literary switches
     * to computer and if the current type is computer switches to literary.
     * 
     * @param context
     *            The application context.
     * @return The new BrailleType.
     */
    public BrailleType switchBrailleType(Context context) {
        BrailleType brailleType = getBrailleType(context).switchType();
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(context.getString(R.string.pref_braille_type_key),
                String.valueOf(brailleType.prefValue()));
        editor.apply();
        setTranslator(context);
        return brailleType;
    }

    /**
     * Switch to the next Braille table in the on the fly list matching the
     * active BrailleType. If there are no more in the list switch to the first
     * one.
     * 
     * @param context
     *            The application context.
     * @return A String describing the new table which can be shown to the user.
     */
    public String switchTable(Context context) {
        BrailleType brailleType = getBrailleType(context);
        // get tables matching only the active brailleType.
        List<TableInfo> tables = getTables(brailleType);
        if (tables == null || tables.isEmpty()) {
            return null;
        }
        // Retrieve the list of tables that can be switched on the fly.
        Set<String> onFly = Options.getStringSetPreference(context,
                R.string.pref_switch_tables_key, new HashSet<String>());
        onFly = onFly == null ? new HashSet<String>() : onFly;
        // Get the active Braille table.
        TableInfo defaultTable = getTable(context);

        if (defaultTable == null) {
            return null;
        }

        // Move tablePosition to point at the default table in the table list.
        int tablePosition;
        for (tablePosition = 0; tablePosition < tables.size(); tablePosition++) {
            if (tables.get(tablePosition).getId().equals(defaultTable.getId())) {
                break;
            }
        }
        if (tablePosition >= tables.size()) {
            tablePosition = 0;
        }

        // Move in a circular motion through the tables list until we find the
        // next table in the list that is switchable on the fly. This will
        // traverse to the end of the list and then return to the start and work
        // it's way back to the default table if there are no on the fly tables.
        int i = 0;
        while (i++ < tables.size()) {
            tablePosition = tablePosition < tables.size() - 1 ? tablePosition + 1
                    : 0;
            TableInfo table = tables.get(tablePosition);
            if (onFly.contains(table.getId())) {
                setTable(context, table);
                return describeTable(context, table);
            }
        }
        return null;
    }

    /**
     * Back translate an array of Braille dots to text.
     * 
     * @param context
     *            The application context.
     * @param cellBytes
     *            An array of bytes representing Braille dot patterns. Each byte
     *            represents a single Braille cell. Each cell is represented by
     *            a byte which is a bit string which indicates whether dots are
     *            on or off. The MSB of an 8 bit bitstring represents dot 8
     *            while the lsb represents dot 1. 0b11111111 means all 8 dots
     *            are pressed while 0 means no dots are active.
     * @return The back translation String or null if no backtranslation was
     *         possible.
     */
    public String backTranslate(Context context, Byte[] cellBytes) {
        return backTranslate(context, cellBytes, null);
    }

    public String backTranslate(Context context, Byte[] cellBytes,
            String tableIdOverride) {
        byte[] cells = buildPaddedCells(cellBytes);

        String text = null;
        BrailleTranslator activeTranslator = resolveTranslator(context,
                tableIdOverride);
        if (activeTranslator != null) {
            text = activeTranslator.backTranslate(cells);
            text = handleUnknownPatterns(context, text, cells);
        }
        return text != null ? text.trim() : text;
    }

    public TranslationResult translateText(Context context, CharSequence text,
            int cursorPosition) {
        return translateText(context, text, cursorPosition, null);
    }

    public Integer findDotsForCharacter(Context context, char character) {
        if (status != STATUS_OK) {
            return null;
        }
        String target = String.valueOf(character);
        String normalizedTarget = Character.isLetter(character)
                ? String.valueOf(Character.toLowerCase(character)) : target;
        for (int mask = 1; mask <= 0xFF; mask++) {
            String translated = backTranslate(context,
                    new Byte[] { Byte.valueOf((byte) mask) });
            if (TextUtils.isEmpty(translated) || translated.length() != 1) {
                continue;
            }
            String candidate = Character.isLetter(translated.charAt(0))
                    ? String.valueOf(Character.toLowerCase(translated.charAt(0)))
                    : translated;
            if (TextUtils.equals(candidate, normalizedTarget)) {
                return Integer.valueOf(mask);
            }
        }
        return null;
    }

    public byte[] translateTextToBrailleCells(Context context, CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        TranslationResult translation = translateText(context, text, text.length());
        return translation == null ? null : translation.getCells();
    }

    public TranslationResult translateText(Context context, CharSequence text,
            int cursorPosition, String tableIdOverride) {
        BrailleTranslator activeTranslator = resolveTranslator(context,
                tableIdOverride);
        if (activeTranslator == null) {
            return null;
        }
        String source = text == null ? "" : text.toString();
        int safeCursor = cursorPosition;
        if (safeCursor > source.length()) {
            safeCursor = source.length();
        }
        return activeTranslator.translate(source, safeCursor);
    }

    public int getStatus() {
        return status;
    }

    // Called when the BrailleTranslator becomes ready.
    private void ready(Context context, int translatorClientStatus) {
        tables = loadAvailableTables(context, translatorClientStatus);
        status = resolveReadyStatus(translatorClientStatus);
        if (status == STATUS_OK) {
            setTranslator(context);
        }
        listener.onTranslatorReady(status);
    }

    private List<TableInfo> loadAvailableTables(Context context,
            int translatorClientStatus) {
        if (client != null
                && translatorClientStatus == TranslatorClient.SUCCESS) {
            List<TableInfo> availableTables = client.getTables();
            if (availableTables != null && !availableTables.isEmpty()) {
                return availableTables;
            }
        }
        return loadFallbackTables(context);
    }

    private int resolveReadyStatus(int translatorClientStatus) {
        if (translatorClientStatus == TranslatorClient.SUCCESS) {
            return STATUS_OK;
        }
        return tables == null || tables.isEmpty() ? STATUS_ERROR
                : STATUS_TABLE_ERROR;
    }

    private List<TableInfo> loadFallbackTables(Context context) {
        return BrailleParserTableUtils.loadFallbackTables(context);
    }

    // Sets the translator to the active table.
    public boolean setTranslator(Context context) {
        TableInfo table = getTable(context);
        if (table != null && hasUsableTranslatorStatus()) {
            translator = client.getTranslator(table.getId());
            status = translator == null ? STATUS_TABLE_ERROR : STATUS_OK;
            return true;
        }
        return false;
    }

    private BrailleTranslator resolveTranslator(Context context,
            String tableIdOverride) {
        if (!hasUsableTranslatorStatus()) {
            return null;
        }
        if (tableIdOverride != null && tableIdOverride.length() > 0) {
            return client.getTranslator(tableIdOverride);
        }
        if (translator == null) {
            setTranslator(context);
        }
        return translator;
    }

    // Checks if a given Braille table matches the given BrailleType filter.
    private static boolean matchesBrailleType(TableInfo table,
            BrailleType brailleType) {
        if (brailleType == BrailleType.ALL) {
            return true;
        }

        if (brailleType == BrailleType.LITERARY && table.getGrade() > 0) {
            return true;
        } else if (table.isEightDot() && brailleType == BrailleType.COMPUTER) {
            return true;
        }
        return false;
    }

    // Filter a list of tables and return the filtered list.
    // Tables must be declared in the arrays.xml file in the braille_tables
    // array.
    // tables must match the specified BrailleType.
    private List<TableInfo> filterTables(List<TableInfo> tables,
            BrailleType brailleType) {
        List<TableInfo> list = new ArrayList<TableInfo>();

        for (TableInfo table : tables) {
            if (tableIds.contains(table.getId())
                    && matchesBrailleType(table, brailleType)) {
                list.add(table);
            }
        }
        return list;
    }

    // Set the app preferences to have a new active table. Set the translator to
    // use this table.
    private void setTable(Context context, TableInfo table) {
        if (table != null) {
            SharedPreferences.Editor editor = sharedPref.edit();
            String defaultId = getDefaultId(context,
                    table.isEightDot() ? BrailleType.COMPUTER
                            : BrailleType.LITERARY);
            String id = table.getId().equals(defaultId) ? context
                    .getString(R.string.pref_braille_table_auto) : table
                    .getId();
            editor.putString(getTablePreferenceKey(context,
                    table.isEightDot() ? BrailleType.COMPUTER
                            : BrailleType.LITERARY), id);
            editor.apply();
            setTranslator(context);
        }
    }

    private boolean hasUsableTranslatorStatus() {
        return status == STATUS_OK || status == STATUS_TABLE_ERROR;
    }

    private String getSelectedTableId(Context context, BrailleType brailleType) {
        String defaultId = getDefaultId(context, brailleType);
        String id = sharedPref.getString(getTablePreferenceKey(context,
                brailleType), defaultId);
        if (TextUtils.isEmpty(id)
                || TextUtils.equals(id,
                        context.getString(R.string.pref_braille_table_auto))) {
            return defaultId;
        }
        return id;
    }

    private String getTablePreferenceKey(Context context,
            BrailleType brailleType) {
        return context.getString(brailleType == BrailleType.COMPUTER
                ? R.string.pref_braille_computer_table_key
                : R.string.pref_braille_literary_table_key);
    }

    private String describeTable(Context context, TableInfo table) {
        return BrailleParserTableUtils.describeTable(context, table);
    }

    private byte[] buildPaddedCells(Byte[] cellBytes) {
        return BrailleParserTableUtils.buildPaddedCells(cellBytes);
    }

    // The BrailleTranslator can populate the output string with garbage for
    // unknown Braille patterns. Remove these from the string.
    // These are of the form \dotpattern/ eg. \12/ if dots 12 is unknown.
    private String handleUnknownPatterns(Context context, String text,
            byte[] cells) {
        return BrailleParserTableUtils.handleUnknownPatterns(text, cells);
    }

    private TableInfo findDefaultTableInfo(BrailleType brailleType) {
        List<TableInfo> filteredTables = getTables(brailleType);
        if (filteredTables == null) {
            return null;
        }

        TableInfo best = null;
        for (TableInfo info : filteredTables) {
            if (betterTable(info, best)) {
                best = info;
            }
        }
        return best;
    }

    private static boolean betterTable(TableInfo first, TableInfo second) {
        return BrailleParserTableUtils.betterTable(first, second);
    }

    private static int matchRank(Locale first, Locale second) {
        return BrailleParserTableUtils.matchRank(first, second);
    }

    public String getDefaultId(Context context, BrailleType brailleType) {
        TableInfo table = findDefaultTableInfo(brailleType);
        if (table != null) {
            return table.getId();
        }

        if (brailleType == BrailleType.COMPUTER) {
            return context
                    .getString(R.string.pref_braille_computer_table_default);
        } else {
            return context
                    .getString(R.string.pref_braille_literary_table_default);
        }
    }
}
