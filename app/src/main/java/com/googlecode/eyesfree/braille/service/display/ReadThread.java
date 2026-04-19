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

import android.bluetooth.BluetoothSocket;
import android.content.res.Resources;
import android.hardware.usb.UsbDevice;
import android.util.Log;

import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;
import com.dalton.braillekeyboard.R;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The thread that connects to a device that it finds, reads from the device
 * and starts another thread that manages the driver.
 */
class ReadThread extends Thread implements DriverThread.OnInitListener {
    private static final String LOG_TAG = ReadThread.class.getSimpleName();
    private static final long USB_IDLE_WAIT_MILLIS = 250;

    private final DisplayService mDisplayService;
    private final DeviceFinder mDeviceFinder;
    private final File mTablesDir;
    private final String mDeviceAddressToConnectTo;
    private final Resources mResources;
    private volatile BluetoothSocket mSocket;
    private volatile boolean mDisconnecting;
    private volatile DriverThread mDriverThread;
    private volatile DeviceFinder.DeviceInfo mConnectedDeviceInfo;

    public ReadThread(DisplayService displayService, File tablesDir,
            String deviceAddressToConnectTo) {
        mDisplayService = displayService;
        mDeviceFinder = new DeviceFinder(displayService);
        mTablesDir = tablesDir;
        mDeviceAddressToConnectTo = deviceAddressToConnectTo;
        mResources = displayService.getResources();
    }

    @Override
    public void run() {
        try {
            if (connect()) {
                if (mConnectedDeviceInfo != null && mConnectedDeviceInfo.isUsb()) {
                    usbLoop();
                } else {
                    readLoop();
                }
            }
        } finally {
            cleanup();
        }
    }

    public DriverThread getDriverThread() {
        return mDriverThread;
    }

    public void disconnect() {
        closeSocket();
        mDisconnecting = true;
        interrupt();
    }

    private boolean connect() {
        List<DeviceFinder.DeviceInfo> devices = mDeviceFinder.findDevices();
        if (devices.isEmpty()) {
            mDisplayService.setConnectionProgress(
                    mResources.getString(R.string.connprog_no_devices));
            return false;
        }

        tryToConnect(devices);
        if (mConnectedDeviceInfo == null) {
            return false;
        }

        mDisplayService.setConnectionProgress(mResources.getString(
                R.string.connprog_initializing,
                mConnectedDeviceInfo.getDeviceName()));
        try {
            mDriverThread = new DriverThread(mDisplayService,
                    mSocket == null ? null : mSocket.getOutputStream(),
                    mConnectedDeviceInfo, mResources, mTablesDir,
                    this /* initListener */,
                    mDisplayService /* inputEventListener */);
            Log.i(LOG_TAG, "Device connected");
            return true;
        } catch (IOException ex) {
            Log.e(LOG_TAG, "Error while starting driver thread", ex);
        }
        return false;
    }

    private void readLoop() {
        try {
            byte[] buf = new byte[128];
            int readSize;
            do {
                readSize = mSocket.getInputStream().read(buf, 0, buf.length);
                if (readSize > 0) {
                    mDriverThread.addReadOperation(buf, readSize);
                }
            } while (readSize >= 0 && !mDisconnecting);
            Log.i(LOG_TAG, "End of input from device.");
        } catch (IOException ex) {
            Log.i(LOG_TAG, "Socket closed while reading: " + ex);
        }
    }

    private void usbLoop() {
        while (!mDisconnecting) {
            try {
                Thread.sleep(USB_IDLE_WAIT_MILLIS);
            } catch (InterruptedException ex) {
                if (mDisconnecting) {
                    break;
                }
            }
        }
    }

    private void tryToConnect(List<DeviceFinder.DeviceInfo> devices) {
        mSocket = null;
        try {
            for (DeviceFinder.DeviceInfo dev : devices) {
                if (mDisconnecting) {
                    return;
                }
                if (mDeviceAddressToConnectTo != null
                        && !mDeviceAddressToConnectTo.equals(dev.getDeviceAddress())) {
                    continue;
                }
                mDisplayService.setConnectionProgress(
                        mResources.getString(R.string.connprog_trying,
                                dev.getDeviceName()));
                if (dev.isUsb()) {
                    if (connectUsbDevice(dev)) {
                        return;
                    }
                } else if (connectBluetoothDevice(dev)) {
                    return;
                }
            }
        } finally {
            if (mConnectedDeviceInfo == null) {
                mDisplayService.setConnectionProgress(null);
            }
        }
    }

    private boolean connectBluetoothDevice(DeviceFinder.DeviceInfo dev) {
        try {
            BluetoothSocket socket;
            if (dev.getConnectSecurely()) {
                socket = dev.getBluetoothDevice()
                        .createRfcommSocketToServiceRecord(dev.getSdpUuid());
            } else {
                socket = dev.getBluetoothDevice()
                        .createInsecureRfcommSocketToServiceRecord(dev.getSdpUuid());
            }
            if (socket != null) {
                socket.connect();
                mSocket = socket;
                mConnectedDeviceInfo = dev;
                return true;
            }
        } catch (SecurityException ex) {
            Log.e(LOG_TAG, "Bluetooth permission denied while connecting", ex);
        } catch (IOException ex) {
            Log.e(LOG_TAG, "Error opening bluetooth socket: " + ex);
        }
        return false;
    }

    private boolean connectUsbDevice(DeviceFinder.DeviceInfo dev) {
        UsbDevice usbDevice = dev.getUsbDevice();
        if (usbDevice == null) {
            return false;
        }
        if (!mDeviceFinder.hasUsbPermission(dev)) {
            mDeviceFinder.requestUsbPermission(dev);
            mDisplayService.setConnectionProgress(mResources.getString(
                    R.string.connprog_waiting_usb_permission,
                    dev.getDeviceName()));
            return false;
        }
        mConnectedDeviceInfo = dev;
        mSocket = null;
        return true;
    }

    private void closeSocket() {
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException ex) {
                Log.d(LOG_TAG, "Error closing socket: ", ex);
            }
        }
    }

    private void cleanup() {
        closeSocket();
        if (mDriverThread != null) {
            DriverThread localDriverThread = mDriverThread;
            mDriverThread = null;
            localDriverThread.stop();
        }
        mDisplayService.onDisplayDisconnected();
        Log.i(LOG_TAG, "Display disconnected");
    }

    @Override
    public void onInit(BrailleDisplayProperties properties) {
        if (properties != null) {
            mDeviceFinder.rememberSuccessfulConnection(mConnectedDeviceInfo);
            mDisplayService.setConnectionProgress(null);
            mDisplayService.onDisplayConnected(properties);
        } else {
            mDisplayService.setConnectionProgress(mResources.getString(
                    R.string.connprog_failed_to_initialize,
                    mConnectedDeviceInfo == null ? "unknown"
                            : mConnectedDeviceInfo.getDeviceName()));
            disconnect();
        }
    }
}
