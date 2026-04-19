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

import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.BrailleKeyBinding;
import com.googlecode.eyesfree.braille.service.display.DeviceFinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BrailleCommandRemapper {
    public static final int RAW_REMAP_ARG_NONE = 0;
    public static final int RAW_REMAP_ARG_PRESERVE = 1;
    public static final int RAW_REMAP_ARG_FIXED = 2;
    public static final int RAW_REMAP_ARG_CURRENT = -1;

    public static final class RawRemapConfig {
        public final String[] signatures;
        public final int[] targetCommands;
        public final int[] targetArgumentModes;
        public final int[] targetArgumentValues;

        private RawRemapConfig(String[] signatures, int[] targetCommands,
                int[] targetArgumentModes, int[] targetArgumentValues) {
            this.signatures = signatures;
            this.targetCommands = targetCommands;
            this.targetArgumentModes = targetArgumentModes;
            this.targetArgumentValues = targetArgumentValues;
        }
    }

    private static final int[] REMAPPABLE_COMMANDS = new int[] {
            BrailleInputEvent.CMD_NAV_PAN_LEFT,
            BrailleInputEvent.CMD_NAV_PAN_RIGHT,
            BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS,
            BrailleInputEvent.CMD_NAV_ITEM_NEXT,
            BrailleInputEvent.CMD_NAV_LINE_PREVIOUS,
            BrailleInputEvent.CMD_NAV_LINE_NEXT,
            BrailleInputEvent.CMD_NAV_TOP,
            BrailleInputEvent.CMD_NAV_BOTTOM,
            BrailleInputEvent.CMD_SCROLL_BACKWARD,
            BrailleInputEvent.CMD_SCROLL_FORWARD,
            BrailleInputEvent.CMD_ACTIVATE_CURRENT,
            BrailleInputEvent.CMD_LONG_PRESS_CURRENT,
            BrailleInputEvent.CMD_ROUTE,
            BrailleInputEvent.CMD_LONG_PRESS_ROUTE,
            BrailleInputEvent.CMD_KEY_ENTER,
            BrailleInputEvent.CMD_KEY_DEL,
            BrailleInputEvent.CMD_KEY_FORWARD_DEL,
            BrailleInputEvent.CMD_GLOBAL_BACK,
            BrailleInputEvent.CMD_GLOBAL_HOME,
            BrailleInputEvent.CMD_GLOBAL_RECENTS,
            BrailleInputEvent.CMD_GLOBAL_NOTIFICATIONS,
            BrailleInputEvent.CMD_SECTION_NEXT,
            BrailleInputEvent.CMD_SECTION_PREVIOUS,
            BrailleInputEvent.CMD_CONTROL_NEXT,
            BrailleInputEvent.CMD_CONTROL_PREVIOUS,
            BrailleInputEvent.CMD_LIST_NEXT,
            BrailleInputEvent.CMD_LIST_PREVIOUS,
            BrailleInputEvent.CMD_TOGGLE_BRAILLE_GRADE,
            BrailleInputEvent.CMD_HELP
    };

    private BrailleCommandRemapper() {
    }

    public static int[] getRemappableCommands() {
        return REMAPPABLE_COMMANDS.clone();
    }

    public static int remapCommand(Context context, String deviceAddress,
            int command) {
        return BrailleDisplayPreferences.getRemappedCommand(context,
                deviceAddress, command);
    }

    public static int remapCommand(Context context, DeviceFinder.DeviceInfo info,
            int command) {
        if (info == null) {
            return command;
        }
        return remapCommand(context, info.getDeviceAddress(), command);
    }

    public static int remapCommand(Context context, DeviceFinder.DeviceInfo info,
            BrailleDisplayProperties properties, int command) {
        if (info == null) {
            return command;
        }
        Integer bindingAware = getBindingAwareRemap(context,
                info.getDeviceAddress(), properties, command);
        if (bindingAware != null) {
            return bindingAware.intValue();
        }
        return remapCommand(context, info, command);
    }

    public static String getBindingSignature(BrailleKeyBinding binding) {
        if (binding == null) {
            return null;
        }
        String[] keyNames = binding.getKeyNames();
        String[] copy = keyNames == null ? new String[0] : keyNames.clone();
        Arrays.sort(copy);
        StringBuilder sb = new StringBuilder();
        if (binding.isLongPress()) {
            sb.append("long|");
        } else {
            sb.append("tap|");
        }
        for (int i = 0; i < copy.length; i++) {
            if (i > 0) {
                sb.append('+');
            }
            sb.append(copy[i]);
        }
        return sb.toString();
    }

    public static Integer getBindingRemap(Context context, String address,
            BrailleKeyBinding binding) {
        return binding == null ? null : BrailleDisplayPreferences.getBindingRemap(
                context, address, getBindingSignature(binding));
    }

    public static Integer cycleBindingRemap(Context context, String address,
            BrailleKeyBinding binding, int[] supportedTargets) {
        return binding == null ? null : BrailleDisplayPreferences.cycleBindingRemap(
                context, address, getBindingSignature(binding), supportedTargets);
    }

    public static void clearBindingRemap(Context context, String address,
            BrailleKeyBinding binding) {
        if (binding == null) {
            return;
        }
        BrailleDisplayPreferences.clearBindingRemap(context, address,
                getBindingSignature(binding));
    }

    public static boolean isBindingRuntimeAddressable(
            BrailleDisplayProperties properties, BrailleKeyBinding binding) {
        if (properties == null || binding == null) {
            return false;
        }
        BrailleKeyBinding[] bindings = properties.getKeyBindings();
        if (bindings == null) {
            return false;
        }
        int count = 0;
        for (BrailleKeyBinding candidate : bindings) {
            if (candidate.getCommand() == binding.getCommand()) {
                count++;
            }
        }
        return count <= 1;
    }

    public static boolean isBindingRemapEffective(Context context, String address,
            BrailleDisplayProperties properties, BrailleKeyBinding binding) {
        Integer target = getBindingRemap(context, address, binding);
        if (target == null) {
            return false;
        }
        Integer effective = getBindingAwareRemap(context, address, properties,
                binding.getCommand());
        return effective != null && effective.intValue() == target.intValue();
    }

    private static Integer getBindingAwareRemap(Context context, String address,
            BrailleDisplayProperties properties, int sourceCommand) {
        if (context == null || address == null || properties == null) {
            return null;
        }
        BrailleKeyBinding[] bindings = properties.getKeyBindings();
        if (bindings == null || bindings.length == 0) {
            return null;
        }

        int bindingCount = 0;
        Integer target = null;
        for (BrailleKeyBinding binding : bindings) {
            if (binding.getCommand() != sourceCommand) {
                continue;
            }
            bindingCount++;
            Integer candidate = getBindingRemap(context, address, binding);
            if (candidate == null) {
                if (bindingCount == 1) {
                    continue;
                }
                return null;
            }
            if (target == null) {
                target = candidate;
            } else if (target.intValue() != candidate.intValue()) {
                return null;
            }
        }

        if (bindingCount == 1) {
            return target;
        }
        return bindingCount > 1 ? target : null;
    }

    public static RawRemapConfig buildRawRemapConfig(Context context,
            String address, BrailleDisplayProperties properties) {
        if (context == null || address == null || properties == null) {
            return new RawRemapConfig(new String[0], new int[0], new int[0],
                    new int[0]);
        }
        BrailleKeyBinding[] bindings = properties.getKeyBindings();
        if (bindings == null || bindings.length == 0) {
            return new RawRemapConfig(new String[0], new int[0], new int[0],
                    new int[0]);
        }
        Map<String, Integer> remaps = new LinkedHashMap<String, Integer>();
        Map<String, Integer> argumentModes = new LinkedHashMap<String, Integer>();
        Map<String, Integer> argumentValues = new LinkedHashMap<String, Integer>();
        for (BrailleKeyBinding binding : bindings) {
            Integer target = getBindingRemap(context, address, binding);
            String signature = getBindingSignature(binding);
            if (target == null || signature == null) {
                continue;
            }
            remaps.put(signature, target);
            argumentModes.put(signature, Integer.valueOf(resolveArgumentMode(
                    binding.getCommand(), target.intValue())));
            argumentValues.put(signature, Integer.valueOf(resolveArgumentValue(
                    target.intValue())));
        }
        if (remaps.isEmpty()) {
            return new RawRemapConfig(new String[0], new int[0], new int[0],
                    new int[0]);
        }
        ArrayList<String> signatures = new ArrayList<String>(remaps.size());
        int[] targetCommands = new int[remaps.size()];
        int[] targetArgModes = new int[remaps.size()];
        int[] targetArgValues = new int[remaps.size()];
        int index = 0;
        for (Map.Entry<String, Integer> entry : remaps.entrySet()) {
            String signature = entry.getKey();
            signatures.add(signature);
            targetCommands[index] = entry.getValue().intValue();
            targetArgModes[index] = argumentModes.get(signature).intValue();
            targetArgValues[index] = argumentValues.get(signature).intValue();
            index++;
        }
        return new RawRemapConfig(
                signatures.toArray(new String[signatures.size()]),
                targetCommands, targetArgModes, targetArgValues);
    }

    private static int resolveArgumentMode(int sourceCommand, int targetCommand) {
        switch (targetCommand) {
            case BrailleInputEvent.CMD_ACTIVATE_CURRENT:
            case BrailleInputEvent.CMD_LONG_PRESS_CURRENT:
                return RAW_REMAP_ARG_FIXED;
            default:
                int targetArgumentType = BrailleInputEvent.argumentType(targetCommand);
                if (targetArgumentType == BrailleInputEvent.ARGUMENT_NONE) {
                    return RAW_REMAP_ARG_NONE;
                }
                return BrailleInputEvent.argumentType(sourceCommand)
                        == targetArgumentType
                        ? RAW_REMAP_ARG_PRESERVE
                        : RAW_REMAP_ARG_FIXED;
        }
    }

    private static int resolveArgumentValue(int targetCommand) {
        switch (targetCommand) {
            case BrailleInputEvent.CMD_ACTIVATE_CURRENT:
            case BrailleInputEvent.CMD_LONG_PRESS_CURRENT:
                return RAW_REMAP_ARG_CURRENT;
            default:
                return 0;
        }
    }

    public static String describeCommand(int command) {
        return BrailleInputEvent.commandToString(command);
    }
}
