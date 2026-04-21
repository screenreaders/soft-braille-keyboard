package com.dalton.braillekeyboard;

import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;

final class BrailleAccessibilityNodeActionUtils {
    private BrailleAccessibilityNodeActionUtils() {
    }

    static boolean setSelection(AccessibilityNodeInfo node, int start, int end) {
        int max = getNodeTextLength(node);
        Bundle arguments = new Bundle();
        arguments.putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                clampSelectionIndex(start, max));
        arguments.putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                clampSelectionIndex(end, max));
        return node != null && node.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments);
    }

    static int[] getEditableSelectionRange(AccessibilityNodeInfo node,
            String current) {
        int max = current == null ? 0 : current.length();
        int start = node == null ? -1 : node.getTextSelectionStart();
        int end = node == null ? -1 : node.getTextSelectionEnd();
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

    static int getNodeTextLength(AccessibilityNodeInfo node) {
        return node == null || node.getText() == null ? 0 : node.getText().length();
    }

    static int clampSelectionIndex(int value, int max) {
        if (value < 0) {
            return 0;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
