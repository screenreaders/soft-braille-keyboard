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

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.BrailleKeyBinding;
import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.braille.display.DisplayClient;
import com.googlecode.eyesfree.braille.service.display.DeviceFinder;
import org.json.JSONException;
import org.a11y.brltty.android.UsbHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;

public class BrailleDisplayActivity extends Activity {
    private static final int BLUETOOTH_CONNECT_REQUEST = 2;
    private static final int REQUEST_EXPORT_PROFILES_FILE = 3;
    private static final int REQUEST_IMPORT_PROFILES_FILE = 4;
    private static final int MAX_LOG_LINES = 60;

    private final StringBuilder eventLog = new StringBuilder();

    private DisplayClient displayClient;
    private TextView statusView;
    private TextView progressView;
    private TextView serviceView;
    private TextView profileView;
    private TextView namedProfileView;
    private TextView commandView;
    private TextView contentView;
    private TextView remapView;
    private TextView devicesView;
    private TextView displayPropsView;
    private TextView eventLogView;
    private EditText namedProfileNameInput;
    private Spinner namedProfileSpinner;
    private ArrayAdapter<String> namedProfileAdapter;
    private final List<String> namedProfileNames = new java.util.ArrayList<String>();
    private int selectedBindingIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_braille_display);
        setTitle(R.string.braille_displays_title);

        statusView = (TextView) findViewById(R.id.braille_status);
        progressView = (TextView) findViewById(R.id.braille_progress);
        serviceView = (TextView) findViewById(R.id.braille_service_status);
        profileView = (TextView) findViewById(R.id.braille_profile_status);
        namedProfileView = (TextView) findViewById(
                R.id.braille_named_profile_status);
        commandView = (TextView) findViewById(R.id.braille_command_status);
        contentView = (TextView) findViewById(R.id.braille_content_status);
        remapView = (TextView) findViewById(R.id.braille_remap_status);
        devicesView = (TextView) findViewById(R.id.braille_devices);
        displayPropsView = (TextView) findViewById(R.id.braille_properties);
        eventLogView = (TextView) findViewById(R.id.braille_event_log);
        namedProfileNameInput = (EditText) findViewById(
                R.id.braille_named_profile_name);
        namedProfileSpinner = (Spinner) findViewById(
                R.id.braille_named_profile_spinner);
        eventLogView.setMovementMethod(new ScrollingMovementMethod());
        if (namedProfileSpinner != null) {
            namedProfileAdapter = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_item, namedProfileNames);
            namedProfileAdapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item);
            namedProfileSpinner.setAdapter(namedProfileAdapter);
        }

        maybeRequestBluetoothPermission();
        connectDisplayClient();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (displayClient != null) {
            displayClient.shutdown();
            displayClient = null;
        }
    }

    public void onBluetoothSettings(View view) {
        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
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

    public void onRequestUsbPermission(View view) {
        DeviceFinder finder = new DeviceFinder(this);
        List<DeviceFinder.DeviceInfo> devices = finder
                .findSupportedUsbDevicesNeedingPermission();
        if (devices.isEmpty()) {
            appendLog(getString(R.string.braille_log_no_usb_permission_needed));
            return;
        }
        int requested = 0;
        for (DeviceFinder.DeviceInfo info : devices) {
            if (finder.requestUsbPermission(info)) {
                requested++;
            }
        }
        if (requested == 0) {
            appendLog(getString(R.string.braille_log_no_usb_permission_needed));
            return;
        }
        appendLog(getString(R.string.braille_log_usb_permission_requested,
                requested));
        refreshAll();
    }

    public void onRefreshDisplays(View view) {
        maybeRequestBluetoothPermission();
        refreshAll();
    }

    public void onReconnectDisplays(View view) {
        if (displayClient != null) {
            displayClient.poll();
        }
        appendLog(getString(R.string.braille_log_poll_requested));
    }

    public void onSendTestPattern(View view) {
        if (displayClient == null) {
            appendLog(getString(R.string.braille_log_no_client));
            return;
        }

        BrailleDisplayProperties properties = displayClient.getDisplayProperties();
        if (properties == null) {
            appendLog(getString(R.string.braille_log_no_display));
            return;
        }

        int cells = properties.getNumTextCells();
        if (cells <= 0) {
            appendLog(getString(R.string.braille_log_no_cells));
            return;
        }

        byte[] pattern = new byte[cells];
        int[] positions = new int[cells];
        StringBuilder text = new StringBuilder(cells);
        for (int i = 0; i < cells; i++) {
            pattern[i] = (byte) (1 << (i % 8));
            positions[i] = i;
            text.append((char) ('a' + (i % 26)));
        }
        displayClient.displayDots(pattern, text.toString(), positions);
        appendLog(getString(R.string.braille_log_test_pattern_sent, cells));
    }

    public void onCyclePreferredDisplay(View view) {
        List<DeviceFinder.DeviceInfo> devices = getRecognizedDevices();
        if (devices.isEmpty()) {
            appendLog(getString(R.string.braille_log_no_devices_for_profile));
            return;
        }

        String current = BrailleDisplayPreferences.getPreferredDeviceAddress(this);
        int nextIndex = 0;
        if (current != null) {
            for (int i = 0; i < devices.size(); i++) {
                if (TextUtils.equals(current, devices.get(i).getDeviceAddress())) {
                    nextIndex = (i + 1) % devices.size();
                    break;
                }
            }
        }

        DeviceFinder.DeviceInfo target = devices.get(nextIndex);
        BrailleDisplayPreferences.setPreferredDeviceAddress(this,
                target.getDeviceAddress());
        appendLog(getString(R.string.braille_log_preferred_display_set,
                target.getDeviceName(),
                target.getDeviceAddress()));
        refreshAll();
    }

    public void onClearPreferredDisplay(View view) {
        BrailleDisplayPreferences.clearPreferredDeviceAddress(this);
        appendLog(getString(R.string.braille_log_preferred_display_cleared));
        refreshAll();
    }

    public void onCycleDeviceProfileTable(View view) {
        String address = getProfileTargetAddress();
        if (address == null) {
            appendLog(getString(R.string.braille_log_no_devices_for_profile));
            return;
        }
        String[] tables = getResources().getStringArray(R.array.braille_tables);
        if (tables == null || tables.length == 0) {
            appendLog(getString(R.string.braille_log_no_devices_for_profile));
            return;
        }
        String nextTable = BrailleDisplayPreferences.cycleDeviceTable(this,
                address, tables);
        if (nextTable == null) {
            appendLog(getString(R.string.braille_log_profile_table_global,
                    address));
        } else {
            appendLog(getString(R.string.braille_log_profile_table_set,
                    address, nextTable));
        }
        refreshAll();
    }

    public void onClearDeviceProfileTable(View view) {
        String address = getProfileTargetAddress();
        if (address == null) {
            appendLog(getString(R.string.braille_log_no_devices_for_profile));
            return;
        }
        BrailleDisplayPreferences.clearDeviceTable(this, address);
        appendLog(getString(R.string.braille_log_profile_table_global, address));
        refreshAll();
    }

    public void onSaveNamedBrailleProfile(View view) {
        String name = namedProfileNameInput == null ? null
                : namedProfileNameInput.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            appendLog(getString(R.string.braille_named_profiles_name_required));
            return;
        }
        if (BrailleDisplayNamedProfiles.saveCurrentProfile(this, name)) {
            appendLog(getString(R.string.braille_named_profiles_saved, name));
            refreshAll();
            setSelectedNamedProfile(name);
        } else {
            appendLog(getString(R.string.braille_named_profiles_save_failed));
        }
    }

    public void onApplyNamedBrailleProfile(View view) {
        String name = getSelectedNamedProfile();
        if (TextUtils.isEmpty(name)) {
            appendLog(getString(R.string.braille_named_profiles_none));
            return;
        }
        if (BrailleDisplayNamedProfiles.applyProfile(this, name)) {
            appendLog(getString(R.string.braille_named_profiles_applied, name));
            refreshAll();
        } else {
            appendLog(getString(R.string.braille_named_profiles_apply_failed));
        }
    }

    public void onDeleteNamedBrailleProfile(View view) {
        String name = getSelectedNamedProfile();
        if (TextUtils.isEmpty(name)) {
            appendLog(getString(R.string.braille_named_profiles_none));
            return;
        }
        if (BrailleDisplayNamedProfiles.deleteProfile(this, name)) {
            appendLog(getString(R.string.braille_named_profiles_deleted, name));
            refreshAll();
        } else {
            appendLog(getString(R.string.braille_named_profiles_delete_failed));
        }
    }

    public void onCycleNamedBrailleProfile(View view) {
        BrailleDisplayNamedProfiles.Profile profile =
                BrailleDisplayNamedProfiles.cycleToNextProfile(this);
        if (profile == null) {
            appendLog(getString(R.string.braille_named_profiles_none));
            return;
        }
        appendLog(getString(R.string.braille_named_profiles_applied,
                profile.name));
        refreshAll();
        setSelectedNamedProfile(profile.name);
    }

    public void onExportBrailleProfiles(View view) {
        exportBrailleProfilesToClipboard();
    }

    public void onImportBrailleProfiles(View view) {
        importBrailleProfilesFromClipboard();
    }

    public void onShareBrailleProfiles(View view) {
        shareBrailleProfiles();
    }

    public void onExportBrailleProfilesToFile(View view) {
        Intent intent = createProfilesExportIntent();
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivityForResult(intent, REQUEST_EXPORT_PROFILES_FILE);
        } else {
            appendLog(getString(R.string.braille_log_profiles_file_export_failed));
        }
    }

    public void onImportBrailleProfilesFromFile(View view) {
        Intent intent = createProfilesImportIntent();
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivityForResult(intent, REQUEST_IMPORT_PROFILES_FILE);
        } else {
            appendLog(getString(R.string.braille_log_profiles_file_import_failed));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQUEST_EXPORT_PROFILES_FILE) {
            exportProfilesToUri(uri);
        } else if (requestCode == REQUEST_IMPORT_PROFILES_FILE) {
            importProfilesFromUri(uri);
        }
    }

    public void onCycleRemapBinding(View view) {
        BrailleKeyBinding[] bindings = getCurrentBindings();
        if (bindings == null || bindings.length == 0) {
            appendLog(getString(R.string.braille_log_no_bindings_for_remap));
            return;
        }
        selectedBindingIndex = (selectedBindingIndex + 1) % bindings.length;
        refreshServiceSection();
    }

    public void onCycleRemapCommand(View view) {
        BrailleKeyBinding binding = getSelectedBinding();
        String address = getProfileTargetAddress();
        if (binding == null || address == null) {
            appendLog(getString(R.string.braille_log_no_bindings_for_remap));
            return;
        }
        Integer target = BrailleCommandRemapper.cycleBindingRemap(this, address,
                binding,
                BrailleCommandRemapper.getRemappableCommands());
        if (target == null) {
            appendLog(getString(R.string.braille_log_remap_cleared,
                    BrailleCommandRemapper.getBindingSignature(binding),
                    address));
        } else {
            int message = BrailleCommandRemapper.isBindingRemapEffective(this,
                    address, getCurrentDisplayProperties(), binding)
                    ? R.string.braille_log_remap_set
                    : R.string.braille_log_remap_pending;
            appendLog(getString(message,
                    BrailleCommandRemapper.getBindingSignature(binding),
                    BrailleInputEvent.commandToString(target.intValue()),
                    address));
        }
        refreshServiceSection();
        updateDisplayProperties();
    }

    public void onClearRemapCommand(View view) {
        BrailleKeyBinding binding = getSelectedBinding();
        String address = getProfileTargetAddress();
        if (binding == null || address == null) {
            appendLog(getString(R.string.braille_log_no_bindings_for_remap));
            return;
        }
        BrailleCommandRemapper.clearBindingRemap(this, address, binding);
        appendLog(getString(R.string.braille_log_remap_cleared,
                BrailleCommandRemapper.getBindingSignature(binding),
                address));
        refreshServiceSection();
        updateDisplayProperties();
    }

    public void onClearBrailleLog(View view) {
        eventLog.setLength(0);
        updateEventLog();
    }

    public void onCopyBrailleDiagnostics(View view) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            appendLog(getString(R.string.braille_log_diagnostics_export_failed,
                    getString(R.string.braille_diagnostics_clip_label)));
            return;
        }
        String report = buildDiagnosticsReport();
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.braille_diagnostics_clip_label), report));
        appendLog(getString(R.string.braille_log_diagnostics_copied));
    }

    public void onExportBrailleDiagnostics(View view) {
        File directory = getExternalFilesDir("diagnostics");
        if (directory == null) {
            directory = new File(getFilesDir(), "diagnostics");
        }
        if (!directory.exists() && !directory.mkdirs()) {
            appendLog(getString(R.string.braille_log_diagnostics_export_failed,
                    directory.getAbsolutePath()));
            return;
        }

        File output = new File(directory,
                "braille-diagnostics-" + System.currentTimeMillis() + ".txt");
        try {
            FileOutputStream stream = new FileOutputStream(output);
            try {
                stream.write(buildDiagnosticsReport()
                        .getBytes(StandardCharsets.UTF_8));
            } finally {
                stream.close();
            }
            appendLog(getString(R.string.braille_log_diagnostics_exported,
                    output.getAbsolutePath()));
        } catch (IOException e) {
            appendLog(getString(R.string.braille_log_diagnostics_export_failed,
                    output.getAbsolutePath()));
        }
    }

    public void onReportBrailleIssue(View view) {
        Intent intent = new Intent(this, SupportReportActivity.class);
        intent.putExtra(SupportReportSender.EXTRA_ADDITIONAL_DIAGNOSTICS,
                buildDiagnosticsReport());
        intent.putExtra(SupportReportActivity.EXTRA_REPORT_TYPE,
                "braille_display");
        if (ActivityLaunchUtils.canStartActivity(this, intent)) {
            startActivity(intent);
        }
    }

    private void connectDisplayClient() {
        if (displayClient != null) {
            return;
        }
        displayClient = new DisplayClient(this);
        bindDisplayClientListeners();
    }

    private void refreshAll() {
        statusView.setText(displayClient == null
                ? getString(R.string.braille_status_disconnected)
                : formatConnectionState(Display.STATE_NOT_CONNECTED));
        progressView.setText(getString(R.string.braille_progress_idle));
        refreshServiceSection();
        refreshRecognizedDevices();
        updateDisplayProperties();
        updateEventLog();
    }

    private void refreshServiceSection() {
        serviceView.setText(buildAccessibilityStatus());
        profileView.setText(buildProfileSummary());
        refreshNamedProfilesSection();
        String lastCommand = BrailleDisplayPreferences.getServiceCommand(this);
        commandView.setText(TextUtils.isEmpty(lastCommand)
                ? getString(R.string.braille_command_waiting)
                : lastCommand);
        String lastContent = BrailleDisplayPreferences.getServiceContent(this);
        contentView.setText(TextUtils.isEmpty(lastContent)
                ? getString(R.string.braille_content_waiting)
                : lastContent);
        remapView.setText(buildRemapSummary());
    }

    private void refreshRecognizedDevices() {
        devicesView.setText(buildRecognizedDevicesText());
    }

    private void updateDisplayProperties() {
        if (displayClient == null) {
            displayPropsView.setText(getString(R.string.braille_display_not_connected));
            return;
        }

        BrailleDisplayProperties properties = displayClient.getDisplayProperties();
        if (properties == null) {
            displayPropsView.setText(getString(R.string.braille_display_not_connected));
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.braille_properties_cells,
                properties.getNumTextCells(), properties.getNumStatusCells()));
        sb.append('\n');
        BrailleKeyBinding[] bindings = properties.getKeyBindings();
        sb.append(getString(R.string.braille_properties_bindings,
                bindings == null ? 0 : bindings.length));
        if (bindings != null && bindings.length > 0) {
            if (selectedBindingIndex >= bindings.length) {
                selectedBindingIndex = 0;
            }
            int shown = Math.min(bindings.length, 12);
            String profileAddress = getProfileTargetAddress();
            for (int i = 0; i < shown; i++) {
                BrailleKeyBinding binding = bindings[i];
                sb.append('\n');
                if (i == selectedBindingIndex) {
                    sb.append("* ");
                }
                sb.append(BrailleInputEvent.commandToString(binding.getCommand()));
                if (binding.isLongPress()) {
                    sb.append(" (long)");
                }
                Integer remapped = profileAddress == null ? null
                        : BrailleCommandRemapper.getBindingRemap(this,
                                profileAddress, binding);
                if (remapped != null) {
                    sb.append(" -> ");
                    sb.append(BrailleInputEvent.commandToString(remapped.intValue()));
                    if (!BrailleCommandRemapper.isBindingRemapEffective(this,
                            profileAddress, properties, binding)) {
                        sb.append(" ");
                        sb.append(getString(R.string.braille_remap_pending_short));
                    }
                }
                sb.append(": ");
                sb.append(formatKeyNames(binding));
            }
            if (bindings.length > shown) {
                sb.append('\n');
                sb.append(getString(R.string.braille_properties_more_bindings,
                        bindings.length - shown));
            }
        }
        displayPropsView.setText(sb.toString());
    }

    private String formatConnectionState(int state) {
        switch (state) {
        case Display.STATE_CONNECTED:
            return getString(R.string.braille_status_connected);
        case Display.STATE_ERROR:
            return getString(R.string.braille_status_error);
        case Display.STATE_NOT_CONNECTED:
        default:
            return getString(R.string.braille_status_disconnected);
        }
    }

    private String formatInputEvent(BrailleInputEvent event) {
        if (event == null) {
            return getString(R.string.braille_command_waiting);
        }
        if (event.isRawKeyEvent()) {
            return "RAW " + (event.isRawPress() ? "down" : "up")
                    + " group=" + event.getRawGroup()
                    + " number=" + event.getRawNumber();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(BrailleInputEvent.commandToString(event.getCommand()));
        if (!TextUtils.isEmpty(event.getBindingSignature())) {
            sb.append(" sig=");
            sb.append(event.getBindingSignature());
        }
        if (event.isRawRemapped()) {
            sb.append(" raw-remap");
        }
        switch (BrailleInputEvent.argumentType(event.getCommand())) {
        case BrailleInputEvent.ARGUMENT_DOTS:
            sb.append(" dots=");
            sb.append(formatDots(event.getArgument()));
            break;
        case BrailleInputEvent.ARGUMENT_POSITION:
            sb.append(" position=");
            sb.append(event.getArgument());
            break;
        default:
            if (event.getArgument() != 0) {
                sb.append(" arg=");
                sb.append(event.getArgument());
            }
        }
        return sb.toString();
    }

    private String buildCommandStatus(BrailleInputEvent event) {
        if (event == null) {
            return getString(R.string.braille_command_waiting);
        }
        String raw = formatInputEvent(event);
        if (event.isRawKeyEvent()) {
            return getString(R.string.braille_command_status_template, raw,
                    getString(R.string.braille_command_raw_key));
        }
        String interpretation;
        switch (event.getCommand()) {
        case BrailleInputEvent.CMD_NAV_PAN_LEFT:
            interpretation = getString(R.string.braille_command_pan_left);
            break;
        case BrailleInputEvent.CMD_NAV_PAN_RIGHT:
            interpretation = getString(R.string.braille_command_pan_right);
            break;
        case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
            interpretation = getString(R.string.braille_command_focus_previous);
            break;
        case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
            interpretation = getString(R.string.braille_command_focus_next);
            break;
        case BrailleInputEvent.CMD_NAV_LINE_PREVIOUS:
            interpretation = getString(R.string.braille_command_line_previous);
            break;
        case BrailleInputEvent.CMD_NAV_LINE_NEXT:
            interpretation = getString(R.string.braille_command_line_next);
            break;
        case BrailleInputEvent.CMD_SCROLL_BACKWARD:
            interpretation = getString(R.string.braille_command_scroll_backward);
            break;
        case BrailleInputEvent.CMD_SCROLL_FORWARD:
            interpretation = getString(R.string.braille_command_scroll_forward);
            break;
        case BrailleInputEvent.CMD_NAV_TOP:
            interpretation = getString(R.string.braille_command_nav_top);
            break;
        case BrailleInputEvent.CMD_NAV_BOTTOM:
            interpretation = getString(R.string.braille_command_nav_bottom);
            break;
        case BrailleInputEvent.CMD_SECTION_NEXT:
            interpretation = getString(R.string.braille_command_section_next);
            break;
        case BrailleInputEvent.CMD_SECTION_PREVIOUS:
            interpretation = getString(R.string.braille_command_section_previous);
            break;
        case BrailleInputEvent.CMD_CONTROL_NEXT:
            interpretation = getString(R.string.braille_command_control_next);
            break;
        case BrailleInputEvent.CMD_CONTROL_PREVIOUS:
            interpretation = getString(R.string.braille_command_control_previous);
            break;
        case BrailleInputEvent.CMD_LIST_NEXT:
            interpretation = getString(R.string.braille_command_list_next);
            break;
        case BrailleInputEvent.CMD_LIST_PREVIOUS:
            interpretation = getString(R.string.braille_command_list_previous);
            break;
        case BrailleInputEvent.CMD_ROUTE:
            interpretation = getString(R.string.braille_command_route_template,
                    event.getArgument());
            break;
        case BrailleInputEvent.CMD_BRAILLE_KEY:
            interpretation = getString(R.string.braille_command_dots_template,
                    formatDots(event.getArgument()));
            break;
        case BrailleInputEvent.CMD_KEY_DEL:
            interpretation = getString(R.string.braille_command_delete_backward);
            break;
        case BrailleInputEvent.CMD_KEY_FORWARD_DEL:
            interpretation = getString(R.string.braille_command_delete_forward);
            break;
        case BrailleInputEvent.CMD_KEY_ENTER:
            interpretation = getString(R.string.braille_command_insert_newline);
            break;
        case BrailleInputEvent.CMD_GLOBAL_BACK:
            interpretation = getString(R.string.braille_command_back);
            break;
        case BrailleInputEvent.CMD_GLOBAL_HOME:
            interpretation = getString(R.string.braille_command_home);
            break;
        case BrailleInputEvent.CMD_GLOBAL_RECENTS:
            interpretation = getString(R.string.braille_command_recents);
            break;
        case BrailleInputEvent.CMD_GLOBAL_NOTIFICATIONS:
            interpretation = getString(R.string.braille_command_notifications);
            break;
        case BrailleInputEvent.CMD_TOGGLE_BRAILLE_GRADE:
            interpretation = getString(R.string.braille_command_toggle_grade);
            break;
        default:
            interpretation = getString(R.string.braille_command_unknown);
            break;
        }
        return getString(R.string.braille_command_status_template, raw,
                interpretation);
    }

    private String formatDots(int dotsMask) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if ((dotsMask & (1 << i)) != 0) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(i + 1);
            }
        }
        return sb.length() == 0 ? getString(R.string.blank) : sb.toString();
    }

    private void appendLog(String line) {
        if (eventLog.length() > 0) {
            eventLog.append('\n');
        }
        eventLog.append(line);
        trimLogIfNeeded();
        updateEventLog();
    }

    private void trimLogIfNeeded() {
        int lines = 0;
        for (int i = 0; i < eventLog.length(); i++) {
            if (eventLog.charAt(i) == '\n') {
                lines++;
            }
        }
        while (lines >= MAX_LOG_LINES) {
            int idx = eventLog.indexOf("\n");
            if (idx < 0) {
                break;
            }
            eventLog.delete(0, idx + 1);
            lines--;
        }
    }

    private void updateEventLog() {
        eventLogView.setText(eventLog.length() == 0
                ? getString(R.string.braille_log_empty)
                : eventLog.toString());
    }

    private List<DeviceFinder.DeviceInfo> getRecognizedDevices() {
        return new DeviceFinder(this).findDevices();
    }

    private String buildRecognizedDevicesText() {
        StringBuilder sb = new StringBuilder();
        String preferredAddress = BrailleDisplayPreferences
                .getPreferredDeviceAddress(this);
        String lastAddress = BrailleDisplayPreferences
                .getLastConnectedDeviceAddress(this);
        List<DeviceFinder.DeviceInfo> devices = getRecognizedDevices();
        if (devices.isEmpty()) {
            sb.append(getString(R.string.braille_no_recognized_devices));
        } else {
            for (DeviceFinder.DeviceInfo info : devices) {
                String address = info.getDeviceAddress();
                sb.append(info.getDeviceName());
                sb.append(" [");
                sb.append(info.isUsb() ? "USB/" : "BT/");
                sb.append(info.getDriverCode());
                sb.append("] ");
                sb.append(TextUtils.isEmpty(address)
                        ? getString(R.string.braille_profile_no_device)
                        : address);
                if (TextUtils.equals(address, preferredAddress)) {
                    sb.append(" ");
                    sb.append(getString(R.string.braille_profile_marker_preferred));
                }
                if (TextUtils.equals(address, lastAddress)) {
                    sb.append(" ");
                    sb.append(getString(R.string.braille_profile_marker_last));
                }
                String tableOverride = TextUtils.isEmpty(address) ? null
                        : BrailleDisplayPreferences.getDeviceTable(this, address);
                if (tableOverride != null) {
                    sb.append(" ");
                    sb.append(getString(R.string.braille_profile_table_short,
                            tableOverride));
                }
                if (info.isBluetooth()) {
                    sb.append(info.getConnectSecurely() ? " secure" : " insecure");
                } else {
                    sb.append(" ");
                    sb.append(String.format(Locale.US, "%04X:%04X",
                            info.getUsbVendorId(), info.getUsbProductId()));
                    sb.append(" if=");
                    sb.append(info.getUsbInterfaceCount());
                    if (info.hasExactUsbProfile()) {
                        sb.append(" ");
                        sb.append(getString(R.string.braille_usb_profile_exact));
                    }
                    sb.append(" ");
                    UsbDevice usbDevice = info.getUsbDevice();
                    sb.append(usbDevice != null && UsbHelper.hasPermission(usbDevice)
                            ? getString(R.string.braille_usb_permission_granted)
                            : getString(R.string.braille_usb_permission_missing));
                }
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    private String buildAccessibilityStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(isBrailleAccessibilityServiceEnabled()
                ? R.string.braille_accessibility_enabled
                : R.string.braille_accessibility_disabled));
        String storedStatus = BrailleDisplayPreferences.getServiceStatus(this);
        if (!TextUtils.isEmpty(storedStatus)) {
            sb.append('\n');
            sb.append(storedStatus);
        }
        return sb.toString();
    }

    private String buildProfileSummary() {
        String preferred = BrailleDisplayPreferences.getPreferredDeviceAddress(this);
        String last = BrailleDisplayPreferences.getLastConnectedDeviceAddress(this);
        String target = getProfileTargetAddress();
        String targetTable = BrailleDisplayPreferences.getDeviceTable(this, target);
        DeviceFinder finder = new DeviceFinder(this);
        DeviceFinder.DeviceInfo preferredInfo = finder.findByAddress(preferred);
        DeviceFinder.DeviceInfo lastInfo = finder.findByAddress(last);
        DeviceFinder.DeviceInfo targetInfo = finder.findByAddress(target);
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.braille_profile_preferred_label));
        sb.append(' ');
        sb.append(formatProfileDevice(preferred, preferredInfo));
        sb.append('\n');
        sb.append(getString(R.string.braille_profile_last_label));
        sb.append(' ');
        sb.append(formatProfileDevice(last, lastInfo));
        sb.append('\n');
        sb.append(getString(R.string.braille_profile_target_label));
        sb.append(' ');
        sb.append(formatProfileDevice(target, targetInfo));
        sb.append('\n');
        sb.append(getString(R.string.braille_profile_table_label));
        sb.append(' ');
        sb.append(targetTable == null
                ? getString(R.string.braille_profile_table_global)
                : targetTable);
        return sb.toString();
    }

    private void refreshNamedProfilesSection() {
        if (namedProfileView != null) {
            String active = BrailleDisplayNamedProfiles.getActiveProfileName(this);
            namedProfileView.setText(TextUtils.isEmpty(active)
                    ? getString(R.string.braille_named_profiles_active_none)
                    : getString(R.string.braille_named_profiles_active_value,
                            active));
        }
        if (namedProfileAdapter != null) {
            String active = BrailleDisplayNamedProfiles.getActiveProfileName(this);
            namedProfileNames.clear();
            for (BrailleDisplayNamedProfiles.Profile profile
                    : BrailleDisplayNamedProfiles.getProfiles(this)) {
                namedProfileNames.add(profile.name);
            }
            namedProfileAdapter.notifyDataSetChanged();
            if (!TextUtils.isEmpty(active)) {
                setSelectedNamedProfile(active);
            } else if (!namedProfileNames.isEmpty() && namedProfileSpinner != null) {
                namedProfileSpinner.setSelection(0);
            }
            if (namedProfileNameInput != null
                    && TextUtils.isEmpty(namedProfileNameInput.getText())) {
                namedProfileNameInput.setText(active == null ? "" : active);
            }
        }
    }

    private void setSelectedNamedProfile(String name) {
        if (namedProfileSpinner == null || TextUtils.isEmpty(name)) {
            return;
        }
        for (int i = 0; i < namedProfileNames.size(); i++) {
            if (name.equalsIgnoreCase(namedProfileNames.get(i))) {
                namedProfileSpinner.setSelection(i);
                return;
            }
        }
    }

    private String getSelectedNamedProfile() {
        if (namedProfileSpinner == null) {
            return null;
        }
        Object item = namedProfileSpinner.getSelectedItem();
        return item == null ? null : String.valueOf(item);
    }

    private String buildRemapSummary() {
        BrailleKeyBinding binding = getSelectedBinding();
        String address = getProfileTargetAddress();
        if (binding == null) {
            return getString(R.string.braille_remap_no_bindings);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.braille_remap_selected_binding));
        sb.append(' ');
        sb.append(BrailleInputEvent.commandToString(binding.getCommand()));
        if (binding.isLongPress()) {
            sb.append(" (long)");
        }
        sb.append('\n');
        sb.append(getString(R.string.braille_remap_signature_label));
        sb.append(' ');
        sb.append(BrailleCommandRemapper.getBindingSignature(binding));
        sb.append('\n');
        sb.append(getString(R.string.braille_remap_keys_label));
        sb.append(' ');
        sb.append(formatKeyNames(binding));
        sb.append('\n');
        sb.append(getString(R.string.braille_remap_target_label));
        sb.append(' ');
        if (address == null) {
            sb.append(getString(R.string.braille_profile_no_device));
        } else {
            Integer remapped = BrailleCommandRemapper.getBindingRemap(this,
                    address, binding);
            sb.append(remapped == null
                    ? getString(R.string.braille_remap_identity)
                    : BrailleInputEvent.commandToString(remapped.intValue()));
        }
        sb.append('\n');
        sb.append(getString(R.string.braille_remap_runtime_status_label));
        sb.append(' ');
        if (address == null) {
            sb.append(getString(R.string.braille_profile_no_device));
        } else if (BrailleCommandRemapper.isBindingRemapEffective(this, address,
                getCurrentDisplayProperties(), binding)) {
            sb.append(getString(R.string.braille_remap_runtime_effective));
        } else if (BrailleCommandRemapper.isBindingRuntimeAddressable(
                getCurrentDisplayProperties(), binding)) {
            sb.append(getString(R.string.braille_remap_runtime_ready));
        } else {
            sb.append(getString(R.string.braille_remap_runtime_ambiguous));
        }
        return sb.toString();
    }

    private BrailleDisplayProperties getCurrentDisplayProperties() {
        return displayClient == null ? null : displayClient.getDisplayProperties();
    }

    private BrailleKeyBinding[] getCurrentBindings() {
        BrailleDisplayProperties properties = getCurrentDisplayProperties();
        return properties == null ? null : properties.getKeyBindings();
    }

    private BrailleKeyBinding getSelectedBinding() {
        BrailleKeyBinding[] bindings = getCurrentBindings();
        if (bindings == null || bindings.length == 0) {
            return null;
        }
        if (selectedBindingIndex >= bindings.length) {
            selectedBindingIndex = 0;
        }
        return bindings[selectedBindingIndex];
    }

    private String formatProfileDevice(String address, DeviceFinder.DeviceInfo info) {
        if (TextUtils.isEmpty(address)) {
            return getString(R.string.braille_profile_no_device);
        }
        if (info == null) {
            return address;
        }
        return info.getDeviceName() + " (" + address + ")";
    }

    private String formatKeyNames(BrailleKeyBinding binding) {
        if (binding == null) {
            return getString(R.string.blank);
        }
        String[] keyNames = binding.getKeyNames();
        if (keyNames == null || keyNames.length == 0) {
            return getString(R.string.blank);
        }
        BrailleDisplayProperties properties = getCurrentDisplayProperties();
        java.util.Map<String, String> friendlyNames = properties == null
                ? null : properties.getFriendlyKeyNames();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyNames.length; i++) {
            if (i > 0) {
                sb.append(" + ");
            }
            String keyName = keyNames[i];
            if (TextUtils.isEmpty(keyName)) {
                sb.append(getString(R.string.blank));
                continue;
            }
            String friendly = friendlyNames == null ? null
                    : friendlyNames.get(keyName);
            sb.append(TextUtils.isEmpty(friendly) ? keyName : friendly);
        }
        return sb.toString();
    }

    private String getProfileTargetAddress() {
        String preferred = BrailleDisplayPreferences.getPreferredDeviceAddress(this);
        if (!TextUtils.isEmpty(preferred)) {
            return preferred;
        }
        String last = BrailleDisplayPreferences.getLastConnectedDeviceAddress(this);
        if (!TextUtils.isEmpty(last)) {
            return last;
        }
        List<DeviceFinder.DeviceInfo> devices = getRecognizedDevices();
        return devices.isEmpty() ? null : devices.get(0).getDeviceAddress();
    }

    private String buildDiagnosticsReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Soft Braille Keyboard braille diagnostics");
        sb.append('\n');
        sb.append("Generated at: ");
        sb.append(new java.util.Date());
        sb.append('\n');
        sb.append("Bluetooth permission: ");
        sb.append(hasBluetoothConnectPermission() ? "granted" : "missing");
        sb.append('\n');
        sb.append("Bluetooth adapter: ");
        sb.append(BluetoothAdapter.getDefaultAdapter() == null ? "unavailable" : "present");
        sb.append('\n');
        sb.append("Accessibility service: ");
        sb.append(isBrailleAccessibilityServiceEnabled() ? "enabled" : "disabled");
        sb.append('\n');
        sb.append("Service status:");
        sb.append('\n');
        sb.append(serviceView.getText());
        sb.append('\n');
        sb.append('\n');
        sb.append("Profile:");
        sb.append('\n');
        sb.append(profileView.getText());
        sb.append('\n');
        sb.append('\n');
        sb.append("Remap:");
        sb.append('\n');
        sb.append(remapView.getText());
        sb.append('\n');
        sb.append('\n');
        sb.append("Last command:");
        sb.append('\n');
        sb.append(commandView.getText());
        sb.append('\n');
        sb.append('\n');
        sb.append("Last rendered content:");
        sb.append('\n');
        sb.append(contentView.getText());
        sb.append('\n');
        sb.append('\n');
        sb.append("Connection state: ");
        sb.append(statusView.getText());
        sb.append('\n');
        sb.append("Progress: ");
        sb.append(progressView.getText());
        sb.append('\n');
        sb.append('\n');
        sb.append("Recognized paired displays:");
        sb.append('\n');
        sb.append(buildRecognizedDevicesText());
        sb.append('\n');
        sb.append('\n');
        sb.append("Connected display properties:");
        sb.append('\n');
        sb.append(displayPropsView.getText());
        sb.append('\n');
        sb.append('\n');
        sb.append("IME trace:");
        sb.append('\n');
        sb.append(BrailleIME.dumpImeTrace());
        sb.append('\n');
        sb.append('\n');
        sb.append("Event log:");
        sb.append('\n');
        sb.append(eventLogView.getText());
        return sb.toString();
    }

    private void maybeRequestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !hasBluetoothConnectPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.BLUETOOTH_CONNECT },
                    BLUETOOTH_CONNECT_REQUEST);
        }
    }

    private boolean hasBluetoothConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(this,
                        Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isBrailleAccessibilityServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(
                ACCESSIBILITY_SERVICE);
        if (manager == null || !manager.isEnabled()) {
            return false;
        }
        List<AccessibilityServiceInfo> enabledServices = manager
                .getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        String target = new ComponentName(this, BrailleAccessibilityService.class)
                .flattenToString();
        for (AccessibilityServiceInfo info : enabledServices) {
            if (info.getResolveInfo() != null
                    && info.getResolveInfo().serviceInfo != null) {
                ComponentName component = new ComponentName(
                        info.getResolveInfo().serviceInfo.packageName,
                        info.getResolveInfo().serviceInfo.name);
                if (target.equals(component.flattenToString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void bindDisplayClientListeners() {
        displayClient.setOnConnectionStateChangeListener(
                new Display.OnConnectionStateChangeListener() {
                    @Override
                    public void onConnectionStateChanged(int state) {
                        handleConnectionStateChanged(state);
                    }
                });
        displayClient.setOnConnectionChangeProgressListener(
                new Display.OnConnectionChangeProgressListener() {
                    @Override
                    public void onConnectionChangeProgress(String description) {
                        progressView.setText(description == null
                                ? getString(R.string.braille_progress_idle)
                                : description);
                    }
                });
        displayClient.setOnInputEventListener(
                new Display.OnInputEventListener() {
                    @Override
                    public void onInputEvent(BrailleInputEvent inputEvent) {
                        commandView.setText(buildCommandStatus(inputEvent));
                        appendLog(formatInputEvent(inputEvent));
                    }
                });
    }

    private void handleConnectionStateChanged(int state) {
        statusView.setText(formatConnectionState(state));
        updateDisplayProperties();
        refreshServiceSection();
        appendLog(getString(R.string.braille_log_state_changed,
                formatConnectionState(state)));
    }

    private void exportBrailleProfilesToClipboard() {
        ClipboardManager clipboard = TextTransferUtils.getClipboardManager(this);
        if (clipboard == null) {
            appendLog(getString(R.string.braille_log_profiles_import_failed));
            return;
        }
        try {
            String export = BrailleDisplayPreferences.exportProfileBundle(this);
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    getString(R.string.braille_profiles_clip_label), export));
            appendLog(getString(R.string.braille_log_profiles_exported));
        } catch (JSONException e) {
            appendLog(getString(R.string.braille_log_profiles_import_failed));
        }
    }

    private void importBrailleProfilesFromClipboard() {
        ClipboardManager clipboard = TextTransferUtils.getClipboardManager(this);
        CharSequence importedText = TextTransferUtils.getClipboardText(this, clipboard);
        if (TextUtils.isEmpty(importedText)) {
            appendLog(getString(R.string.braille_log_profiles_import_empty));
            return;
        }
        try {
            BrailleDisplayPreferences.ImportResult result =
                    BrailleDisplayPreferences.importProfileBundle(this,
                            importedText.toString());
            if (!result.hadContent) {
                appendLog(getString(R.string.braille_log_profiles_import_empty));
                return;
            }
            appendLog(getString(R.string.braille_log_profiles_imported,
                    result.restoredEntries));
            refreshAll();
        } catch (JSONException e) {
            appendLog(getString(R.string.braille_log_profiles_import_failed));
        }
    }

    private void shareBrailleProfiles() {
        try {
            String export = BrailleDisplayPreferences.exportProfileBundle(this);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_SUBJECT,
                    getString(R.string.braille_profiles_clip_label));
            intent.putExtra(Intent.EXTRA_TEXT, export);
            Intent chooser = Intent.createChooser(intent,
                    getString(R.string.braille_share_profiles));
            if (ActivityLaunchUtils.canStartActivity(this, chooser)) {
                startActivity(chooser);
            } else if (ActivityLaunchUtils.canStartActivity(this, intent)) {
                startActivity(intent);
            } else {
                appendLog(getString(R.string.braille_log_profiles_share_failed));
            }
        } catch (JSONException e) {
            appendLog(getString(R.string.braille_log_profiles_share_failed));
        }
    }

    private Intent createProfilesExportIntent() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "braille-display-profiles.json");
        return intent;
    }

    private Intent createProfilesImportIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        return intent;
    }

    private void exportProfilesToUri(Uri uri) {
        try {
            String export = BrailleDisplayPreferences.exportProfileBundle(this);
            if (!TextTransferUtils.writeTextToUri(this, uri, export)) {
                appendLog(getString(R.string.braille_log_profiles_file_export_failed));
                return;
            }
            appendLog(getString(R.string.braille_log_profiles_file_exported,
                    uri.toString()));
        } catch (JSONException e) {
            appendLog(getString(R.string.braille_log_profiles_file_export_failed));
        }
    }

    private void importProfilesFromUri(Uri uri) {
        try {
            String importedText = TextTransferUtils.readTextFromUri(this, uri);
            if (importedText == null) {
                appendLog(getString(R.string.braille_log_profiles_file_import_failed));
                return;
            }
            BrailleDisplayPreferences.ImportResult result =
                    BrailleDisplayPreferences.importProfileBundle(this,
                            importedText);
            if (!result.hadContent) {
                appendLog(getString(R.string.braille_log_profiles_import_empty));
                return;
            }
            appendLog(getString(R.string.braille_log_profiles_file_imported,
                    result.restoredEntries, uri.toString()));
            refreshAll();
        } catch (JSONException e) {
            appendLog(getString(R.string.braille_log_profiles_file_import_failed));
        }
    }

}
