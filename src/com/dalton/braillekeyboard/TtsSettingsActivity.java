package com.dalton.braillekeyboard;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TtsSettingsActivity extends Activity {
    private static final int MIN_PERCENT = 50;
    private static final int MAX_PERCENT = 200;
    private static final int STEP_PERCENT = 5;

    private RadioGroup engineGroup;
    private RadioGroup voiceGroup;
    private TextView statusView;
    private TextView rateLabelView;
    private TextView pitchLabelView;
    private TextView volumeLabelView;
    private SeekBar rateSeekBar;
    private SeekBar pitchSeekBar;
    private SeekBar volumeSeekBar;

    private final List<EngineOption> engineOptions = new ArrayList<EngineOption>();
    private final List<VoiceOption> voiceOptions = new ArrayList<VoiceOption>();

    private TextToSpeech tts;
    private boolean suppressEngineCallback;
    private boolean suppressVoiceCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tts_settings);
        setTitle(R.string.pref_text_to_speech_title);

        engineGroup = (RadioGroup) findViewById(R.id.tts_engine_group);
        voiceGroup = (RadioGroup) findViewById(R.id.tts_voice_group);
        statusView = (TextView) findViewById(R.id.tts_status);
        rateLabelView = (TextView) findViewById(R.id.tts_rate_label);
        pitchLabelView = (TextView) findViewById(R.id.tts_pitch_label);
        volumeLabelView = (TextView) findViewById(R.id.tts_volume_label);
        rateSeekBar = (SeekBar) findViewById(R.id.tts_rate_seekbar);
        pitchSeekBar = (SeekBar) findViewById(R.id.tts_pitch_seekbar);
        volumeSeekBar = (SeekBar) findViewById(R.id.tts_volume_seekbar);

        setupSeekBar(rateSeekBar, rateLabelView,
                R.string.pref_text_to_speech_rate_title,
                R.string.pref_text_to_speech_rate_key,
                R.string.pref_text_to_speech_rate_default,
                R.id.tts_rate_decrease, R.id.tts_rate_increase);
        setupSeekBar(pitchSeekBar, pitchLabelView,
                R.string.pref_text_to_speech_pitch_title,
                R.string.pref_text_to_speech_pitch_key,
                R.string.pref_text_to_speech_pitch_default,
                R.id.tts_pitch_decrease, R.id.tts_pitch_increase);
        setupSeekBar(volumeSeekBar, volumeLabelView,
                R.string.pref_text_to_speech_volume_title,
                R.string.pref_text_to_speech_volume_key,
                R.string.pref_text_to_speech_volume_default,
                R.id.tts_volume_decrease, R.id.tts_volume_increase);

        if (engineGroup != null) {
            engineGroup.setOnCheckedChangeListener(
                    new RadioGroup.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(RadioGroup group,
                                int checkedId) {
                            if (suppressEngineCallback || checkedId == View.NO_ID) {
                                return;
                            }
                            View checkedView = group.findViewById(checkedId);
                            if (!(checkedView instanceof RadioButton)) {
                                return;
                            }
                            Object tag = checkedView.getTag();
                            String engineName = tag == null ? "" : tag.toString();
                            Options.writeStringPreference(
                                    TtsSettingsActivity.this,
                                    R.string.pref_text_to_speech_engine_key,
                                    engineName);
                            rebuildTtsForEngine(engineName);
                        }
                    });
        }

        if (voiceGroup != null) {
            voiceGroup.setOnCheckedChangeListener(
                    new RadioGroup.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(RadioGroup group,
                                int checkedId) {
                            if (suppressVoiceCallback || checkedId == View.NO_ID) {
                                return;
                            }
                            View checkedView = group.findViewById(checkedId);
                            if (!(checkedView instanceof RadioButton)) {
                                return;
                            }
                            Object tag = checkedView.getTag();
                            String voiceName = tag == null ? "" : tag.toString();
                            Options.writeStringPreference(
                                    TtsSettingsActivity.this,
                                    R.string.pref_text_to_speech_voice_key,
                                    voiceName);
                            applySelectedVoice();
                        }
                    });
        }

        Button previewButton = (Button) findViewById(R.id.tts_preview_button);
        if (previewButton != null) {
            previewButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    speakPreview();
                }
            });
        }

        populateEngineGroup();
        rebuildTtsForEngine(getSelectedEngineName());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        shutdownTts();
    }

    private void setupSeekBar(final SeekBar seekBar, final TextView labelView,
            final int titleRes, final int keyRes, int defaultRes,
            int decreaseButtonId, int increaseButtonId) {
        if (seekBar == null || labelView == null) {
            return;
        }
        final int currentValue = clampPercent(Options.getIntPreference(this,
                keyRes, getString(defaultRes)));
        seekBar.setMax(MAX_PERCENT - MIN_PERCENT);
        seekBar.setKeyProgressIncrement(STEP_PERCENT);
        seekBar.setProgress(currentValue - MIN_PERCENT);
        updateParameterLabel(labelView, titleRes, currentValue);
        seekBar.setContentDescription(buildParameterLabel(titleRes, currentValue));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                int value = progress + MIN_PERCENT;
                updateParameterLabel(labelView, titleRes, value);
                seekBar.setContentDescription(buildParameterLabel(titleRes, value));
                if (fromUser) {
                    Options.writeStringPreference(TtsSettingsActivity.this,
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
        seekBar.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    stepSeekBar(seekBar, keyRes, STEP_PERCENT);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    stepSeekBar(seekBar, keyRes, -STEP_PERCENT);
                    return true;
                }
                return false;
            }
        });

        View decreaseButton = findViewById(decreaseButtonId);
        if (decreaseButton != null) {
            decreaseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    stepSeekBar(seekBar, keyRes, -STEP_PERCENT);
                }
            });
        }
        View increaseButton = findViewById(increaseButtonId);
        if (increaseButton != null) {
            increaseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    stepSeekBar(seekBar, keyRes, STEP_PERCENT);
                }
            });
        }
    }

    private void stepSeekBar(SeekBar seekBar, int keyRes, int delta) {
        if (seekBar == null) {
            return;
        }
        int target = clampPercent((seekBar.getProgress() + MIN_PERCENT) + delta);
        seekBar.setProgress(target - MIN_PERCENT);
        Options.writeStringPreference(this, keyRes, String.valueOf(target));
        applyCurrentSpeechParameters();
    }

    private void populateEngineGroup() {
        engineOptions.clear();
        engineOptions.add(new EngineOption("",
                getString(R.string.pref_text_to_speech_engine_auto)));

        PackageManager packageManager = getPackageManager();
        Intent engineIntent = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
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

        populateRadioGroup(engineGroup, engineOptions, getSelectedEngineName());
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

    private void rebuildTtsForEngine(String engineName) {
        shutdownTts();
        populateVoiceGroup(null);
        if (statusView != null) {
            statusView.setText(R.string.pref_text_to_speech_loading_voices);
        }

        final String requestedEngine = TextUtils.isEmpty(engineName)
                ? null : engineName;
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (tts == null) {
                        return;
                    }
                    if (status == TextToSpeech.SUCCESS) {
                        applyCurrentSpeechParameters();
                        populateVoiceGroup(tts);
                        if (statusView != null) {
                            statusView.setText(
                                    R.string.pref_text_to_speech_ready);
                        }
                    } else {
                        populateVoiceGroup(null);
                        if (statusView != null) {
                            statusView.setText(
                                    R.string.pref_text_to_speech_engine_unavailable);
                        }
                    }
                }
            }, requestedEngine);
        } catch (RuntimeException e) {
            tts = null;
            populateVoiceGroup(null);
            if (statusView != null) {
                statusView.setText(
                        R.string.pref_text_to_speech_engine_unavailable);
            }
        }
    }

    private void populateVoiceGroup(TextToSpeech activeTts) {
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
        populateRadioGroup(voiceGroup, voiceOptions, selectedVoiceName);
        applySelectedVoice();
    }

    private <T extends LabeledOption> void populateRadioGroup(RadioGroup group,
            List<T> options, String selectedName) {
        if (group == null) {
            return;
        }
        if (group == engineGroup) {
            suppressEngineCallback = true;
        } else if (group == voiceGroup) {
            suppressVoiceCallback = true;
        }
        try {
            group.removeAllViews();
            for (T option : options) {
                RadioButton button = new RadioButton(this);
                button.setId(View.generateViewId());
                button.setTag(option.getName());
                button.setText(option.getLabel());
                group.addView(button);
                if (TextUtils.equals(option.getName(), selectedName)) {
                    button.setChecked(true);
                }
            }
        } finally {
            if (group == engineGroup) {
                suppressEngineCallback = false;
            } else if (group == voiceGroup) {
                suppressVoiceCallback = false;
            }
        }
    }

    private void applyCurrentSpeechParameters() {
        if (tts == null) {
            return;
        }
        float rate = getSeekPercent(rateSeekBar) / 100f;
        float pitch = getSeekPercent(pitchSeekBar) / 100f;
        tts.setSpeechRate(rate);
        tts.setPitch(pitch);
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
                if (voice != null
                        && TextUtils.equals(voice.getName(), selectedVoiceName)) {
                    tts.setVoice(voice);
                    return;
                }
            }
        } catch (RuntimeException e) {
            // Ignore engine-specific voice enumeration failures.
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
                    getSeekPercent(volumeSeekBar) / 100f);
            tts.speak(getString(R.string.pref_text_to_speech_preview_text),
                    TextToSpeech.QUEUE_FLUSH, params, "preview");
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

    private String getSelectedEngineName() {
        String value = Options.getStringPreference(this,
                R.string.pref_text_to_speech_engine_key, "");
        return value == null ? "" : value;
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

    private void updateParameterLabel(TextView view, int titleRes, int value) {
        if (view != null) {
            view.setText(buildParameterLabel(titleRes, value));
        }
    }

    private String buildParameterLabel(int titleRes, int value) {
        return getString(R.string.pref_text_to_speech_slider_label,
                getString(titleRes), Integer.valueOf(clampPercent(value)));
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

    private interface LabeledOption {
        String getName();

        String getLabel();
    }

    private static class EngineOption implements LabeledOption {
        final String name;
        final String label;

        EngineOption(String name, String label) {
            this.name = name;
            this.label = label;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }

    private static class VoiceOption implements LabeledOption {
        final String name;
        final String label;

        VoiceOption(String name, String label) {
            this.name = name;
            this.label = label;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }
}
