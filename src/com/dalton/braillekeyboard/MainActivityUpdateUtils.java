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

import android.app.DownloadManager;
import android.content.Context;
import android.text.TextUtils;

final class MainActivityUpdateUtils {
    private MainActivityUpdateUtils() {
    }

    static String getInstalledVersionLabel() {
        return BuildConfig.VERSION_NAME;
    }

    static String getReleaseVersionLabel(GitHubReleaseChecker.ReleaseInfo release) {
        if (release == null || TextUtils.isEmpty(release.getDisplayVersion())) {
            return "";
        }
        return release.getDisplayVersion();
    }

    static String getReleasePageUrl(GitHubReleaseChecker.RepoInfo repoInfo,
            GitHubReleaseChecker.ReleaseInfo release) {
        if (release != null && !TextUtils.isEmpty(release.htmlUrl)) {
            return release.htmlUrl;
        }
        if (repoInfo == null) {
            return null;
        }
        return "https://github.com/" + repoInfo.owner + "/" + repoInfo.repo
                + "/releases";
    }

    static String buildAvailableUpdateMessage(Context context,
            GitHubReleaseChecker.ReleaseInfo release) {
        if (release == null) {
            return "";
        }
        int messageId = TextUtils.isEmpty(release.apkUrl)
                ? R.string.update_no_apk_message
                : R.string.update_available_message;
        StringBuilder message = new StringBuilder();
        message.append(context.getString(messageId, getInstalledVersionLabel(),
                getReleaseVersionLabel(release)));
        String notes = trimReleaseNotes(release.body);
        if (!TextUtils.isEmpty(notes)) {
            message.append("\n\n");
            message.append(context.getString(R.string.update_release_notes_label));
            message.append("\n");
            message.append(notes);
        }
        return message.toString();
    }

    static String buildDownloadStatusMessage(Context context, int status,
            long downloaded, long total) {
        if (status == DownloadManager.STATUS_RUNNING && total > 0) {
            int percent = (int) ((downloaded * 100L) / total);
            return context.getString(R.string.update_download_status_running,
                    Integer.valueOf(Math.max(0, Math.min(percent, 100))));
        }
        if (status == DownloadManager.STATUS_RUNNING) {
            return context.getString(R.string.update_download_status_preparing);
        }
        if (status == DownloadManager.STATUS_PENDING
                || status == DownloadManager.STATUS_PAUSED) {
            return context.getString(R.string.update_download_status_preparing);
        }
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            return context.getString(R.string.update_download_status_complete);
        }
        if (status == DownloadManager.STATUS_FAILED) {
            return context.getString(R.string.update_download_status_failed);
        }
        return context.getString(R.string.update_download_status_preparing);
    }

    private static String trimReleaseNotes(String body) {
        if (TextUtils.isEmpty(body)) {
            return "";
        }
        String trimmed = body.trim();
        if (trimmed.length() <= 700) {
            return trimmed;
        }
        return trimmed.substring(0, 700).trim() + "\n…";
    }
}
