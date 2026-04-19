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
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;

import java.io.IOException;

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
    private volatile boolean updateCheckInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        maybeRequestBluetoothPermission();
        updateUIStates();
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
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    // Triggered when the button to change default input method is pressed.
    public void onDefaultInputMethod(View view) {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (inputManager != null) {
            try {
                inputManager.showInputMethodPicker();
            } catch (RuntimeException e) {
                // Ignore picker launch failure and leave UI responsive.
            }
        }
    }

    // Triggered when the user clicks the button to read the manual.
    // Prefer the bundled offline quick start guide and only fall back to the
    // browser if the in-app guide cannot be opened.
    public void onURL(View view) {
        Intent intent = new Intent(this, QuickStartActivity.class);
        if (canStartActivity(intent)) {
            startActivity(intent);
            return;
        }
        Intent browserIntent = new Intent(Intent.ACTION_VIEW);
        browserIntent.setData(Uri.parse(getString(R.string.info_url)));
        if (canStartActivity(browserIntent)) {
            startActivity(browserIntent);
        }
    }

    public void onBrailleDisplays(View view) {
        Intent intent = new Intent(this, BrailleDisplayActivity.class);
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    public void onAccessibilitySettings(View view) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    public void onBrailleLearn(View view) {
        Intent intent = new Intent(this, BrailleLearnActivity.class);
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    public void onBrailleNotes(View view) {
        Intent intent = new Intent(this, BrailleNotesActivity.class);
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    public void onCheckForUpdates(View view) {
        if (updateCheckInProgress) {
            return;
        }
        updateCheckInProgress = true;
        setUpdateCheckEnabled(false);
        Toast.makeText(this, R.string.update_check_in_progress,
                Toast.LENGTH_SHORT).show();
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
                        setUpdateCheckEnabled(true);
                        handleUpdateCheckResult(repoInfo, releaseInfo, error);
                    }
                });
            }
        }).start();
    }

    public void onReportIssue(View view) {
        Intent intent = new Intent(this, SupportReportActivity.class);
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_braille_displays) {
            Intent intent = new Intent(this, BrailleDisplayActivity.class);
            if (canStartActivity(intent)) {
                startActivity(intent);
            }
            return true;
        }
        if (id == R.id.action_quick_start) {
            Intent intent = new Intent(this, QuickStartActivity.class);
            if (canStartActivity(intent)) {
                startActivity(intent);
            }
            return true;
        }
        if (id == R.id.action_accessibility_settings) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            if (canStartActivity(intent)) {
                startActivity(intent);
            }
            return true;
        }
        if (id == R.id.action_braille_learn) {
            onBrailleLearn(null);
            return true;
        }
        if (id == R.id.action_braille_notes) {
            onBrailleNotes(null);
            return true;
        }
        if (id == R.id.action_check_updates) {
            onCheckForUpdates(null);
            return true;
        }
        if (id == R.id.action_report_issue) {
            onReportIssue(null);
            return true;
        }
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, PreferenceIME.class);
            if (canStartActivity(intent)) {
                startActivity(intent);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean canStartActivity(Intent intent) {
        return intent != null && getPackageManager() != null
                && intent.resolveActivity(getPackageManager()) != null;
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
            new AlertDialog.Builder(this)
                    .setTitle(R.string.update_up_to_date_title)
                    .setMessage(getString(R.string.update_up_to_date_message,
                            installedVersion, latestVersion))
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton(R.string.update_action_open_release,
                            new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(
                                        android.content.DialogInterface dialog,
                                        int which) {
                                    openUri(releasePage);
                                }
                            })
                    .show();
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
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, fileName);
            downloadManager.enqueue(request);
            Toast.makeText(this, R.string.update_download_started,
                    Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.update_download_fallback,
                    Toast.LENGTH_LONG).show();
            openUri(TextUtils.isEmpty(releaseInfo.htmlUrl)
                    ? releaseInfo.apkUrl : releaseInfo.htmlUrl);
        }
    }

    private void openUri(String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(value));
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    // Update the state of buttons (clickable) or not and decide whether to show
    // the sample text field.
    private void updateUIStates() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        Button btnEnable = (Button) findViewById(R.id.btn_enable);
        Button btnDefaultKeyboard = (Button) findViewById(R.id.btn_default_keyboard);
        EditText text = (EditText) findViewById(R.id.txt_practice);
        if (btnEnable == null || btnDefaultKeyboard == null || text == null) {
            return;
        }
        btnEnable.setEnabled(true);
        btnDefaultKeyboard.setEnabled(false);
        text.setVisibility(View.INVISIBLE);
        if (inputManager == null) {
            return;
        }
        List<InputMethodInfo> list;
        try {
            list = inputManager.getEnabledInputMethodList();
        } catch (RuntimeException e) {
            return;
        }
        if (list == null || list.isEmpty()) {
            return;
        }

        for (InputMethodInfo info : list) {
            if (info != null && getPackageName().equals(info.getPackageName())) {
                // sbk is enabled as an input method, may or may not be default.
                btnEnable.setEnabled(false);
                btnDefaultKeyboard.setEnabled(true);
                String id = Settings.Secure.getString(getContentResolver(),
                        Settings.Secure.DEFAULT_INPUT_METHOD);
                if (info.getId() != null && info.getId().equals(id)) {
                    // SBK is default so disable make sbk default button and
                    // show the sample text field.
                    btnDefaultKeyboard.setEnabled(false);
                    text.setVisibility(View.VISIBLE);
                }
                return;
            }
        }
    }
}
