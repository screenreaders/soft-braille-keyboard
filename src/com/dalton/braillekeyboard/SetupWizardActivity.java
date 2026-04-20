package com.dalton.braillekeyboard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.googlecode.eyesfree.braille.translate.TableInfo;

import java.util.List;

public class SetupWizardActivity extends Activity
        implements BrailleParser.BrailleParserListener {
    private static final int BLUETOOTH_CONNECT_REQUEST = 30;
    private static final int RECORD_AUDIO_REQUEST = 31;

    private BrailleParser brailleParser;
    private TextView keyboardStatusView;
    private TextView defaultStatusView;
    private TextView brailleStatusView;
    private TextView accessibilityStatusView;
    private TextView permissionsStatusView;
    private Button finishButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_wizard);
        setTitle(R.string.setup_wizard_title);

        keyboardStatusView = (TextView) findViewById(R.id.setup_keyboard_status);
        defaultStatusView = (TextView) findViewById(R.id.setup_default_status);
        brailleStatusView = (TextView) findViewById(R.id.setup_braille_status);
        accessibilityStatusView = (TextView) findViewById(
                R.id.setup_accessibility_status);
        permissionsStatusView = (TextView) findViewById(
                R.id.setup_permissions_status);
        finishButton = (Button) findViewById(R.id.setup_finish_button);

        brailleParser = new BrailleParser(this, this);
        refreshStatuses();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatuses();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (brailleParser != null) {
            brailleParser.destroy();
            brailleParser = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshStatuses();
    }

    @Override
    public void onTranslatorReady(int status) {
        refreshStatuses();
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
                // Keep wizard responsive even if the picker cannot be shown.
            }
        }
    }

    public void onOpenBrailleTranslationSettings(View view) {
        Intent intent = new Intent(this, PreferenceIME.class);
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    public void onOpenAccessibilitySettings(View view) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    public void onOpenBrailleDisplayTools(View view) {
        Intent intent = new Intent(this, BrailleDisplayActivity.class);
        if (canStartActivity(intent)) {
            startActivity(intent);
        }
    }

    public void onRequestRecordAudio(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.RECORD_AUDIO },
                    RECORD_AUDIO_REQUEST);
        } else {
            Toast.makeText(this, R.string.setup_permission_already_granted,
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void onRequestBluetooth(View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(this, R.string.setup_bluetooth_not_required,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.BLUETOOTH_CONNECT },
                    BLUETOOTH_CONNECT_REQUEST);
        } else {
            Toast.makeText(this, R.string.setup_permission_already_granted,
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void onFinishWizard(View view) {
        Options.writeBooleanPreference(this,
                R.string.pref_setup_wizard_completed_key, true);
        finish();
    }

    private void refreshStatuses() {
        boolean enabled = isKeyboardEnabled();
        boolean isDefault = isKeyboardDefault();
        boolean accessibilityEnabled = isBrailleAccessibilityEnabled();
        keyboardStatusView.setText(enabled
                ? R.string.setup_status_keyboard_enabled
                : R.string.setup_status_keyboard_disabled);
        defaultStatusView.setText(isDefault
                ? R.string.setup_status_default_enabled
                : R.string.setup_status_default_disabled);
        accessibilityStatusView.setText(accessibilityEnabled
                ? R.string.setup_status_accessibility_enabled
                : R.string.setup_status_accessibility_disabled);
        permissionsStatusView.setText(buildPermissionsStatus());
        brailleStatusView.setText(buildBrailleTranslationStatus());
        if (finishButton != null) {
            finishButton.setEnabled(true);
        }
    }

    private boolean isKeyboardEnabled() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (inputManager == null) {
            return false;
        }
        try {
            List<InputMethodInfo> list = inputManager.getEnabledInputMethodList();
            if (list == null) {
                return false;
            }
            for (InputMethodInfo info : list) {
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

    private boolean isBrailleAccessibilityEnabled() {
        String enabledServices = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null
                && enabledServices.contains(getPackageName() + "/"
                        + BrailleAccessibilityService.class.getName());
    }

    private String buildPermissionsStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                                ? R.string.setup_status_microphone_granted
                                : R.string.setup_status_microphone_missing));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sb.append('\n');
            sb.append(getString(
                    ContextCompat.checkSelfPermission(this,
                            Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED
                                    ? R.string.setup_status_bluetooth_granted
                                    : R.string.setup_status_bluetooth_missing));
        } else {
            sb.append('\n');
            sb.append(getString(R.string.setup_bluetooth_not_required));
        }
        sb.append('\n');
        sb.append(getString(R.string.setup_status_usb_info));
        return sb.toString();
    }

    private String buildBrailleTranslationStatus() {
        if (brailleParser == null || brailleParser.getStatus() == BrailleParser.STATUS_PREPARING) {
            return getString(R.string.setup_status_braille_loading);
        }
        TableInfo table = brailleParser.getTable(this);
        BrailleParser.BrailleType type = brailleParser.getBrailleType(this);
        if (table == null) {
            return getString(R.string.setup_status_braille_missing);
        }
        String kind = type == BrailleParser.BrailleType.COMPUTER
                ? getString(R.string.grade_computer)
                : getString(R.string.grade_literary);
        return getString(R.string.setup_status_braille_ready,
                kind, table.getId());
    }

    private boolean canStartActivity(Intent intent) {
        return intent != null && getPackageManager() != null
                && intent.resolveActivity(getPackageManager()) != null;
    }
}
