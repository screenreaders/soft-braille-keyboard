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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.googlecode.eyesfree.braille.display.BrailleInputEvent;

public final class BrailleDisplayPreferences {
    private static final String PREF_LAST_CONNECTED_DEVICE =
            "lastBluetoothDevice";
    private static final String PREF_PREFERRED_DEVICE =
            "preferredBluetoothDevice";
    private static final String PREF_DEVICE_TABLE_PREFIX =
            "brailleDisplayTable.";
    private static final String PREF_COMMAND_REMAP_PREFIX =
            "brailleDisplayCommandRemap.";
    private static final String PREF_BINDING_REMAP_PREFIX =
            "brailleDisplayBindingRemap.";
    private static final String PREF_SERVICE_STATUS =
            "brailleDisplayServiceStatus";
    private static final String PREF_SERVICE_CONTENT =
            "brailleDisplayServiceContent";
    private static final String PREF_SERVICE_COMMAND =
            "brailleDisplayServiceCommand";

    private BrailleDisplayPreferences() {
    }

    public static String getLastConnectedDeviceAddress(Context context) {
        try {
            return getPreferences(context).getString(PREF_LAST_CONNECTED_DEVICE,
                    null);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public static void setLastConnectedDeviceAddress(Context context,
            String address) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putString(PREF_LAST_CONNECTED_DEVICE, address);
        editor.apply();
    }

    public static String getPreferredDeviceAddress(Context context) {
        try {
            return getPreferences(context).getString(PREF_PREFERRED_DEVICE, null);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public static void setPreferredDeviceAddress(Context context, String address) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putString(PREF_PREFERRED_DEVICE, address);
        editor.apply();
    }

    public static void clearPreferredDeviceAddress(Context context) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.remove(PREF_PREFERRED_DEVICE);
        editor.apply();
    }

    public static String getDeviceTable(Context context, String address) {
        if (address == null || address.length() == 0) {
            return null;
        }
        try {
            return getPreferences(context).getString(prefixedDeviceKey(address),
                    null);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public static void setDeviceTable(Context context, String address,
            String tableId) {
        if (address == null || address.length() == 0) {
            return;
        }
        SharedPreferences.Editor editor = getPreferences(context).edit();
        if (tableId == null || tableId.length() == 0) {
            editor.remove(prefixedDeviceKey(address));
        } else {
            editor.putString(prefixedDeviceKey(address), tableId);
        }
        editor.apply();
    }

    public static void clearDeviceTable(Context context, String address) {
        if (address == null || address.length() == 0) {
            return;
        }
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.remove(prefixedDeviceKey(address));
        editor.apply();
    }

    public static Integer getCommandRemap(Context context, String address,
            int sourceCommand) {
        if (address == null || address.length() == 0) {
            return null;
        }
        SharedPreferences preferences = getPreferences(context);
        String key = prefixedCommandKey(address, sourceCommand);
        if (!preferences.contains(key)) {
            return null;
        }
        try {
            return Integer.valueOf(preferences.getInt(key, sourceCommand));
        } catch (ClassCastException e) {
            return null;
        }
    }

    public static Integer getBindingRemap(Context context, String address,
            String bindingSignature) {
        if (address == null || address.length() == 0
                || bindingSignature == null || bindingSignature.length() == 0) {
            return null;
        }
        SharedPreferences preferences = getPreferences(context);
        String key = prefixedBindingKey(address, bindingSignature);
        if (!preferences.contains(key)) {
            return null;
        }
        try {
            return Integer.valueOf(preferences.getInt(key,
                    BrailleInputEvent.CMD_NONE));
        } catch (ClassCastException e) {
            return null;
        }
    }

    public static void setBindingRemap(Context context, String address,
            String bindingSignature, int targetCommand) {
        if (address == null || address.length() == 0
                || bindingSignature == null || bindingSignature.length() == 0) {
            return;
        }
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putInt(prefixedBindingKey(address, bindingSignature), targetCommand);
        editor.apply();
    }

    public static void clearBindingRemap(Context context, String address,
            String bindingSignature) {
        if (address == null || address.length() == 0
                || bindingSignature == null || bindingSignature.length() == 0) {
            return;
        }
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.remove(prefixedBindingKey(address, bindingSignature));
        editor.apply();
    }

    public static Integer cycleBindingRemap(Context context, String address,
            String bindingSignature, int[] supportedTargets) {
        if (address == null || address.length() == 0
                || bindingSignature == null || bindingSignature.length() == 0
                || supportedTargets == null || supportedTargets.length == 0) {
            return null;
        }
        Integer current = getBindingRemap(context, address, bindingSignature);
        if (current == null) {
            setBindingRemap(context, address, bindingSignature,
                    supportedTargets[0]);
            return Integer.valueOf(supportedTargets[0]);
        }
        for (int i = 0; i < supportedTargets.length; i++) {
            if (current.intValue() == supportedTargets[i]) {
                if (i == supportedTargets.length - 1) {
                    clearBindingRemap(context, address, bindingSignature);
                    return null;
                }
                setBindingRemap(context, address, bindingSignature,
                        supportedTargets[i + 1]);
                return Integer.valueOf(supportedTargets[i + 1]);
            }
        }
        setBindingRemap(context, address, bindingSignature, supportedTargets[0]);
        return Integer.valueOf(supportedTargets[0]);
    }

    public static int getRemappedCommand(Context context, String address,
            int sourceCommand) {
        Integer mapped = getCommandRemap(context, address, sourceCommand);
        return mapped == null ? sourceCommand : mapped.intValue();
    }

    public static void setCommandRemap(Context context, String address,
            int sourceCommand, int targetCommand) {
        if (address == null || address.length() == 0) {
            return;
        }
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putInt(prefixedCommandKey(address, sourceCommand), targetCommand);
        editor.apply();
    }

    public static void clearCommandRemap(Context context, String address,
            int sourceCommand) {
        if (address == null || address.length() == 0) {
            return;
        }
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.remove(prefixedCommandKey(address, sourceCommand));
        editor.apply();
    }

    public static Integer cycleCommandRemap(Context context, String address,
            int sourceCommand, int[] supportedTargets) {
        if (address == null || address.length() == 0 || supportedTargets == null
                || supportedTargets.length == 0) {
            return null;
        }
        Integer current = getCommandRemap(context, address, sourceCommand);
        if (current == null) {
            setCommandRemap(context, address, sourceCommand, supportedTargets[0]);
            return Integer.valueOf(supportedTargets[0]);
        }
        for (int i = 0; i < supportedTargets.length; i++) {
            if (current.intValue() == supportedTargets[i]) {
                if (i == supportedTargets.length - 1) {
                    clearCommandRemap(context, address, sourceCommand);
                    return null;
                }
                setCommandRemap(context, address, sourceCommand,
                        supportedTargets[i + 1]);
                return Integer.valueOf(supportedTargets[i + 1]);
            }
        }
        setCommandRemap(context, address, sourceCommand, supportedTargets[0]);
        return Integer.valueOf(supportedTargets[0]);
    }

    public static String cycleDeviceTable(Context context, String address,
            String[] tables) {
        if (address == null || address.length() == 0 || tables == null
                || tables.length == 0) {
            return null;
        }

        String current = getDeviceTable(context, address);
        if (current == null) {
            setDeviceTable(context, address, tables[0]);
            return tables[0];
        }

        for (int i = 0; i < tables.length; i++) {
            if (current.equals(tables[i])) {
                if (i == tables.length - 1) {
                    clearDeviceTable(context, address);
                    return null;
                }
                setDeviceTable(context, address, tables[i + 1]);
                return tables[i + 1];
            }
        }

        setDeviceTable(context, address, tables[0]);
        return tables[0];
    }

    public static String getServiceStatus(Context context) {
        try {
            return getPreferences(context).getString(PREF_SERVICE_STATUS, null);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public static void setServiceStatus(Context context, String status) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putString(PREF_SERVICE_STATUS, status);
        editor.apply();
    }

    public static String getServiceContent(Context context) {
        try {
            return getPreferences(context).getString(PREF_SERVICE_CONTENT, null);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public static void setServiceContent(Context context, String content) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putString(PREF_SERVICE_CONTENT, content);
        editor.apply();
    }

    public static String getServiceCommand(Context context) {
        try {
            return getPreferences(context).getString(PREF_SERVICE_COMMAND, null);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public static void setServiceCommand(Context context, String command) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putString(PREF_SERVICE_COMMAND, command);
        editor.apply();
    }

    private static SharedPreferences getPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(
                context.getApplicationContext());
    }

    private static String prefixedDeviceKey(String address) {
        return PREF_DEVICE_TABLE_PREFIX + address.replace(':', '_');
    }

    private static String prefixedCommandKey(String address, int sourceCommand) {
        return PREF_COMMAND_REMAP_PREFIX + address.replace(':', '_') + "."
                + sourceCommand;
    }

    private static String prefixedBindingKey(String address,
            String bindingSignature) {
        return PREF_BINDING_REMAP_PREFIX + address.replace(':', '_') + "."
                + sanitizeKeyPart(bindingSignature);
    }

    private static String sanitizeKeyPart(String input) {
        if (input == null || input.length() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if ((ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '_' || ch == '-') {
                sb.append(ch);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}
