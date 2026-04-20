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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
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

    private Spinner engineSpinner;
    private Spinner voiceSpinner;
    private TextView statusView;
    private TextView rateValueView;
    private TextView pitchValueView;
    private TextView volumeValueView;
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

        engineSpinner = (Spinner) findViewById(R.id.tts_engine_spinner);
        voiceSpinner = (Spinner) findViewById(R.id.tts_voice_spinner);
        statusView = (TextView) findViewById(R.id.tts_status);
        rateValueView = (TextView) findViewById(R.id.tts_rate_value);
        pitchValueView = (TextView) findViewById(R.id.tts_pitch_value);
        volumeValueView = (TextView) findViewById(R.id.tts_volume_value);
        rateSeekBar = (SeekBar) findViewById(R.id.tts_rate_seekbar);
        pitchSeekBar = (SeekBar) findViewById(R.id.tts_pitch_seekbar);
        volumeSeekBar = (SeekBar) findViewById(R.id.tts_volume_seekbar);
        Button previewButton = (Button) findViewById(R.id.tts_preview_button);

        setupSeekBar(rateSeekBar, rateValueView,
                R.string.pref_text_to_speech_rate_key,
                R.string.pref_text_to_speech_rate_default);
        setupSeekBar(pitchSeekBar, pitchValueView,
                R.string.pref_text_to_speech_pitch_key,
                R.string.pref_text_to_speech_pitch_default);
        setupSeekBar(volumeSeekBar, volumeValueView,
                R.string.pref_text_to_speech_volume_key,
                R.string.pref_text_to_speech_volume_default);

        engineSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                            int position, long id) {
                        if (suppressEngineCallback || position < 0
                                || position >= engineOptions.size()) {
                            return;
                        }
                        String engineName = engineOptions.get(position).name;
                        Options.writeStringPreference(TtsSettingsActivity.this,
                                R.string.pref_text_to_speech_engine_key,
                                engineName);
                        rebuildTtsForEngine(engineName);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        voiceSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                            int position, long id) {
                        if (suppressVoiceCallback || position < 0
                                || position >= voiceOptions.size()) {
                            return;
                        }
                        String voiceName = voiceOptions.get(position).name;
                        Options.writeStringPreference(TtsSettingsActivity.this,
                                R.string.pref_text_to_speech_voice_key,
                                voiceName);
                        applySelectedVoice();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        if (previewButton != null) {
            previewButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    speakPreview();
                }
            });
        }

        populateEngineSpinner();
        rebuildTtsForEngine(getSelectedEngineName());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        shutdownTts();
    }

    private void setupSeekBar(SeekBar seekBar, final TextView valueView,
            final int keyRes, int defaultRes) {
        if (seekBar == null || valueView == null) {
            return;
        }
        final int currentValue = clampPercent(Options.getIntPreference(this,
                keyRes, getString(defaultRes)));
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
    }

    private void populateEngineSpinner() {
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

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, getEngineLabels());
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        engineSpinner.setAdapter(adapter);
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
            engineSpinner.setSelection(selection);
        } finally {
            suppressEngineCallback = false;
        }
    }

    private void rebuildTtsForEngine(String engineName) {
        shutdownTts();
        populateVoiceSpinner(null);
        statusView.setText(R.string.pref_text_to_speech_loading_voices);

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
                        statusView.setText(R.string.pref_text_to_speech_ready);
                    } else {
                        populateVoiceSpinner(null);
                        statusView.setText(
                                R.string.pref_text_to_speech_engine_unavailable);
                    }
                }
            }, requestedEngine);
        } catch (RuntimeException e) {
            tts = null;
            populateVoiceSpinner(null);
            statusView.setText(R.string.pref_text_to_speech_engine_unavailable);
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
        voiceSpinner.setAdapter(adapter);

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
            voiceSpinner.setSelection(selection);
        } finally {
            suppressVoiceCallback = false;
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
                if (voice != null && TextUtils.equals(voice.getName(),
                        selectedVoiceName)) {
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

    private String getVoiceLabel(Voice voice) {
        if (voice == null) {
            return "";
        }
        Locale locale = voice.getLocale();
        String localeLabel = locale == null ? ""
                : locale.getDisplayName(locale);
        if (TextUtils.isEmpty(localeLabel)) {
            localeLabel = getString(R.string.pref_text_to_speech_voice_unknown_locale);
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

    private static class EngineOption {
        final String name;
        final String label;

        EngineOption(String name, String label) {
            this.name = name;
            this.label = label;
        }
    }

    private static class VoiceOption {
        final String name;
        final String label;

        VoiceOption(String name, String label) {
            this.name = name;
            this.label = label;
        }
    }
}
