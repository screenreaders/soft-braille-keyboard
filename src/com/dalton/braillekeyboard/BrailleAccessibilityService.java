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
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.DisplayClient;
import com.googlecode.eyesfree.braille.translate.TranslationResult;

import java.util.ArrayList;
import java.io.File;
import java.util.List;

public class BrailleAccessibilityService extends AccessibilityService
        implements BrailleParser.BrailleParserListener {
    public interface ScreenshotListener {
        void onSaved(File file);

        void onError(String reason);
    }

    private static volatile BrailleAccessibilityService activeInstance;

    private DisplayClient displayClient;
    private BrailleParser brailleParser;
    private AccessibilityNodeInfo focusedNode;
    private TranslationResult lastTranslation;
    private String lastRenderedText = "";
    private int[] lastVisiblePositions = new int[0];
    private int lastCursorPosition;
    private int panOffset;
    private final Rect imeKeyboardRegion = new Rect();
    private boolean imeKeyboardVisible;

    @Override
    public void onCreate() {
        super.onCreate();
        brailleParser = new BrailleParser(this, this);
        BrailleDisplayPreferences.setServiceStatus(this,
                getString(R.string.braille_service_status_starting));
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            setServiceInfo(info);
        }
        activeInstance = this;

        BrailleImePassthroughBridge.registerService(this);
        applyImePassthroughRegion();
        connectDisplayClient();
        updateDisplayedContent(null, null);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
        case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        case AccessibilityEvent.TYPE_VIEW_FOCUSED:
        case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
        case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
        case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
        case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
        case AccessibilityEvent.TYPE_VIEW_SELECTED:
            updateDisplayedContent(obtainBestNode(event), event);
            break;
        default:
            break;
        }
    }

    @Override
    public void onInterrupt() {
        BrailleDisplayPreferences.setServiceStatus(this,
                getString(R.string.braille_service_status_interrupted));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clearFocusedNode();
        if (displayClient != null) {
            displayClient.shutdown();
            displayClient = null;
        }
        if (brailleParser != null) {
            brailleParser.destroy();
            brailleParser = null;
        }
        if (activeInstance == this) {
            activeInstance = null;
        }
        BrailleImePassthroughBridge.unregisterService(this);
        BrailleDisplayPreferences.setServiceStatus(this,
                getString(R.string.braille_service_status_stopped));
    }

    public static boolean canCaptureScreens() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && activeInstance != null;
    }

    public static void captureCurrentScreen(File file,
            ScreenshotListener listener) {
        BrailleAccessibilityService service = activeInstance;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || service == null) {
            if (listener != null) {
                listener.onError("screenshot service unavailable");
            }
            return;
        }
        service.captureCurrentScreenInternal(file, listener);
    }

    private void captureCurrentScreenInternal(final File file,
            final ScreenshotListener listener) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (listener != null) {
                listener.onError("unsupported android version");
            }
            return;
        }
        final int displayId = getDisplay() != null
                ? getDisplay().getDisplayId()
                : android.view.Display.DEFAULT_DISPLAY;
        try {
            takeScreenshot(displayId, getMainExecutor(),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(ScreenshotResult result) {
                            saveScreenshotResult(result, file, listener);
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            if (listener != null) {
                                listener.onError("takeScreenshot failed: "
                                        + errorCode);
                            }
                        }
                    });
        } catch (RuntimeException e) {
            if (listener != null) {
                listener.onError("takeScreenshot runtime error");
            }
        }
    }

    private void saveScreenshotResult(ScreenshotResult result, final File file,
            final ScreenshotListener listener) {
        BrailleAccessibilityScreenshotUtils.saveScreenshotResult(result, file,
                listener);
    }

    void onImeKeyboardRegionChanged(Rect region, boolean visible) {
        if (region != null) {
            imeKeyboardRegion.set(region);
        } else {
            imeKeyboardRegion.setEmpty();
        }
        imeKeyboardVisible = visible && !imeKeyboardRegion.isEmpty();
        applyImePassthroughRegion();
    }

    private void applyImePassthroughRegion() {
        BrailleAccessibilityScreenshotUtils.applyImePassthroughRegion(this,
                imeKeyboardRegion, imeKeyboardVisible);
    }

    @Override
    public void onTranslatorReady(int status) {
        if (status == BrailleParser.STATUS_OK) {
            updateDisplayedContent(null, null);
        } else {
            BrailleDisplayPreferences.setServiceStatus(this,
                    getString(R.string.braille_service_status_translator_error));
        }
    }

    private void connectDisplayClient() {
        if (displayClient != null) {
            return;
        }
        displayClient = new DisplayClient(this);
        bindDisplayClientListeners();
    }

    private void handleDisplayInput(BrailleInputEvent inputEvent) {
        if (inputEvent.isRawKeyEvent()) {
            return;
        }
        String interpretation = interpretInputEvent(inputEvent);
        BrailleDisplayPreferences.setServiceCommand(this, interpretation);

        boolean handled;
        switch (inputEvent.getCommand()) {
        case BrailleInputEvent.CMD_NAV_PAN_LEFT:
            handled = panDisplay(-getDisplayWidth());
            break;
        case BrailleInputEvent.CMD_NAV_PAN_RIGHT:
            handled = panDisplay(getDisplayWidth());
            break;
        case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
            handled = moveFocus(false);
            break;
        case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
            handled = moveFocus(true);
            break;
        case BrailleInputEvent.CMD_NAV_LINE_PREVIOUS:
            handled = navigateLine(false);
            break;
        case BrailleInputEvent.CMD_NAV_LINE_NEXT:
            handled = navigateLine(true);
            break;
        case BrailleInputEvent.CMD_NAV_TOP:
            handled = navigateToBoundary(false);
            break;
        case BrailleInputEvent.CMD_NAV_BOTTOM:
            handled = navigateToBoundary(true);
            break;
        case BrailleInputEvent.CMD_SCROLL_BACKWARD:
            handled = scroll(false);
            break;
        case BrailleInputEvent.CMD_SCROLL_FORWARD:
            handled = scroll(true);
            break;
        case BrailleInputEvent.CMD_SECTION_NEXT:
            handled = navigateHtmlElement(true, "SECTION")
                    || navigateSemanticNode(true, SemanticTarget.SECTION);
            break;
        case BrailleInputEvent.CMD_SECTION_PREVIOUS:
            handled = navigateHtmlElement(false, "SECTION")
                    || navigateSemanticNode(false, SemanticTarget.SECTION);
            break;
        case BrailleInputEvent.CMD_CONTROL_NEXT:
            handled = navigateHtmlElement(true, "CONTROL")
                    || navigateSemanticNode(true, SemanticTarget.CONTROL);
            break;
        case BrailleInputEvent.CMD_CONTROL_PREVIOUS:
            handled = navigateHtmlElement(false, "CONTROL")
                    || navigateSemanticNode(false, SemanticTarget.CONTROL);
            break;
        case BrailleInputEvent.CMD_LIST_NEXT:
            handled = navigateHtmlElement(true, "LIST")
                    || navigateSemanticNode(true, SemanticTarget.LIST);
            break;
        case BrailleInputEvent.CMD_LIST_PREVIOUS:
            handled = navigateHtmlElement(false, "LIST")
                    || navigateSemanticNode(false, SemanticTarget.LIST);
            break;
        case BrailleInputEvent.CMD_ACTIVATE_CURRENT:
            handled = performCurrentAction(AccessibilityNodeInfo.ACTION_CLICK);
            break;
        case BrailleInputEvent.CMD_LONG_PRESS_CURRENT:
        case BrailleInputEvent.CMD_LONG_PRESS_ROUTE:
            handled = performCurrentAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
            break;
        case BrailleInputEvent.CMD_ROUTE:
            handled = routeToPosition(inputEvent.getArgument());
            break;
        case BrailleInputEvent.CMD_BRAILLE_KEY:
            handled = commitBrailleDots(inputEvent.getArgument());
            break;
        case BrailleInputEvent.CMD_KEY_DEL:
            handled = deleteFromFocusedNode(false);
            break;
        case BrailleInputEvent.CMD_KEY_FORWARD_DEL:
            handled = deleteFromFocusedNode(true);
            break;
        case BrailleInputEvent.CMD_KEY_ENTER:
            handled = replaceSelection("\n");
            break;
        case BrailleInputEvent.CMD_GLOBAL_BACK:
            handled = performGlobalAction(GLOBAL_ACTION_BACK);
            break;
        case BrailleInputEvent.CMD_GLOBAL_HOME:
            handled = performGlobalAction(GLOBAL_ACTION_HOME);
            break;
        case BrailleInputEvent.CMD_GLOBAL_RECENTS:
            handled = performGlobalAction(GLOBAL_ACTION_RECENTS);
            break;
        case BrailleInputEvent.CMD_GLOBAL_NOTIFICATIONS:
            handled = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
            break;
        case BrailleInputEvent.CMD_TOGGLE_BRAILLE_GRADE:
            brailleParser.switchBrailleType(this);
            updateDisplayedContent(null, null);
            handled = true;
            break;
        case BrailleInputEvent.CMD_HELP:
        case BrailleInputEvent.CMD_TOGGLE_BRAILLE_MENU:
            handled = openDiagnostics();
            break;
        default:
            handled = false;
            break;
        }

        if (!handled) {
            BrailleDisplayPreferences.setServiceCommand(this,
                    interpretation + " "
                            + getString(R.string.braille_service_action_not_handled));
        }
    }

    private void updateDisplayedContent(AccessibilityNodeInfo node,
            AccessibilityEvent event) {
        AccessibilityNodeInfo snapshot = updateFocusedSnapshot(node);
        CharSequence rendered = buildRenderedText(snapshot, event);
        updateRenderedState(snapshot, rendered);
        if (snapshot != null) {
            snapshot.recycle();
        }
        BrailleDisplayPreferences.setServiceContent(this, lastRenderedText);
        renderToBrailleDisplay();
    }

    private void bindDisplayClientListeners() {
        displayClient.setOnConnectionStateChangeListener(
                new com.googlecode.eyesfree.braille.display.Display.OnConnectionStateChangeListener() {
                    @Override
                    public void onConnectionStateChanged(int state) {
                        handleConnectionStateChanged(state);
                    }
                });
        displayClient.setOnConnectionChangeProgressListener(
                new com.googlecode.eyesfree.braille.display.Display.OnConnectionChangeProgressListener() {
                    @Override
                    public void onConnectionChangeProgress(String description) {
                        BrailleDisplayPreferences.setServiceStatus(
                                BrailleAccessibilityService.this,
                                description == null
                                        ? getString(
                                                R.string.braille_service_status_idle)
                                        : description);
                    }
                });
        displayClient.setOnInputEventListener(
                new com.googlecode.eyesfree.braille.display.Display.OnInputEventListener() {
                    @Override
                    public void onInputEvent(BrailleInputEvent inputEvent) {
                        handleDisplayInput(inputEvent);
                    }
                });
    }

    private void handleConnectionStateChanged(int state) {
        BrailleDisplayPreferences.setServiceStatus(this, buildStatusText(state));
        if (state == com.googlecode.eyesfree.braille.display.Display.STATE_CONNECTED) {
            panOffset = 0;
            updateDisplayedContent(null, null);
        } else if (state
                == com.googlecode.eyesfree.braille.display.Display.STATE_NOT_CONNECTED) {
            clearRenderedState();
        }
    }

    private AccessibilityNodeInfo updateFocusedSnapshot(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo activeNode = node;
        if (activeNode == null) {
            activeNode = obtainCurrentFocusedNode();
        }
        if (activeNode != null) {
            replaceFocusedNode(activeNode);
        } else {
            clearFocusedNode();
        }
        return obtainStoredFocusedNode();
    }

    private void updateRenderedState(AccessibilityNodeInfo snapshot,
            CharSequence rendered) {
        lastRenderedText = rendered == null ? "" : rendered.toString();
        lastCursorPosition = getCursorPosition(snapshot, rendered);
        BrailleDisplayPreferences.setServiceContent(this, lastRenderedText);
    }

    private void clearRenderedState() {
        lastTranslation = null;
        lastVisiblePositions = new int[0];
    }

    private void renderToBrailleDisplay() {
        if (displayClient == null || brailleParser == null) {
            return;
        }

        BrailleDisplayProperties properties = displayClient.getDisplayProperties();
        if (properties == null || properties.getNumTextCells() <= 0) {
            return;
        }

        String address = BrailleDisplayPreferences.getLastConnectedDeviceAddress(this);
        String tableOverride = BrailleDisplayPreferences.getDeviceTable(this,
                address);
        TranslationResult translation = brailleParser.translateText(this,
                lastRenderedText, lastCursorPosition, tableOverride);
        if (translation == null || translation.getCells() == null) {
            BrailleDisplayPreferences.setServiceStatus(this,
                    getString(R.string.braille_service_status_waiting_for_translation));
            return;
        }

        lastTranslation = translation;
        int displayWidth = properties.getNumTextCells();
        byte[] translatedCells = translation.getCells();
        int maxOffset = Math.max(0, translatedCells.length - displayWidth);
        if (panOffset > maxOffset) {
            panOffset = maxOffset;
        }
        if (panOffset < 0) {
            panOffset = 0;
        }

        byte[] visibleCells = new byte[displayWidth];
        int[] visiblePositions = new int[displayWidth];
        int[] brailleToTextPositions = translation.getBrailleToTextPositions();
        for (int i = 0; i < displayWidth; i++) {
            int translatedIndex = panOffset + i;
            if (translatedIndex < translatedCells.length) {
                visibleCells[i] = translatedCells[translatedIndex];
            }
            int textPosition = 0;
            if (brailleToTextPositions != null
                    && translatedIndex < brailleToTextPositions.length
                    && brailleToTextPositions[translatedIndex] >= 0
                    && brailleToTextPositions[translatedIndex] <= lastRenderedText.length()) {
                textPosition = brailleToTextPositions[translatedIndex];
            }
            visiblePositions[i] = textPosition;
        }
        lastVisiblePositions = visiblePositions;
        displayClient.displayDots(visibleCells, lastRenderedText, visiblePositions);
        BrailleDisplayPreferences.setServiceStatus(this, buildStatusText(
                com.googlecode.eyesfree.braille.display.Display.STATE_CONNECTED));
    }

    private boolean panDisplay(int delta) {
        if (lastTranslation == null) {
            return false;
        }
        int displayWidth = getDisplayWidth();
        if (displayWidth <= 0) {
            return false;
        }
        int maxOffset = Math.max(0, lastTranslation.getCells().length - displayWidth);
        int nextOffset = panOffset + delta;
        if (nextOffset < 0) {
            nextOffset = 0;
        }
        if (nextOffset > maxOffset) {
            nextOffset = maxOffset;
        }
        if (nextOffset == panOffset) {
            return false;
        }
        panOffset = nextOffset;
        renderToBrailleDisplay();
        return true;
    }

    private boolean moveFocus(boolean forward) {
        AccessibilityNodeInfo node = obtainCurrentOrStoredFocusedNode();
        if (node == null) {
            return false;
        }
        AccessibilityNodeInfo target = node.focusSearch(
                forward ? View.FOCUS_FORWARD : View.FOCUS_BACKWARD);
        boolean handled = false;
        if (target != null) {
            handled = focusAndRender(target);
            target.recycle();
        }
        node.recycle();
        return handled;
    }

    private boolean navigateLine(boolean forward) {
        AccessibilityNodeInfo editableNode = obtainEditableNode();
        if (editableNode != null) {
            try {
                int action = forward
                        ? AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
                        : AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
                Bundle arguments = new Bundle();
                arguments.putInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                        AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE);
                boolean handled = editableNode.performAction(action, arguments);
                if (handled) {
                    renderFocusedNode(editableNode);
                }
                return handled;
            } finally {
                editableNode.recycle();
            }
        }
        return scroll(forward);
    }

    private boolean scroll(boolean forward) {
        AccessibilityNodeInfo node = obtainStoredFocusedNode();
        while (node != null) {
            int action = forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
            if (performActionIfSupported(node, action)) {
                renderFocusedNode(null);
                node.recycle();
                return true;
            }
            AccessibilityNodeInfo parent = node.getParent();
            node.recycle();
            node = parent;
        }
        return false;
    }

    private boolean navigateHtmlElement(boolean forward, String element) {
        AccessibilityNodeInfo node = obtainCurrentOrStoredFocusedNode();
        if (node == null) {
            return false;
        }
        try {
            Bundle arguments = new Bundle();
            arguments.putString(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_HTML_ELEMENT_STRING,
                    element);
            boolean handled = node.performAction(
                    forward ? AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT
                            : AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT,
                    arguments);
            if (handled) {
                renderFocusedNode(null);
            }
            return handled;
        } finally {
            node.recycle();
        }
    }

    private enum SemanticTarget {
        SECTION,
        CONTROL,
        LIST
    }

    private boolean navigateSemanticNode(boolean forward, SemanticTarget target) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        AccessibilityNodeInfo current = obtainStoredFocusedNode();
        if (current == null) {
            current = obtainCurrentFocusedNode();
        }
        List<AccessibilityNodeInfo> nodes = new ArrayList<AccessibilityNodeInfo>();
        try {
            collectTraversalNodes(root, nodes, target);
            if (nodes.isEmpty()) {
                return false;
            }
            int currentIndex = -1;
            if (current != null) {
                for (int i = 0; i < nodes.size(); i++) {
                    if (areSameNode(current, nodes.get(i))) {
                        currentIndex = i;
                        break;
                    }
                }
            }
            int index = forward ? currentIndex + 1
                    : (currentIndex < 0 ? nodes.size() - 1 : currentIndex - 1);
            while (index >= 0 && index < nodes.size()) {
                AccessibilityNodeInfo candidate = nodes.get(index);
                if (focusNodeOrDescendant(candidate)) {
                    renderFocusedNode(candidate);
                    return true;
                }
                index += forward ? 1 : -1;
            }
        } finally {
            if (current != null) {
                current.recycle();
            }
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null) {
                    node.recycle();
                }
            }
            root.recycle();
        }
        return false;
    }

    private void collectTraversalNodes(AccessibilityNodeInfo node,
            List<AccessibilityNodeInfo> out, SemanticTarget target) {
        if (node == null || out == null) {
            return;
        }
        if (matchesSemanticTarget(node, target)) {
            out.add(AccessibilityNodeInfo.obtain(node));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            try {
                collectTraversalNodes(child, out, target);
            } finally {
                child.recycle();
            }
        }
    }

    private boolean focusNodeOrDescendant(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                || node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            try {
                if (focusNodeOrDescendant(child)) {
                    return true;
                }
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private boolean areSameNode(AccessibilityNodeInfo first,
            AccessibilityNodeInfo second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.equals(second)) {
            return true;
        }
        Rect firstBounds = new Rect();
        Rect secondBounds = new Rect();
        first.getBoundsInScreen(firstBounds);
        second.getBoundsInScreen(secondBounds);
        return TextUtils.equals(first.getViewIdResourceName(),
                second.getViewIdResourceName())
                && TextUtils.equals(first.getClassName(), second.getClassName())
                && TextUtils.equals(first.getText(), second.getText())
                && TextUtils.equals(first.getContentDescription(),
                        second.getContentDescription())
                && firstBounds.equals(secondBounds);
    }

    private boolean navigateToBoundary(boolean end) {
        AccessibilityNodeInfo editableNode = obtainEditableNode();
        if (editableNode != null) {
            try {
                CharSequence text = editableNode.getText();
                int boundary = end
                        ? (text == null ? 0 : text.length()) : 0;
                boolean handled = setSelection(editableNode, boundary, boundary);
                if (handled) {
                    panOffset = 0;
                    updateDisplayedContent(AccessibilityNodeInfo.obtain(editableNode),
                            null);
                }
                return handled;
            } finally {
                editableNode.recycle();
            }
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        try {
            AccessibilityNodeInfo target = findFocusableNode(root, end);
            if (target == null) {
                return false;
            }
            try {
                return focusAndRender(target);
            } finally {
                target.recycle();
            }
        } finally {
            root.recycle();
        }
    }

    private AccessibilityNodeInfo findFocusableNode(AccessibilityNodeInfo node,
            boolean last) {
        AccessibilityNodeInfo best = isSemanticallyFocusable(node)
                ? AccessibilityNodeInfo.obtain(node) : null;
        int childCount = node.getChildCount();
        if (last) {
            for (int i = childCount - 1; i >= 0; i--) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) {
                    continue;
                }
                try {
                    AccessibilityNodeInfo candidate = findFocusableNode(child, true);
                    if (candidate != null) {
                        if (best != null) {
                            best.recycle();
                        }
                        best = candidate;
                        break;
                    }
                } finally {
                    child.recycle();
                }
            }
        } else {
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) {
                    continue;
                }
                try {
                    AccessibilityNodeInfo candidate = findFocusableNode(child, false);
                    if (candidate != null) {
                        if (best != null) {
                            best.recycle();
                        }
                        best = candidate;
                        break;
                    }
                } finally {
                    child.recycle();
                }
            }
        }
        return best;
    }

    private boolean isSemanticallyFocusable(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        return node.isFocusable() || node.isClickable() || node.isEditable()
                || node.isCheckable()
                || !TextUtils.isEmpty(node.getText())
                || !TextUtils.isEmpty(node.getContentDescription());
    }

    private boolean performCurrentAction(int action) {
        AccessibilityNodeInfo node = obtainStoredFocusedNode();
        if (node == null) {
            return false;
        }
        try {
            boolean handled = node.performAction(action);
            if (handled) {
                renderFocusedNode(null);
            }
            return handled;
        } finally {
            node.recycle();
        }
    }

    private boolean routeToPosition(int position) {
        if (lastVisiblePositions == null || lastVisiblePositions.length == 0) {
            return false;
        }
        int index = position;
        if (index < 0) {
            index = 0;
        }
        if (index >= lastVisiblePositions.length) {
            index = lastVisiblePositions.length - 1;
        }
        int textPosition = lastVisiblePositions[index];
        AccessibilityNodeInfo editableNode = obtainEditableNode();
        if (editableNode == null) {
            return performCurrentAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        try {
            boolean handled = setSelection(editableNode, textPosition, textPosition);
            if (handled) {
                panOffset = Math.max(0, panOffset + index - (getDisplayWidth() / 2));
                updateDisplayedContent(AccessibilityNodeInfo.obtain(editableNode), null);
            }
            return handled;
        } finally {
            editableNode.recycle();
        }
    }

    private boolean commitBrailleDots(int dotsMask) {
        String overrideTable = BrailleDisplayPreferences.getDeviceTable(this,
                BrailleDisplayPreferences.getLastConnectedDeviceAddress(this));
        Byte[] dots = new Byte[] { Byte.valueOf((byte) dotsMask) };
        String text = brailleParser.backTranslate(this, dots, overrideTable);
        if (TextUtils.isEmpty(text) && dotsMask == 0) {
            text = " ";
        }
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        return replaceSelection(text);
    }

    private boolean replaceSelection(String replacement) {
        AccessibilityNodeInfo editableNode = obtainEditableNode();
        if (editableNode == null) {
            return false;
        }
        try {
            CharSequence currentText = editableNode.getText();
            String current = currentText == null ? "" : currentText.toString();
            int[] range = getEditableSelectionRange(editableNode, current);
            int start = range[0];
            int end = range[1];
            String next = current.substring(0, start) + replacement
                    + current.substring(end);
            Bundle arguments = new Bundle();
            arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    next);
            boolean handled = editableNode.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            if (handled) {
                int nextCursor = start + replacement.length();
                setSelection(editableNode, nextCursor, nextCursor);
                renderFocusedNode(editableNode);
            }
            return handled;
        } finally {
            editableNode.recycle();
        }
    }

    private boolean deleteFromFocusedNode(boolean forwardDelete) {
        AccessibilityNodeInfo editableNode = obtainEditableNode();
        if (editableNode == null) {
            return false;
        }
        try {
            CharSequence currentText = editableNode.getText();
            String current = currentText == null ? "" : currentText.toString();
            int[] range = getEditableSelectionRange(editableNode, current);
            int start = range[0];
            int end = range[1];
            if (start == end) {
                if (forwardDelete) {
                    if (end >= current.length()) {
                        return false;
                    }
                    end++;
                } else {
                    if (start <= 0) {
                        return false;
                    }
                    start--;
                }
            }

            String next = current.substring(0, start) + current.substring(end);
            Bundle arguments = new Bundle();
            arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    next);
            boolean handled = editableNode.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            if (handled) {
                setSelection(editableNode, start, start);
                renderFocusedNode(editableNode);
            }
            return handled;
        } finally {
            editableNode.recycle();
        }
    }

    private boolean setSelection(AccessibilityNodeInfo node, int start, int end) {
        int max = getNodeTextLength(node);
        Bundle arguments = new Bundle();
        arguments.putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                clampSelectionIndex(start, max));
        arguments.putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                clampSelectionIndex(end, max));
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,
                arguments);
    }

    private int[] getEditableSelectionRange(AccessibilityNodeInfo node,
            String current) {
        int max = current == null ? 0 : current.length();
        int start = node.getTextSelectionStart();
        int end = node.getTextSelectionEnd();
        if (start < 0 || end < 0) {
            start = max;
            end = max;
        }
        start = clampSelectionIndex(start, max);
        end = clampSelectionIndex(end, max);
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        return new int[] { start, end };
    }

    private int getNodeTextLength(AccessibilityNodeInfo node) {
        if (node == null || node.getText() == null) {
            return 0;
        }
        return node.getText().length();
    }

    private static int clampSelectionIndex(int value, int max) {
        if (value < 0) {
            return 0;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private AccessibilityNodeInfo obtainEditableNode() {
        AccessibilityNodeInfo node = obtainStoredFocusedNode();
        while (node != null && !node.isEditable()) {
            AccessibilityNodeInfo parent = node.getParent();
            node.recycle();
            node = parent;
        }
        return node;
    }

    private AccessibilityNodeInfo obtainCurrentOrStoredFocusedNode() {
        AccessibilityNodeInfo node = obtainCurrentFocusedNode();
        return node != null ? node : obtainStoredFocusedNode();
    }

    private boolean focusAndRender(AccessibilityNodeInfo node) {
        if (!requestAccessibilityFocus(node)) {
            return false;
        }
        renderFocusedNode(node);
        return true;
    }

    private boolean requestAccessibilityFocus(AccessibilityNodeInfo node) {
        return node != null
                && (node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                        || node.performAction(AccessibilityNodeInfo.ACTION_FOCUS));
    }

    private boolean performActionIfSupported(AccessibilityNodeInfo node, int action) {
        return node != null && (node.getActions() & action) != 0
                && node.performAction(action);
    }

    private void renderFocusedNode(AccessibilityNodeInfo node) {
        panOffset = 0;
        updateDisplayedContent(node == null ? null : AccessibilityNodeInfo.obtain(node),
                null);
    }

    private AccessibilityNodeInfo obtainCurrentFocusedNode() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return null;
        }
        AccessibilityNodeInfo accessibilityFocused = null;
        AccessibilityNodeInfo inputFocused = null;
        try {
            accessibilityFocused = root.findFocus(
                    AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (accessibilityFocused != null) {
                return AccessibilityNodeInfo.obtain(accessibilityFocused);
            }
            inputFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (inputFocused != null) {
                return AccessibilityNodeInfo.obtain(inputFocused);
            }
            return null;
        } finally {
            if (accessibilityFocused != null) {
                accessibilityFocused.recycle();
            }
            if (inputFocused != null) {
                inputFocused.recycle();
            }
            root.recycle();
        }
    }

    private AccessibilityNodeInfo obtainBestNode(AccessibilityEvent event) {
        if (event != null) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                try {
                    return AccessibilityNodeInfo.obtain(source);
                } finally {
                    source.recycle();
                }
            }
        }
        return obtainCurrentFocusedNode();
    }

    private AccessibilityNodeInfo obtainStoredFocusedNode() {
        return focusedNode == null ? null : AccessibilityNodeInfo.obtain(focusedNode);
    }

    private void replaceFocusedNode(AccessibilityNodeInfo node) {
        clearFocusedNode();
        focusedNode = AccessibilityNodeInfo.obtain(node);
        node.recycle();
    }

    private void clearFocusedNode() {
        if (focusedNode != null) {
            focusedNode.recycle();
            focusedNode = null;
        }
    }

    private CharSequence buildRenderedText(AccessibilityNodeInfo node,
            AccessibilityEvent event) {
        return BrailleAccessibilityRenderUtils.buildRenderedText(this, node, event);
    }

    private int getCursorPosition(AccessibilityNodeInfo node,
            CharSequence renderedText) {
        return BrailleAccessibilityRenderUtils.getCursorPosition(node,
                renderedText);
    }

    private int getDisplayWidth() {
        BrailleDisplayProperties properties = displayClient == null ? null
                : displayClient.getDisplayProperties();
        return properties == null ? 0 : properties.getNumTextCells();
    }

    private String buildStatusText(int connectionState) {
        String address = BrailleDisplayPreferences.getLastConnectedDeviceAddress(this);
        String tableOverride = BrailleDisplayPreferences.getDeviceTable(this,
                address);
        return BrailleAccessibilityRenderUtils.buildStatusText(this,
                connectionState, address, tableOverride);
    }

    private String interpretInputEvent(BrailleInputEvent event) {
        return BrailleAccessibilityRenderUtils.interpretInputEvent(this, event);
    }

    private String formatDots(int dotsMask) {
        return BrailleAccessibilityRenderUtils.formatDots(this, dotsMask);
    }

    private boolean openDiagnostics() {
        Intent intent = new Intent(this, BrailleDisplayActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (getPackageManager() != null
                && intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
            return true;
        }
        return false;
    }

    private boolean matchesSemanticTarget(AccessibilityNodeInfo node,
            SemanticTarget target) {
        if (node == null || target == null) {
            return false;
        }
        switch (target) {
        case SECTION:
            return BrailleNodeUtils.isHeading(node)
                    || BrailleNodeUtils.isLandmark(node)
                    || BrailleNodeUtils.isTable(node)
                    || BrailleNodeUtils.isListLike(node)
                    || BrailleNodeUtils.isDialogOrPane(node);
        case CONTROL:
            return BrailleNodeUtils.isFormField(node)
                    || BrailleNodeUtils.isLink(node)
                    || BrailleNodeUtils.isTab(node)
                    || BrailleNodeUtils.isSlider(node) || node.isClickable()
                    || node.isCheckable();
        case LIST:
            return BrailleNodeUtils.isListLike(node)
                    || BrailleNodeUtils.isTable(node)
                    || BrailleNodeUtils.isPager(node);
        default:
            return false;
        }
    }
}
