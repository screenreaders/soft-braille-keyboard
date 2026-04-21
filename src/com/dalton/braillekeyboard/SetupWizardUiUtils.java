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

final class SetupWizardUiUtils {
    private SetupWizardUiUtils() {
    }

    static int clampPage(int page, int minPage, int maxPage) {
        return Math.max(minPage, Math.min(page, maxPage));
    }

    static int getStepTitleRes(int page) {
        switch (page) {
        case SetupWizardActivity.PAGE_CALIBRATION:
            return R.string.setup_section_calibration;
        case SetupWizardActivity.PAGE_TTS:
            return R.string.user_profile_setup_speech_title;
        case SetupWizardActivity.PAGE_PROFILE:
            return R.string.setup_section_profile;
        case SetupWizardActivity.PAGE_KEYBOARD:
        default:
            return R.string.setup_section_keyboard;
        }
    }

    static int getStepIntroRes(int page) {
        switch (page) {
        case SetupWizardActivity.PAGE_CALIBRATION:
            return R.string.setup_intro_calibration;
        case SetupWizardActivity.PAGE_TTS:
            return R.string.setup_intro_tts;
        case SetupWizardActivity.PAGE_PROFILE:
            return R.string.setup_intro_profile;
        case SetupWizardActivity.PAGE_KEYBOARD:
        default:
            return R.string.setup_intro_keyboard;
        }
    }

    static String buildAccessibilityStatus(Context context,
            boolean requiresAccessibilityForTalkBack,
            boolean talkBackBrailleModeEnabled,
            boolean accessibilityEnabled) {
        if (requiresAccessibilityForTalkBack) {
            if (!talkBackBrailleModeEnabled) {
                return context.getString(
                        R.string.setup_status_accessibility_disabled_for_talkback_mode_off);
            }
            return context.getString(accessibilityEnabled
                    ? R.string.setup_status_accessibility_enabled_for_talkback
                    : R.string.setup_status_accessibility_required_for_talkback);
        }
        return context.getString(accessibilityEnabled
                ? R.string.setup_status_accessibility_enabled
                : R.string.setup_status_accessibility_disabled);
    }

    static String buildPermissionsStatus(Context context,
            boolean microphoneGranted,
            boolean usingBrailleDisplay,
            boolean bluetoothGranted,
            boolean bluetoothRuntimeRequired) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.getString(microphoneGranted
                ? R.string.setup_status_microphone_granted
                : R.string.setup_status_microphone_missing));
        if (!usingBrailleDisplay) {
            sb.append('\n');
            sb.append(context.getString(
                    R.string.setup_status_braille_display_optional));
            return sb.toString();
        }
        sb.append('\n');
        if (bluetoothRuntimeRequired) {
            sb.append(context.getString(bluetoothGranted
                    ? R.string.setup_status_bluetooth_granted
                    : R.string.setup_status_bluetooth_missing));
        } else {
            sb.append(context.getString(R.string.setup_bluetooth_not_required));
        }
        sb.append('\n');
        sb.append(context.getString(R.string.setup_status_usb_info));
        return sb.toString();
    }

    static String buildBrailleTranslationStatus(Context context,
            BrailleParser parser) {
        if (parser == null || parser.getStatus() == BrailleParser.STATUS_PREPARING) {
            return context.getString(R.string.setup_status_braille_loading);
        }
        TableInfo table = parser.getTable(context);
        BrailleParser.BrailleType type = parser.getBrailleType(context);
        if (table == null) {
            return context.getString(R.string.setup_status_braille_missing);
        }
        String kind = type == BrailleParser.BrailleType.COMPUTER
                ? context.getString(R.string.grade_computer)
                : context.getString(R.string.grade_literary);
        return context.getString(R.string.setup_status_braille_ready, kind,
                table.getId());
    }

    static String buildProfileStatus(Context context, String echoText,
            boolean misspellings,
            boolean doubleSpace,
            boolean talkBackMode,
            boolean autoUpdates,
            boolean crashPrompt,
            String engineLabel) {
        String safeEcho = TextUtils.isEmpty(echoText)
                ? context.getString(R.string.keyboard_echo_character)
                : echoText;
        String safeEngine = TextUtils.isEmpty(engineLabel)
                ? context.getString(R.string.pref_text_to_speech_engine_auto)
                : engineLabel;
        return context.getString(R.string.setup_status_profile_summary,
                safeEcho,
                StatusTextUtils.yesNo(context, misspellings),
                StatusTextUtils.yesNo(context, doubleSpace),
                StatusTextUtils.yesNo(context, talkBackMode),
                StatusTextUtils.yesNo(context, autoUpdates),
                StatusTextUtils.yesNo(context, crashPrompt),
                safeEngine);
    }
}
