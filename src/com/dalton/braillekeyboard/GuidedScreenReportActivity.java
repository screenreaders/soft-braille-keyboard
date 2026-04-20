package com.dalton.braillekeyboard;

import android.app.Activity;
import android.content.Intent;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuidedScreenReportActivity extends Activity {
    private static final long STEP_DELAY_MS = 1400L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ScreenStep> steps = new ArrayList<ScreenStep>();
    private final StringBuilder stepLog = new StringBuilder();

    private TextView statusView;
    private Button startButton;
    private Button shareButton;
    private Button sendButton;

    private boolean running;
    private int currentStep;
    private File reportDirectory;
    private File reportZip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guided_screen_report);
        setTitle(R.string.screen_report_title);
        statusView = (TextView) findViewById(R.id.screen_report_status);
        startButton = (Button) findViewById(R.id.screen_report_start_button);
        shareButton = (Button) findViewById(R.id.screen_report_share_button);
        sendButton = (Button) findViewById(R.id.screen_report_send_button);
        buildSteps();
        updateButtons();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        updateButtons();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    public void onStartReport(View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            statusView.setText(R.string.screen_report_requires_android_r);
            return;
        }
        if (!BrailleAccessibilityService.canCaptureScreens()) {
            statusView.setText(R.string.screen_report_enable_accessibility);
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
            return;
        }
        running = true;
        currentStep = 0;
        stepLog.setLength(0);
        reportZip = null;
        reportDirectory = ScreenReportExporter.createReportDirectory(this);
        stepLog.append(getString(R.string.screen_report_log_header));
        stepLog.append("\n\n");
        updateButtons();
        runNextStep();
    }

    public void onShareReport(View view) {
        if (reportZip == null || !reportZip.exists()) {
            Toast.makeText(this, R.string.screen_report_nothing_to_share,
                    Toast.LENGTH_LONG).show();
            return;
        }
        ScreenReportExporter.shareReport(this, reportZip);
    }

    public void onSendReport(View view) {
        if (reportZip == null || !reportZip.exists()) {
            Toast.makeText(this, R.string.screen_report_nothing_to_share,
                    Toast.LENGTH_LONG).show();
            return;
        }
        uploadReportZip(reportZip, true);
    }

    private void buildSteps() {
        steps.clear();
        steps.add(new ScreenStep(getString(R.string.screen_report_step_main),
                new Intent(this, MainActivity.class)));
        steps.add(new ScreenStep(getString(R.string.screen_report_step_setup),
                new Intent(this, SetupWizardActivity.class)));
        steps.add(new ScreenStep(getString(R.string.screen_report_step_settings),
                new Intent(this, PreferenceIME.class)));
        steps.add(new ScreenStep(getString(R.string.screen_report_step_tts),
                new Intent(this, TtsSettingsActivity.class)));
        steps.add(new ScreenStep(getString(R.string.screen_report_step_profiles),
                new Intent(this, BrailleProfilesActivity.class)));
        steps.add(new ScreenStep(getString(R.string.screen_report_step_tables),
                new Intent(this, BrailleTableTestActivity.class)));
        steps.add(new ScreenStep(getString(R.string.screen_report_step_keyboard_test),
                new Intent(this, BrailleKeyboardTestActivity.class)));
        steps.add(new ScreenStep(getString(R.string.screen_report_step_learning),
                new Intent(this, BrailleLearnActivity.class)));
        steps.add(new ScreenStep(getString(R.string.screen_report_step_notes),
                new Intent(this, BrailleNotesActivity.class)));
        steps.add(new ScreenStep(getString(R.string.screen_report_step_display),
                new Intent(this, BrailleDisplayActivity.class)));
        steps.add(new ScreenStep(getString(R.string.screen_report_step_help),
                new Intent(this, QuickStartActivity.class)));
        steps.add(new ScreenStep(getString(R.string.screen_report_step_support),
                new Intent(this, SupportReportActivity.class)));
    }

    private void runNextStep() {
        if (!running) {
            return;
        }
        if (currentStep >= steps.size()) {
            finishReport();
            return;
        }
        final ScreenStep step = steps.get(currentStep);
        statusView.setText(getString(R.string.screen_report_status_running,
                currentStep + 1, steps.size(), step.title));
        Intent intent = new Intent(step.intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                captureStep(step);
            }
        }, STEP_DELAY_MS);
    }

    private void captureStep(final ScreenStep step) {
        if (!running || reportDirectory == null) {
            return;
        }
        final File screenshot = new File(reportDirectory,
                String.format("%02d-%s.png", currentStep + 1,
                        ScreenReportExporter.sanitizeFileSegment(step.title)));
        BrailleAccessibilityService.captureCurrentScreen(screenshot,
                new BrailleAccessibilityService.ScreenshotListener() {
                    @Override
                    public void onSaved(File file) {
                        stepLog.append(step.title).append(": OK -> ")
                                .append(file.getName()).append('\n');
                        currentStep++;
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                runNextStep();
                            }
                        }, 400L);
                    }

                    @Override
                    public void onError(String reason) {
                        stepLog.append(step.title).append(": ERROR -> ")
                                .append(reason).append('\n');
                        currentStep++;
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                runNextStep();
                            }
                        }, 400L);
                    }
                });
    }

    private void finishReport() {
        running = false;
        try {
            ScreenReportExporter.writeTextFile(new File(reportDirectory,
                    "steps.txt"), stepLog.toString());
            ScreenReportExporter.writeTextFile(new File(reportDirectory,
                    "diagnostics.txt"), SupportDiagnostics.buildReport(this,
                    getString(R.string.screen_report_diagnostics_subject), null));
            reportZip = ScreenReportExporter.zipDirectory(reportDirectory);
            statusView.setText(getString(R.string.screen_report_status_done,
                    reportZip.getName()));
            uploadReportZip(reportZip, false);
            Intent intent = new Intent(this, GuidedScreenReportActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (IOException e) {
            statusView.setText(R.string.screen_report_status_failed);
        }
        updateButtons();
    }

    private void updateButtons() {
        if (startButton != null) {
            startButton.setEnabled(!running);
        }
        if (shareButton != null) {
            shareButton.setEnabled(!running && reportZip != null
                    && reportZip.exists());
        }
        if (sendButton != null) {
            sendButton.setEnabled(!running && reportZip != null
                    && reportZip.exists());
        }
        if (!running && statusView != null && TextUtils.isEmpty(statusView.getText())) {
            statusView.setText(R.string.screen_report_intro);
        }
    }

    private void uploadReportZip(final File zipFile, final boolean manualRetry) {
        if (zipFile == null || !zipFile.exists()) {
            return;
        }
        statusView.setText(manualRetry
                ? getString(R.string.screen_report_status_sending_manual)
                : getString(R.string.screen_report_status_sending));
        if (sendButton != null) {
            sendButton.setEnabled(false);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final RemoteReportUploader.UploadResult result
                        = RemoteReportUploader.uploadZipReport(
                                GuidedScreenReportActivity.this,
                                zipFile,
                                "Soft Braille Keyboard guided screen report",
                                "guided-screen-report");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result.success) {
                            statusView.setText(getString(
                                    R.string.screen_report_status_uploaded,
                                    TextUtils.isEmpty(result.serverFile)
                                            ? zipFile.getName()
                                            : result.serverFile));
                            Toast.makeText(GuidedScreenReportActivity.this,
                                    R.string.screen_report_uploaded,
                                    Toast.LENGTH_LONG).show();
                        } else {
                            statusView.setText(getString(
                                    R.string.screen_report_status_upload_failed,
                                    zipFile.getName()));
                            Toast.makeText(GuidedScreenReportActivity.this,
                                    R.string.screen_report_upload_failed,
                                    Toast.LENGTH_LONG).show();
                        }
                        updateButtons();
                    }
                });
            }
        }).start();
    }

    private static final class ScreenStep {
        final String title;
        final Intent intent;

        ScreenStep(String title, Intent intent) {
            this.title = title;
            this.intent = intent;
        }
    }
}
