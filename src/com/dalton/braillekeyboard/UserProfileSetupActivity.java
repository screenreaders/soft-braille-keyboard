package com.dalton.braillekeyboard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import com.dalton.braillekeyboard.Options.KeyboardEcho;
import com.dalton.braillekeyboard.Options.KeyboardType;

public class UserProfileSetupActivity extends Activity {
    private Spinner startProfileSpinner;
    private Spinner keyboardEchoSpinner;
    private Spinner keyboardLayoutSpinner;
    private Spinner keyboardStyleSpinner;
    private CheckBox misspellingsCheckBox;
    private CheckBox doubleSpaceCheckBox;
    private CheckBox autoCapsCheckBox;
    private CheckBox voiceShortcutCheckBox;
    private CheckBox autoUpdatesCheckBox;
    private CheckBox crashPromptCheckBox;
    private CheckBox usesBrailleDisplayCheckBox;
    private CheckBox saveNamedProfileCheckBox;
    private EditText profileNameView;
    private TextView ttsSummaryView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile_setup);
        setTitle(R.string.user_profile_setup_title);
        bindViews();
        bindSpinners();
        bindCurrentValues();
    }

    private void bindViews() {
        startProfileSpinner = (Spinner) findViewById(
                R.id.user_profile_start_profile_spinner);
        keyboardEchoSpinner = (Spinner) findViewById(
                R.id.user_profile_keyboard_echo_spinner);
        keyboardLayoutSpinner = (Spinner) findViewById(
                R.id.user_profile_keyboard_layout_spinner);
        keyboardStyleSpinner = (Spinner) findViewById(
                R.id.user_profile_keyboard_style_spinner);
        misspellingsCheckBox = (CheckBox) findViewById(
                R.id.user_profile_misspellings_checkbox);
        doubleSpaceCheckBox = (CheckBox) findViewById(
                R.id.user_profile_double_space_checkbox);
        autoCapsCheckBox = (CheckBox) findViewById(
                R.id.user_profile_auto_caps_checkbox);
        voiceShortcutCheckBox = (CheckBox) findViewById(
                R.id.user_profile_voice_shortcut_checkbox);
        autoUpdatesCheckBox = (CheckBox) findViewById(
                R.id.user_profile_auto_updates_checkbox);
        crashPromptCheckBox = (CheckBox) findViewById(
                R.id.user_profile_crash_prompt_checkbox);
        usesBrailleDisplayCheckBox = (CheckBox) findViewById(
                R.id.user_profile_uses_braille_display_checkbox);
        saveNamedProfileCheckBox = (CheckBox) findViewById(
                R.id.user_profile_save_named_profile_checkbox);
        profileNameView = (EditText) findViewById(
                R.id.user_profile_name_input);
        ttsSummaryView = (TextView) findViewById(R.id.user_profile_tts_summary);
    }

    private void bindSpinners() {
        bindSimpleSpinner(startProfileSpinner, new String[] {
                getString(R.string.user_profile_setup_start_profile_custom),
                getString(R.string.user_profile_setup_start_profile_polish),
                getString(R.string.user_profile_setup_start_profile_english)
        });
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item,
                new String[] {
                        getString(R.string.keyboard_echo_none),
                        getString(R.string.keyboard_echo_character),
                        getString(R.string.keyboard_echo_word),
                        getString(R.string.keyboard_echo_all)
                });
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        keyboardEchoSpinner.setAdapter(adapter);
        bindSimpleSpinner(keyboardLayoutSpinner, new String[] {
                getString(R.string.keyboard_auto),
                getString(R.string.keyboard_vertical),
                getString(R.string.keyboard_horizontal)
        });
        bindSimpleSpinner(keyboardStyleSpinner, new String[] {
                getString(R.string.keyboard_style_normal),
                getString(R.string.keyboard_style_slate),
                getString(R.string.keyboard_style_top_bottom)
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindCurrentValues();
    }

    public void onOpenTtsSettings(View view) {
        startActivity(new Intent(this, TtsSettingsActivity.class));
    }

    public void onSaveProfileSettings(View view) {
        int preset = getSelectedPreset();
        UserProfilePresetUtils.applyPreset(this, preset);
        writeKeyboardEchoPreference();
        syncKeyboardDotsWithBrailleType();
        writeKeyboardOptions();
        writeBooleanOptions();
        maybeSaveNamedProfile(preset);
        setResult(RESULT_OK);
        finish();
    }

    private void bindSimpleSpinner(Spinner spinner, String[] items) {
        if (spinner == null) {
            return;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void bindCurrentValues() {
        if (startProfileSpinner != null) {
            startProfileSpinner.setSelection(0);
        }
        if (keyboardEchoSpinner != null) {
            int echoValue = Options.getIntPreference(this,
                    R.string.pref_echo_feedback_key,
                    KeyboardEcho.CHARACTER.getValue());
            keyboardEchoSpinner.setSelection(Math.max(0,
                    Math.min(echoValue, KeyboardEcho.values().length - 1)));
        }
        if (keyboardLayoutSpinner != null) {
            int keyboardType = Options.getIntPreference(this,
                    R.string.pref_default_keyboard_key,
                    getString(R.string.pref_default_keyboard_default));
            keyboardLayoutSpinner.setSelection(Math.max(0,
                    Math.min(keyboardType, KeyboardType.values().length - 1)));
        }
        if (keyboardStyleSpinner != null) {
            String style = Options.getStringPreference(this,
                    R.string.pref_keyboard_style_key,
                    getString(R.string.pref_keyboard_style_normal_value));
            int selection = 0;
            if (TextUtils.equals(style,
                    getString(R.string.pref_keyboard_style_slate_value))) {
                selection = 1;
            } else if (TextUtils.equals(style,
                    getString(R.string.pref_keyboard_style_top_bottom_value))) {
                selection = 2;
            }
            keyboardStyleSpinner.setSelection(selection);
        }
        if (misspellingsCheckBox != null) {
            misspellingsCheckBox.setChecked(Options.getBooleanPreference(this,
                    R.string.pref_echo_misspellings_key,
                    Boolean.parseBoolean(getString(
                            R.string.pref_echo_misspellings_default))));
        }
        if (doubleSpaceCheckBox != null) {
            doubleSpaceCheckBox.setChecked(Options.getBooleanPreference(this,
                    R.string.pref_double_space_period_key,
                    Boolean.parseBoolean(getString(
                            R.string.pref_double_space_period_default))));
        }
        if (autoCapsCheckBox != null) {
            autoCapsCheckBox.setChecked(Options.getBooleanPreference(this,
                    R.string.pref_auto_caps_key,
                    Boolean.parseBoolean(getString(
                            R.string.pref_auto_caps_default))));
        }
        if (voiceShortcutCheckBox != null) {
            voiceShortcutCheckBox.setChecked(Options.getBooleanPreference(this,
                    R.string.pref_voice_shortcut_key,
                    Boolean.parseBoolean(getString(
                            R.string.pref_voice_shortcut_default))));
        }
        if (autoUpdatesCheckBox != null) {
            autoUpdatesCheckBox.setChecked(Options.getBooleanPreference(this,
                    R.string.pref_auto_check_updates_key,
                    Boolean.parseBoolean(getString(
                            R.string.pref_auto_check_updates_default))));
        }
        if (crashPromptCheckBox != null) {
            crashPromptCheckBox.setChecked(Options.getBooleanPreference(this,
                    R.string.pref_prompt_crash_report_key,
                    Boolean.parseBoolean(getString(
                            R.string.pref_prompt_crash_report_default))));
        }
        if (usesBrailleDisplayCheckBox != null) {
            usesBrailleDisplayCheckBox.setChecked(Options.getBooleanPreference(this,
                    R.string.pref_user_uses_braille_display_key,
                    Boolean.parseBoolean(getString(
                            R.string.pref_user_uses_braille_display_default))));
        }
        if (saveNamedProfileCheckBox != null) {
            saveNamedProfileCheckBox.setChecked(true);
        }
        if (ttsSummaryView != null) {
            String engine = Options.getStringPreference(this,
                    R.string.pref_text_to_speech_engine_key, "");
            String voice = Options.getStringPreference(this,
                    R.string.pref_text_to_speech_voice_key, "");
            ttsSummaryView.setText(getString(
                    R.string.user_profile_setup_tts_summary_value,
                    TextUtils.isEmpty(engine)
                            ? getString(R.string.pref_text_to_speech_engine_auto)
                            : engine,
                    TextUtils.isEmpty(voice)
                            ? getString(R.string.pref_text_to_speech_voice_auto)
                            : voice));
        }
    }

    private int getSelectedPreset() {
        return startProfileSpinner == null ? UserProfilePresetUtils.PRESET_CUSTOM
                : startProfileSpinner.getSelectedItemPosition();
    }

    private void writeKeyboardEchoPreference() {
        KeyboardEcho[] echoes = KeyboardEcho.values();
        int index = keyboardEchoSpinner == null ? KeyboardEcho.CHARACTER.value
                : keyboardEchoSpinner.getSelectedItemPosition();
        index = Math.max(0, Math.min(index, echoes.length - 1));
        Options.writeStringPreference(this, R.string.pref_echo_feedback_key,
                echoes[index].getValue());
    }

    private void writeKeyboardOptions() {
        Options.writeStringPreference(this, R.string.pref_default_keyboard_key,
                String.valueOf(keyboardLayoutSpinner == null ? 0
                        : keyboardLayoutSpinner.getSelectedItemPosition()));
        Options.writeStringPreference(this, R.string.pref_keyboard_style_key,
                getSelectedKeyboardStyleValue());
    }

    private String getSelectedKeyboardStyleValue() {
        if (keyboardStyleSpinner == null) {
            return getString(R.string.pref_keyboard_style_normal_value);
        }
        int selection = keyboardStyleSpinner.getSelectedItemPosition();
        if (selection == 1) {
            return getString(R.string.pref_keyboard_style_slate_value);
        }
        if (selection == 2) {
            return getString(R.string.pref_keyboard_style_top_bottom_value);
        }
        return getString(R.string.pref_keyboard_style_normal_value);
    }

    private void writeBooleanOptions() {
        writeBooleanPreference(R.string.pref_echo_misspellings_key, misspellingsCheckBox);
        writeBooleanPreference(R.string.pref_double_space_period_key, doubleSpaceCheckBox);
        writeBooleanPreference(R.string.pref_auto_caps_key, autoCapsCheckBox);
        writeBooleanPreference(R.string.pref_voice_shortcut_key, voiceShortcutCheckBox);
        writeBooleanPreference(R.string.pref_auto_check_updates_key, autoUpdatesCheckBox);
        writeBooleanPreference(R.string.pref_prompt_crash_report_key, crashPromptCheckBox);
        writeBooleanPreference(R.string.pref_user_uses_braille_display_key,
                usesBrailleDisplayCheckBox);
    }

    private void writeBooleanPreference(int keyRes, CheckBox checkBox) {
        Options.writeBooleanPreference(this, keyRes,
                checkBox != null && checkBox.isChecked());
    }

    private void maybeSaveNamedProfile(int preset) {
        if (saveNamedProfileCheckBox == null || !saveNamedProfileCheckBox.isChecked()) {
            return;
        }
        CharSequence customName = profileNameView == null ? "" : profileNameView.getText();
        String name = UserProfilePresetUtils.resolveProfileName(this, preset, customName);
        BrailleUserProfiles.saveCurrentProfile(this, name);
    }

    private void syncKeyboardDotsWithBrailleType() {
        String brailleType = Options.getStringPreference(this,
                R.string.pref_braille_type_key,
                getString(R.string.pref_braille_type_default));
        Options.writeBooleanPreference(this, R.string.pref_use_eight_dots_key,
                TextUtils.equals(brailleType,
                        String.valueOf(
                                BrailleParser.BrailleType.COMPUTER.prefValue())));
    }
}
