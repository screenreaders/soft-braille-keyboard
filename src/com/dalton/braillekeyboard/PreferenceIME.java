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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.EngineInfo;
import android.speech.tts.Voice;
import android.text.TextUtils;

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
        private TextToSpeech tts;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.ime_preferences);
            ListPreference keyboardEcho = (ListPreference) findPreference(getString(R.string.pref_echo_feedback_key));
            ListPreference keyboardFeedback = (ListPreference) findPreference(getString(R.string.pref_keyboard_feedback_key));
            ListPreference textToSpeechPreference = (ListPreference) findPreference(getString(R.string.pref_text_to_speech_engine_key));
            ListPreference textToSpeechVoicePreference = (ListPreference) findPreference(getString(R.string.pref_text_to_speech_voice_key));
            ListPreference textToSpeechRatePreference = (ListPreference) findPreference(getString(R.string.pref_text_to_speech_rate_key));
            ListPreference textToSpeechPitchPreference = (ListPreference) findPreference(getString(R.string.pref_text_to_speech_pitch_key));
            ListPreference textToSpeechVolumePreference = (ListPreference) findPreference(getString(R.string.pref_text_to_speech_volume_key));
            Preference brailleDisplayTools = findPreference(getString(
                    R.string.pref_braille_display_tools_key));
            Preference brailleLearn = findPreference(getString(
                    R.string.pref_braille_learn_key));
            Preference accessibilityTools = findPreference(getString(
                    R.string.pref_accessibility_tools_key));
            Preference quickStartGuide = findPreference(getString(
                    R.string.pref_quick_start_guide_key));

            addOptions(keyboardFeedback, KeyboardFeedback.ALL);
            addOptions(keyboardEcho, KeyboardEcho.ALL);
            addPercentOptions(textToSpeechRatePreference,
                    new int[] { 50, 75, 100, 125, 150, 175, 200 });
            addPercentOptions(textToSpeechPitchPreference,
                    new int[] { 50, 75, 100, 125, 150, 175, 200 });
            addPercentOptions(textToSpeechVolumePreference,
                    new int[] { 0, 25, 50, 75, 100 });
            addTTSList(textToSpeechPreference, textToSpeechVoicePreference);

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
            if (accessibilityTools != null) {
                accessibilityTools.setIntent(new Intent(
                        android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            if (quickStartGuide != null) {
                quickStartGuide.setIntent(new Intent(getActivity(),
                        QuickStartActivity.class));
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
            if (tts != null) {
                tts.shutdown();
                tts = null;
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
            if (status == BrailleParser.STATUS_OK) {
                tables = brailleParser.getTables(BrailleType.ALL);
            }
            populateWithTables(tables, entries, entryValues, true, null);
            switchPref.setEntries(entries.toArray(new String[entries.size()]));
            switchPref.setEntryValues(entryValues
                    .toArray(new String[entryValues.size()]));

            resetLists(entries, entryValues);
            if (status == BrailleParser.STATUS_OK) {
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
            if (status == BrailleParser.STATUS_OK) {
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

        private void addTTSList(final ListPreference enginePreference,
                final ListPreference voicePreference) {
            if (enginePreference == null || getActivity() == null) {
                return;
            }
            if (voicePreference != null) {
                voicePreference.setEntries(new CharSequence[] {
                        getString(R.string.pref_text_to_speech_voice_auto) });
                voicePreference.setEntryValues(new CharSequence[] { "" });
            }
            enginePreference.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference,
                                Object newValue) {
                            refreshTts(enginePreference, voicePreference,
                                    newValue == null ? null : newValue.toString());
                            return true;
                        }
                    });
            refreshTts(enginePreference, voicePreference,
                    enginePreference.getValue());
        }

        private void addPercentOptions(ListPreference preference, int[] values) {
            if (preference == null || values == null || values.length == 0) {
                return;
            }
            CharSequence[] entries = new CharSequence[values.length];
            CharSequence[] entryValues = new CharSequence[values.length];
            for (int i = 0; i < values.length; i++) {
                entries[i] = values[i] + "%";
                entryValues[i] = String.valueOf(values[i]);
            }
            preference.setEntries(entries);
            preference.setEntryValues(entryValues);
        }

        private void refreshTts(final ListPreference enginePreference,
                final ListPreference voicePreference, String engineName) {
            if (getActivity() == null) {
                return;
            }
            if (tts != null) {
                tts.shutdown();
                tts = null;
            }
            String requestedEngine = TextUtils.isEmpty(engineName) ? null
                    : engineName;
            try {
                tts = new TextToSpeech(getActivity(),
                        new TextToSpeech.OnInitListener() {

                            @Override
                            public void onInit(int status) {
                                doEnginesList(enginePreference);
                                doVoicesList(voicePreference);
                            }
                        }, requestedEngine);
            } catch (RuntimeException e) {
                tts = null;
                doEnginesList(enginePreference);
                doVoicesList(voicePreference);
            }
        }

        private void doEnginesList(ListPreference preference) {
            if (preference == null || getActivity() == null) {
                return;
            }
            PackageManager packageManager = getActivity().getPackageManager();
            List<EngineInfo> engines = new ArrayList<EngineInfo>();
            String defaultEngine = null;
            if (tts != null) {
                defaultEngine = tts.getDefaultEngine();
                List<EngineInfo> queried = tts.getEngines();
                if (queried != null) {
                    engines.addAll(queried);
                }
            }

            Intent engineIntent = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
            int queryFlags = PackageManager.GET_META_DATA;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                queryFlags |= PackageManager.MATCH_ALL;
            }
            List<ResolveInfo> services = packageManager.queryIntentServices(
                    engineIntent, queryFlags);
            if (services != null) {
                for (ResolveInfo service : services) {
                    if (service == null || service.serviceInfo == null) {
                        continue;
                    }
                    String packageName = service.serviceInfo.packageName;
                    CharSequence label = service.loadLabel(packageManager);
                    addEngineIfMissing(engines, packageName,
                            label == null ? packageName : label.toString());
                }
            }
            if (!TextUtils.isEmpty(defaultEngine)) {
                CharSequence label = defaultEngine;
                try {
                    label = packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(defaultEngine, 0));
                } catch (PackageManager.NameNotFoundException e) {
                    // Fall back to package name.
                }
                addEngineIfMissing(engines, defaultEngine, label == null
                        ? defaultEngine : label.toString());
            }
            Collections.sort(engines, new Comparator<EngineInfo>() {
                @Override
                public int compare(EngineInfo o1, EngineInfo o2) {
                    String label1 = o1 == null || o1.label == null ? ""
                            : o1.label.toLowerCase(Locale.getDefault());
                    String label2 = o2 == null || o2.label == null ? ""
                            : o2.label.toLowerCase(Locale.getDefault());
                    return label1.compareTo(label2);
                }
            });

            CharSequence[] entries = new CharSequence[engines.size() + 1];
            CharSequence[] entryValues = new CharSequence[engines.size() + 1];
            entries[0] = getString(R.string.pref_text_to_speech_engine_auto);
            entryValues[0] = "";

            for (int i = 0; i < engines.size(); i++) {
                EngineInfo engine = engines.get(i);
                String label = engine == null || engine.label == null ? ""
                        : engine.label;
                String name = engine == null || engine.name == null ? ""
                        : engine.name;
                entryValues[i + 1] = name.subSequence(0, name.length());
                entries[i + 1] = label.subSequence(0, label.length());
            }

            preference.setEntries(entries);
            preference.setEntryValues(entryValues);
            String currentValue = preference.getValue();
            boolean foundValue = TextUtils.isEmpty(currentValue);
            for (CharSequence value : entryValues) {
                if (TextUtils.equals(currentValue, value)) {
                    foundValue = true;
                    break;
                }
            }
            if (!foundValue) {
                preference.setValue("");
            }
        }

        private void doVoicesList(ListPreference preference) {
            if (preference == null || getActivity() == null) {
                return;
            }
            List<Voice> voices = new ArrayList<Voice>();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP
                    && tts != null) {
                try {
                    java.util.Set<Voice> availableVoices = tts.getVoices();
                    if (availableVoices != null) {
                        voices.addAll(availableVoices);
                    }
                } catch (RuntimeException e) {
                    voices.clear();
                }
            }
            Collections.sort(voices, new Comparator<Voice>() {
                @Override
                public int compare(Voice left, Voice right) {
                    return getVoiceLabel(left).toLowerCase(Locale.getDefault())
                            .compareTo(getVoiceLabel(right).toLowerCase(
                                    Locale.getDefault()));
                }
            });

            CharSequence[] entries = new CharSequence[voices.size() + 1];
            CharSequence[] entryValues = new CharSequence[voices.size() + 1];
            entries[0] = getString(R.string.pref_text_to_speech_voice_auto);
            entryValues[0] = "";
            for (int i = 0; i < voices.size(); i++) {
                Voice voice = voices.get(i);
                entries[i + 1] = getVoiceLabel(voice);
                entryValues[i + 1] = voice == null || voice.getName() == null
                        ? "" : voice.getName();
            }
            preference.setEntries(entries);
            preference.setEntryValues(entryValues);

            String currentValue = preference.getValue();
            boolean foundValue = TextUtils.isEmpty(currentValue);
            for (CharSequence value : entryValues) {
                if (TextUtils.equals(currentValue, value)) {
                    foundValue = true;
                    break;
                }
            }
            if (!foundValue) {
                preference.setValue("");
            }
        }

        private String getVoiceLabel(Voice voice) {
            if (voice == null) {
                return "";
            }
            Locale locale = voice.getLocale();
            String localeLabel = locale == null ? "" : locale.getDisplayName();
            String name = voice.getName();
            if (TextUtils.isEmpty(localeLabel)) {
                return TextUtils.isEmpty(name) ? "" : name;
            }
            if (TextUtils.isEmpty(name)) {
                return localeLabel;
            }
            return localeLabel + " - " + name;
        }

        private void addEngineIfMissing(List<EngineInfo> engines,
                String packageName, String label) {
            if (TextUtils.isEmpty(packageName)) {
                return;
            }
            for (EngineInfo engine : engines) {
                if (engine != null && packageName.equals(engine.name)) {
                    if (TextUtils.isEmpty(engine.label) && !TextUtils.isEmpty(label)) {
                        engine.label = label;
                    }
                    return;
                }
            }
            EngineInfo info = new EngineInfo();
            info.name = packageName;
            info.label = TextUtils.isEmpty(label) ? packageName : label;
            engines.add(info);
        }
    }
}
