/*
 * Copyright (C) 2016 The Soft Braille Keyboard Authors
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.Manifest;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import com.googlecode.eyesfree.braille.translate.TableInfo;
import com.googlecode.eyesfree.braille.translate.TranslationResult;

/**
 * Implementation of an Input method service for Android.
 * 
 * Specifically, this IME service implements the capabilities to support Braille
 * input from a BrailleView and several editing capabilities.
 * 
 * You should not instantiate this class directly rather it will create it's own
 * View with the onCreateView method and set this service in that View to
 * facilitate communication between the View and the IME. You should communicate
 * according to the KeyboardListener interface and consult that for further
 * documentation.
 * 
 */
public class BrailleIME extends InputMethodService implements KeyboardListener {
    private static final int IME_TRACE_LIMIT = 120;
    private static final long FALLBACK_COMMIT_DEDUP_WINDOW_MS = 750;
    private static final ArrayDeque<String> IME_TRACE = new ArrayDeque<String>();

    private final List<Byte> cells = new ArrayList<Byte>();
    private final StringBuilder composingText = new StringBuilder();

    private BrailleParser brailleParser;
    private BrailleView brailleView = null;
    private int caps;
    private int cursor = -1;
    private int mark = -1;
    private boolean predictionOn;
    private boolean selectAll = false;
    private long lastFallbackCommitAt;
    private String lastFallbackCommitText;
    private int lastFallbackCommitCursor = -1;
    private final View.OnLayoutChangeListener brailleViewLayoutListener =
            new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right,
                        int bottom, int oldLeft, int oldTop, int oldRight,
                        int oldBottom) {
                    publishAccessibilityPassthroughRegion();
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        if (brailleParser == null) {
            brailleParser = new BrailleParser(this,
                    new BrailleParser.BrailleParserListener() {

                        @Override
                        public void onTranslatorReady(int status) {
                            brailleParserReady(status);
                        }
                    });
        }
    }

    @Override
    public View onCreateInputView() {
        super.onCreateInputView();
        if (brailleView != null) {
            brailleView.removeOnLayoutChangeListener(brailleViewLayoutListener);
        }
        brailleView = (BrailleView) getLayoutInflater().inflate(
                R.layout.keyboard, null);
        brailleView.addOnLayoutChangeListener(brailleViewLayoutListener);

        if (!Options.getBooleanPreference(this,
                R.string.pref_has_asked_record_audio_key, false)) {
            Options.switchBooleanPreference(this,
                    R.string.pref_has_asked_record_audio_key, false);
            // Android 6+ show a permission dialog for record audio dangerous
            // permission.
            // Only do this once on the very first run though.
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(this, IntentActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setAction(getString(R.string.action_record_audio_permission));
                if (canStartActivity(intent)) {
                    startActivity(intent);
                }
            }
        }
        return brailleView;
    }

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        clearComposingState();
        // remove any existing selection.
        selectAll = false;
        mark = -1;

        predictionOn = false;
        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (info.inputType & InputType.TYPE_MASK_CLASS) {
        case InputType.TYPE_CLASS_TEXT:
            predictionOn = true;
            // We now look for a few special variations of text that will
            // modify our behavior.
            int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_URI) {
                // Do not display predictions / what the user is typing
                // when they are entering a password or uri.
                predictionOn = false;
            }

            if ((info.inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                predictionOn = false;
            }
            break;
        default:
        }
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        synchronizeComposingStateWithEditor();
        if (!restarting && brailleView != null) {
            // Tell the user the keyboard is ready, but only the first time it
            // starts for this input field, not restarts. That'll be annoying.
            brailleView.onInitialiseForInput(this, this);
        }
        brailleParser.setTranslator(this);
        syncKeyboardDotsWithBrailleType();
        publishAccessibilityPassthroughRegion();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            finishComposingText(false);
        }

        if (brailleView != null) {
            brailleView.close();
        }
        clearAccessibilityPassthroughRegion();
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        publishAccessibilityPassthroughRegion();
    }

    @Override
    public void onWindowHidden() {
        super.onWindowHidden();
        clearAccessibilityPassthroughRegion();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        publishAccessibilityPassthroughRegion();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (brailleParser != null) {
            brailleParser.destroy();
            brailleParser = null;
        }
        clearAccessibilityPassthroughRegion();
    }

    @Override
    public void updateFullscreenMode() {
        super.updateFullscreenMode();
        publishAccessibilityPassthroughRegion();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // The view dictates whether we are using the full screen.
        // If the keyboard is being used it will always take up the whole
        // screen.
        // If the keyboard is in the shrink state it will not use the full
        // screen.
        return brailleView != null ? !brailleView.getShrinkKeyboard() : false;
    }

    private void brailleParserReady(int status) {
        if (status == BrailleParser.STATUS_OK) {
            syncKeyboardDotsWithBrailleType();
            if (brailleView != null) {
                brailleView.setLocale(getLocale());
            }
            publishAccessibilityPassthroughRegion();
        }
    }

    private void publishAccessibilityPassthroughRegion() {
        if (brailleView == null || !isTalkBackBrailleModeActive()) {
            clearAccessibilityPassthroughRegion();
            return;
        }
        brailleView.post(new Runnable() {
            @Override
            public void run() {
                if (brailleView == null || !brailleView.isShown()
                        || brailleView.getWidth() <= 0
                        || brailleView.getHeight() <= 0) {
                    clearAccessibilityPassthroughRegion();
                    return;
                }
                int[] location = new int[2];
                brailleView.getLocationOnScreen(location);
                Rect region = new Rect(location[0], location[1],
                        location[0] + brailleView.getWidth(),
                        location[1] + brailleView.getHeight());
                BrailleImePassthroughBridge.updateKeyboardRegion(region, true);
            }
        });
    }

    private void clearAccessibilityPassthroughRegion() {
        BrailleImePassthroughBridge.updateKeyboardRegion(new Rect(), false);
    }

    private boolean isTalkBackBrailleModeActive() {
        return Options.getBooleanPreference(this,
                R.string.pref_talkback_braille_mode_key,
                Boolean.parseBoolean(getString(
                        R.string.pref_talkback_braille_mode_default)))
                && brailleView != null
                && brailleView.isTalkBackTouchModeActive();
    }

    private boolean canStartActivity(Intent intent) {
        return intent != null && getPackageManager() != null
                && intent.resolveActivity(getPackageManager()) != null;
    }

    @Override
    public ExtractedText getAllText() {
        InputConnection ic = getCurrentInputConnection();
        return ic == null ? null : ic.getExtractedText(
                new ExtractedTextRequest(), 0);
    }

    @Override
    public CharSequence getTextBeforeCursor(int n) {
        InputConnection ic = getCurrentInputConnection();
        return ic == null ? null : ic.getTextBeforeCursor(n, 0);
    }

    @Override
    public CharSequence getTextAfterCursor(int n) {
        InputConnection ic = getCurrentInputConnection();
        return ic == null ? null : ic.getTextAfterCursor(n, 0);
    }

    @Override
    public CharSequence getSelectedText(int flags) {
        InputConnection ic = getCurrentInputConnection();
        return ic == null ? null : ic.getSelectedText(flags);
    }

    @Override
    public boolean setSelection() {
        if (mark < 0 && !selectAll) {
            return false;
        }

        int cursor = selectAll ? getSelectionStart() : getCursor();
        int[] positions = getSelectionBoundaries(cursor);
        return mark >= 0 && positions != null ? setSelection(positions[0],
                positions[1]) : false;
    }

    @Override
    public boolean setSelection(int cursor) {
        // Disable any selection first.
        if (selectAll) {
            toggleMark();
            selectAll = false;
        }
        finishComposingText();
        this.cursor = cursor;

        // Set the cursor to the new requested position.
        return setSelection(cursor, cursor);
    }

    @Override
    public boolean performContextMenuAction(int id) {
        InputConnection ic = getCurrentInputConnection();
        return ic != null && ic.performContextMenuAction(id);
    }

    @Override
    public boolean deleteSurroundingText(int before, int after) {
        InputConnection ic = getCurrentInputConnection();
        selectAll = false;
        mark = -1;
        return ic != null && ic.deleteSurroundingText(before, after);
    }

    @Override
    public boolean deleteSelection() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        if (mark < 0 && !selectAll) {
            return false;
        }
        int cursor = 0;
        if (!selectAll) {
            cursor = getCursor();
        }
        int[] positions = getSelectionBoundaries(cursor);
        if (positions == null || positions[0] == positions[1]) {
            return false;
        }
        setSelection(positions[1], positions[1]);
        boolean handled = ic.deleteSurroundingText(positions[1] - positions[0],
                0);
        if (handled) {
            mark = -1;
            selectAll = false;
        }
        return handled;
    }

    @Override
    public boolean toggleMark() {
        int cursor = getCursor();
        if (cursor == mark || selectAll) {
            mark = -1;
            selectAll = false;
        } else {
            mark = cursor;
        }
        return mark != -1 ? true : false;
    }

    @Override
    public int getCursor() {
        ExtractedText extractedText = getAllText();
        if (extractedText != null) {
            if (extractedText.startOffset + extractedText.selectionStart == extractedText.startOffset
                    + extractedText.selectionEnd) {
                cursor = extractedText.startOffset
                        + extractedText.selectionStart;
            }
        } else {
            cursor = -1;
        }
        return cursor;
    }

    @Override
    public boolean deselect() {
        int end = getCursor();
        if (selectAll) {
            ExtractedText text = getAllText();
            if (text == null) {
                return false;
            }
            end = text.startOffset + text.selectionEnd;
        }
        if (end < 0) {
            return false;
        }
        selectAll = false;
        mark = -1;
        return setSelection(end, end);
    }

    @Override
    public boolean setCursorToStartOfSelection() {
        if (selectAll) {
            int start = getSelectionStart();
            return start >= 0 && setSelection(start, start);
        }
        if (mark < 0) {
            return false;
        }
        cursor = Math.min(getCursor(), mark);
        return setSelection(cursor, cursor);
    }

    @Override
    public boolean selectAll() {
        ExtractedText text = getAllText();
        if (text != null && text.text != null) {
            int start = text.startOffset;
            int end = start + text.text.length();
            mark = end;
            selectAll = setSelection(start, end);
        }
        return selectAll;
    }

    @Override
    public boolean isSelectAll() {
        return selectAll;
    }

    @Override
    public Locale getLocale() {
        if (brailleParser != null) {
            TableInfo table = brailleParser.getTable(this);
            return table != null ? table.getLocale() : null;
        }
        return null;
    }

    @Override
    public int getDots() {
        if (brailleParser != null) {
            return brailleParser.getBrailleType(this).dots;
        }
        return -1;
    }

    @Override
    public String handleTypedCharacter(byte dots) {
        synchronizeComposingStateWithEditor();
        initializeCompositionFromWordPrefix();
        traceIme("typed dots=" + Integer.toBinaryString(dots & 0xFF)
                + " prediction=" + predictionOn
                + " composing=" + composingText.length()
                + " cells=" + cells.size());
        if (brailleParser != null) {
            String oldText = composingText.toString();
            setCells(dots);
            String text = brailleParser.backTranslate(this,
                    cells.toArray(new Byte[cells.size()]));
            if (text != null) {
                text = compose(text.subSequence(0, text.length()));
                traceIme("translated -> " + String.valueOf(text));
            } else { // unable to translate this byte string
                cells.remove(cells.size() - 1);
                traceIme("translate failed");
                return null;
            }

            // Return the update to the input field to be read to the user.
            return text != null ? stringDifference(oldText, text) : null;
        }
        return null;
    }

    @Override
    public String deletePreviousBrailleCharacter() {
        synchronizeComposingStateWithEditor();
        if (brailleParser == null || cells.size() <= 1) {
            return null;
        }

        String oldText = composingText.toString();
        cells.remove(cells.size() - 1);

        if (cells.size() <= 1) {
            clearComposingTextFromEditor();
            clearComposingState();
            return "";
        }

        String text = brailleParser.backTranslate(this,
                cells.toArray(new Byte[cells.size()]));
        if (text == null) {
            clearComposingTextFromEditor();
            clearComposingState();
            return "";
        }

        text = compose(text.subSequence(0, text.length()));
        return stringDifference(oldText, text);
    }

    @Override
    public int switchBrailleType() {
        finishComposingText();
        if (brailleParser != null) {
            int dots = brailleParser.switchBrailleType(this).dots;
            syncKeyboardDotsWithBrailleType();
            return dots;
        }
        return -1;
    }

    @Override
    public String switchTable() {
        finishComposingText();
        if (brailleParser != null) {
            String table = brailleParser.switchTable(this);
            syncKeyboardDotsWithBrailleType();
            return table;
        }
        return null;
    }

    private void syncKeyboardDotsWithBrailleType() {
        if (brailleParser == null) {
            return;
        }
        boolean useEightDots = brailleParser.getBrailleType(this).dots == 8;
        Options.writeBooleanPreference(this, R.string.pref_use_eight_dots_key,
                useEightDots);
        if (brailleView != null) {
            brailleView.refreshKeyboardDots();
        }
    }

    @Override
    public boolean isPasswordField() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if (editorInfo == null) {
            return false;
        }
        int inputType = editorInfo.inputType;
        return (inputType & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0;
    }

    private String compose(CharSequence text) {
        synchronizeComposingStateWithEditor();
        if (composingText.length() == 0) {
            updateShiftState(); // auto-caps
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return null;
        }
        if (selectAll) {
            toggleMark();
            selectAll = false;
        }

        // Braille is context specific and the text previously can change as the
        // user adds more Braille patterns.
        // First make sure the new text gets capitalised in the appropriate way
        // according to auto-capitalisation rules.
        text = capitalise(text);

        if (predictionOn) {
            // we can use composing text capabilities of android to make life
            // easy and efficient here.
            composingText.setLength(0);
            composingText.append(text);
            ic.setComposingText(composingText.toString(),
                    composingText.length());
            traceIme("setComposingText \"" + composingText + "\"");
        } else if (text.length() > 0) {
            // We have something to write to the field.
            // The IME could do strange things with our input here.
            // First clear our last text translation from n-1 Braille cells.
            ic.deleteSurroundingText(composingText.length(), 0);
            composingText.setLength(0);
            composingText.append(text);

            // Now write the text corresponding to n Braille cells.
            // We must write individual characters so that the input field
            // doesn't misbehave.
            // This is the case for some fields that do validation like banking
            // apps for security and auto-completing fields.
            for (int i = 0; i < text.length(); i++) {
                ic.commitText(text.subSequence(i, i + 1), 1);
            }
            traceIme("commitText direct \"" + text + "\"");
        }

        // return the new text we wrote if any.
        return text.toString();
    }

    // Capitalise the text if auto-caps is enabled and the IME told us to
    // capitalise this first character.
    private CharSequence capitalise(CharSequence text) {
        if (Options
                .getBooleanPreference(
                        this,
                        R.string.pref_auto_caps_key,
                        Boolean.parseBoolean(getString(R.string.pref_auto_caps_default)))) {
            if (caps != 0 && text != null) {
                if (text.length() > 0) {
                    text = String
                            .valueOf(Character.toUpperCase(text.charAt(0)))
                            + text.subSequence(1, text.length());
                }
            }
        }
        return text;
    }

    @Override
    public void onKey(int keyCode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        // disable selection
        if (selectAll) {
            toggleMark();
            selectAll = false;
        }
        finishComposingText();
        switch (keyCode) {
        case Keyboard.KEYCODE_DELETE:
            ic.deleteSurroundingText(1, 0);
            break;
        case Keyboard.KEYCODE_DONE:
        case '\n':
            keyDownUp(ic, KeyEvent.KEYCODE_ENTER);
            break;
        default:
            if (keyCode >= '0' && keyCode <= '9') {
                keyDownUp(ic, keyCode - '0' + KeyEvent.KEYCODE_0);
            } else {
                ic.commitText(String.valueOf((char) keyCode), 1);
            }
            break;
        }
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(InputConnection ic, int keyEventCode) {
        if (ic == null) {
            return;
        }
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    private boolean setSelection(int start, int end) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        finishComposingText();
        return ic.setSelection(start, end);
    }

    private int[] getSelectionBoundaries(int cursor) {
        ExtractedText text = getAllText();
        if (text == null || text.text == null || cursor < 0) {
            return null;
        }

        int start = getSelectionStart(text);
        int end = text.startOffset + text.text.length();
        mark = clampToRange(mark, start, end);
        cursor = clampToRange(cursor, start, end);
        return new int[] { Math.min(cursor, mark), Math.max(cursor, mark) };
    }

    @Override
    public void finishComposingText() {
        finishComposingText(true);
    }

    private void finishComposingText(boolean commit) {
        InputConnection ic = getCurrentInputConnection();
        String composingSnapshot = composingText.toString();
        if (composingSnapshot.length() > 0) {
            boolean editorMatchesComposingText = hasEditorComposingState(ic);
            traceIme("finishComposingText commit=" + commit
                    + " prediction=" + predictionOn
                    + " editorMatches=" + editorMatchesComposingText
                    + " text=\"" + composingSnapshot + "\"");
            if (predictionOn && ic != null) {
                ic.finishComposingText();
            }
            if (predictionOn && commit && editorMatchesComposingText) {
                if (shouldSkipFallbackCommit(ic, composingSnapshot)) {
                    traceIme("skip fallback commit \"" + composingSnapshot + "\"");
                } else {
                    ic.commitText(composingSnapshot, 1);
                    lastFallbackCommitAt = SystemClock.uptimeMillis();
                    lastFallbackCommitText = composingSnapshot;
                    lastFallbackCommitCursor = getCursor();
                    traceIme("fallback commit \"" + composingSnapshot + "\"");
                }
            }
        }
        clearComposingState();
    }

    private void setCells(byte dots) {
        if (cells.size() == 0) {
            cells.add((byte) 0);
        }
        cells.add(dots);
    }

    /**
     * Return the difference between to strings so that the user knows what
     * change occurred to the input. If str2 is completely unique to str1 then
     * return the entire string as the input has totally changed. Otherwise
     * return from the point of difference to end of str2 which represents the
     * new text that the user should know about.
     * 
     * @param str1
     *            The old text.
     * @param str2
     *            The new text.
     */
    private static String stringDifference(String str1, String str2) {
        if (str1 == null) {
            return str2;
        }
        if (str2 == null) {
            return null;
        }
        int i = -1;
        while (++i < Math.min(str1.length(), str2.length())
                && Character.toLowerCase(str1.charAt(i)) == Character
                        .toLowerCase(str2.charAt(i))) {
        }
        return i >= str2.length() ? str2 : str2.substring(i, str2.length());
    }

    private void updateShiftState() {
        caps = 0;
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        if (editorInfo != null && ic != null
                && editorInfo.inputType != InputType.TYPE_NULL) {
            caps = ic.getCursorCapsMode(editorInfo.inputType);
        }
    }

    @Override
    public void commitText(String text, int newCursorPosition) {
        synchronizeComposingStateWithEditor();
        finishComposingText();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || text == null) {
            return;
        }
        if (selectAll) {
            toggleMark();
            selectAll = false;
        }
        updateShiftState();
        text = capitalise(text.subSequence(0, text.length())).toString();
        ic.commitText(text, newCursorPosition);
        traceIme("external commitText \"" + text + "\"");
    }

    private void clearComposingState() {
        composingText.setLength(0);
        cells.clear();
    }

    private void initializeCompositionFromWordPrefix() {
        if (brailleParser == null || composingText.length() > 0
                || cells.size() > 0 || selectAll) {
            return;
        }

        EditingUtilities.Word word = EditingUtilities.getWord(this);
        if (word == null || word.charsBefore <= 0 || word.word == null) {
            return;
        }

        int prefixEnd = Math.max(0, Math.min(word.charsBefore, word.word.length()));
        String prefix = word.word.substring(0, prefixEnd);
        if (prefix.length() == 0) {
            return;
        }

        TranslationResult translation = brailleParser.translateText(this, prefix,
                prefix.length());
        if (translation == null || translation.getCells() == null
                || translation.getCells().length == 0) {
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        int cursor = getCursor();
        if (predictionOn && ic != null && cursor >= prefix.length()) {
            ic.setSelection(cursor - prefix.length(), cursor);
            ic.setComposingText(prefix, prefix.length());
        }
        traceIme("init composition from prefix \"" + prefix + "\"");

        composingText.setLength(0);
        composingText.append(prefix);
        cells.clear();
        cells.add((byte) 0);
        for (byte cell : translation.getCells()) {
            cells.add(Byte.valueOf(cell));
        }
    }

    private void synchronizeComposingStateWithEditor() {
        if (composingText.length() == 0) {
            return;
        }
        if (!hasEditorComposingState(getCurrentInputConnection())) {
            traceIme("clear stale composing state \"" + composingText + "\"");
            clearComposingState();
        }
    }

    private void clearComposingTextFromEditor() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || composingText.length() == 0) {
            return;
        }

        if (predictionOn) {
            ic.setComposingText("", 1);
            ic.finishComposingText();
        } else {
            ic.deleteSurroundingText(composingText.length(), 0);
        }
    }

    private boolean hasEditorComposingState(InputConnection ic) {
        if (composingText.length() == 0) {
            return false;
        }
        if (ic == null) {
            return false;
        }

        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && composingText.toString().contentEquals(selected)) {
            return true;
        }

        CharSequence beforeCursor = ic.getTextBeforeCursor(composingText.length(),
                0);
        return beforeCursor != null
                && composingText.toString().contentEquals(beforeCursor);
    }

    private boolean shouldSkipFallbackCommit(InputConnection ic, String text) {
        if (text == null || text.length() == 0) {
            return true;
        }
        if (editorEndsWithText(ic, text)) {
            return true;
        }
        int cursor = getCursor();
        return cursor >= 0
                && cursor == lastFallbackCommitCursor
                && text.equals(lastFallbackCommitText)
                && SystemClock.uptimeMillis() - lastFallbackCommitAt
                < FALLBACK_COMMIT_DEDUP_WINDOW_MS;
    }

    private static boolean editorEndsWithText(InputConnection ic, String text) {
        if (ic == null || text == null || text.length() == 0) {
            return false;
        }
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && text.contentEquals(selected)) {
            return true;
        }
        CharSequence beforeCursor = ic.getTextBeforeCursor(text.length(), 0);
        return beforeCursor != null && text.contentEquals(beforeCursor);
    }

    private static void traceIme(String message) {
        synchronized (IME_TRACE) {
            if (IME_TRACE.size() >= IME_TRACE_LIMIT) {
                IME_TRACE.removeFirst();
            }
            IME_TRACE.addLast(System.currentTimeMillis() + " " + message);
        }
    }

    public static String dumpImeTrace() {
        synchronized (IME_TRACE) {
            if (IME_TRACE.isEmpty()) {
                return "No IME trace collected.";
            }
            StringBuilder sb = new StringBuilder();
            for (String line : IME_TRACE) {
                sb.append(line);
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    private int getSelectionStart() {
        return getSelectionStart(getAllText());
    }

    private int getSelectionStart(ExtractedText text) {
        return text == null ? -1 : text.startOffset;
    }

    private static int clampToRange(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
