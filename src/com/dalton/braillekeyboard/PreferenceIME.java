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

import java.util.ArrayList;
import java.util.List;

import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

import com.dalton.braillekeyboard.BrailleParser.BrailleType;
import com.dalton.braillekeyboard.Options.KeyboardEcho;
import com.dalton.braillekeyboard.Options.KeyboardFeedback;
import com.dalton.braillekeyboard.Options.OptionList;
import com.googlecode.eyesfree.braille.translate.TableInfo;

public class PreferenceIME extends PreferenceActivity {
    @Override
    protected boolean isValidFragment(String fragmentName) {
        return Settings.class.getName().equals(fragmentName);
    }

    @Override
    public Intent getIntent() {
        final Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, Settings.class.getName());
        modIntent.putExtra(EXTRA_NO_HEADERS, true);
        return modIntent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.settings_name);
    }

    public static class Settings extends PreferenceFragment {
        private BrailleParser brailleParser;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.ime_preferences);
            ListPreference keyboardEcho = (ListPreference) findPreference(getString(R.string.pref_echo_feedback_key));
            ListPreference keyboardFeedback = (ListPreference) findPreference(getString(R.string.pref_keyboard_feedback_key));
            Preference brailleDisplayTools = findPreference(getString(
                    R.string.pref_braille_display_tools_key));
            Preference brailleLearn = findPreference(getString(
                    R.string.pref_braille_learn_key));
            Preference brailleProfiles = findPreference(getString(
                    R.string.pref_braille_profiles_key));
            Preference accessibilityTools = findPreference(getString(
                    R.string.pref_accessibility_tools_key));
            Preference quickStartGuide = findPreference(getString(
                    R.string.pref_quick_start_guide_key));
            Preference ttsSettings = findPreference(getString(
                    R.string.pref_text_to_speech_settings_key));

            addOptions(keyboardFeedback, KeyboardFeedback.ALL);
            addOptions(keyboardEcho, KeyboardEcho.ALL);

            Preference preference = findPreference(getActivity().getString(
                    R.string.pref_app_version_key));
            try {
                String versionCode = getActivity().getPackageManager()
                        .getPackageInfo(getActivity().getPackageName(), 0).versionName;
                if (preference != null) {
                    preference.setTitle(String.format(
                            getActivity()
                                    .getString(R.string.pref_app_version_title),
                            versionCode));
                }
            } catch (Exception e) {
                if (preference != null) {
                    preference.setEnabled(false);
                }
            }
            if (brailleDisplayTools != null) {
                brailleDisplayTools.setIntent(new Intent(getActivity(),
                        BrailleDisplayActivity.class));
            }
            if (brailleLearn != null) {
                brailleLearn.setIntent(new Intent(getActivity(),
                        BrailleLearnActivity.class));
            }
            if (brailleProfiles != null) {
                brailleProfiles.setIntent(new Intent(getActivity(),
                        BrailleProfilesActivity.class));
            }
            if (accessibilityTools != null) {
                accessibilityTools.setIntent(new Intent(
                        android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            if (quickStartGuide != null) {
                quickStartGuide.setIntent(new Intent(getActivity(),
                        QuickStartActivity.class));
            }
            if (ttsSettings != null) {
                ttsSettings.setIntent(new Intent(getActivity(),
                        TtsSettingsActivity.class));
            }

            brailleParser = new BrailleParser(getActivity(),
                    new BrailleParser.BrailleParserListener() {

                        @Override
                        public void onTranslatorReady(int status) {
                            addTables(status);
                        }
                    });
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (brailleParser != null) {
                brailleParser.destroy();
            }
        }

        private void addTables(int status) {
            List<String> entries = new ArrayList<String>();
            List<String> entryValues = new ArrayList<String>();

            ListPreference compBraille = (ListPreference) findPreference(getString(R.string.pref_braille_computer_table_key));
            ListPreference literaryBraille = (ListPreference) findPreference(getString(R.string.pref_braille_literary_table_key));
            MultiSelectListPreference switchPref = (MultiSelectListPreference) findPreference(getActivity()
                    .getString(R.string.pref_switch_tables_key));
            if (compBraille == null || literaryBraille == null || switchPref == null
                    || brailleParser == null || getActivity() == null) {
                return;
            }

            List<TableInfo> tables = new ArrayList<TableInfo>();
            if (status == BrailleParser.STATUS_OK
                    || status == BrailleParser.STATUS_TABLE_ERROR) {
                tables = brailleParser.getTables(BrailleType.ALL);
            }
            populateWithTables(tables, entries, entryValues, true, null);
            switchPref.setEntries(entries.toArray(new String[entries.size()]));
            switchPref.setEntryValues(entryValues
                    .toArray(new String[entryValues.size()]));

            resetLists(entries, entryValues);
            if (status == BrailleParser.STATUS_OK
                    || status == BrailleParser.STATUS_TABLE_ERROR) {
                tables = brailleParser.getTables(BrailleType.LITERARY);
            }
            populateWithTables(tables, entries, entryValues, true,
                    brailleParser.getDefaultId(getActivity(),
                            BrailleType.LITERARY));
            literaryBraille.setEntries(entries.toArray(new String[entries
                    .size()]));
            literaryBraille.setEntryValues(entryValues
                    .toArray(new String[entryValues.size()]));

            resetLists(entries, entryValues);
            if (status == BrailleParser.STATUS_OK
                    || status == BrailleParser.STATUS_TABLE_ERROR) {
                tables = brailleParser.getTables(BrailleType.COMPUTER);
            }
            populateWithTables(tables, entries, entryValues, false,
                    brailleParser.getDefaultId(getActivity(),
                            BrailleType.COMPUTER));
            compBraille.setEntries(entries.toArray(new String[entries.size()]));
            compBraille.setEntryValues(entryValues
                    .toArray(new String[entryValues.size()]));
        }

        private void addOptions(ListPreference pref, OptionList option) {
            if (pref == null || option == null) {
                return;
            }
            OptionList[] types = option.getValues();
            CharSequence[] entries = new CharSequence[types.length];
            CharSequence[] entryValues = new CharSequence[entries.length];
            for (int i = 0; i < entries.length; i++) {
                entries[i] = getString(types[i].getResource());
                entryValues[i] = types[i].getValue();
            }
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

        private void populateWithTables(List<TableInfo> tables,
                List<String> entries, List<String> entryValues,
                boolean verbose, String defaultId) {
            if (tables == null || entries == null || entryValues == null
                    || getActivity() == null) {
                return;
            }
            for (TableInfo table : tables) {
                if (table == null || table.getLocale() == null
                        || table.getId() == null) {
                    continue;
                }
                String text = table.getLocale().getDisplayLanguage();
                String country = table.getLocale().getDisplayCountry();
                text += (country.equals("") ? "" : " (" + country + ")");
                if (verbose) {
                    String grade = getActivity().getString(
                            R.string.grade_computer);
                    if (table.getGrade() > 0) {
                        grade = String.format(
                                getActivity().getString(R.string.grade_table),
                                table.getGrade());
                    }
                    text += ": " + grade;
                }
                entries.add(text);
                if (table.getId().equals(defaultId)) {
                    entryValues.add(getActivity().getString(
                            R.string.pref_braille_table_auto));
                } else {
                    entryValues.add(table.getId());
                }
            }
        }

        private static void resetLists(List<String> list1, List<String> list2) {
            list1.clear();
            list2.clear();
        }
    }
}
