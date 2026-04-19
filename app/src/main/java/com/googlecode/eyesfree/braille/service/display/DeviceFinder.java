/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.eyesfree.braille.service.display;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import androidx.core.content.ContextCompat;
import com.dalton.braillekeyboard.BrailleDisplayPreferences;
import com.dalton.braillekeyboard.R;
import org.a11y.brltty.android.UsbHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Finds supported devices among bonded devices.
 */
public class DeviceFinder {

    private static final UUID SERIAL_BOARD_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Context mContext;

    /**
     * Information about a supported bonded bluetooth device.
     */
    public static class DeviceInfo {
        public enum Transport {
            BLUETOOTH,
            USB
        }

        private final Transport mTransport;
        private final BluetoothDevice mBluetoothDevice;
        private final UsbDevice mUsbDevice;
        private final String mDeviceName;
        private final String mDeviceAddress;
        private final String mDriverCode;
        private final UUID mSdpUuid;
        private final boolean mConnectSecurely;
        private final Map<String, Integer> mFriendlyKeyNames;
        private final UsbDeviceProfile mUsbProfile;

        public DeviceInfo(BluetoothDevice bluetoothDevice,
                String driverCode, UUID sdpUuid,
                boolean connectSecurely,
                Map<String, Integer> friendlyKeyNames) {
            this(Transport.BLUETOOTH, bluetoothDevice, null,
                    bluetoothDevice == null ? null : bluetoothDevice.getName(),
                    bluetoothDevice == null ? null : bluetoothDevice.getAddress(),
                    driverCode, sdpUuid, connectSecurely, friendlyKeyNames, null);
        }

        public DeviceInfo(UsbDevice usbDevice,
                String driverCode, UUID sdpUuid,
                boolean connectSecurely,
                Map<String, Integer> friendlyKeyNames) {
            this(usbDevice, driverCode, sdpUuid, connectSecurely,
                    friendlyKeyNames, null);
        }

        public DeviceInfo(UsbDevice usbDevice,
                String driverCode, UUID sdpUuid,
                boolean connectSecurely,
                Map<String, Integer> friendlyKeyNames,
                UsbDeviceProfile usbProfile) {
            this(Transport.USB, null, usbDevice, getUsbDeviceName(usbDevice,
                    usbProfile),
                    getUsbDeviceAddress(usbDevice), driverCode, sdpUuid,
                    connectSecurely, friendlyKeyNames, usbProfile);
        }

        private DeviceInfo(Transport transport, BluetoothDevice bluetoothDevice,
                UsbDevice usbDevice, String deviceName, String deviceAddress,
                String driverCode, UUID sdpUuid,
                boolean connectSecurely,
                Map<String, Integer> friendlyKeyNames,
                UsbDeviceProfile usbProfile) {
            mTransport = transport;
            mBluetoothDevice = bluetoothDevice;
            mUsbDevice = usbDevice;
            mDeviceName = deviceName;
            mDeviceAddress = deviceAddress;
            mDriverCode = driverCode;
            mSdpUuid = sdpUuid;
            mConnectSecurely = connectSecurely;
            mFriendlyKeyNames = friendlyKeyNames;
            mUsbProfile = usbProfile;
        }

        public Transport getTransport() {
            return mTransport;
        }

        /**
         * Returns the bluetooth device from the system.
         */
        public BluetoothDevice getBluetoothDevice() {
            return mBluetoothDevice;
        }

        public UsbDevice getUsbDevice() {
            return mUsbDevice;
        }

        public String getDeviceName() {
            return mDeviceName;
        }

        public String getDeviceAddress() {
            return mDeviceAddress;
        }

        public String getBrlttyAddress() {
            if (mTransport == Transport.USB) {
                return buildUsbBrlttyAddress(mUsbDevice, mUsbProfile);
            }
            return "bluetooth:" + mBluetoothDevice.getAddress();
        }

        public boolean isUsb() {
            return mTransport == Transport.USB;
        }

        public boolean isBluetooth() {
            return mTransport == Transport.BLUETOOTH;
        }

        /**
         * Returns the brltty driver code to use for this device.
         */
        public String getDriverCode() {
            return mDriverCode;
        }

        /**
         * Returns the service record uuid to use when connecting to
         * this device.
         */
        public UUID getSdpUuid() {
            return mSdpUuid;
        }

