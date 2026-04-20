package com.dalton.braillekeyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

public class BrailleKeyboardTestActivity extends Activity {
    private TextView statusView;
    private EditText practiceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_braille_keyboard_test);
        setTitle(R.string.braille_keyboard_test_title);
        statusView = (TextView) findViewById(R.id.braille_keyboard_test_status);
        practiceView = (EditText) findViewById(R.id.braille_keyboard_test_input);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    public void onOpenKeyboardSettings(View view) {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    public void onChooseDefaultKeyboard(View view) {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (inputManager != null) {
            try {
                inputManager.showInputMethodPicker();
            } catch (RuntimeException e) {
                // Keep the test screen responsive if the system picker fails.
            }
        }
    }

    public void onFocusPracticeField(View view) {
        if (practiceView == null) {
            return;
        }
        practiceView.requestFocus();
        InputMethodManager inputManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (inputManager != null) {
            inputManager.showSoftInput(practiceView,
                    InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void onDone(View view) {
        finish();
    }

    private void refreshStatus() {
        if (statusView == null) {
            return;
        }
        boolean enabled = isKeyboardEnabled();
        boolean isDefault = isKeyboardDefault();
        int calibrationCount = KeyboardCalibrationUtils.countSavedCalibrations(this);
        String calibrationStatus = calibrationCount > 0
                ? getString(R.string.setup_status_calibration_saved,
                        calibrationCount)
                : getString(R.string.setup_status_calibration_missing);
        statusView.setText(getString(R.string.braille_keyboard_test_status_template,
                yesNo(enabled), yesNo(isDefault), calibrationStatus));
    }

    private boolean isKeyboardEnabled() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (inputManager == null) {
            return false;
        }
        try {
            java.util.List<android.view.inputmethod.InputMethodInfo> list
                    = inputManager.getEnabledInputMethodList();
            if (list == null) {
                return false;
            }
            for (android.view.inputmethod.InputMethodInfo info : list) {
                if (info != null && getPackageName().equals(info.getPackageName())) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            return false;
        }
        return false;
    }

    private boolean isKeyboardDefault() {
        String id = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        return !TextUtils.isEmpty(id) && id.startsWith(getPackageName());
    }

    private String yesNo(boolean value) {
        return getString(value ? R.string.main_status_yes : R.string.main_status_no);
    }

    private boolean canStartActivity(Intent intent) {
        return intent != null && getPackageManager() != null
                && intent.resolveActivity(getPackageManager()) != null;
    }
}
