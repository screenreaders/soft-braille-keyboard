package com.dalton.braillekeyboard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.googlecode.eyesfree.braille.translate.TableInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SetupWizardActivity extends Activity
        implements BrailleParser.BrailleParserListener {
    private static final int BLUETOOTH_CONNECT_REQUEST = 30;
    private static final int RECORD_AUDIO_REQUEST = 31;
    private static final int PAGE_KEYBOARD = 0;
    private static final int PAGE_CALIBRATION = 1;
    private static final int PAGE_TTS = 2;
    private static final int PAGE_PROFILE = 3;
    private static final int PAGE_COUNT = 4;
    private static final int MIN_PERCENT = 50;
    private static final int MAX_PERCENT = 200;

    private BrailleParser brailleParser;
    private TextToSpeech tts;
    private boolean bindingValues;
    private boolean suppressEngineCallback;
    private boolean suppressVoiceCallback;
    private int currentPage;

    private ViewFlipper pages;
    private TextView stepIndicatorView;
    private TextView stepIntroView;
    private Button previousButton;
    private Button nextButton;
    private Button finishButton;

    private TextView keyboardStatusView;
    private TextView defaultStatusView;
    private TextView brailleStatusView;
    private Spinner brailleTypeSpinner;
    private Spinner literaryTableSpinner;
    private Spinner computerTableSpinner;
    private Spinner keyboardDotsSpinner;
    private Spinner keyboardLayoutSpinner;
    private Spinner keyboardStyleSpinner;

    private TextView calibrationStatusView;
    private EditText practiceInputView;

    private Spinner ttsEngineSpinner;
    private Spinner ttsVoiceSpinner;
    private TextView ttsStatusView;
    private TextView ttsRateValueView;
    private TextView ttsPitchValueView;
    private TextView ttsVolumeValueView;
    private SeekBar ttsRateSeekBar;
    private SeekBar ttsPitchSeekBar;
    private SeekBar ttsVolumeSeekBar;

    private TextView profileStatusView;
    private Spinner keyboardEchoSpinner;
    private CheckBox misspellingsCheckBox;
    private CheckBox doubleSpaceCheckBox;
    private CheckBox autoCapsCheckBox;
    private CheckBox voiceShortcutCheckBox;
    private CheckBox autoUpdatesCheckBox;
    private CheckBox crashPromptCheckBox;
    private CheckBox usesDisplayCheckBox;
    private TextView accessibilityStatusView;
    private TextView permissionsStatusView;
    private Button microphoneButton;
    private Button bluetoothButton;
    private CheckBox saveProfileCheckBox;
    private EditText profileNameInputView;

    private final List<TableEntry> literaryTables = new ArrayList<TableEntry>();
    private final List<TableEntry> computerTables = new ArrayList<TableEntry>();
    private final List<EngineOption> engineOptions = new ArrayList<EngineOption>();
    private final List<VoiceOption> voiceOptions = new ArrayList<VoiceOption>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_wizard);
        setTitle(R.string.setup_wizard_title);
        bindViews();
        bindStaticSpinners();
        bindListeners();
        brailleParser = new BrailleParser(this, this);
        bindStoredValues();
        refreshStatuses();
        showPage(PAGE_KEYBOARD);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindStoredValues();
        refreshStatuses();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (brailleParser != null) {
            brailleParser.destroy();
            brailleParser = null;
        }
        shutdownTts();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshStatuses();
    }

    @Override
    public void onTranslatorReady(int status) {
        populateTableSpinners();
        refreshStatuses();
    }

    public void onOpenKeyboardSettings(View view) {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    public void onChooseDefaultKeyboard(View view) {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (inputManager != null) {
            try {
                inputManager.showInputMethodPicker();
            } catch (RuntimeException e) {
                // Leave wizard responsive if the picker cannot be shown.
            }
        }
    }

    public void onFocusPracticeField(View view) {
        if (practiceInputView == null) {
            return;
        }
        practiceInputView.requestFocus();
        InputMethodManager inputManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (inputManager != null) {
            inputManager.showSoftInput(practiceInputView,
                    InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void onOpenAccessibilitySettings(View view) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    public void onOpenBrailleDisplayTools(View view) {
        Intent intent = new Intent(this, BrailleDisplayActivity.class);
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    public void onRequestRecordAudio(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.RECORD_AUDIO },
                    RECORD_AUDIO_REQUEST);
        } else {
            Toast.makeText(this, R.string.setup_permission_already_granted,
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void onRequestBluetooth(View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(this, R.string.setup_bluetooth_not_required,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.BLUETOOTH_CONNECT },
                    BLUETOOTH_CONNECT_REQUEST);
        } else {
            Toast.makeText(this, R.string.setup_permission_already_granted,
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void onSpeakTtsPreview(View view) {
        speakPreview();
    }

    public void onPreviousPage(View view) {
        if (currentPage > PAGE_KEYBOARD) {
            showPage(currentPage - 1);
        }
    }

    public void onNextPage(View view) {
        if (currentPage < PAGE_PROFILE) {
            showPage(currentPage + 1);
        }
    }

    public void onFinishWizard(View view) {
        if (saveProfileCheckBox != null && saveProfileCheckBox.isChecked()) {
            String name = profileNameInputView == null
                    || profileNameInputView.getText() == null
                            ? "" : profileNameInputView.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                name = getString(
                        R.string.user_profile_setup_default_profile_name_custom);
            }
            BrailleUserProfiles.saveCurrentProfile(this, name);
        }
        Options.writeBooleanPreference(this,
                R.string.pref_setup_wizard_completed_key, true);
        finish();
    }

    private void bindViews() {
        pages = (ViewFlipper) findViewById(R.id.setup_pages);
        stepIndicatorView = (TextView) findViewById(R.id.setup_step_indicator);
        stepIntroView = (TextView) findViewById(R.id.setup_step_intro);
        previousButton = (Button) findViewById(R.id.setup_prev_button);
        nextButton = (Button) findViewById(R.id.setup_next_button);
        finishButton = (Button) findViewById(R.id.setup_finish_button);

        keyboardStatusView = (TextView) findViewById(R.id.setup_keyboard_status);
        defaultStatusView = (TextView) findViewById(R.id.setup_default_status);
        brailleStatusView = (TextView) findViewById(R.id.setup_braille_status);
        brailleTypeSpinner = (Spinner) findViewById(R.id.setup_braille_type_spinner);
        literaryTableSpinner = (Spinner) findViewById(
                R.id.setup_literary_table_spinner);
        computerTableSpinner = (Spinner) findViewById(
                R.id.setup_computer_table_spinner);
        keyboardDotsSpinner = (Spinner) findViewById(R.id.setup_keyboard_dots_spinner);
        keyboardLayoutSpinner = (Spinner) findViewById(
                R.id.setup_keyboard_layout_spinner);
        keyboardStyleSpinner = (Spinner) findViewById(
                R.id.setup_keyboard_style_spinner);

        calibrationStatusView = (TextView) findViewById(
                R.id.setup_calibration_status);
        practiceInputView = (EditText) findViewById(R.id.setup_practice_input);

        ttsEngineSpinner = (Spinner) findViewById(R.id.setup_tts_engine_spinner);
        ttsVoiceSpinner = (Spinner) findViewById(R.id.setup_tts_voice_spinner);
        ttsStatusView = (TextView) findViewById(R.id.setup_tts_status);
        ttsRateValueView = (TextView) findViewById(R.id.setup_tts_rate_value);
        ttsPitchValueView = (TextView) findViewById(R.id.setup_tts_pitch_value);
        ttsVolumeValueView = (TextView) findViewById(R.id.setup_tts_volume_value);
        ttsRateSeekBar = (SeekBar) findViewById(R.id.setup_tts_rate_seekbar);
        ttsPitchSeekBar = (SeekBar) findViewById(R.id.setup_tts_pitch_seekbar);
        ttsVolumeSeekBar = (SeekBar) findViewById(R.id.setup_tts_volume_seekbar);

        profileStatusView = (TextView) findViewById(R.id.setup_profile_status);
        keyboardEchoSpinner = (Spinner) findViewById(
                R.id.setup_keyboard_echo_spinner);
        misspellingsCheckBox = (CheckBox) findViewById(
                R.id.setup_misspellings_checkbox);
        doubleSpaceCheckBox = (CheckBox) findViewById(
                R.id.setup_double_space_checkbox);
        autoCapsCheckBox = (CheckBox) findViewById(R.id.setup_auto_caps_checkbox);
        voiceShortcutCheckBox = (CheckBox) findViewById(
                R.id.setup_voice_shortcut_checkbox);
        autoUpdatesCheckBox = (CheckBox) findViewById(
                R.id.setup_auto_updates_checkbox);
        crashPromptCheckBox = (CheckBox) findViewById(
                R.id.setup_crash_prompt_checkbox);
        usesDisplayCheckBox = (CheckBox) findViewById(
                R.id.setup_uses_display_checkbox);
        accessibilityStatusView = (TextView) findViewById(
                R.id.setup_accessibility_status);
        permissionsStatusView = (TextView) findViewById(
                R.id.setup_permissions_status);
        microphoneButton = (Button) findViewById(R.id.setup_microphone_button);
        bluetoothButton = (Button) findViewById(R.id.setup_bluetooth_button);
        saveProfileCheckBox = (CheckBox) findViewById(
                R.id.setup_save_profile_checkbox);
        profileNameInputView = (EditText) findViewById(
                R.id.setup_profile_name_input);
    }

    private void bindStaticSpinners() {
        bindSimpleSpinner(brailleTypeSpinner, new String[] {
                getString(R.string.grade_literary),
                getString(R.string.grade_computer)
        });
        bindSimpleSpinner(keyboardDotsSpinner, new String[] {
                getString(R.string.user_profile_setup_dots_six),
                getString(R.string.user_profile_setup_dots_eight)
        });
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
        bindSimpleSpinner(keyboardEchoSpinner, new String[] {
                getString(R.string.keyboard_echo_none),
                getString(R.string.keyboard_echo_character),
                getString(R.string.keyboard_echo_word),
                getString(R.string.keyboard_echo_all)
        });
        setupSeekBar(ttsRateSeekBar, ttsRateValueView,
                R.string.pref_text_to_speech_rate_key,
                R.string.pref_text_to_speech_rate_default);
        setupSeekBar(ttsPitchSeekBar, ttsPitchValueView,
                R.string.pref_text_to_speech_pitch_key,
                R.string.pref_text_to_speech_pitch_default);
        setupSeekBar(ttsVolumeSeekBar, ttsVolumeValueView,
                R.string.pref_text_to_speech_volume_key,
                R.string.pref_text_to_speech_volume_default);
        populateTableSpinners();
        populateEngineSpinner();
    }

    private void bindListeners() {
        brailleTypeSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                            int position, long id) {
                        if (bindingValues) {
                            return;
                        }
                        BrailleParser.BrailleType type = position == 1
                                ? BrailleParser.BrailleType.COMPUTER
                                : BrailleParser.BrailleType.LITERARY;
                        Options.writeStringPreference(SetupWizardActivity.this,
                                R.string.pref_braille_type_key,
                                String.valueOf(type.prefValue()));
                        int dotsSelection = type == BrailleParser.BrailleType.COMPUTER
                                ? 1 : 0;
                        if (keyboardDotsSpinner != null
                                && keyboardDotsSpinner.getSelectedItemPosition()
                                        != dotsSelection) {
                            bindingValues = true;
                            keyboardDotsSpinner.setSelection(dotsSelection);
                            bindingValues = false;
                            persistKeyboardDots();
                        }
                        if (brailleParser != null) {
                            brailleParser.setTranslator(SetupWizardActivity.this);
                        }
                        refreshStatuses();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        literaryTableSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                            int position, long id) {
                        if (bindingValues || position < 0
                                || position >= literaryTables.size()) {
                            return;
                        }
                        Options.writeStringPreference(SetupWizardActivity.this,
                                R.string.pref_braille_literary_table_key,
                                literaryTables.get(position).id);
                        if (brailleParser != null) {
                            brailleParser.setTranslator(SetupWizardActivity.this);
                        }
                        refreshStatuses();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        computerTableSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                            int position, long id) {
                        if (bindingValues || position < 0
                                || position >= computerTables.size()) {
                            return;
                        }
                        Options.writeStringPreference(SetupWizardActivity.this,
                                R.string.pref_braille_computer_table_key,
                                computerTables.get(position).id);
                        if (brailleParser != null) {
                            brailleParser.setTranslator(SetupWizardActivity.this);
                        }
                        refreshStatuses();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        keyboardDotsSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(int position) {
                        persistKeyboardDots();
                        refreshStatuses();
                    }
                });

        keyboardLayoutSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(int position) {
                        Options.writeStringPreference(SetupWizardActivity.this,
                                R.string.pref_default_keyboard_key,
                                String.valueOf(position));
                        refreshStatuses();
                    }
                });

        keyboardStyleSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(int position) {
                        String value = getString(position == 1
                                ? R.string.pref_keyboard_style_slate_value
                                : position == 2
                                        ? R.string.pref_keyboard_style_top_bottom_value
                                        : R.string.pref_keyboard_style_normal_value);
                        Options.writeStringPreference(SetupWizardActivity.this,
                                R.string.pref_keyboard_style_key, value);
                        refreshStatuses();
                    }
                });

        keyboardEchoSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(int position) {
                        Options.writeStringPreference(SetupWizardActivity.this,
                                R.string.pref_echo_feedback_key,
                                String.valueOf(position));
                        refreshStatuses();
                    }
                });

        CompoundButton.OnCheckedChangeListener profileListener
                = new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        if (bindingValues) {
                            return;
                        }
                        persistProfilePreferences();
                        refreshStatuses();
                    }
                };
        if (misspellingsCheckBox != null) {
            misspellingsCheckBox.setOnCheckedChangeListener(profileListener);
        }
        if (doubleSpaceCheckBox != null) {
            doubleSpaceCheckBox.setOnCheckedChangeListener(profileListener);
        }
        if (autoCapsCheckBox != null) {
            autoCapsCheckBox.setOnCheckedChangeListener(profileListener);
        }
        if (voiceShortcutCheckBox != null) {
            voiceShortcutCheckBox.setOnCheckedChangeListener(profileListener);
        }
        if (autoUpdatesCheckBox != null) {
            autoUpdatesCheckBox.setOnCheckedChangeListener(profileListener);
        }
        if (crashPromptCheckBox != null) {
            crashPromptCheckBox.setOnCheckedChangeListener(profileListener);
        }
        if (usesDisplayCheckBox != null) {
            usesDisplayCheckBox.setOnCheckedChangeListener(profileListener);
        }

        ttsEngineSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                            int position, long id) {
                        if (suppressEngineCallback || position < 0
                                || position >= engineOptions.size()) {
                            return;
                        }
                        String engineName = engineOptions.get(position).name;
                        Options.writeStringPreference(SetupWizardActivity.this,
                                R.string.pref_text_to_speech_engine_key,
                                engineName);
                        rebuildTtsForEngine(engineName);
                        refreshStatuses();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        ttsVoiceSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                            int position, long id) {
                        if (suppressVoiceCallback || position < 0
                                || position >= voiceOptions.size()) {
                            return;
                        }
                        String voiceName = voiceOptions.get(position).name;
                        Options.writeStringPreference(SetupWizardActivity.this,
                                R.string.pref_text_to_speech_voice_key,
                                voiceName);
                        applySelectedVoice();
                        refreshStatuses();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });
    }

    private void bindStoredValues() {
        bindingValues = true;
        try {
            BrailleParser.BrailleType brailleType = brailleParser == null
                    ? BrailleParser.BrailleType.LITERARY
                    : brailleParser.getBrailleType(this);
            brailleTypeSpinner.setSelection(brailleType
                    == BrailleParser.BrailleType.COMPUTER ? 1 : 0);
            keyboardDotsSpinner.setSelection(Options.getBooleanPreference(this,
                    R.string.pref_use_eight_dots_key,
                    Boolean.parseBoolean(getString(
                            R.string.pref_use_eight_dots_default))) ? 1 : 0);
            int keyboardType = Options.getIntPreference(this,
                    R.string.pref_default_keyboard_key,
                    getString(R.string.pref_default_keyboard_default));
            keyboardLayoutSpinner.setSelection(Math.max(0,
                    Math.min(keyboardType, 2)));
            String keyboardStyle = Options.getStringPreference(this,
                    R.string.pref_keyboard_style_key,
                    getString(R.string.pref_keyboard_style_normal_value));
            int keyboardStyleSelection = 0;
            if (TextUtils.equals(keyboardStyle,
                    getString(R.string.pref_keyboard_style_slate_value))) {
                keyboardStyleSelection = 1;
            } else if (TextUtils.equals(keyboardStyle,
                    getString(R.string.pref_keyboard_style_top_bottom_value))) {
                keyboardStyleSelection = 2;
            }
            keyboardStyleSpinner.setSelection(keyboardStyleSelection);

            int echoValue = Options.getIntPreference(this,
                    R.string.pref_echo_feedback_key,
                    Options.KeyboardEcho.CHARACTER.getValue());
            keyboardEchoSpinner.setSelection(Math.max(0,
                    Math.min(echoValue, 3)));

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
            if (usesDisplayCheckBox != null) {
                usesDisplayCheckBox.setChecked(Options.getBooleanPreference(this,
                        R.string.pref_user_uses_braille_display_key,
                        Boolean.parseBoolean(getString(
                                R.string.pref_user_uses_braille_display_default))));
            }
            if (saveProfileCheckBox != null) {
                saveProfileCheckBox.setChecked(true);
            }
            if (TextUtils.isEmpty(profileNameInputView == null ? null
                    : profileNameInputView.getText())) {
                if (profileNameInputView != null) {
                    profileNameInputView.setText(getString(
                            R.string.user_profile_setup_default_profile_name_custom));
                }
            }
            populateTableSpinners();
            populateEngineSpinner();
            setupSeekBarValues();
            rebuildTtsForEngine(getSelectedEngineName());
        } finally {
            bindingValues = false;
        }
    }

    private void refreshStatuses() {
        boolean enabled = isKeyboardEnabled();
        boolean isDefault = isKeyboardDefault();
        boolean accessibilityEnabled = isBrailleAccessibilityEnabled();
        if (keyboardStatusView != null) {
            keyboardStatusView.setText(enabled
                    ? R.string.setup_status_keyboard_enabled
                    : R.string.setup_status_keyboard_disabled);
        }
        if (defaultStatusView != null) {
            defaultStatusView.setText(isDefault
                    ? R.string.setup_status_default_enabled
                    : R.string.setup_status_default_disabled);
        }
        if (brailleStatusView != null) {
            brailleStatusView.setText(buildBrailleTranslationStatus());
        }
        if (calibrationStatusView != null) {
            int savedCount = KeyboardCalibrationUtils.countSavedCalibrations(this);
            calibrationStatusView.setText(savedCount > 0
                    ? getString(R.string.setup_status_calibration_saved,
                            savedCount)
                    : getString(R.string.setup_status_calibration_missing));
        }
        if (accessibilityStatusView != null) {
            accessibilityStatusView.setText(buildAccessibilityStatus(
                    accessibilityEnabled));
        }
        if (permissionsStatusView != null) {
            permissionsStatusView.setText(buildPermissionsStatus());
        }
        if (profileStatusView != null) {
            profileStatusView.setText(buildProfileStatus());
        }
        updatePermissionButtons();
        updateNavigationButtons();
    }

    private void updatePermissionButtons() {
        boolean microphoneGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        if (microphoneButton != null) {
            microphoneButton.setEnabled(!microphoneGranted);
        }
        boolean usingBrailleDisplay = usesDisplayCheckBox != null
                && usesDisplayCheckBox.isChecked();
        boolean bluetoothNeeded = usingBrailleDisplay
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED;
        if (bluetoothButton != null) {
            bluetoothButton.setVisibility(bluetoothNeeded ? View.VISIBLE : View.GONE);
        }
    }

    private void updateNavigationButtons() {
        if (previousButton != null) {
            previousButton.setEnabled(currentPage > PAGE_KEYBOARD);
        }
        if (nextButton != null) {
            nextButton.setVisibility(currentPage < PAGE_PROFILE
                    ? View.VISIBLE : View.GONE);
        }
        if (finishButton != null) {
            finishButton.setVisibility(currentPage == PAGE_PROFILE
                    ? View.VISIBLE : View.GONE);
            finishButton.setEnabled(!requiresAccessibilityForTalkBack()
                    || isBrailleAccessibilityEnabled());
        }
    }

    private void showPage(int page) {
        currentPage = Math.max(PAGE_KEYBOARD, Math.min(page, PAGE_PROFILE));
        if (pages != null) {
            pages.setDisplayedChild(currentPage);
        }
        if (stepIndicatorView != null) {
            stepIndicatorView.setText(getString(
                    R.string.setup_step_indicator_value, currentPage + 1,
                    PAGE_COUNT, getString(getStepTitleRes(currentPage))));
        }
        if (stepIntroView != null) {
            stepIntroView.setText(getString(getStepIntroRes(currentPage)));
        }
        updateNavigationButtons();
    }

    private int getStepTitleRes(int page) {
        switch (page) {
        case PAGE_CALIBRATION:
            return R.string.setup_section_calibration;
        case PAGE_TTS:
            return R.string.user_profile_setup_speech_title;
        case PAGE_PROFILE:
            return R.string.setup_section_profile;
        case PAGE_KEYBOARD:
        default:
            return R.string.setup_section_keyboard;
        }
    }

    private int getStepIntroRes(int page) {
        switch (page) {
        case PAGE_CALIBRATION:
            return R.string.setup_intro_calibration;
        case PAGE_TTS:
            return R.string.setup_intro_tts;
        case PAGE_PROFILE:
            return R.string.setup_intro_profile;
        case PAGE_KEYBOARD:
        default:
            return R.string.setup_intro_keyboard;
        }
    }

    private boolean isKeyboardEnabled() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (inputManager == null) {
            return false;
        }
        try {
            List<InputMethodInfo> list = inputManager.getEnabledInputMethodList();
            if (list == null) {
                return false;
            }
            for (InputMethodInfo info : list) {
                if (info != null && getPackageName().equals(info.getPackageName())) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            return false;
        }
        return false;
    }

    private boolean isKeyboardDefault() {
        String id = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        return !TextUtils.isEmpty(id) && id.startsWith(getPackageName());
    }

    private boolean isBrailleAccessibilityEnabled() {
        String enabledServices = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null
                && enabledServices.contains(getPackageName() + "/"
                        + BrailleAccessibilityService.class.getName());
    }

    private boolean requiresAccessibilityForTalkBack() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(
                Context.ACCESSIBILITY_SERVICE);
        return manager != null && manager.isTouchExplorationEnabled();
    }

    private String buildAccessibilityStatus(boolean accessibilityEnabled) {
        if (requiresAccessibilityForTalkBack()) {
            return getString(accessibilityEnabled
                    ? R.string.setup_status_accessibility_enabled_for_talkback
                    : R.string.setup_status_accessibility_required_for_talkback);
        }
        return getString(accessibilityEnabled
                ? R.string.setup_status_accessibility_enabled
                : R.string.setup_status_accessibility_disabled);
    }

    private String buildPermissionsStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
                        ? R.string.setup_status_microphone_granted
                        : R.string.setup_status_microphone_missing));
        boolean usingBrailleDisplay = usesDisplayCheckBox != null
                && usesDisplayCheckBox.isChecked();
        if (!usingBrailleDisplay) {
            sb.append('\n');
            sb.append(getString(R.string.setup_status_braille_display_optional));
            return sb.toString();
        }
        sb.append('\n');
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sb.append(getString(ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED
                            ? R.string.setup_status_bluetooth_granted
                            : R.string.setup_status_bluetooth_missing));
        } else {
            sb.append(getString(R.string.setup_bluetooth_not_required));
        }
        sb.append('\n');
        sb.append(getString(R.string.setup_status_usb_info));
        return sb.toString();
    }

    private String buildBrailleTranslationStatus() {
        if (brailleParser == null
                || brailleParser.getStatus() == BrailleParser.STATUS_PREPARING) {
            return getString(R.string.setup_status_braille_loading);
        }
        TableInfo table = brailleParser.getTable(this);
        BrailleParser.BrailleType type = brailleParser.getBrailleType(this);
        if (table == null) {
            return getString(R.string.setup_status_braille_missing);
        }
        String kind = type == BrailleParser.BrailleType.COMPUTER
                ? getString(R.string.grade_computer)
                : getString(R.string.grade_literary);
        return getString(R.string.setup_status_braille_ready,
                kind, table.getId());
    }

    private String buildProfileStatus() {
        String echoText = keyboardEchoSpinner == null
                || keyboardEchoSpinner.getSelectedItem() == null
                        ? getString(R.string.keyboard_echo_character)
                        : keyboardEchoSpinner.getSelectedItem().toString();
        String engine = getSelectedEngineLabel();
        return getString(R.string.setup_status_profile_summary,
                echoText,
                yesNo(misspellingsCheckBox != null
                        && misspellingsCheckBox.isChecked()),
                yesNo(doubleSpaceCheckBox != null
                        && doubleSpaceCheckBox.isChecked()),
                yesNo(autoUpdatesCheckBox != null
                        && autoUpdatesCheckBox.isChecked()),
                yesNo(crashPromptCheckBox != null
                        && crashPromptCheckBox.isChecked()),
                engine);
    }

    private String yesNo(boolean value) {
        return getString(value ? R.string.main_status_yes : R.string.main_status_no);
    }

    private void persistKeyboardDots() {
        Options.writeBooleanPreference(this, R.string.pref_use_eight_dots_key,
                keyboardDotsSpinner != null
                        && keyboardDotsSpinner.getSelectedItemPosition() == 1);
    }

    private void persistProfilePreferences() {
        Options.writeBooleanPreference(this,
                R.string.pref_echo_misspellings_key,
                misspellingsCheckBox != null && misspellingsCheckBox.isChecked());
        Options.writeBooleanPreference(this,
                R.string.pref_double_space_period_key,
                doubleSpaceCheckBox != null && doubleSpaceCheckBox.isChecked());
        Options.writeBooleanPreference(this, R.string.pref_auto_caps_key,
                autoCapsCheckBox != null && autoCapsCheckBox.isChecked());
        Options.writeBooleanPreference(this, R.string.pref_voice_shortcut_key,
                voiceShortcutCheckBox != null
                        && voiceShortcutCheckBox.isChecked());
        Options.writeBooleanPreference(this,
                R.string.pref_auto_check_updates_key,
                autoUpdatesCheckBox != null && autoUpdatesCheckBox.isChecked());
        Options.writeBooleanPreference(this,
                R.string.pref_prompt_crash_report_key,
                crashPromptCheckBox != null && crashPromptCheckBox.isChecked());
        Options.writeBooleanPreference(this,
                R.string.pref_user_uses_braille_display_key,
                usesDisplayCheckBox != null && usesDisplayCheckBox.isChecked());
    }

    private void populateTableSpinners() {
        literaryTables.clear();
        computerTables.clear();
        if (brailleParser != null && brailleParser.getStatus() == BrailleParser.STATUS_OK) {
            addTableEntries(literaryTables,
                    brailleParser.getTables(BrailleParser.BrailleType.LITERARY));
            addTableEntries(computerTables,
                    brailleParser.getTables(BrailleParser.BrailleType.COMPUTER));
        }
        bindTableSpinner(literaryTableSpinner, literaryTables);
        bindTableSpinner(computerTableSpinner, computerTables);
        selectStoredTable(literaryTableSpinner, literaryTables,
                R.string.pref_braille_literary_table_key,
                R.string.pref_braille_literary_table_default);
        selectStoredTable(computerTableSpinner, computerTables,
                R.string.pref_braille_computer_table_key,
                R.string.pref_braille_computer_table_default);
    }

    private void addTableEntries(List<TableEntry> target, List<TableInfo> infos) {
        if (infos == null) {
            return;
        }
        for (TableInfo info : infos) {
            if (info != null) {
                target.add(new TableEntry(info.getId(), formatTableLabel(info)));
            }
        }
    }

    private void bindTableSpinner(Spinner spinner, List<TableEntry> entries) {
        List<String> labels = new ArrayList<String>();
        if (entries.isEmpty()) {
            labels.add(getString(R.string.setup_status_braille_loading));
        } else {
            for (TableEntry entry : entries) {
                labels.add(entry.label);
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void selectStoredTable(Spinner spinner, List<TableEntry> entries,
            int keyRes, int defaultRes) {
        if (spinner == null || entries.isEmpty()) {
            return;
        }
        String selectedId = Options.getStringPreference(this, keyRes,
                getString(defaultRes));
        int selection = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (TextUtils.equals(entries.get(i).id, selectedId)) {
                selection = i;
                break;
            }
        }
        spinner.setSelection(selection);
    }

    private String formatTableLabel(TableInfo table) {
        Locale locale = table == null || table.getLocale() == null
                ? Locale.getDefault() : table.getLocale();
        String language = locale.getDisplayLanguage(locale);
        String country = locale.getDisplayCountry(locale);
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(language)) {
            builder.append(language);
        }
        if (!TextUtils.isEmpty(country)) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(country);
        }
        if (table != null && !table.isEightDot()) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(getString(R.string.grade_table, table.getGrade()));
        } else if (table != null && table.isEightDot()) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(getString(R.string.grade_computer));
        }
        if (table != null) {
            builder.append(" / ").append(table.getId());
        }
        return builder.toString();
    }

    private void bindSimpleSpinner(Spinner spinner, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void setupSeekBar(SeekBar seekBar, final TextView valueView,
            final int keyRes, int defaultRes) {
        if (seekBar == null || valueView == null) {
            return;
        }
        int currentValue = clampPercent(Options.getIntPreference(this, keyRes,
                getString(defaultRes)));
        seekBar.setMax(MAX_PERCENT - MIN_PERCENT);
        seekBar.setProgress(currentValue - MIN_PERCENT);
        updatePercentLabel(valueView, currentValue);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                int value = progress + MIN_PERCENT;
                updatePercentLabel(valueView, value);
                if (fromUser) {
                    Options.writeStringPreference(SetupWizardActivity.this,
                            keyRes, String.valueOf(value));
                    applyCurrentSpeechParameters();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void setupSeekBarValues() {
        if (ttsRateSeekBar != null) {
            ttsRateSeekBar.setProgress(clampPercent(Options.getIntPreference(this,
                    R.string.pref_text_to_speech_rate_key,
                    getString(R.string.pref_text_to_speech_rate_default)))
                    - MIN_PERCENT);
        }
        if (ttsPitchSeekBar != null) {
            ttsPitchSeekBar.setProgress(clampPercent(Options.getIntPreference(this,
                    R.string.pref_text_to_speech_pitch_key,
                    getString(R.string.pref_text_to_speech_pitch_default)))
                    - MIN_PERCENT);
        }
        if (ttsVolumeSeekBar != null) {
            ttsVolumeSeekBar.setProgress(clampPercent(Options.getIntPreference(this,
                    R.string.pref_text_to_speech_volume_key,
                    getString(R.string.pref_text_to_speech_volume_default)))
                    - MIN_PERCENT);
        }
        updatePercentLabel(ttsRateValueView,
                getSeekPercent(ttsRateSeekBar));
        updatePercentLabel(ttsPitchValueView,
                getSeekPercent(ttsPitchSeekBar));
        updatePercentLabel(ttsVolumeValueView,
                getSeekPercent(ttsVolumeSeekBar));
    }

    private void populateEngineSpinner() {
        engineOptions.clear();
        engineOptions.add(new EngineOption("",
                getString(R.string.pref_text_to_speech_engine_auto)));
        PackageManager packageManager = getPackageManager();
        Intent engineIntent = new Intent(
                TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
        int queryFlags = PackageManager.GET_META_DATA;
        if (Build.VERSION.SDK_INT >= 23) {
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
                addEngineOption(packageName,
                        label == null ? packageName : label.toString());
            }
        }
        Collections.sort(engineOptions.subList(1, engineOptions.size()),
                new Comparator<EngineOption>() {
                    @Override
                    public int compare(EngineOption left, EngineOption right) {
                        return left.label.compareToIgnoreCase(right.label);
                    }
                });
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, getEngineLabels());
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        ttsEngineSpinner.setAdapter(adapter);
        setEngineSelection(getSelectedEngineName());
    }

    private void addEngineOption(String packageName, String label) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        for (EngineOption option : engineOptions) {
            if (TextUtils.equals(option.name, packageName)) {
                return;
            }
        }
        engineOptions.add(new EngineOption(packageName,
                TextUtils.isEmpty(label) ? packageName : label));
    }

    private void setEngineSelection(String engineName) {
        suppressEngineCallback = true;
        try {
            int selection = 0;
            for (int i = 0; i < engineOptions.size(); i++) {
                if (TextUtils.equals(engineOptions.get(i).name, engineName)) {
                    selection = i;
                    break;
                }
            }
            ttsEngineSpinner.setSelection(selection);
        } finally {
            suppressEngineCallback = false;
        }
    }

    private void rebuildTtsForEngine(String engineName) {
        shutdownTts();
        populateVoiceSpinner(null);
        if (ttsStatusView != null) {
            ttsStatusView.setText(R.string.pref_text_to_speech_loading_voices);
        }
        final String requestedEngine = TextUtils.isEmpty(engineName) ? null
                : engineName;
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (tts == null) {
                        return;
                    }
                    if (status == TextToSpeech.SUCCESS) {
                        applyCurrentSpeechParameters();
                        populateVoiceSpinner(tts);
                        if (ttsStatusView != null) {
                            ttsStatusView.setText(
                                    R.string.pref_text_to_speech_ready);
                        }
                    } else {
                        populateVoiceSpinner(null);
                        if (ttsStatusView != null) {
                            ttsStatusView.setText(
                                    R.string.pref_text_to_speech_engine_unavailable);
                        }
                    }
                }
            }, requestedEngine);
        } catch (RuntimeException e) {
            tts = null;
            populateVoiceSpinner(null);
            if (ttsStatusView != null) {
                ttsStatusView.setText(
                        R.string.pref_text_to_speech_engine_unavailable);
            }
        }
    }

    private void populateVoiceSpinner(TextToSpeech activeTts) {
        voiceOptions.clear();
        voiceOptions.add(new VoiceOption("",
                getString(R.string.pref_text_to_speech_voice_auto)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && activeTts != null) {
            try {
                Set<Voice> voices = activeTts.getVoices();
                if (voices != null) {
                    for (Voice voice : voices) {
                        if (voice == null || TextUtils.isEmpty(voice.getName())) {
                            continue;
                        }
                        voiceOptions.add(new VoiceOption(voice.getName(),
                                getVoiceLabel(voice)));
                    }
                }
            } catch (RuntimeException e) {
                voiceOptions.clear();
                voiceOptions.add(new VoiceOption("",
                        getString(R.string.pref_text_to_speech_voice_auto)));
            }
        }
        Collections.sort(voiceOptions.subList(1, voiceOptions.size()),
                new Comparator<VoiceOption>() {
                    @Override
                    public int compare(VoiceOption left, VoiceOption right) {
                        return left.label.compareToIgnoreCase(right.label);
                    }
                });
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, getVoiceLabels());
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        ttsVoiceSpinner.setAdapter(adapter);
        String selectedVoiceName = Options.getStringPreference(this,
                R.string.pref_text_to_speech_voice_key,
                getString(R.string.pref_text_to_speech_voice_default));
        boolean found = false;
        for (VoiceOption option : voiceOptions) {
            if (TextUtils.equals(option.name, selectedVoiceName)) {
                found = true;
                break;
            }
        }
        if (!found) {
            selectedVoiceName = "";
            Options.writeStringPreference(this,
                    R.string.pref_text_to_speech_voice_key, "");
        }
        setVoiceSelection(selectedVoiceName);
        applySelectedVoice();
    }

    private void setVoiceSelection(String voiceName) {
        suppressVoiceCallback = true;
        try {
            int selection = 0;
            for (int i = 0; i < voiceOptions.size(); i++) {
                if (TextUtils.equals(voiceOptions.get(i).name, voiceName)) {
                    selection = i;
                    break;
                }
            }
            ttsVoiceSpinner.setSelection(selection);
        } finally {
            suppressVoiceCallback = false;
        }
    }

    private void applyCurrentSpeechParameters() {
        if (tts == null) {
            return;
        }
        tts.setSpeechRate(getSeekPercent(ttsRateSeekBar) / 100f);
        tts.setPitch(getSeekPercent(ttsPitchSeekBar) / 100f);
    }

    private void applySelectedVoice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || tts == null) {
            return;
        }
        String selectedVoiceName = Options.getStringPreference(this,
                R.string.pref_text_to_speech_voice_key,
                getString(R.string.pref_text_to_speech_voice_default));
        if (TextUtils.isEmpty(selectedVoiceName)) {
            return;
        }
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) {
                return;
            }
            for (Voice voice : voices) {
                if (voice != null && TextUtils.equals(voice.getName(),
                        selectedVoiceName)) {
                    tts.setVoice(voice);
                    return;
                }
            }
        } catch (RuntimeException e) {
            // Ignore engine-specific voice failures.
        }
    }

    private void speakPreview() {
        if (tts == null) {
            return;
        }
        applyCurrentSpeechParameters();
        applySelectedVoice();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,
                    getSeekPercent(ttsVolumeSeekBar) / 100f);
            tts.speak(getString(R.string.pref_text_to_speech_preview_text),
                    TextToSpeech.QUEUE_FLUSH, params, "setup-preview");
        } else {
            tts.speak(getString(R.string.pref_text_to_speech_preview_text),
                    TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private void shutdownTts() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    private List<String> getEngineLabels() {
        List<String> labels = new ArrayList<String>(engineOptions.size());
        for (EngineOption option : engineOptions) {
            labels.add(option.label);
        }
        return labels;
    }

    private List<String> getVoiceLabels() {
        List<String> labels = new ArrayList<String>(voiceOptions.size());
        for (VoiceOption option : voiceOptions) {
            labels.add(option.label);
        }
        return labels;
    }

    private String getSelectedEngineName() {
        String value = Options.getStringPreference(this,
                R.string.pref_text_to_speech_engine_key, "");
        return value == null ? "" : value;
    }

    private String getSelectedEngineLabel() {
        if (ttsEngineSpinner == null || ttsEngineSpinner.getSelectedItem() == null) {
            return getString(R.string.pref_text_to_speech_engine_auto);
        }
        return ttsEngineSpinner.getSelectedItem().toString();
    }

    private String getVoiceLabel(Voice voice) {
        if (voice == null) {
            return "";
        }
        Locale locale = voice.getLocale();
        String localeLabel = locale == null ? ""
                : locale.getDisplayName(locale);
        if (TextUtils.isEmpty(localeLabel)) {
            localeLabel = getString(
                    R.string.pref_text_to_speech_voice_unknown_locale);
        }
        return TextUtils.isEmpty(voice.getName()) ? localeLabel
                : localeLabel + " - " + voice.getName();
    }

    private int getSeekPercent(SeekBar seekBar) {
        return clampPercent((seekBar == null ? 0 : seekBar.getProgress())
                + MIN_PERCENT);
    }

    private void updatePercentLabel(TextView view, int value) {
        if (view != null) {
            view.setText(getString(R.string.pref_text_to_speech_percent_value,
                    clampPercent(value)));
        }
    }

    private int clampPercent(int value) {
        if (value < MIN_PERCENT) {
            return MIN_PERCENT;
        }
        if (value > MAX_PERCENT) {
            return MAX_PERCENT;
        }
        return value;
    }

    private boolean canStartActivity(Intent intent) {
        return intent != null && getPackageManager() != null
                && intent.resolveActivity(getPackageManager()) != null;
    }

    private abstract class SimpleItemSelectedListener
            implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view,
                int position, long id) {
            if (!bindingValues) {
                onItemSelected(position);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }

        public abstract void onItemSelected(int position);
    }

    private static final class TableEntry {
        final String id;
        final String label;

        TableEntry(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private static final class EngineOption {
        final String name;
        final String label;

        EngineOption(String name, String label) {
            this.name = name;
            this.label = label;
        }
    }

    private static final class VoiceOption {
        final String name;
        final String label;

        VoiceOption(String name, String label) {
            this.name = name;
            this.label = label;
        }
    }
}