        /**
         * Returns whether to connect securely (preferred)
         * or not.
         * @see BluetoothDevice#createInsecureRfcommSocketToServiceRecord
         * @see BluetoothDevice#createRfcommSocketToServiceRecord
         */
        public boolean getConnectSecurely() {
            return mConnectSecurely;
        }

        /**
         */
        public Map<String, Integer> getFriendlyKeyNames() {
            return mFriendlyKeyNames;
        }

        public int getUsbVendorId() {
            return mUsbDevice == null ? -1 : mUsbDevice.getVendorId();
        }

        public int getUsbProductId() {
            return mUsbDevice == null ? -1 : mUsbDevice.getProductId();
        }

        public int getUsbInterfaceCount() {
            return mUsbDevice == null ? 0 : mUsbDevice.getInterfaceCount();
        }

        public boolean hasExactUsbProfile() {
            return mUsbProfile != null;
        }

        public String getUsbProfileLabel() {
            return mUsbProfile == null ? null : mUsbProfile.mDisplayName;
        }
    }

    private static class UsbDeviceProfile {
        private final int mVendorId;
        private final int mProductId;
        private final String mDisplayName;
        private final boolean mDisableGenericDevices;

        UsbDeviceProfile(int vendorId, int productId, String displayName) {
            this(vendorId, productId, displayName, true);
        }

        UsbDeviceProfile(int vendorId, int productId, String displayName,
                boolean disableGenericDevices) {
            mVendorId = vendorId;
            mProductId = productId;
            mDisplayName = displayName;
            mDisableGenericDevices = disableGenericDevices;
        }

        boolean matches(UsbDevice usbDevice) {
            return usbDevice != null
                    && usbDevice.getVendorId() == mVendorId
                    && usbDevice.getProductId() == mProductId;
        }
    }

    public DeviceFinder(Context context) {
        mContext = context.getApplicationContext();
        UsbHelper.initialize(mContext);
    }

    /**
     * Returns a list of bonded and supported devices in the order they
     * should be tried.
     */
    public List<DeviceInfo> findDevices() {
        List<DeviceInfo> ret = new ArrayList<DeviceInfo>();
        ret.addAll(findBluetoothOnlyDevices());
        ret.addAll(findUsbDevices());
        String preferredAddress = BrailleDisplayPreferences
                .getPreferredDeviceAddress(mContext);
        if (preferredAddress != null) {
            for (int i = 0; i < ret.size(); ++i) {
                if (preferredAddress.equals(ret.get(i).getDeviceAddress())) {
                    Collections.swap(ret, 0, i);
                    break;
                }
            }
        }

        String lastAddress = BrailleDisplayPreferences
                .getLastConnectedDeviceAddress(mContext);
        if (lastAddress != null) {
            // If the last device that was successfully connected is
            // not already preferred, put it right after the preferred
            // device if one is configured, otherwise first.
            int targetIndex = preferredAddress == null ? 0 : 1;
            for (int i = targetIndex; i < ret.size(); ++i) {
                if (lastAddress.equals(ret.get(i).getDeviceAddress())) {
                    Collections.swap(ret, targetIndex, i);
                    break;
                }
            }
        }
        return ret;
    }

    public List<DeviceInfo> findBluetoothOnlyDevices() {
        if (!hasBluetoothConnectPermission()) {
            return Collections.emptyList();
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return Collections.emptyList();
        }

        List<DeviceInfo> ret = new ArrayList<DeviceInfo>();
        Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
        if (bondedDevices == null) {
            return ret;
        }
        for (BluetoothDevice dev : bondedDevices) {
            if (dev == null) {
                continue;
            }
            for (SupportedDevice matcher : SUPPORTED_DEVICES) {
                DeviceInfo matched = matcher.match(dev);
                if (matched != null) {
                    ret.add(matched);
                    break;
                }
            }
        }
        return ret;
    }

    public List<DeviceInfo> findUsbDevices() {
        List<DeviceInfo> ret = new ArrayList<DeviceInfo>();
        for (UsbDevice dev : UsbHelper.getConnectedDevices()) {
            if (dev == null) {
                continue;
            }
            for (SupportedDevice matcher : SUPPORTED_DEVICES) {
                DeviceInfo matched = matcher.match(dev);
                if (matched != null) {
                    ret.add(matched);
                    break;
                }
            }
        }
        return ret;
    }

