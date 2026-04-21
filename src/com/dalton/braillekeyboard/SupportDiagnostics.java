package com.dalton.braillekeyboard;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import androidx.core.content.ContextCompat;

import com.googlecode.eyesfree.braille.service.display.DeviceFinder;

import java.util.List;
import java.util.Locale;

public final class SupportDiagnostics {
    private SupportDiagnostics() {
    }

    public static String buildReport(Context context,
            String userMessage, String additionalDiagnostics) {
        StringBuilder sb = new StringBuilder();
        Context appContext = context.getApplicationContext();
        appendSection(sb, "Soft Braille Keyboard support report");
        appendRuntimeSummary(sb, appContext);

        appendBlankLine(sb);
        appendSection(sb, "Recognized braille displays");
        appendBrailleDisplays(sb, appContext);

        appendBlankLine(sb);
        appendSection(sb, "User message");
        sb.append(TextUtils.isEmpty(userMessage) ? "(empty)" : userMessage);
        sb.append('\n');

        appendBlankLine(sb);
        appendSection(sb, "IME trace");
        sb.append(BrailleIME.dumpImeTrace());
        sb.append('\n');

        if (!TextUtils.isEmpty(additionalDiagnostics)) {
            appendBlankLine(sb);
            appendSection(sb, "Additional diagnostics");
            sb.append(additionalDiagnostics);
            if (!additionalDiagnostics.endsWith("\n")) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static void appendRuntimeSummary(StringBuilder sb, Context context) {
        appendLine(sb, "Generated at", new java.util.Date().toString());
        appendLine(sb, "Version", BuildConfig.VERSION_NAME + " ("
                + BuildConfig.VERSION_CODE + ")");
        appendLine(sb, "Build type", BuildConfig.DEBUG ? "debug" : "release");
        appendLine(sb, "Package", BuildConfig.APPLICATION_ID);
        appendLine(sb, "Locale", Locale.getDefault().toLanguageTag());
        appendLine(sb, "Android", Build.VERSION.RELEASE + " (SDK "
                + Build.VERSION.SDK_INT + ")");
        appendLine(sb, "Device", Build.MANUFACTURER + " " + Build.MODEL);
        appendLine(sb, "Hardware", Build.DEVICE + " / " + Build.PRODUCT);
        appendLine(sb, "Bluetooth permission",
                hasBluetoothPermission(context) ? "granted" : "missing");
        appendLine(sb, "Bluetooth adapter",
                BluetoothAdapter.getDefaultAdapter() == null ? "unavailable"
                        : "present");
        appendLine(sb, "Braille accessibility service",
                isBrailleAccessibilityServiceEnabled(context) ? "enabled"
                        : "disabled");
        appendLine(sb, "Preferred display",
                BrailleDisplayPreferences.getPreferredDeviceAddress(context));
        appendLine(sb, "Last connected display",
                BrailleDisplayPreferences.getLastConnectedDeviceAddress(context));
    }

    private static void appendBrailleDisplays(StringBuilder sb, Context context) {
        List<DeviceFinder.DeviceInfo> devices = new DeviceFinder(context)
                .findDevices();
        if (devices.isEmpty()) {
            sb.append("None\n");
            return;
        }
        for (DeviceFinder.DeviceInfo device : devices) {
            if (device != null) {
                sb.append("- ").append(formatDeviceLine(device)).append('\n');
            }
        }
    }

    private static String formatDeviceLine(DeviceFinder.DeviceInfo device) {
        StringBuilder sb = new StringBuilder();
        sb.append(TextUtils.isEmpty(device.getDeviceName()) ? "(unnamed)"
                : device.getDeviceName());
        sb.append(" | ").append(device.getTransport());
        sb.append(" | ").append(TextUtils.isEmpty(device.getDeviceAddress())
                ? "(no address)" : device.getDeviceAddress());
        sb.append(" | ").append(device.getDriverCode());
        if (device.getUsbDevice() != null) {
            sb.append(" | USB ");
            sb.append(String.format(Locale.ROOT, "%04X:%04X",
                    device.getUsbDevice().getVendorId(),
                    device.getUsbDevice().getProductId()));
        }
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String title) {
        sb.append(title);
        sb.append('\n');
        for (int i = 0; i < title.length(); i++) {
            sb.append('=');
        }
        sb.append('\n');
    }

    private static void appendLine(StringBuilder sb, String key, String value) {
        sb.append(key);
        sb.append(": ");
        sb.append(TextUtils.isEmpty(value) ? "(none)" : value);
        sb.append('\n');
    }

    private static void appendBlankLine(StringBuilder sb) {
        sb.append('\n');
    }

    private static boolean hasBluetoothPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(context,
                        Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isBrailleAccessibilityServiceEnabled(Context context) {
        AccessibilityManager manager = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null || !manager.isEnabled()) {
            return false;
        }
        List<AccessibilityServiceInfo> enabledServices = manager
                .getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        String target = new ComponentName(context,
                BrailleAccessibilityService.class).flattenToString();
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
}
