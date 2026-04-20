package com.dalton.braillekeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class ActivityLaunchSmokeTest {

    @Test
    public void mainAndSetupScreensLaunch() {
        launch(MainActivity.class);
        launch(SetupWizardActivity.class);
    }

    @Test
    public void preferenceAndProfileScreensLaunch() {
        launch(PreferenceIME.class);
        launch(UserProfileSetupActivity.class);
        launch(TtsSettingsActivity.class);
    }

    @Test
    public void brailleToolScreensLaunch() {
        launch(BrailleDisplayActivity.class);
        launch(BrailleLearnActivity.class);
        launch(BrailleProfilesActivity.class);
        verifyBrailleTableTestActivity();
        launch(BrailleKeyboardTestActivity.class);
        launch(BrailleNotesActivity.class);
    }

    @Test
    public void supportScreensLaunch() {
        launch(QuickStartActivity.class);
        launch(SupportReportActivity.class);
    }

    private <T extends android.app.Activity> void launch(Class<T> activityClass) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                activityClass);
        try (ActivityScenario<T> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> assertFalse(
                    activityClass.getSimpleName() + " should not be finishing",
                    activity.isFinishing()));
        }
    }

    private void verifyBrailleTableTestActivity() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                BrailleTableTestActivity.class);
        try (ActivityScenario<BrailleTableTestActivity> scenario =
                     ActivityScenario.launch(intent)) {
            AtomicReference<String> currentTable = new AtomicReference<String>();
            AtomicReference<String> statusText = new AtomicReference<String>();

            for (int i = 0; i < 12; i++) {
                scenario.onActivity(activity -> {
                    TextView currentView = activity.findViewById(
                            R.id.braille_table_test_current);
                    TextView statusView = activity.findViewById(
                            R.id.braille_table_test_status);
                    currentTable.set(currentView == null ? null
                            : String.valueOf(currentView.getText()));
                    statusText.set(statusView == null ? null
                            : String.valueOf(statusView.getText()));
                });

                String current = currentTable.get();
                String status = statusText.get();
                if (current != null && current.contains("Current table:")) {
                    break;
                }
                if (status != null && status.equals(
                        ApplicationProvider.getApplicationContext()
                                .getString(R.string.braille_table_test_no_tables))) {
                    break;
                }

                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            assertNotNull("Braille table current value should not be null",
                    currentTable.get());
            assertNotNull("Braille table status should not be null",
                    statusText.get());
            assertFalse("Braille table test should not report no tables",
                    statusText.get().equals(
                            ApplicationProvider.getApplicationContext()
                                    .getString(R.string.braille_table_test_no_tables)));
            assertFalse("Braille table test should show a non-empty current table",
                    currentTable.get().trim().isEmpty());
            assertFalse("Braille table test should not stay on loading text",
                    currentTable.get().equals(
                            ApplicationProvider.getApplicationContext()
                                    .getString(R.string.braille_table_test_loading_tables)));
        }
    }
}