    public List<DeviceInfo> findSupportedUsbDevicesNeedingPermission() {
        List<DeviceInfo> result = new ArrayList<DeviceInfo>();
        for (DeviceInfo info : findUsbDevices()) {
            if (info.getUsbDevice() != null
                    && !UsbHelper.hasPermission(info.getUsbDevice())) {
                result.add(info);
            }
        }
        return result;
    }

    public DeviceInfo findByAddress(String address) {
        if (address == null) {
            return null;
        }
        for (DeviceInfo info : findDevices()) {
            if (address.equals(info.getDeviceAddress())) {
                return info;
            }
        }
        return null;
    }

    public boolean requestUsbPermission(DeviceInfo info) {
        return info != null && info.getUsbDevice() != null
                && UsbHelper.requestPermission(mContext, info.getUsbDevice());
    }

    public boolean hasUsbPermission(DeviceInfo info) {
        return info != null && info.getUsbDevice() != null
                && UsbHelper.hasPermission(info.getUsbDevice());
    }

    public void rememberSuccessfulConnection(DeviceInfo info) {
        if (info == null) {
            return;
        }
        BrailleDisplayPreferences.setLastConnectedDeviceAddress(mContext,
                info.getDeviceAddress());
    }

    public String getLastConnectedDeviceAddress() {
        return BrailleDisplayPreferences.getLastConnectedDeviceAddress(mContext);
    }

    public String getPreferredDeviceAddress() {
        return BrailleDisplayPreferences.getPreferredDeviceAddress(mContext);
    }

    public void setPreferredDeviceAddress(String address) {
        BrailleDisplayPreferences.setPreferredDeviceAddress(mContext, address);
    }

    public void clearPreferredDeviceAddress() {
        BrailleDisplayPreferences.clearPreferredDeviceAddress(mContext);
    }

