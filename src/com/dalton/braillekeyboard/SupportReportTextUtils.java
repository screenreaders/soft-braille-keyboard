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

import android.os.Build;
import android.text.TextUtils;

import java.util.Locale;

final class SupportReportTextUtils {
    private SupportReportTextUtils() {
    }

    static String buildReportTitle(SupportReportSender.ReportData data) {
        return data.reportType == SupportReportSender.ReportData.ReportType.BRAILLE_DISPLAY
                ? "[hardware] " + data.subject
                : "[bug] " + data.subject;
    }

    static String buildGitHubBody(SupportReportSender.ReportData data) {
        boolean hardware = data.reportType
                == SupportReportSender.ReportData.ReportType.BRAILLE_DISPLAY;
        StringBuilder body = new StringBuilder();
        body.append(hardware
                ? "Hardware or braille-display report sent from the app.\n\n"
                : "General app report sent from the app.\n\n");
        body.append("Summary\n");
        body.append(data.subject);
        body.append("\n\n");
        body.append("Description\n");
        body.append(data.message);
        body.append("\n\n");
        body.append("Expected result\n");
        body.append("-\n\n");
        body.append("Actual result\n");
        body.append("-\n\n");
        body.append("Steps to reproduce\n");
        body.append("1. \n2. \n3. \n\n");
        body.append("Environment\n");
        body.append("- App version: ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        body.append("- Android: ").append(Build.VERSION.RELEASE)
                .append(" / SDK ").append(Build.VERSION.SDK_INT).append('\n');
        body.append("- Device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n');
        body.append("- Locale: ").append(Locale.getDefault().toLanguageTag())
                .append("\n\n");
        appendReporter(body, data);
        body.append("Diagnostics\n");
        body.append("Paste the diagnostics copied from the app here.\n");
        if (hardware) {
            body.append("\nBraille display\n");
            body.append("- Model / transport:\n");
            body.append("- Connection type: Bluetooth / USB\n");
        }
        return body.toString();
    }

    static String buildRemoteBody(SupportReportSender.ReportData data, String title,
            String diagnostics) {
        StringBuilder remoteBody = new StringBuilder();
        remoteBody.append("Soft Braille Keyboard report\n");
        remoteBody.append("Title: ").append(title).append('\n');
        appendReporter(remoteBody, data);
        remoteBody.append("Type: ").append(data.reportType.name()).append('\n');
        remoteBody.append('\n');
        remoteBody.append("Message\n");
        remoteBody.append("=======\n");
        remoteBody.append(data.message).append('\n');
        appendDiagnostics(remoteBody, diagnostics);
        return remoteBody.toString();
    }

    private static void appendReporter(StringBuilder sb,
            SupportReportSender.ReportData data) {
        if (!TextUtils.isEmpty(data.name) || !TextUtils.isEmpty(data.email)) {
            if (sb.indexOf("Environment\n") >= 0) {
                sb.append("Reporter:\n");
                sb.append(TextUtils.isEmpty(data.name) ? "(not provided)"
                        : data.name);
                if (!TextUtils.isEmpty(data.email)) {
                    sb.append(" <").append(data.email).append(">");
                }
                sb.append("\n\n");
                return;
            }
        }
        sb.append("Reporter: ")
                .append(TextUtils.isEmpty(data.name) ? "(not provided)"
                        : data.name);
        if (!TextUtils.isEmpty(data.email)) {
            sb.append(" <").append(data.email).append('>');
        }
        sb.append('\n');
    }

    private static void appendDiagnostics(StringBuilder sb, String diagnostics) {
        if (TextUtils.isEmpty(diagnostics)) {
            return;
        }
        sb.append('\n');
        sb.append(diagnostics);
        if (!diagnostics.endsWith("\n")) {
            sb.append('\n');
        }
    }
}
