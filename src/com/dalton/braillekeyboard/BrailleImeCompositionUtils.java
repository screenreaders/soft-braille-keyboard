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

import android.view.inputmethod.InputConnection;
import android.view.inputmethod.ExtractedText;

final class BrailleImeCompositionUtils {
    private BrailleImeCompositionUtils() {
    }

    static CharSequence capitalise(CharSequence text, boolean autoCapsEnabled,
            int caps) {
        if (!autoCapsEnabled || caps == 0 || text == null || text.length() == 0) {
            return text;
        }
        return String.valueOf(Character.toUpperCase(text.charAt(0)))
                + text.subSequence(1, text.length());
    }

    static int[] getSelectionBoundaries(ExtractedText text, int mark, int cursor) {
        if (text == null || text.text == null || cursor < 0) {
            return null;
        }
        int start = InputConnectionTextUtils.getSelectionStart(text);
        int end = text.startOffset + text.text.length();
        int safeMark = InputConnectionTextUtils.clampToRange(mark, start, end);
        int safeCursor = InputConnectionTextUtils.clampToRange(cursor, start, end);
        return new int[] { Math.min(safeCursor, safeMark),
                Math.max(safeCursor, safeMark) };
    }

    static boolean hasEditorComposingState(InputConnection ic, String composingText) {
        return composingText != null
                && composingText.length() > 0
                && ic != null
                && InputConnectionTextUtils.matchesSelectedOrPreviousText(ic,
                        composingText);
    }

    static boolean shouldSkipFallbackCommit(InputConnection ic, String text,
            int cursor,
            String lastFallbackCommitText,
            int lastFallbackCommitCursor,
            long lastFallbackCommitAt,
            long now,
            long dedupWindowMs) {
        if (text == null || text.length() == 0) {
            return true;
        }
        if (InputConnectionTextUtils.matchesSelectedOrPreviousText(ic, text)) {
            return true;
        }
        return cursor >= 0
                && cursor == lastFallbackCommitCursor
                && text.equals(lastFallbackCommitText)
                && now - lastFallbackCommitAt < dedupWindowMs;
    }

    static String stringDifference(String str1, String str2) {
        if (str1 == null) {
            return str2;
        }
        if (str2 == null) {
            return null;
        }
        int i = -1;
        while (++i < Math.min(str1.length(), str2.length())
                && Character.toLowerCase(str1.charAt(i))
                == Character.toLowerCase(str2.charAt(i))) {
        }
        return i >= str2.length() ? str2 : str2.substring(i, str2.length());
    }
}