    private boolean hasBluetoothConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(mContext,
                        android.Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static String getUsbDeviceName(UsbDevice usbDevice,
            UsbDeviceProfile usbProfile) {
        if (usbDevice == null) {
            return null;
        }
        if (usbProfile != null && usbProfile.mDisplayName != null
                && usbProfile.mDisplayName.length() > 0) {
            return usbProfile.mDisplayName;
        }
        String product = usbDevice.getProductName();
        String manufacturer = usbDevice.getManufacturerName();
        if (manufacturer != null && manufacturer.length() > 0
                && product != null && product.length() > 0) {
            return manufacturer + " " + product;
        }
        if (product != null && product.length() > 0) {
            return product;
        }
        if (manufacturer != null && manufacturer.length() > 0) {
            return manufacturer;
        }
        return String.format("USB %04X:%04X", usbDevice.getVendorId(),
                usbDevice.getProductId());
    }

    private static String getUsbDeviceName(UsbDevice usbDevice) {
        return getUsbDeviceName(usbDevice, null);
    }

    private static String getUsbDeviceAddress(UsbDevice usbDevice) {
        if (usbDevice == null) {
            return null;
        }
        return String.format("usb:%04x:%04x:%d", usbDevice.getVendorId(),
                usbDevice.getProductId(), usbDevice.getDeviceId());
    }

    private static String buildUsbBrlttyAddress(UsbDevice usbDevice,
            UsbDeviceProfile usbProfile) {
        if (usbDevice == null) {
            return "usb:";
        }
        StringBuilder sb = new StringBuilder("usb:");
        sb.append("vendorIdentifier=");
        sb.append(formatUsbId(usbDevice.getVendorId()));
        sb.append("+productIdentifier=");
        sb.append(formatUsbId(usbDevice.getProductId()));
        if (usbProfile != null && usbProfile.mDisableGenericDevices) {
            sb.append("+genericDevices=no");
        }
        return sb.toString();
    }

    private static String formatUsbId(int identifier) {
        return String.format("0X%04X", identifier & 0xffff);
    }

    private static interface SupportedDevice {
        DeviceInfo match(BluetoothDevice bluetoothDevice);
        DeviceInfo match(UsbDevice usbDevice);
    }

    private static class NameRegexSupportedDevice
            implements SupportedDevice {
        private final String mDriverCode;
        private final boolean mConnectSecurely;
        private final Map<String, Integer> mFriendlyKeyNames;
        private final UsbDeviceProfile[] mUsbProfiles;
        private final Pattern[] mNameRegexes;

        public NameRegexSupportedDevice(String driverCode,
                boolean connectSecurely,
                Map<String, Integer> friendlyKeyNames,
                Pattern... nameRegexes) {
            this(driverCode, connectSecurely, friendlyKeyNames, null,
                    nameRegexes);
        }

        public NameRegexSupportedDevice(String driverCode,
                boolean connectSecurely,
                Map<String, Integer> friendlyKeyNames,
                UsbDeviceProfile[] usbProfiles,
                Pattern... nameRegexes) {
            mDriverCode = driverCode;
            mConnectSecurely = connectSecurely;
            mFriendlyKeyNames = friendlyKeyNames;
            mUsbProfiles = usbProfiles;
            mNameRegexes = nameRegexes;
        }

        @Override
        public DeviceInfo match(BluetoothDevice bluetoothDevice) {
            String name = bluetoothDevice.getName();
            if (name == null) {
                return null;
            }
            for (Pattern nameRegex : mNameRegexes) {
                if (nameRegex.matcher(name).lookingAt()) {
                    return new DeviceInfo(bluetoothDevice, mDriverCode,
                            SERIAL_BOARD_UUID, mConnectSecurely,
                            mFriendlyKeyNames);
                }
            }
            return null;
        }

        @Override
        public DeviceInfo match(UsbDevice usbDevice) {
            if (mUsbProfiles != null) {
                for (UsbDeviceProfile usbProfile : mUsbProfiles) {
                    if (usbProfile.matches(usbDevice)) {
                        return new DeviceInfo(usbDevice, mDriverCode,
                                SERIAL_BOARD_UUID, true, mFriendlyKeyNames,
                                usbProfile);
                    }
                }
            }
            String name = getUsbDeviceName(usbDevice);
            if (name == null) {
                return null;
            }
            for (Pattern nameRegex : mNameRegexes) {
                if (nameRegex.matcher(name).lookingAt()) {
                    return new DeviceInfo(usbDevice, mDriverCode,
                            SERIAL_BOARD_UUID, true, mFriendlyKeyNames);
                }
            }
            return null;
        }

        @Override
        public String toString() {
          StringBuilder s = new StringBuilder();
          s.append(mDriverCode);
          for (Pattern p : mNameRegexes) {
            s.append(" " + p);
          }
          return s.toString();
        }
    }

    private static class KeyNameMapBuilder {
        private final Map<String, Integer> mNameMap =
                new HashMap<String, Integer>();

        /**
         * Adds a mapping from the internal {@code name} to a friendly name
         * with resource id {@code resId}.
         */
        public KeyNameMapBuilder add(String name, int resId) {
            mNameMap.put(name, resId);
            return this;
        }

        public KeyNameMapBuilder dots6() {
            add("Dot1", R.string.key_Dot1);
            add("Dot2", R.string.key_Dot2);
            add("Dot3", R.string.key_Dot3);
            add("Dot4", R.string.key_Dot4);
            add("Dot5", R.string.key_Dot5);
            add("Dot6", R.string.key_Dot6);
            return this;
        }

        public KeyNameMapBuilder dots8() {
            dots6();
            add("Dot7", R.string.key_Dot7);
            add("Dot8", R.string.key_Dot8);
            return this;
        }

        public KeyNameMapBuilder routing() {
            return add("RoutingKey", R.string.key_Routing);
        }

        public KeyNameMapBuilder dualJoysticks() {
            add("LeftJoystickLeft", R.string.key_LeftJoystickLeft);
            add("LeftJoystickRight", R.string.key_LeftJoystickRight);
            add("LeftJoystickUp", R.string.key_LeftJoystickUp);
            add("LeftJoystickDown", R.string.key_LeftJoystickDown);
            add("LeftJoystickPress", R.string.key_LeftJoystickCenter);
            add("RightJoystickLeft", R.string.key_RightJoystickLeft);
            add("RightJoystickRight", R.string.key_RightJoystickRight);
            add("RightJoystickUp", R.string.key_RightJoystickUp);
            add("RightJoystickDown", R.string.key_RightJoystickDown);
            add("RightJoystickPress", R.string.key_RightJoystickCenter);
            return this;
        }

        public Map<String, Integer> build() {
            return Collections.unmodifiableMap(mNameMap);
        }
    }

    // ADD_DEVICE_SUPPORT
    private static final List<SupportedDevice> SUPPORTED_DEVICES;
    static {
        // TODO: Follow up on why secure connections can't be established
        // with some devices.
        ArrayList<SupportedDevice> l = new ArrayList<SupportedDevice>();

        // BraillePen
        l.add(new NameRegexSupportedDevice("vo", true,
                new KeyNameMapBuilder()
                        .dots6()
                        .add("Shift", R.string.key_BP_Shift)
                        .add("Space", R.string.key_Space)
                        .add("Control", R.string.key_BP_Control)
                        .add("JoystickLeft", R.string.key_JoystickLeft)
                        .add("JoystickRight", R.string.key_JoystickRight)
                        .add("JoystickUp", R.string.key_JoystickUp)
                        .add("JoystickDown", R.string.key_JoystickDown)
                        .add("JoystickEnter", R.string.key_JoystickCenter)
                        .add("ScrollLeft", R.string.key_BP_ScrollLeft)
                        .add("ScrollRight", R.string.key_BP_ScrollRight)
                        .build(),
                        Pattern.compile("EL12-")));

        // Esys
        l.add(new NameRegexSupportedDevice("eu", true,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Switch1Left", R.string.key_esys_SwitchLeft)
                        .add("Switch1Right", R.string.key_esys_SwitchRight)
                        .dualJoysticks()
                        .add("Backspace", R.string.key_Backspace)
                        .add("Space", R.string.key_Space)
                        .add("RoutingKey1", R.string.key_Routing)
                        .build(),
                        Pattern.compile("Esys-")));

        // Freedom Scientific Focus blue displays.
        l.add(new NameRegexSupportedDevice("fs", true,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Space", R.string.key_Space)
                        .add("LeftAdvance", R.string.key_focus_LeftAdvance)
                        .add("RightAdvance", R.string.key_focus_RightAdvance)
                        .add("LeftWheelPress",
                                R.string.key_focus_LeftWheelPress)
                        .add("LeftWheelDown",
                                R.string.key_focus_LeftWheelDown)
                        .add("LeftWheelUp",
                                R.string.key_focus_LeftWheelUp)
                        .add("RightWheelPress",
                                R.string.key_focus_RightWheelPress)
                        .add("RightWheelDown",
                                R.string.key_focus_RightWheelDown)
                        .add("RightWheelUp",
                                R.string.key_focus_RightWheelUp)
                        .routing()
                        .add("LeftShift", R.string.key_focus_LeftShift)
                        .add("RightShift", R.string.key_focus_RightShift)
                        .add("LeftGdf", R.string.key_focus_LeftGdf)
                        .add("RightGdf", R.string.key_focus_RightGdf)
                        .add("LeftRockerUp", R.string.key_focus_LeftRockerUp)
                        .add("LeftRockerDown",
                                R.string.key_focus_LeftRockerDown)
                        .add("RightRockerUp", R.string.key_focus_RightRockerUp)
                        .add("RightRockerDown",
                                R.string.key_focus_RightRockerDown)
                        .build(),
                        new UsbDeviceProfile[] {
                                new UsbDeviceProfile(0X0F4E, 0X0100,
                                        "Focus 1"),
                                new UsbDeviceProfile(0X0F4E, 0X0111,
                                        "PAC Mate"),
                                new UsbDeviceProfile(0X0F4E, 0X0112,
                                        "Focus 2"),
                                new UsbDeviceProfile(0X0F4E, 0X0114,
                                        "Focus Blue")
                        },
                        Pattern.compile("Focus (14|40|80) BT"),
                        Pattern.compile("Focus Blue (14|40|80|5th Generation|5G|5)"),
                        Pattern.compile("Focus 40 Blue 5")));

        // Brailliant
        // Secure connections currently fail on Android devices for the
        // Brailliant.
        l.add(new NameRegexSupportedDevice("hw", false,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Left", R.string.key_JoystickLeft)
                        .add("Right", R.string.key_JoystickRight)
                        .add("Up", R.string.key_JoystickUp)
                        .add("Down", R.string.key_JoystickDown)
                        .add("Press", R.string.key_JoystickCenter)
                        .routing()
                        .add("Space", R.string.key_Space)
                        .add("Power", R.string.key_brailliant_Power)
                        .add("Display1", R.string.key_brailliant_Display1)
                        .add("Display2", R.string.key_brailliant_Display2)
                        .add("Display3", R.string.key_brailliant_Display3)
                        .add("Display4", R.string.key_brailliant_Display4)
                        .add("Display5", R.string.key_brailliant_Display5)
                        .add("Display6", R.string.key_brailliant_Display6)
                        .add("Thumb1", R.string.key_brailliant_Thumb1)
                        .add("Thumb2", R.string.key_brailliant_Thumb2)
                        .add("Thumb3", R.string.key_brailliant_Thumb3)
                        .add("Thumb4", R.string.key_brailliant_Thumb4)
                        .build(),
                        new UsbDeviceProfile[] {
                                new UsbDeviceProfile(0X1C71, 0XC005,
                                        "Brailliant BI"),
                                new UsbDeviceProfile(0X1C71, 0XC006,
                                        "Brailliant BI"),
                                new UsbDeviceProfile(0X1C71, 0XC00A,
                                        "BrailleNote Touch")
                        },
                        Pattern.compile("Brailliant BI"),
                        Pattern.compile("Brailliant BI (20X|40X|40|20|14)"),
                        Pattern.compile("Mantis Q40")));

        // HIMS
        l.add(new NameRegexSupportedDevice("hm", false,
                new KeyNameMapBuilder()
                        .dots8()
                        .routing()
                        .add("Space", R.string.key_Space)
                        .add("F1", R.string.key_F1)
                        .add("F2", R.string.key_F2)
                        .add("F3", R.string.key_F3)
                        .add("F4", R.string.key_F4)
                        .add("Backward", R.string.key_Backward)
                        .add("Forward", R.string.key_Forward)
                        .build(),
                        new UsbDeviceProfile[] {
                                new UsbDeviceProfile(0X045E, 0X930A,
                                        "BrailleSense"),
                                new UsbDeviceProfile(0X045E, 0X930B,
                                        "Braille Edge")
                        },
                        Pattern.compile("Hansone|HansoneXL|BrailleSense|BrailleEDGE|SmartBeetle|BrailleSense 6|BrailleSense 6 MINI")));

        // APH Refreshabraille.
        // Secure connections get prematurely closed 50% of the time
        // by the Refreshabraille.
        l.add(new NameRegexSupportedDevice("bm", false,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Left", R.string.key_JoystickLeft)
                        .add("Right", R.string.key_JoystickRight)
                        .add("Up", R.string.key_JoystickUp)
                        .add("Down", R.string.key_JoystickDown)
                        .add("Press", R.string.key_JoystickCenter)
                        .routing()
                        .add("Display2", R.string.key_APH_AdvanceLeft)
                        .add("Display5", R.string.key_APH_AdvanceRight)
                        .add("B9", R.string.key_Space)
                        .add("B10", R.string.key_Space)
                        .build(),
                        new UsbDeviceProfile[] {
                                new UsbDeviceProfile(0X0904, 0X3000,
                                        "Refreshabraille 18"),
                                new UsbDeviceProfile(0X0904, 0X3001,
                                        "Refreshabraille / Orbit emulation")
                        },
                        Pattern.compile("Refreshabraille")));

        // APH Orbit Reader.
        // Secure connections get prematurely closed 50% of the time
        // by the Orbit Reader.
        l.add(new NameRegexSupportedDevice("bm", false,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Left", R.string.key_JoystickLeft)
                        .add("Right", R.string.key_JoystickRight)
                        .add("Up", R.string.key_JoystickUp)
                        .add("Down", R.string.key_JoystickDown)
                        .add("Press", R.string.key_JoystickCenter)
                        .add("Display2", R.string.key_APH_AdvanceLeft)
                        .add("Display5", R.string.key_APH_AdvanceRight)
                        .add("Space", R.string.key_Space)
                        .build(),
                        new UsbDeviceProfile[] {
                                new UsbDeviceProfile(0X0483, 0XA1D3,
                                        "Orbit 20")
                        },
                        Pattern.compile("Orbit"),
                        Pattern.compile("Orbit Reader (20|20 Plus|40)")));

        // Baum VarioConnect
        l.add(new NameRegexSupportedDevice("bm", false,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Left", R.string.key_JoystickLeft)
                        .add("Right", R.string.key_JoystickRight)
                        .add("Up", R.string.key_JoystickUp)
                        .add("Down", R.string.key_JoystickDown)
                        .add("Press", R.string.key_JoystickCenter)
                        .routing()
                        .add("Display2", R.string.key_APH_AdvanceLeft)
                        .add("Display5", R.string.key_APH_AdvanceRight)
                        .add("B9", R.string.key_Space)
                        .add("B10", R.string.key_Space)
                        .build(),
                        Pattern.compile("VarioConnect")));
        // Baum VarioUltra
        l.add(new NameRegexSupportedDevice("bm", false,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Left", R.string.key_JoystickLeft)
                        .add("Right", R.string.key_JoystickRight)
                        .add("Up", R.string.key_JoystickUp)
                        .add("Down", R.string.key_JoystickDown)
                        .add("Press", R.string.key_JoystickCenter)
                        .routing()
                        .add("Display2", R.string.key_APH_AdvanceLeft)
                        .add("Display5", R.string.key_APH_AdvanceRight)
                        .add("B9", R.string.key_Space)
                        .add("B10", R.string.key_Space)
                        .build(),
                        Pattern.compile("VarioUltra")));

        // Older Brailliant, from Humanware group. Uses Baum
        // protocol. No Braille keyboard on this one. Secure
        // connections currently fail on Android devices with this
        // display.
        l.add(new NameRegexSupportedDevice("bm", false,
                new KeyNameMapBuilder()
                        .add("Display1", R.string.key_hwg_brailliant_Display1)
                        .add("Display2", R.string.key_hwg_brailliant_Display2)
                        .add("Display3", R.string.key_hwg_brailliant_Display3)
                        .add("Display4", R.string.key_hwg_brailliant_Display4)
                        .add("Display5", R.string.key_hwg_brailliant_Display5)
                        .add("Display6", R.string.key_hwg_brailliant_Display6)
                        .routing()
                        .build(),
                        Pattern.compile("HWG Brailliant")));

        // Braillex Trio
        l.add(new NameRegexSupportedDevice("pm", true,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("LeftSpace", R.string.key_Space)
                        .add("RightSpace", R.string.key_Space)
                        .add("Space", R.string.key_Space)
                        .add("LeftThumb", R.string.key_braillex_LeftThumb)
                        .add("RightThumb", R.string.key_braillex_RightThumb)
                        .add("RoutingKey1", R.string.key_Routing)
                        .add("BarLeft1", R.string.key_braillex_BarLeft1)
                        .add("BarLeft2", R.string.key_braillex_BarLeft2)
                        .add("BarRight1", R.string.key_braillex_BarRight1)
                        .add("BarRight2", R.string.key_braillex_BarRight2)
                        .add("BarUp1", R.string.key_braillex_BarUp1)
                        .add("BarUp2", R.string.key_braillex_BarUp2)
                        .add("BarDown1", R.string.key_braillex_BarDown1)
                        .add("BarDown2", R.string.key_braillex_BarDown2)
                        .add("LeftKeyRear", R.string.key_braillex_LeftKeyRear)
                        .add("LeftKeyFront", R.string.key_braillex_LeftKeyFront)
                        .add("RightKeyRear", R.string.key_braillex_RightKeyRear)
                        .add("RightKeyFront",
                                R.string.key_braillex_RightKeyFront)
                        .build(),
                        Pattern.compile("braillex trio")));

        // Alva BC640/BC680
        l.add(new NameRegexSupportedDevice("al", false,
                new KeyNameMapBuilder()
                // No braille dot keys.
                .add("ETouchLeftRear", R.string.key_albc_ETouchLeftRear)
                .add("ETouchRightRear", R.string.key_albc_ETouchRightRear)
                .add("ETouchLeftFront", R.string.key_albc_ETouchLeftFront)
                .add("ETouchRightFront", R.string.key_albc_ETouchRightFront)
                .add("SmartpadF1", R.string.key_albc_SmartpadF1)
                .add("SmartpadF2", R.string.key_albc_SmartpadF2)
                .add("SmartpadF3", R.string.key_albc_SmartpadF3)
                .add("SmartpadF4", R.string.key_albc_SmartpadF4)
                .add("SmartpadUp", R.string.key_albc_SmartpadUp)
                .add("SmartpadDown", R.string.key_albc_SmartpadDown)
                .add("SmartpadLeft", R.string.key_albc_SmartpadLeft)
                .add("SmartpadRight", R.string.key_albc_SmartpadRight)
                .add("SmartpadEnter", R.string.key_albc_SmartpadEnter)
                .add("ThumbLeft", R.string.key_albc_ThumbLeft)
                .add("ThumbRight", R.string.key_albc_ThumbRight)
                .add("ThumbUp", R.string.key_albc_ThumbUp)
                .add("ThumbDown", R.string.key_albc_ThumbDown)
                .add("ThumbHome", R.string.key_albc_ThumbHome)
                .add("RoutingKey1", R.string.key_Routing)
                .build(),
                Pattern.compile("Alva BC", Pattern.CASE_INSENSITIVE)));

        // HandyTech displays
        l.add(new NameRegexSupportedDevice("ht", true,
                new KeyNameMapBuilder()
                    .add("B4", R.string.key_Dot1)
                    .add("B3", R.string.key_Dot2)
                    .add("B2", R.string.key_Dot3)
                    .add("B1", R.string.key_Dot7)
                    .add("B5", R.string.key_Dot4)
                    .add("B6", R.string.key_Dot5)
                    .add("B7", R.string.key_Dot6)
                    .add("B8", R.string.key_Dot8)
                    .routing()
                    .add("LeftRockerTop",
                        R.string.key_handytech_LeftTrippleActionTop)
                    .add("LeftRockerBottom",
                        R.string.key_handytech_LeftTrippleActionBottom)
                    .add("LeftRockerTop+LeftRockerBottom",
                        R.string.key_handytech_LeftTrippleActionMiddle)
                    .add("RightRockerTop",
                        R.string.key_handytech_RightTrippleActionTop)
                    .add("RightRockerBottom",
                        R.string.key_handytech_RightTrippleActionBottom)
                    .add("RightRockerTop+RightRockerBottom",
                        R.string.key_handytech_RightTrippleActionMiddle)
                    .add("SpaceLeft", R.string.key_handytech_LeftSpace)
                    .add("SpaceRight", R.string.key_handytech_RightSpace)
                    .add("Display1", R.string.key_hwg_brailliant_Display1)
                    .add("Display2", R.string.key_hwg_brailliant_Display2)
                    .add("Display3", R.string.key_hwg_brailliant_Display3)
                    .add("Display4", R.string.key_hwg_brailliant_Display4)
                    .add("Display5", R.string.key_hwg_brailliant_Display5)
                    .add("Display6", R.string.key_hwg_brailliant_Display6)
                    .build(),
                    Pattern.compile("(Braille Wave( BRW)?|Braillino( BL2)?|Braille Star 40( BS4)?|Easy Braille( EBR)?|Active Braille( AB4)?|Basic Braille BB[3,4,6]?)\\/[a-zA-Z][0-9]-[0-9]{5}"),
                    Pattern.compile("(BRW|BL2|BS4|EBR|AB4|BB(3|4|6)?)\\/[a-zA-Z][0-9]-[0-9]{5}")));

        // Seika Mini Note Taker. Secure connections fail to connect reliably.
        l.add(new NameRegexSupportedDevice("sk", false,
                new KeyNameMapBuilder()
                .dots8()
                .routing()
                .dualJoysticks()
                .add("Backspace", R.string.key_Backspace)
                .add("Space", R.string.key_Space)
                .add("LeftButton", R.string.key_skntk_PanLeft)
                .add("RightButton", R.string.key_skntk_PanRight)
                .build(),
                Pattern.compile("TSM")));

        // Seika Braille Display. No Braille keys on this display.
        l.add(new NameRegexSupportedDevice("sk", true,
                new KeyNameMapBuilder()
                .add("K1", R.string.key_skbdp_PanLeft)
                .add("K8", R.string.key_skbdp_PanRight)
                .add("K2", R.string.key_skbdp_LeftRockerLeft)
                .add("K3", R.string.key_skbdp_LeftRockerRight)
                .add("K4", R.string.key_skbdp_LeftLongKey)
                .add("K5", R.string.key_skbdp_RightLongKey)
                .add("K6", R.string.key_skbdp_RightRockerLeft)
                .add("K7", R.string.key_skbdp_RightRockerRight)
                .add("RoutingKey2", R.string.key_Routing)
                .routing()
                .build(),
                Pattern.compile("TS5")));

        SUPPORTED_DEVICES = Collections.unmodifiableList(l);
    }
}
