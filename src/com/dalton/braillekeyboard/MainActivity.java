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

import android.Manifest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.json.JSONException;
import java.nio.charset.StandardCharsets;

/**
 * This is the MainActivity of the application.
 * 
 * This activity is shown when the user opens the app from the app screen. Most
 * of the app's logic is handled as part of the IME, but this activity provides
 * the UI for the user to enable this keyboard, practice in a text field,
 * navigate to the Settings screen and to navigate to the user manual.
 */
public class MainActivity extends Activity {
    private static final int BLUETOOTH_CONNECT_REQUEST = 1;
    private static final int REQUEST_EXPORT_APP_SETTINGS_FILE = 10;
    private static final int REQUEST_IMPORT_APP_SETTINGS_FILE = 11;

    private volatile boolean updateCheckInProgress;
    private boolean wizardAutoLaunched;
    private boolean startupAutoCheckTriggered;
    private boolean pendingCrashPromptShown;
    private AlertDialog updateDownloadDialog;
    private long activeUpdateDownloadId = -1L;
    private String activeUpdateDownloadFallbackUrl;
    private final Handler updateDownloadHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateDownloadPoller = new Runnable() {
        @Override
        public void run() {
            pollUpdateDownloadStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        maybeRequestBluetoothPermission();
        updateUIStates();
        maybeLaunchSetupWizard();
        maybePromptPendingCrashReport();
        maybeCheckForUpdatesOnStartup();
    }

    // Called when we gain or lose focus.
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            maybeRequestBluetoothPermission();
            // If we have focus update the state of our buttons as system
            // settings might have changed.
            updateUIStates();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopUpdateDownloadPolling();
        if (updateDownloadDialog != null) {
            updateDownloadDialog.dismiss();
            updateDownloadDialog = null;
        }
    }

