package com.dalton.braillekeyboard;

import android.graphics.Rect;

import java.lang.ref.WeakReference;

/**
 * Shares the visible IME region with the braille accessibility service so it
 * can expose the keyboard area as a TalkBack passthrough region.
 */
public final class BrailleImePassthroughBridge {
    private static final Object LOCK = new Object();
    private static final Rect KEYBOARD_REGION = new Rect();
    private static boolean keyboardVisible;
    private static WeakReference<BrailleAccessibilityService> serviceRef =
            new WeakReference<BrailleAccessibilityService>(null);

    private BrailleImePassthroughBridge() {
    }

    public static void registerService(BrailleAccessibilityService service) {
        Rect region = new Rect();
        boolean visible;
        synchronized (LOCK) {
            serviceRef = new WeakReference<BrailleAccessibilityService>(service);
            region.set(KEYBOARD_REGION);
            visible = keyboardVisible;
        }
        if (service != null) {
            service.onImeKeyboardRegionChanged(region, visible);
        }
    }

    public static void unregisterService(BrailleAccessibilityService service) {
        synchronized (LOCK) {
            BrailleAccessibilityService current = serviceRef.get();
            if (current == service) {
                serviceRef = new WeakReference<BrailleAccessibilityService>(null);
            }
        }
    }

    public static void updateKeyboardRegion(Rect region, boolean visible) {
        BrailleAccessibilityService service;
        Rect copy = new Rect();
        synchronized (LOCK) {
            if (region != null) {
                KEYBOARD_REGION.set(region);
                copy.set(region);
            } else {
                KEYBOARD_REGION.setEmpty();
            }
            keyboardVisible = visible && region != null && !region.isEmpty();
            visible = keyboardVisible;
            service = serviceRef.get();
        }
        if (service != null) {
            service.onImeKeyboardRegionChanged(copy, visible);
        }
    }

    public static boolean isPassthroughActive() {
        synchronized (LOCK) {
            return keyboardVisible && !KEYBOARD_REGION.isEmpty();
        }
    }
}
