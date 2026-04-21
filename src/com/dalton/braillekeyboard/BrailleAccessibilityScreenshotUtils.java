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

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.view.Display;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

final class BrailleAccessibilityScreenshotUtils {
    private BrailleAccessibilityScreenshotUtils() {
    }

    static void applyImePassthroughRegion(AccessibilityService service, Rect region,
            boolean visible) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || service == null) {
            return;
        }
        Region passthroughRegion = new Region();
        if (visible && region != null && !region.isEmpty()) {
            passthroughRegion.set(region);
        }
        try {
            int displayId = getDisplayId(service);
            service.setTouchExplorationPassthroughRegion(displayId, passthroughRegion);
            service.setGestureDetectionPassthroughRegion(displayId, passthroughRegion);
        } catch (RuntimeException e) {
            // Keep the service alive even if passthrough is rejected.
        }
    }

    static void saveScreenshotResult(AccessibilityService.ScreenshotResult result,
            File file, BrailleAccessibilityService.ScreenshotListener listener) {
        if (result == null || file == null) {
            notifyError(listener, "empty screenshot result");
            return;
        }
        HardwareBuffer hardwareBuffer = result.getHardwareBuffer();
        ColorSpace colorSpace = result.getColorSpace();
        if (hardwareBuffer == null) {
            notifyError(listener, "missing hardware buffer");
            return;
        }
        Bitmap hardwareBitmap = null;
        Bitmap bitmap = null;
        try {
            hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace);
            if (hardwareBitmap == null) {
                notifyError(listener, "could not wrap hardware buffer");
                return;
            }
            bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false);
        } finally {
            hardwareBuffer.close();
            if (hardwareBitmap != null) {
                hardwareBitmap.recycle();
            }
        }
        if (bitmap == null) {
            notifyError(listener, "could not copy screenshot bitmap");
            return;
        }
        saveBitmap(bitmap, file, listener);
    }

    private static int getDisplayId(AccessibilityService service) {
        Display display = service.getDisplay();
        return display == null ? Display.DEFAULT_DISPLAY : display.getDisplayId();
    }

    private static void saveBitmap(Bitmap bitmap, File file,
            BrailleAccessibilityService.ScreenshotListener listener) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.flush();
            if (listener != null) {
                listener.onSaved(file);
            }
        } catch (IOException e) {
            notifyError(listener, "could not save screenshot");
        } finally {
            bitmap.recycle();
            closeQuietly(stream);
        }
    }

    private static void closeQuietly(FileOutputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // Ignore close failure.
        }
    }

    private static void notifyError(BrailleAccessibilityService.ScreenshotListener listener,
            String reason) {
        if (listener != null) {
            listener.onError(reason);
        }
    }
}
