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

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.text.TextUtils;

import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.BrailleKeyBinding;
import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.braille.service.display.DeviceFinder;

import org.a11y.brltty.android.UsbHelper;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BrailleDisplayUiUtils {
    private BrailleDisplayUiUtils() {
    }

    static String formatConnectionState(Context context, int state) {
        switch (state) {
        case Display.STATE_CONNECTED:
            return context.getString(R.string.braille_status_connected);
        case Display.STATE_ERROR:
            return context.getString(R.string.braille_status_error);
        case Display.STATE_NOT_CONNECTED:
        default:
            return context.getString(R.string.braille_status_disconnected);
        }
    }

    static String formatInputEvent(Context context, BrailleInputEvent event) {
        if (event == null) {
            return context.getString(R.string.braille_command_waiting);
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
            sb.append(formatDots(context, event.getArgument()));
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

    static String buildCommandStatus(Context context, BrailleInputEvent event) {
        if (event == null) {
            return context.getString(R.string.braille_command_waiting);
        }
        String raw = formatInputEvent(context, event);
        if (event.isRawKeyEvent()) {
            return context.getString(R.string.braille_command_status_template,
                    raw, context.getString(R.string.braille_command_raw_key));
        }
        String interpretation;
        switch (event.getCommand()) {
        case BrailleInputEvent.CMD_NAV_PAN_LEFT:
            interpretation = context.getString(R.string.braille_command_pan_left);
            break;
        case BrailleInputEvent.CMD_NAV_PAN_RIGHT:
            interpretation = context.getString(R.string.braille_command_pan_right);
            break;
        case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
            interpretation = context.getString(
                    R.string.braille_command_focus_previous);
            break;
        case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
            interpretation = context.getString(R.string.braille_command_focus_next);
            break;
        case BrailleInputEvent.CMD_NAV_LINE_PREVIOUS:
            interpretation = context.getString(R.string.braille_command_line_previous);
            break;
        case BrailleInputEvent.CMD_NAV_LINE_NEXT:
            interpretation = context.getString(R.string.braille_command_line_next);
            break;
        case BrailleInputEvent.CMD_SCROLL_BACKWARD:
            interpretation = context.getString(
                    R.string.braille_command_scroll_backward);
            break;
        case BrailleInputEvent.CMD_SCROLL_FORWARD:
            interpretation = context.getString(
                    R.string.braille_command_scroll_forward);
            break;
        case BrailleInputEvent.CMD_NAV_TOP:
            interpretation = context.getString(R.string.braille_command_nav_top);
            break;
        case BrailleInputEvent.CMD_NAV_BOTTOM:
            interpretation = context.getString(R.string.braille_command_nav_bottom);
            break;
        case BrailleInputEvent.CMD_SECTION_NEXT:
            interpretation = context.getString(R.string.braille_command_section_next);
            break;
        case BrailleInputEvent.CMD_SECTION_PREVIOUS:
            interpretation = context.getString(
                    R.string.braille_command_section_previous);
            break;
        case BrailleInputEvent.CMD_CONTROL_NEXT:
            interpretation = context.getString(R.string.braille_command_control_next);
            break;
        case BrailleInputEvent.CMD_CONTROL_PREVIOUS:
            interpretation = context.getString(
                    R.string.braille_command_control_previous);
            break;
        case BrailleInputEvent.CMD_LIST_NEXT:
            interpretation = context.getString(R.string.braille_command_list_next);
            break;
        case BrailleInputEvent.CMD_LIST_PREVIOUS:
            interpretation = context.getString(R.string.braille_command_list_previous);
            break;
        case BrailleInputEvent.CMD_ROUTE:
            interpretation = context.getString(
                    R.string.braille_command_route_template,
                    event.getArgument());
            break;
        case BrailleInputEvent.CMD_BRAILLE_KEY:
            interpretation = context.getString(
                    R.string.braille_command_dots_template,
                    formatDots(context, event.getArgument()));
            break;
        case BrailleInputEvent.CMD_KEY_DEL:
            interpretation = context.getString(
                    R.string.braille_command_delete_backward);
            break;
        case BrailleInputEvent.CMD_KEY_FORWARD_DEL:
            interpretation = context.getString(
                    R.string.braille_command_delete_forward);
            break;
        case BrailleInputEvent.CMD_KEY_ENTER:
            interpretation = context.getString(R.string.braille_command_insert_newline);
            break;
        case BrailleInputEvent.CMD_GLOBAL_BACK:
            interpretation = context.getString(R.string.braille_command_back);
            break;
        case BrailleInputEvent.CMD_GLOBAL_HOME:
            interpretation = context.getString(R.string.braille_command_home);
            break;
        case BrailleInputEvent.CMD_GLOBAL_RECENTS:
            interpretation = context.getString(R.string.braille_command_recents);
            break;
        case BrailleInputEvent.CMD_GLOBAL_NOTIFICATIONS:
            interpretation = context.getString(R.string.braille_command_notifications);
            break;
        case BrailleInputEvent.CMD_TOGGLE_BRAILLE_GRADE:
            interpretation = context.getString(R.string.braille_command_toggle_grade);
            break;
        default:
            interpretation = context.getString(R.string.braille_command_unknown);
            break;
        }
        return context.getString(R.string.braille_command_status_template, raw,
                interpretation);
    }

    static String formatDots(Context context, int dotsMask) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if ((dotsMask & (1 << i)) != 0) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(i + 1);
            }
        }
        return sb.length() == 0 ? context.getString(R.string.blank) : sb.toString();
    }

    static String buildRecognizedDevicesText(Context context,
            List<DeviceFinder.DeviceInfo> devices) {
        StringBuilder sb = new StringBuilder();
        String preferredAddress = BrailleDisplayPreferences
                .getPreferredDeviceAddress(context);
        String lastAddress = BrailleDisplayPreferences
                .getLastConnectedDeviceAddress(context);
        if (devices.isEmpty()) {
            sb.append(context.getString(R.string.braille_no_recognized_devices));
        } else {
            for (DeviceFinder.DeviceInfo info : devices) {
                String address = info.getDeviceAddress();
                sb.append(info.getDeviceName());
                sb.append(" [");
                sb.append(info.isUsb() ? "USB/" : "BT/");
                sb.append(info.getDriverCode());
                sb.append("] ");
                sb.append(TextUtils.isEmpty(address)
                        ? context.getString(R.string.braille_profile_no_device)
                        : address);
                if (TextUtils.equals(address, preferredAddress)) {
                    sb.append(" ");
                    sb.append(context.getString(
                            R.string.braille_profile_marker_preferred));
                }
                if (TextUtils.equals(address, lastAddress)) {
                    sb.append(" ");
                    sb.append(context.getString(R.string.braille_profile_marker_last));
                }
                String tableOverride = TextUtils.isEmpty(address) ? null
                        : BrailleDisplayPreferences.getDeviceTable(context, address);
                if (tableOverride != null) {
                    sb.append(" ");
                    sb.append(context.getString(
                            R.string.braille_profile_table_short, tableOverride));
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
                        sb.append(context.getString(
                                R.string.braille_usb_profile_exact));
                    }
                    sb.append(" ");
                    UsbDevice usbDevice = info.getUsbDevice();
                    sb.append(usbDevice != null && UsbHelper.hasPermission(usbDevice)
                            ? context.getString(
                                    R.string.braille_usb_permission_granted)
                            : context.getString(
                                    R.string.braille_usb_permission_missing));
                }
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    static String formatProfileDevice(Context context, String address,
            DeviceFinder.DeviceInfo info) {
        if (TextUtils.isEmpty(address)) {
            return context.getString(R.string.braille_profile_no_device);
        }
        if (info == null) {
            return address;
        }
        return info.getDeviceName() + " (" + address + ")";
    }

    static String formatKeyNames(Context context, BrailleKeyBinding binding,
            BrailleDisplayProperties properties) {
        if (binding == null) {
            return context.getString(R.string.blank);
        }
        String[] keyNames = binding.getKeyNames();
        if (keyNames == null || keyNames.length == 0) {
            return context.getString(R.string.blank);
        }
        Map<String, String> friendlyNames = properties == null
                ? null : properties.getFriendlyKeyNames();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyNames.length; i++) {
            if (i > 0) {
                sb.append(" + ");
            }
            String keyName = keyNames[i];
            if (TextUtils.isEmpty(keyName)) {
                sb.append(context.getString(R.string.blank));
                continue;
            }
            String friendly = friendlyNames == null ? null
                    : friendlyNames.get(keyName);
            sb.append(TextUtils.isEmpty(friendly) ? keyName : friendly);
        }
        return sb.toString();
    }

    static String buildDiagnosticsReport(Context context,
            boolean hasBluetoothPermission,
            boolean hasBluetoothAdapter,
            boolean accessibilityEnabled,
            CharSequence serviceStatus,
            CharSequence profileStatus,
            CharSequence remapStatus,
            CharSequence commandStatus,
            CharSequence contentStatus,
            CharSequence connectionState,
            CharSequence progressStatus,
            String recognizedDevicesText,
            CharSequence displayProperties,
            CharSequence eventLog) {
        StringBuilder sb = new StringBuilder();
        sb.append("Soft Braille Keyboard braille diagnostics");
        sb.append('\n');
        sb.append("Generated at: ");
        sb.append(new Date());
        sb.append('\n');
        sb.append("Bluetooth permission: ");
        sb.append(hasBluetoothPermission ? "granted" : "missing");
        sb.append('\n');
        sb.append("Bluetooth adapter: ");
        sb.append(hasBluetoothAdapter ? "present" : "unavailable");
        sb.append('\n');
        sb.append("Accessibility service: ");
        sb.append(accessibilityEnabled ? "enabled" : "disabled");
        sb.append('\n');
        appendSection(sb, "Service status:", serviceStatus);
        appendSection(sb, "Profile:", profileStatus);
        appendSection(sb, "Remap:", remapStatus);
        appendSection(sb, "Last command:", commandStatus);
        appendSection(sb, "Last rendered content:", contentStatus);
        sb.append("Connection state: ");
        sb.append(connectionState);
        sb.append('\n');
        sb.append("Progress: ");
        sb.append(progressStatus);
        sb.append('\n');
        sb.append('\n');
        sb.append("Recognized paired displays:");
        sb.append('\n');
        sb.append(recognizedDevicesText);
        sb.append('\n');
        appendSection(sb, "Connected display properties:", displayProperties);
        sb.append("IME trace:");
        sb.append('\n');
        sb.append(BrailleIME.dumpImeTrace());
        sb.append('\n');
        sb.append('\n');
        sb.append("Event log:");
        sb.append('\n');
        sb.append(eventLog);
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String title,
            CharSequence value) {
        sb.append('\n');
        sb.append(title);
        sb.append('\n');
        sb.append(value);
        sb.append('\n');
        sb.append('\n');
    }
}
