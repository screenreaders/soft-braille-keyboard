package com.dalton.braillekeyboard;

import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

final class InputConnectionTextUtils {
    private InputConnectionTextUtils() {
    }

    static ExtractedText getExtractedText(InputConnection ic) {
        return ic == null ? null : ic.getExtractedText(new ExtractedTextRequest(), 0);
    }

    static int getSelectionStart(ExtractedText text) {
        return text == null ? -1 : text.startOffset;
    }

    static int clampToRange(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    static boolean matchesSelectedOrPreviousText(InputConnection ic, CharSequence text) {
        if (ic == null || text == null || text.length() == 0) {
            return false;
        }
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && text.toString().contentEquals(selected)) {
            return true;
        }
        CharSequence beforeCursor = ic.getTextBeforeCursor(text.length(), 0);
        return beforeCursor != null && text.toString().contentEquals(beforeCursor);
    }
}
