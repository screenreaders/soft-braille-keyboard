package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class TtsOptionUtils {
    private TtsOptionUtils() {
    }

    static final class NamedOption {
        final String name;
        final String label;

        NamedOption(String name, String label) {
            this.name = name;
            this.label = label;
        }
    }

    static List<NamedOption> collectInstalledEngines(Context context, String autoLabel) {
        List<NamedOption> options = new ArrayList<NamedOption>();
        options.add(new NamedOption("", autoLabel));
        if (context == null) {
            return options;
        }
        PackageManager packageManager = context.getPackageManager();
        Intent engineIntent = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
        int queryFlags = PackageManager.GET_META_DATA;
        if (Build.VERSION.SDK_INT >= 23) {
            queryFlags |= PackageManager.MATCH_ALL;
        }
        List<ResolveInfo> services = packageManager.queryIntentServices(engineIntent,
                queryFlags);
        if (services != null) {
            for (ResolveInfo service : services) {
                if (service == null || service.serviceInfo == null) {
                    continue;
                }
                String packageName = service.serviceInfo.packageName;
                CharSequence label = service.loadLabel(packageManager);
                addUnique(options, packageName,
                        label == null ? packageName : label.toString());
            }
        }
        sortTail(options);
        return options;
    }

    static List<NamedOption> collectVoices(Context context, TextToSpeech tts,
            String autoLabel, String unknownLocaleLabel) {
        List<NamedOption> options = new ArrayList<NamedOption>();
        options.add(new NamedOption("", autoLabel));
        if (context == null || tts == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return options;
        }
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices != null) {
                for (Voice voice : voices) {
                    if (voice == null || TextUtils.isEmpty(voice.getName())) {
                        continue;
                    }
                    options.add(new NamedOption(voice.getName(),
                            getVoiceLabel(voice, unknownLocaleLabel)));
                }
            }
        } catch (RuntimeException e) {
            options.clear();
            options.add(new NamedOption("", autoLabel));
            return options;
        }
        sortTail(options);
        return options;
    }

    static List<String> getLabels(List<NamedOption> options) {
        List<String> labels = new ArrayList<String>(options == null ? 0 : options.size());
        if (options == null) {
            return labels;
        }
        for (NamedOption option : options) {
            labels.add(option.label);
        }
        return labels;
    }

    static boolean containsName(List<NamedOption> options, String name) {
        if (options == null) {
            return false;
        }
        for (NamedOption option : options) {
            if (TextUtils.equals(option.name, name)) {
                return true;
            }
        }
        return false;
    }

    static String getVoiceLabel(Voice voice, String unknownLocaleLabel) {
        if (voice == null) {
            return "";
        }
        Locale locale = voice.getLocale();
        String localeLabel = locale == null ? "" : locale.getDisplayName(locale);
        if (TextUtils.isEmpty(localeLabel)) {
            localeLabel = unknownLocaleLabel;
        }
        return TextUtils.isEmpty(voice.getName()) ? localeLabel
                : localeLabel + " - " + voice.getName();
    }

    private static void addUnique(List<NamedOption> options, String name, String label) {
        if (options == null || TextUtils.isEmpty(name)) {
            return;
        }
        for (NamedOption option : options) {
            if (TextUtils.equals(option.name, name)) {
                return;
            }
        }
        options.add(new NamedOption(name, TextUtils.isEmpty(label) ? name : label));
    }

    private static void sortTail(List<NamedOption> options) {
        if (options == null || options.size() <= 2) {
            return;
        }
        Collections.sort(options.subList(1, options.size()),
                new Comparator<NamedOption>() {
                    @Override
                    public int compare(NamedOption left, NamedOption right) {
                        return left.label.compareToIgnoreCase(right.label);
                    }
                });
    }
}