    private void maybeRequestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.BLUETOOTH_CONNECT },
                    BLUETOOTH_CONNECT_REQUEST);
        }
    }

    // Triggered when the user clicks the enable keyboard button.
    public void onKeyboardSettings(View view) {
        Intent intent = new Intent(
                android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    // Triggered when the button to change default input method is pressed.
    public void onDefaultInputMethod(View view) {
        ActivityLaunchUtils.showInputMethodPicker(this);
    }

    // Triggered when the user clicks the button to read the manual.
    // Prefer the bundled offline quick start guide and only fall back to the
    // browser if the in-app guide cannot be opened.
    public void onURL(View view) {
        Intent intent = new Intent(this, QuickStartActivity.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
            return;
        }
        Intent browserIntent = new Intent(Intent.ACTION_VIEW);
        browserIntent.setData(Uri.parse(getString(R.string.info_url)));
        if (ActivityLaunchUtils.canStartActivity(this, browserIntent)) {
            startActivity(browserIntent);
        }
    }

    public void onBrailleDisplays(View view) {
        Intent intent = new Intent(this, BrailleDisplayActivity.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    public void onAccessibilitySettings(View view) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    public void onSetupWizard(View view) {
        Intent intent = new Intent(this, SetupWizardActivity.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    public void onBrailleTranslationSettings(View view) {
        Intent intent = new Intent(this, PreferenceIME.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    public void onBrailleProfiles(View view) {
        Intent intent = new Intent(this, BrailleProfilesActivity.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    public void onAppSettings(View view) {
        Intent intent = new Intent(this, PreferenceIME.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    public void onTtsSettings(View view) {
        Intent intent = new Intent(this, TtsSettingsActivity.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    public void onBrailleLearn(View view) {
        Intent intent = new Intent(this, BrailleLearnActivity.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    public void onBrailleTableTest(View view) {
        Intent intent = new Intent(this, BrailleTableTestActivity.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    public void onBrailleNotes(View view) {
        Intent intent = new Intent(this, BrailleNotesActivity.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    public void onKeyboardCalibrationTest(View view) {
        Intent intent = new Intent(this, BrailleKeyboardTestActivity.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    public void onCheckForUpdates(View view) {
        startUpdateCheck(true);
    }

    private void startUpdateCheck(final boolean userInitiated) {
        if (updateCheckInProgress) {
            return;
        }
        updateCheckInProgress = true;
        if (userInitiated) {
            setUpdateCheckEnabled(false);
            Toast.makeText(this, R.string.update_check_in_progress,
                    Toast.LENGTH_SHORT).show();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final GitHubReleaseChecker.RepoInfo repoInfo
                        = GitHubReleaseChecker.parseRepoInfo(
                                getString(R.string.info_url));
                final GitHubReleaseChecker.ReleaseInfo releaseInfo;
                final Exception error;
                if (repoInfo == null) {
                    error = new IOException("Invalid GitHub repository URL.");
                    releaseInfo = null;
                } else {
                    GitHubReleaseChecker.ReleaseInfo resolvedRelease = null;
                    Exception resolvedError = null;
                    try {
                        resolvedRelease = GitHubReleaseChecker.fetchLatestRelease(
                                repoInfo);
                    } catch (IOException e) {
                        resolvedError = e;
                    } catch (JSONException e) {
                        resolvedError = e;
                    }
                    releaseInfo = resolvedRelease;
                    error = resolvedError;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateCheckInProgress = false;
                        if (userInitiated) {
                            setUpdateCheckEnabled(true);
                            handleUpdateCheckResult(repoInfo, releaseInfo, error);
                        } else {
                            handleSilentUpdateCheckResult(repoInfo, releaseInfo,
                                    error);
                        }
                    }
                });
            }
        }).start();
    }

    public void onReportIssue(View view) {
        Intent intent = new Intent(this, SupportReportActivity.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    public void onGuidedScreenReport(View view) {
        Intent intent = new Intent(this, GuidedScreenReportActivity.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    public void onExportAppSettings(View view) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,
                getString(R.string.app_settings_backup_file_name));
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivityForResult(intent, REQUEST_EXPORT_APP_SETTINGS_FILE);
        } else {
            Toast.makeText(this, R.string.app_settings_backup_export_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    public void onImportAppSettings(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivityForResult(intent, REQUEST_IMPORT_APP_SETTINGS_FILE);
        } else {
            Toast.makeText(this, R.string.app_settings_backup_import_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_APP_SETTINGS_FILE) {
            exportAppSettingsToUri(uri);
        } else if (requestCode == REQUEST_IMPORT_APP_SETTINGS_FILE) {
            importAppSettingsFromUri(uri);
        }
    }

    private void maybeLaunchSetupWizard() {
        if (wizardAutoLaunched || Options.getBooleanPreference(this,
                R.string.pref_setup_wizard_completed_key, false)) {
            return;
        }
        wizardAutoLaunched = true;
        Intent intent = new Intent(this, SetupWizardActivity.class);
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    private void maybeCheckForUpdatesOnStartup() {
        if (startupAutoCheckTriggered || updateCheckInProgress
                || !Options.getBooleanPreference(this,
                        R.string.pref_auto_check_updates_key,
                        Boolean.parseBoolean(getString(
                                R.string.pref_auto_check_updates_default)))) {
            return;
        }
        startupAutoCheckTriggered = true;
        startUpdateCheck(false);
    }

    private void maybePromptPendingCrashReport() {
        if (pendingCrashPromptShown || !Options.getBooleanPreference(this,
                R.string.pref_prompt_crash_report_key,
                Boolean.parseBoolean(getString(
                        R.string.pref_prompt_crash_report_default)))) {
            return;
        }
        final CrashReportStore.PendingCrash pendingCrash
                = CrashReportStore.consumePendingCrash(this);
        if (pendingCrash == null || TextUtils.isEmpty(pendingCrash.details)) {
            return;
        }
        pendingCrashPromptShown = true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_report_prompt_title)
                .setMessage(R.string.crash_report_prompt_message)
                .setPositiveButton(R.string.crash_report_prompt_action,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                Intent intent = new Intent(MainActivity.this,
                                        SupportReportActivity.class);
                                intent.putExtra(
                                        SupportReportActivity.EXTRA_PREFILL_SUBJECT,
                                        getString(
                                                R.string.crash_report_default_subject));
                                intent.putExtra(
                                        SupportReportActivity.EXTRA_PREFILL_MESSAGE,
                                        getString(
                                                R.string.crash_report_default_message));
                                intent.putExtra(
                                        SupportReportSender.EXTRA_ADDITIONAL_DIAGNOSTICS,
                                        pendingCrash.details);
                                if (ActivityLaunchUtils.canStartActivity(
                                        MainActivity.this, intent)) {
                                    startActivity(intent);
                                }
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void exportAppSettingsToUri(Uri uri) {
        if (uri == null) {
            Toast.makeText(this, R.string.app_settings_backup_export_failed,
                    Toast.LENGTH_LONG).show();
            return;
        }
        OutputStream stream = null;
        try {
            String payload = AppSettingsBackup.exportPreferences(this);
            stream = getContentResolver().openOutputStream(uri);
            if (stream == null) {
                Toast.makeText(this, R.string.app_settings_backup_export_failed,
                        Toast.LENGTH_LONG).show();
                return;
            }
            stream.write(payload.getBytes(StandardCharsets.UTF_8));
            stream.flush();
            Toast.makeText(this, getString(R.string.app_settings_backup_exported,
                    uri.toString()), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, R.string.app_settings_backup_export_failed,
                    Toast.LENGTH_LONG).show();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.app_settings_backup_export_failed,
                    Toast.LENGTH_LONG).show();
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // Ignore close failure after export.
                }
            }
        }
    }

    private void importAppSettingsFromUri(Uri uri) {
        if (uri == null) {
            Toast.makeText(this, R.string.app_settings_backup_import_failed,
                    Toast.LENGTH_LONG).show();
            return;
        }
        InputStream stream = null;
        try {
            stream = getContentResolver().openInputStream(uri);
            if (stream == null) {
                Toast.makeText(this, R.string.app_settings_backup_import_failed,
                        Toast.LENGTH_LONG).show();
                return;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            String payload = new String(output.toByteArray(),
                    StandardCharsets.UTF_8);
            int restored = AppSettingsBackup.importPreferences(this, payload);
            if (restored <= 0) {
                Toast.makeText(this, R.string.app_settings_backup_import_empty,
                        Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, getString(R.string.app_settings_backup_imported,
                    restored, uri.toString()), Toast.LENGTH_LONG).show();
            updateUIStates();
        } catch (IOException e) {
            Toast.makeText(this, R.string.app_settings_backup_import_failed,
                    Toast.LENGTH_LONG).show();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.app_settings_backup_import_failed,
                    Toast.LENGTH_LONG).show();
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // Ignore close failure after import.
                }
            }
        }
    }

    private void setUpdateCheckEnabled(boolean enabled) {
        Button button = (Button) findViewById(R.id.btn_check_updates);
        if (button != null) {
            button.setEnabled(enabled);
        }
    }

    private void handleUpdateCheckResult(GitHubReleaseChecker.RepoInfo repoInfo,
            GitHubReleaseChecker.ReleaseInfo releaseInfo, Exception error) {
        if (repoInfo == null) {
            showUpdateErrorDialog(getString(R.string.update_check_invalid_repo));
            return;
        }
        if (error != null) {
            showUpdateErrorDialog(getString(R.string.update_check_error_message));
            return;
        }
        if (releaseInfo == null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.update_no_release_title)
                    .setMessage(R.string.update_no_release_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton(R.string.update_action_open_release,
                            new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(
                                        android.content.DialogInterface dialog,
                                        int which) {
                                    openUri(repoInfo.releasesUrl);
                                }
                            })
                    .show();
            return;
        }

        String installedVersion = TextUtils.isEmpty(BuildConfig.VERSION_NAME)
                ? getString(R.string.update_unknown_version)
                : BuildConfig.VERSION_NAME;
        String latestVersion = TextUtils.isEmpty(releaseInfo.getDisplayVersion())
                ? getString(R.string.update_unknown_version)
                : releaseInfo.getDisplayVersion();

        if (!GitHubReleaseChecker.isNewerThanInstalled(releaseInfo,
                BuildConfig.VERSION_NAME)) {
            final String releasePage = TextUtils.isEmpty(releaseInfo.htmlUrl)
                    ? repoInfo.releasesUrl : releaseInfo.htmlUrl;
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.update_up_to_date_title)
                    .setMessage(getString(R.string.update_up_to_date_message,
                            installedVersion, latestVersion))
                    .setNegativeButton(android.R.string.cancel, null);
            if (!TextUtils.isEmpty(releaseInfo.apkUrl)) {
                final GitHubReleaseChecker.ReleaseInfo currentRelease = releaseInfo;
                builder.setPositiveButton(R.string.update_action_download,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    android.content.DialogInterface dialog,
                                    int which) {
                                startReleaseDownload(currentRelease);
                            }
                        });
                builder.setNeutralButton(R.string.update_action_open_release,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    android.content.DialogInterface dialog,
                                    int which) {
                                openUri(releasePage);
                            }
                        });
            } else {
                builder.setPositiveButton(android.R.string.ok, null)
                        .setNeutralButton(R.string.update_action_open_release,
                                new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(
                                            android.content.DialogInterface dialog,
                                            int which) {
                                        openUri(releasePage);
                                    }
                                });
            }
            builder.show();
            return;
        }

        int messageId = TextUtils.isEmpty(releaseInfo.apkUrl)
                ? R.string.update_no_apk_message
                : R.string.update_available_message;
        StringBuilder message = new StringBuilder(getString(messageId,
                installedVersion, latestVersion));
        if (!TextUtils.isEmpty(releaseInfo.body)) {
            message.append("\n\n")
                    .append(getString(R.string.update_release_notes_label))
                    .append("\n")
                    .append(trimReleaseNotes(releaseInfo.body));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.update_available_title)
                .setMessage(message.toString())
                .setNegativeButton(android.R.string.cancel, null);
        if (TextUtils.isEmpty(releaseInfo.apkUrl)) {
            final String releasePage = TextUtils.isEmpty(releaseInfo.htmlUrl)
                    ? repoInfo.releasesUrl : releaseInfo.htmlUrl;
            builder.setPositiveButton(R.string.update_action_open_release,
                    new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(
                                android.content.DialogInterface dialog,
                                int which) {
                            openUri(releasePage);
                        }
                    });
        } else {
            final GitHubReleaseChecker.ReleaseInfo finalReleaseInfo = releaseInfo;
            builder.setPositiveButton(R.string.update_action_download,
                    new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(
                                android.content.DialogInterface dialog,
                                int which) {
                            startReleaseDownload(finalReleaseInfo);
                        }
                    });
            if (!TextUtils.isEmpty(releaseInfo.htmlUrl)) {
                builder.setNeutralButton(R.string.update_action_open_release,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    android.content.DialogInterface dialog,
                                    int which) {
                                openUri(finalReleaseInfo.htmlUrl);
                            }
                        });
            }
        }
        builder.show();
    }

    private String trimReleaseNotes(String body) {
        if (TextUtils.isEmpty(body)) {
            return "";
        }
        String normalized = body.trim();
        if (normalized.length() <= 600) {
            return normalized;
        }
        return normalized.substring(0, 600).trim() + "\n…";
    }

    private void showUpdateErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.update_check_error_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void startReleaseDownload(
            GitHubReleaseChecker.ReleaseInfo releaseInfo) {
        if (releaseInfo == null || TextUtils.isEmpty(releaseInfo.apkUrl)) {
            openUri(releaseInfo == null ? null : releaseInfo.htmlUrl);
            return;
        }
        DownloadManager downloadManager
                = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            Toast.makeText(this, R.string.update_download_fallback,
                    Toast.LENGTH_LONG).show();
            openUri(TextUtils.isEmpty(releaseInfo.htmlUrl)
                    ? releaseInfo.apkUrl : releaseInfo.htmlUrl);
            return;
        }
        try {
            String fileName = GitHubReleaseChecker.buildApkFileName(
                    getString(R.string.app_name),
                    releaseInfo.getDisplayVersion());
            DownloadManager.Request request = new DownloadManager.Request(
                    Uri.parse(releaseInfo.apkUrl));
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setTitle(getString(R.string.update_download_title,
                    TextUtils.isEmpty(releaseInfo.getDisplayVersion())
                            ? getString(R.string.update_unknown_version)
                            : releaseInfo.getDisplayVersion()));
            request.setDescription(
                    getString(R.string.update_download_description));
            request.setDestinationInExternalFilesDir(this,
                    android.os.Environment.DIRECTORY_DOWNLOADS, fileName);
            long downloadId = downloadManager.enqueue(request);
            showUpdateDownloadDialog(downloadId, releaseInfo);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.update_download_fallback,
                    Toast.LENGTH_LONG).show();
            openUri(TextUtils.isEmpty(releaseInfo.htmlUrl)
                    ? releaseInfo.apkUrl : releaseInfo.htmlUrl);
        }
    }

    private void showUpdateDownloadDialog(long downloadId,
            final GitHubReleaseChecker.ReleaseInfo releaseInfo) {
        stopUpdateDownloadPolling();
        activeUpdateDownloadId = downloadId;
        activeUpdateDownloadFallbackUrl = TextUtils.isEmpty(releaseInfo.htmlUrl)
                ? releaseInfo.apkUrl : releaseInfo.htmlUrl;
        updateDownloadDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.update_download_status_title)
                .setMessage(getString(R.string.update_download_status_preparing))
                .setNegativeButton(R.string.update_action_background, null)
                .setPositiveButton(R.string.update_action_install, null)
                .setNeutralButton(R.string.update_action_open_release,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                openUri(activeUpdateDownloadFallbackUrl);
                            }
                        })
                .create();
        updateDownloadDialog.setOnShowListener(
                new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        Button installButton = updateDownloadDialog.getButton(
                                AlertDialog.BUTTON_POSITIVE);
                        if (installButton != null) {
                            installButton.setEnabled(false);
                            installButton.setOnClickListener(
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            installDownloadedUpdate(
                                                    activeUpdateDownloadId,
                                                    activeUpdateDownloadFallbackUrl);
                                        }
                                    });
                        }
                    }
                });
        updateDownloadDialog.setOnDismissListener(
                new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        stopUpdateDownloadPolling();
                    }
                });
        updateDownloadDialog.show();
        pollUpdateDownloadStatus();
    }

    private void pollUpdateDownloadStatus() {
        if (activeUpdateDownloadId < 0 || updateDownloadDialog == null) {
            return;
        }
        DownloadManager downloadManager
                = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            updateDownloadDialog.setMessage(
                    getString(R.string.update_download_status_failed));
            return;
        }
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(activeUpdateDownloadId);
        Cursor cursor = null;
        try {
            cursor = downloadManager.query(query);
            if (cursor == null || !cursor.moveToFirst()) {
                updateDownloadDialog.setMessage(
                        getString(R.string.update_download_status_failed));
                return;
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_STATUS));
            long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            updateDownloadDialog.setMessage(buildDownloadStatusMessage(status,
                    downloaded, total));
            Button installButton = updateDownloadDialog.getButton(
                    AlertDialog.BUTTON_POSITIVE);
            if (installButton != null) {
                installButton.setEnabled(
                        status == DownloadManager.STATUS_SUCCESSFUL);
            }
            if (status == DownloadManager.STATUS_RUNNING
                    || status == DownloadManager.STATUS_PAUSED
                    || status == DownloadManager.STATUS_PENDING) {
                updateDownloadHandler.postDelayed(updateDownloadPoller, 1000);
            }
        } catch (RuntimeException e) {
            updateDownloadDialog.setMessage(
                    getString(R.string.update_download_status_failed));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String buildDownloadStatusMessage(int status, long downloaded,
            long total) {
        switch (status) {
        case DownloadManager.STATUS_RUNNING:
        case DownloadManager.STATUS_PAUSED:
        case DownloadManager.STATUS_PENDING:
            if (total > 0) {
                int percent = (int) ((downloaded * 100L) / total);
                return getString(R.string.update_download_status_running,
                        Integer.valueOf(Math.max(0, Math.min(percent, 100))));
            }
            return getString(R.string.update_download_status_preparing);
        case DownloadManager.STATUS_SUCCESSFUL:
            return getString(R.string.update_download_status_complete);
        case DownloadManager.STATUS_FAILED:
        default:
            return getString(R.string.update_download_status_failed);
        }
    }

    private void installDownloadedUpdate(long downloadId, String fallbackUrl) {
        if (downloadId < 0) {
            openUri(fallbackUrl);
            return;
        }
        DownloadManager downloadManager
                = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            openUri(fallbackUrl);
            return;
        }
        Uri apkUri = downloadManager.getUriForDownloadedFile(downloadId);
        if (apkUri == null) {
            openUri(fallbackUrl);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, R.string.update_install_permission_required,
                    Toast.LENGTH_LONG).show();
            Intent permissionIntent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            if (ActivityLaunchUtils.canStartActivity(this, permissionIntent)) {
                startActivity(permissionIntent);
            } else {
                openUri(fallbackUrl);
            }
            return;
        }
        Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        installIntent.setData(apkUri);
        installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
        installIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (ActivityLaunchUtils.canStartActivity(this, installIntent)) {
            startActivity(installIntent);
        } else {
            openUri(fallbackUrl);
        }
    }

    private void stopUpdateDownloadPolling() {
        updateDownloadHandler.removeCallbacks(updateDownloadPoller);
        activeUpdateDownloadId = -1L;
        activeUpdateDownloadFallbackUrl = null;
    }

    private void openUri(String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(value));
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    private void handleSilentUpdateCheckResult(
            GitHubReleaseChecker.RepoInfo repoInfo,
            GitHubReleaseChecker.ReleaseInfo releaseInfo, Exception error) {
        if (repoInfo == null || error != null || releaseInfo == null) {
            return;
        }
        if (GitHubReleaseChecker.isNewerThanInstalled(releaseInfo,
                BuildConfig.VERSION_NAME)) {
            handleUpdateCheckResult(repoInfo, releaseInfo, null);
        }
    }

    // Keep the main screen focused on actions, not on a wall of runtime status.
    private void updateUIStates() {
        TextView versionStatus = (TextView) findViewById(R.id.main_version_status);
        Button setupWizardButton = (Button) findViewById(R.id.btn_setup_wizard);
        if (versionStatus != null) {
            versionStatus.setText(getString(R.string.main_version_value,
                    BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        }
        if (setupWizardButton != null) {
            boolean setupCompleted = Options.getBooleanPreference(this,
                    R.string.pref_setup_wizard_completed_key, false);
            setupWizardButton.setVisibility(
                    setupCompleted ? View.GONE : View.VISIBLE);
        }
    }
}
