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

package org.a11y.brltty.android;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public final class UsbHelper {
    public static final String ACTION_USB_PERMISSION =
            "com.dalton.braillekeyboard.action.USB_PERMISSION";

    private static Context sContext;

    private UsbHelper() {
    }

    public static void initialize(Context context) {
        if (context != null) {
            sContext = context.getApplicationContext();
        }
    }

    public static Iterator<UsbDevice> getDeviceIterator() {
        Collection<UsbDevice> devices = getConnectedDevices();
        return devices.iterator();
    }

    public static UsbDevice getNextDevice(Iterator<UsbDevice> iterator) {
        return iterator != null && iterator.hasNext() ? iterator.next() : null;
    }

    public static UsbInterface getDeviceInterface(UsbDevice device, int identifier) {
        if (device == null) {
            return null;
        }
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface usbInterface = device.getInterface(i);
            if (usbInterface != null && usbInterface.getId() == identifier) {
                return usbInterface;
            }
        }
        return null;
    }

    public static UsbEndpoint getInterfaceEndpoint(UsbInterface usbInterface,
            int address) {
        if (usbInterface == null) {
            return null;
        }
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = usbInterface.getEndpoint(i);
            if (endpoint != null && endpoint.getAddress() == address) {
                return endpoint;
            }
        }
        return null;
    }

    public static UsbDeviceConnection openDeviceConnection(UsbDevice device) {
        UsbManager manager = getUsbManager();
        if (manager == null || device == null || !manager.hasPermission(device)) {
            return null;
        }
        return manager.openDevice(device);
    }

    public static Collection<UsbDevice> getConnectedDevices() {
        UsbManager manager = getUsbManager();
        if (manager == null) {
            return Collections.emptyList();
        }
        return manager.getDeviceList().values();
    }

    public static boolean hasPermission(UsbDevice device) {
        UsbManager manager = getUsbManager();
        return manager != null && device != null && manager.hasPermission(device);
    }

    public static boolean requestPermission(Context context, UsbDevice device) {
        Context appContext = context == null ? sContext : context.getApplicationContext();
        if (appContext == null || device == null) {
            return false;
        }
        initialize(appContext);
        UsbManager manager = getUsbManager();
        if (manager == null) {
            return false;
        }
        if (manager.hasPermission(device)) {
            return true;
        }
        Intent intent = new Intent(ACTION_USB_PERMISSION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(appContext,
                device.getDeviceId(), intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);
        manager.requestPermission(device, pendingIntent);
        return true;
    }

    private static UsbManager getUsbManager() {
        return sContext == null ? null : (UsbManager) sContext
                .getSystemService(Context.USB_SERVICE);
    }
}
