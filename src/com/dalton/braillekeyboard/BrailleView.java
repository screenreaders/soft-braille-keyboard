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

import java.util.List;
import java.util.Locale;
import java.util.Collections;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.os.Build;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import com.dalton.braillekeyboard.Options.KeyboardFeedback;
import com.dalton.braillekeyboard.Pad.Coords;
import com.dalton.braillekeyboard.Pad.Swipe;

/**
 * This View facilitates displaying a Braille keyboard to the user on the entire
 * screen and handling taps and swipes by using the ActionHandler.
 * 
 * This View holds an instance to an implementation of Pad and parses all of
 * it's touch events to the Pad to resolve which dots were hit or nearest the
 * user's touch.
 * 
 * The View will then pass the appropriate swipe and dot pressed events to an
 * ActionHandler to perform the appropriate action.
 * 
 * This View also implements the OnActionListener callback and will display
 * results, send notifications or change View states appropriate to the
 * callbacks received from the ActionHandler.
 * 
 * You should register an IME listener with this View in order for the
 * ActionHandler and this View to function. See
 * onInitialiseForInput(KeyboardListener listener).
 * 
 * You should always call close() when you are done with the View to release
 * resources.
 */
public class BrailleView extends View {
    private static final long LONG_VIBRATION = 300;
    private static final long MEDIUM_VIBRATION = 125;
    private static final byte NO_DOTS = 0;
    private static final long LONG_HOLD_DELAY = 800;
    private static final long TALKBACK_HINT_DEBOUNCE_MS = 2000;
    private static final long QUICK_VIBRATION = 25;

    private final AccessibilityManager accessibilityManager;
    private static final int DEFAULT_TEXT_KEY_COUNT = 6;

    private final java.util.ArrayList<Coords> lastDotList
            = new java.util.ArrayList<Coords>();
    private final Paint circlePaint;
    private final Paint paint;
    private final Rect circleTextBounds = new Rect();
    private final Vibrator vibrator;
    private final ActionHandler.OnActionListener actionListener = new ActionHandler.OnActionListener() {

        @Override
        public void onSetDots(boolean dot7, boolean dot8) {
            setDotsSevenEight(dot7, dot8);
        }

        @Override
        public void onText(String format, String text, boolean isPasswordField) {
            if (speech != null) {
                speech.readConsiderPassword(getContext(), format, text,
                        isPasswordField, Speech.QUEUE_FLUSH);
            }
        }

        @Override
        public void onText(String format, String text, boolean isPasswordField,
                int mode) {
            if (speech != null) {
                speech.readConsiderPassword(getContext(), format, text,
                        isPasswordField, mode);
            }
        }

        @Override
        public void onNotify(boolean vibrate, boolean playSound) {
            sendNotification(vibrate, playSound);
        }

        @Override
        public void onSetLocale(Locale locale) {
            setLocale(locale);
        }

        @Override
        public void onShrink() {
            setLocale(Locale.getDefault());
            shrinkKeyboard = true;
            invalidate();
            requestLayout();
            if (listener != null) {
                listener.updateFullscreenMode();
            }
            updateSystemGestureExclusion();
        }

        @Override
        public void onPrivacy() {
            applyPrivacy();
        }

        @Override
        public void onShutup() {
            if (speech != null) {
                speech.stop();
            }
        }
    };

    private ActionHandler actionHandler;
    private DisplayParams displayParams = null;
    private boolean dot7;
    private boolean dot8;
    private Coords[] dotsDown = new Coords[8];
    private boolean handledSwipe = false;
    private KeyboardListener listener;
    private Pad pad;
    private long requiredTouchTime = 0;
    private boolean shrinkKeyboard;
    private Speech speech;
    private Boolean privacyEnabled;
    private long lastTalkBackHintAt;

    public BrailleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        circlePaint = new Paint();
        accessibilityManager = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        vibrator = (Vibrator) context
                .getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * Gets the View ready to receive touch events from the user and facilitates
     * communication with the underlying IME.
     * 
     * @param listener
     *            The KeyboardListener implementation of to communicate with the
     *            IME.
     */
    public void onInitialiseForInput(Context context, KeyboardListener listener) {
        this.listener = listener;

        // Set up speech and announce when it's ready to the user.
        speech = new Speech(getContext(), new Speech.OnReadyListener() {

            @Override
            public void ttsReady() {
                if (BrailleView.this.listener != null) {
                    setLocale(BrailleView.this.listener.getLocale());
                }
                if (speech != null) {
                    speech.speak(getContext(),
                            getContext().getString(R.string.ready),
                            Speech.QUEUE_FLUSH);
                }
            }
        });

        // When we launch the keyboard it should take up the full screen.
        if (shrinkKeyboard) {
            expandKeyboard();
        }

        if (displayParams != null) {
            loadDefaultPad(getWidth(), getHeight());
            invalidate();
            requestLayout();
        }
        actionHandler = new ActionHandler(context);
        actionHandler.setCallback(actionListener);
        actionHandler.setKeyboardListener(listener);
    }

    /**
     * Release resources and vibrate the device to tell the user we are closing.
     */
    public void close() {
        if (Options.getBooleanPreference(
                getContext(),
                R.string.pref_vibrate_on_exit_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_vibrate_on_exit_default)))
                && vibrator != null) {
            vibrator.vibrate(LONG_VIBRATION * 2);
        }
        if (speech != null) {
            speech.shutdown(getContext().getString(R.string.closing_keyboard));
        }
        if (actionHandler != null) {
            actionHandler.shutdown();
        }
        setLocale(Locale.getDefault(), false);
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setDisplayParams(w, h);
        loadDefaultPad(w, h);
        updateSystemGestureExclusion();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        applyPrivacy();
        if (!shrinkKeyboard
                && displayParams != null
                && Options.getBooleanPreference(
                        getContext(),
                        R.string.pref_show_circles_key,
                        Boolean.parseBoolean(getContext().getString(
                                R.string.pref_show_circles_default)))) {
            // We should show a visual representation of the view according to
            // user preference.
            if (isTalkBackTouchModeActive()) {
                setContentDescription(getTalkBackKeyboardDescription());
            } else {
                setContentDescription(null);
            }

            drawPadKeys(canvas);
        } else if (shrinkKeyboard && displayParams != null) {
            String text = getContext().getString(R.string.expand_keyboard);
            if (accessibilityManager != null
                    && accessibilityManager.isTouchExplorationEnabled()) {
                text = getContext()
                        .getString(R.string.expand_keyboard_talkback);
            }
            canvas.drawText(text, displayParams.x, displayParams.y, paint);
            setContentDescription(text);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int SHRINK_FACTOR = 2;
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        int desiredWidth = widthSize;
        int desiredHeight = heightSize;
        if (shrinkKeyboard) {
            desiredHeight /= SHRINK_FACTOR;
        }
        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(desiredWidth, widthSize);
        } else {
            width = desiredWidth;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(desiredHeight, heightSize);
        } else {
            height = desiredHeight;
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateSystemGestureExclusion();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateSystemGestureExclusion();
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (speech != null && isTalkBackTouchModeActive()) {
            if (shrinkKeyboard) {
                announceTalkBackHint(getContext().getString(
                        R.string.expand_keyboard_talkback));
            } else if (!BrailleImePassthroughBridge.isPassthroughActive()) {
                announceTalkBackHint(getContext().getString(
                        R.string.braille_keyboard_talkback_enable_service));
            }
            return true;
        }
        return super.onHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        super.onTouchEvent(motionEvent);
        if (displayParams == null || actionHandler == null) {
            return true;
        }
        if (isTalkBackTouchModeActive()
                && !BrailleImePassthroughBridge.isPassthroughActive()
                && !shrinkKeyboard) {
            announceTalkBackHint(getContext().getString(
                    R.string.braille_keyboard_talkback_enable_service));
            return true;
        }
        // Get the height and width of the keyboard.
        // If autoRotate is enabled then the standard dimenssions are correct.
        // If autoRotate is disabled the width is the maximum of the height and
        // the width and the height is the minimum of the two.
        // This is because the user holds the phone in landscape mode, but the
        // screen might be fixed to portrate mode. It makes more sense to use
        // the keyboard in landscape mode and the user doesn't care about
        // orientation of the screen.
        int width = displayParams.autoRotate ? getWidth() : Math.max(
                getWidth(), getHeight());
        int height = displayParams.autoRotate ? getHeight() : Math.min(
                getWidth(), getHeight());
        int action = motionEvent.getActionMasked();
        int index = motionEvent.getActionIndex();
        int id = motionEvent.getPointerId(index);
        int x = (int) motionEvent.getX(index);
        int y = (int) motionEvent.getY(index);

        // Swap x and y if the view is being used perpendicular to it's intended
        // purpose see above.
        int tempX = x;
        x = displayParams.autoRotate || getWidth() >= getHeight() ? x : y;
        y = displayParams.autoRotate || getWidth() >= getHeight() ? y : tempX;
        boolean calibrationRequested = KeyboardCalibrationUtils
                .isCalibrationModeRequested() || !lastDotList.isEmpty();
        switch (action) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
            if (shrinkKeyboard) {
                expandKeyboard();
            } else {
                // store the time at which the user must hold their fingers down
                // for if they want to calibrate.
                // Only record the time for the first touch.
                requiredTouchTime = requiredTouchTime == 0 ? System
                        .currentTimeMillis() + (KeyboardCalibrationUtils
                                .isCalibrationModeRequested() ? 0
                                        : LONG_HOLD_DELAY)
                        : requiredTouchTime;

                if (!updatePointer(dotsDown, id, x, y, true)
                        && id < dotsDown.length) {
                    // add a new unique dot to the list of dots that were
                    // pushed.
                    dotsDown[id] = new Coords(id, x, y);
                }
            }
            break;
        case MotionEvent.ACTION_HOVER_EXIT:
        case MotionEvent.ACTION_UP:
            handleTouchRelease(id, width, height, calibrationRequested);
            break;
        case MotionEvent.ACTION_HOVER_MOVE:
        case MotionEvent.ACTION_MOVE:
            updatePointer(dotsDown, id, x, y, false);
            break;
        case MotionEvent.ACTION_POINTER_UP:
            if (!setPad(id, width, height, displayParams.autoRotate)) {
                if (calibrationRequested) {
                    break;
                }
                updatePointer(dotsDown, id, x, y, false);
                setDots();
                Swipe swipe = handledSwipeAction(dotsDown,
                        getHeight() > getWidth() && !displayParams.autoRotate);
                if (swipe != Swipe.NONE) {
                    // Hold one finger while swiping with another
                    handledSwipe = true;
                    actionHandler.handleSwipe(getContext(), swipe);
                }
            }
            break;
        default:
        }
        return true;
    }

    private String getTalkBackKeyboardDescription() {
        return getContext().getString(BrailleImePassthroughBridge.isPassthroughActive()
                ? R.string.braille_keyboard_talkback_ready
                : R.string.braille_keyboard_talkback_enable_service);
    }

    private void handleTouchRelease(int id, int width, int height,
            boolean calibrationRequested) {
        if (calibrationRequested) {
            finishCalibrationRelease(id, width, height);
        } else if (isCalibrationGestureActive()) {
            clearCalibrationState();
        } else if (!handleVoiceInput()) {
            handleTypingRelease();
        }
        resetDots();
        updatePadAfterTouch();
        maybeInvalidatePad();
    }

    private void finishCalibrationRelease(int id, int width, int height) {
        boolean calibrationHandled = setPad(id, width, height,
                displayParams.autoRotate);
        if (!calibrationHandled) {
            speakAndVibrateCalibrationFailure();
        }
        clearCalibrationState();
    }

    private void speakAndVibrateCalibrationFailure() {
        if (speech != null) {
            speech.speak(getContext(), getContext().getString(R.string.keyboard_not_set),
                    Speech.QUEUE_FLUSH);
        }
        if (vibrator != null) {
            vibrator.vibrate(QUICK_VIBRATION);
        }
    }

    private void clearCalibrationState() {
        resetDots();
        lastDotList.clear();
        handledSwipe = false;
    }

    private void handleTypingRelease() {
        if (pad == null || pressedDotString() == NO_DOTS) {
            return;
        }
        setDots();
        if (!handledSwipe) {
            Swipe swipe = handledSwipeAction(dotsDown,
                    getHeight() > getWidth() && !displayParams.autoRotate);
            if (swipe != Swipe.NONE) {
                actionHandler.handleSwipe(getContext(), swipe);
            } else {
                handleTypedCharacter();
            }
        }
        lastDotList.clear();
    }

    private void updatePadAfterTouch() {
        if (pad != null) {
            pad.updateKeys(!displayParams.autoRotate && getHeight() > getWidth());
        }
    }

    private void maybeInvalidatePad() {
        if (Options.getBooleanPreference(
                getContext(),
                R.string.pref_show_circles_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_show_circles_default)))) {
            invalidate();
        }
    }

    public boolean getShrinkKeyboard() {
        return shrinkKeyboard;
    }

    public boolean setLocale(Locale locale) {
        return setLocale(locale, true);
    }

    private boolean setLocale(Locale locale, boolean setTTSLocale) {
        if (locale != null) {
            Resources resources = getContext().getResources();
            DisplayMetrics displayMetrics = resources.getDisplayMetrics();
            android.content.res.Configuration conf = resources
                    .getConfiguration();
            if (!conf.locale.equals(locale)) {
                if (!setTTSLocale || (speech != null && speech.setLocale(locale))) {
                    conf.locale = locale;
                    resources.updateConfiguration(conf, displayMetrics);

                    return true;
                }
            }
        }
        return false;
    }

    private void loadDefaultPad(int w, int h) {
        if (displayParams == null) {
            return;
        }
        int width = displayParams.autoRotate ? w : Math.max(w, h);
        int height = displayParams.autoRotate ? h : Math.min(w, h);
        if (!setDefaultPad(w, h, width, height)) {
            if (speech != null) {
                speech.speak(getContext(),
                        getContext().getString(R.string.keyboard_error),
                        Speech.QUEUE_FLUSH);
            }
        }
    }

    public void refreshKeyboardDots() {
        resetDots();
        setDotsSevenEight(false, false);
        if (getWidth() > 0 && getHeight() > 0) {
            loadDefaultPad(getWidth(), getHeight());
        }
        invalidate();
        requestLayout();
        updateSystemGestureExclusion();
    }

    private boolean setPad(int id, int width, int height, boolean autoRotate) {
        final int TOTAL_DOTS = 6;
        final int ONE_SIDE = 3;
        final int activeDots = countDotsDown(dotsDown);
        // For whatever reason we won't be able to set a pad
        if (requiredTouchTime > System.currentTimeMillis()
                || (activeDots != ONE_SIDE && activeDots != TOTAL_DOTS)) {
            return false;
        }
        if (lastDotList.size() != ONE_SIDE && lastDotList.size() != 0) {
            lastDotList.clear();
            return false;
        }

        if (activeDots == TOTAL_DOTS) {
            setDotsSevenEight(false, false);
            return applySixDotCalibration(buildCalibrationDots(TOTAL_DOTS), width,
                    height);
        }

        // Add the first three dots to the current dot list.
        for (int i = 0; i < lastDotList.size(); i++) {
            Coords coord = lastDotList.get(i);
            dotsDown[ONE_SIDE + i] = new Coords(ONE_SIDE + id, coord.x, coord.y);
        }

        if (countDotsDown(dotsDown) == TOTAL_DOTS) {
            setDotsSevenEight(false, false);
            return applySixDotCalibration(buildCalibrationDots(TOTAL_DOTS), width,
                    height);
        } else {
            storeFirstCalibrationHalf();
            notifyCalibrationHalfStored();
            return true;
        }
    }

    private Coords[] buildCalibrationDots(int totalDots) {
        Coords[] sixDots = new Coords[totalDots];
        for (int i = 0, j = 0; i < dotsDown.length && j < sixDots.length; i++) {
            if (dotsDown[i] != null) {
                sixDots[j++] = new Coords(dotsDown[i].getSecondX(),
                        dotsDown[i].getSecondY());
            }
        }
        return sixDots;
    }

    private boolean applySixDotCalibration(Coords[] sixDots, int width,
            int height) {
        boolean result = selectPad(sixDots, width, height);
        if (result) {
            KeyboardCalibrationUtils.clearCalibrationMode();
        }
        speakCalibrationResult(result);
        lastDotList.clear();
        resetDots();
        return result;
    }

    private void speakCalibrationResult(boolean success) {
        if (speech != null) {
            speech.speak(getContext(), getContext().getString(success
                    ? pad.padString : R.string.keyboard_error), Speech.QUEUE_FLUSH);
        }
        if (vibrator != null) {
            vibrator.vibrate(success ? MEDIUM_VIBRATION : QUICK_VIBRATION);
        }
    }

    private void storeFirstCalibrationHalf() {
        for (int i = 0; i < dotsDown.length; i++) {
            if (dotsDown[i] != null) {
                lastDotList.add(dotsDown[i]);
                dotsDown[i] = null;
            }
        }
    }

    private void notifyCalibrationHalfStored() {
        if (speech != null) {
            speech.speak(getContext(),
                    getContext().getString(R.string.keyboard_next_three),
                    Speech.QUEUE_FLUSH);
        }
        if (vibrator != null) {
            vibrator.vibrate(MEDIUM_VIBRATION);
        }
    }

    // Set the pad using a default pad.
    private boolean setDefaultPad(int w, int h, int padWidth, int padHeight) {
        boolean useEightDots = shouldUseEightDots();
        try {
            pad = PadUtilities
                    .displayDefaultPad(getContext(), padWidth, padHeight, h > w
                            && !displayParams.autoRotate, useEightDots);
            return true;
        } catch (IllegalArgumentException e) {
            // handled below
        }
        return false;
    }

    // Display a pad according to the possitioning of the fingers (user
    // calibration)
    private boolean selectPad(Coords[] dots, int width, int height) {
        boolean useEightDots = shouldUseEightDots();
        try {
            pad = PadUtilities.selectPad(getContext(), dots, width, height,
                    !displayParams.autoRotate && getHeight() > getWidth(),
                    useEightDots);
            return true;
        } catch (IllegalArgumentException e) {
            // handled below
        }
        return false;
    }

    private void drawPadKeys(Canvas canvas) {
        if (pad == null || listener == null || displayParams == null) {
            return;
        }
        List<Coords> padKeys = pad.getKeys();
        if (padKeys == null || padKeys.isEmpty()) {
            return;
        }
        int dots = listener.getDots();
        if (dots == -1) {
            dots = padKeys.size();
        }
        int dotsInUse = Math.min(padKeys.size(), dots);
        boolean useViewCoordinates
                = displayParams.autoRotate || getWidth() >= getHeight();
        for (int i = 0; i < dotsInUse; i++) {
            Coords key = padKeys.get(i);
            int x = useViewCoordinates ? key.x : key.y;
            int y = useViewCoordinates ? key.y : key.x;
            String text = String.valueOf(i + 1);
            paint.getTextBounds(text, 0, text.length(), circleTextBounds);
            canvas.drawCircle(x, y, displayParams.radius, circlePaint);
            canvas.drawText(text, x, y, paint);
        }
    }

    private void setDots() {
        if (pad == null || listener == null) {
            return;
        }
        // Sort the dots into their actual positions eg. dotsDown[0] = dot1
        // dotsDown[1] = dot 2 etc.
        // Previous ordering is based on the order that fingers hit the screen.
        dotsDown = pad.getBrailleDots(dotsDown, listener.getDots());
    }

    private void resetDots() {
        requiredTouchTime = 0;
        for (int i = 0; i < dotsDown.length; i++) {
            dotsDown[i] = null;
        }
        handledSwipe = false;
    }

    private void setDotsSevenEight(boolean dot7, boolean dot8) {
        if (!dot7 && !dot8) {
            this.dot7 = dot7;
            this.dot8 = dot8;
        }

        if (dot7) {
            this.dot7 = dot7;
        }
        if (dot8) {
            this.dot8 = dot8;
        }
    }

    private boolean shouldUseEightDots() {
        if (listener != null) {
            return listener.getDots() == 8;
        }
        return Options.getBooleanPreference(
                getContext(),
                R.string.pref_use_eight_dots_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_use_eight_dots_default)));
    }

    private byte pressedDotString() {
        byte mask = 1;
        byte value = 0;

        // See what dots of the first six are pressed.
        for (int i = 0; i < dotsDown.length - 2; i++) {
            if (dotsDown[i] != null) {
                // it's present so set the bit in the bitstring.
                value |= mask;
            }
            mask <<= 1;
        }

        // special case for setting dots 7 and 8.
        // They can be activated by pressing them on the screen or using a swipe
        // gesture.
        if (dot7 || dotsDown[6] != null) {
            value |= mask;
        }
        mask <<= 1;
        if (dot8 || dotsDown[7] != null) {
            value |= mask;
        }
        return value;
    }

    private void sendNotification(boolean vibrate, boolean playSound) {
        int keyboardFeedback = Options.getIntPreference(getContext(),
                R.string.pref_keyboard_feedback_key,
                KeyboardFeedback.ALL.getValue());
        if (vibrate
                && (KeyboardFeedback.VIBRATE.value & keyboardFeedback) != 0
                && vibrator != null) {
            vibrator.vibrate(QUICK_VIBRATION);
        }
        if (playSound
                && (KeyboardFeedback.SOUND.value & keyboardFeedback) != 0) {
            playSoundEffect(SoundEffectConstants.CLICK);
        }
    }

    private void expandKeyboard() {
        if (speech != null) {
            speech.speak(getContext(),
                    getContext().getString(R.string.keyboard_full_screen),
                    Speech.QUEUE_FLUSH);
        }
        shrinkKeyboard = false;
        if (listener != null) {
            setLocale(listener.getLocale());
        }
        invalidate();
        requestLayout();
        if (listener != null) {
            listener.updateFullscreenMode();
        }
        updateSystemGestureExclusion();
    }

    private static int countDotsDown(Coords[] dots) {
        int count = 0;
        for (Coords coords : dots) {
            if (coords != null) {
                ++count;
            }
        }
        return count;
    }

    private static boolean updatePointer(Coords[] coords, int id, int x, int y,
            boolean reset) {
        for (int i = 0; i < coords.length; i++) {
            if (coords[i] != null) {
                if (coords[i].id == id) {
                    if (reset) {
                        coords[i] = new Coords(id, x, y);
                    }
                    coords[i].setSecondCords(x, y);
                    return true;
                }
            }
        }
        return false;
    }

    private Swipe handledSwipeAction(Coords[] coords, boolean swap) {
        if (pad == null) {
            return Swipe.NONE;
        }
        return pad.getSwipe(coords, swap);
    }

    private void handleTypedCharacter() {
        byte value = pressedDotString();
        if (actionHandler != null) {
            actionHandler.handleCharacter(getContext(), value);
        }
    }

    private boolean applyPrivacy() {
        boolean enabled = Options.getBooleanPreference(
                getContext(),
                R.string.pref_privacy_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_privacy_default)));
        if (privacyEnabled == null || privacyEnabled.booleanValue() != enabled) {
            setBackgroundColor(getContext().getResources().getColor(
                    enabled ? android.R.color.black : android.R.color.transparent));
            privacyEnabled = enabled;
        }
        return enabled;
    }

    private void setDisplayParams(int w, int h) {
        final int CIRCLE_RADIUS = 40;
        final int STROKE_WIDTH = 8;
        final int TEXT_SIZE = 20;
        int strokeWidth = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, STROKE_WIDTH, getContext()
                        .getResources().getDisplayMetrics());
        int textSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE, getContext()
                        .getResources().getDisplayMetrics());
        int radius = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, CIRCLE_RADIUS, getContext()
                        .getResources().getDisplayMetrics());
        boolean autoRotate = Options.getBooleanPreference(
                getContext(),
                R.string.pref_auto_rotate_keyboard_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_auto_rotate_keyboard_default)));
        displayParams = new DisplayParams(strokeWidth, textSize, radius,
                autoRotate);
        paint.setColor(getContext().getResources().getColor(
                android.R.color.black));
        paint.setTextSize(displayParams.textSize);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        circlePaint.setColor(getContext().getResources().getColor(
                android.R.color.black));
        circlePaint.setAntiAlias(true);
        circlePaint.setStyle(Style.STROKE);
        circlePaint.setStrokeWidth(displayParams.strokeWidth);

        FontMetrics metrics = paint.getFontMetrics();
        float height = Math.abs(metrics.top - metrics.bottom);
        displayParams.x = getWidth() / 2;
        displayParams.y = (getHeight() / 2) + (height / 2);
    }

    private boolean handleVoiceInput() {
        if (System.currentTimeMillis() > requiredTouchTime
                && countDotsDown(dotsDown) == 1
                && Options.getBooleanPreference(
                        getContext(),
                        R.string.pref_voice_shortcut_key,
                        Boolean.parseBoolean(getContext().getString(
                                R.string.pref_voice_shortcut_default)))
                && actionHandler != null) {
            actionHandler.doVoiceInput(getContext(), false);
            return true;
        }
        return false;
    }

    public boolean isTalkBackTouchModeActive() {
        return accessibilityManager != null
                && accessibilityManager.isTouchExplorationEnabled()
                && Options.getBooleanPreference(
                        getContext(),
                        R.string.pref_talkback_braille_mode_key,
                        Boolean.parseBoolean(getContext().getString(
                                R.string.pref_talkback_braille_mode_default)));
    }

    private boolean isCalibrationGestureActive() {
        return System.currentTimeMillis() > requiredTouchTime
                && (countDotsDown(dotsDown) >= 3 || !lastDotList.isEmpty());
    }

    private void announceTalkBackHint(String message) {
        if (speech == null || message == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTalkBackHintAt < TALKBACK_HINT_DEBOUNCE_MS) {
            return;
        }
        lastTalkBackHintAt = now;
        speech.speak(getContext(), message, Speech.QUEUE_FLUSH);
    }

    private void updateSystemGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        if (!isShown() || getWidth() <= 0 || getHeight() <= 0 || shrinkKeyboard) {
            setSystemGestureExclusionRects(Collections.<Rect>emptyList());
            return;
        }
        Rect exclusionRect = new Rect(0, 0, getWidth(), getHeight());
        setSystemGestureExclusionRects(Collections.singletonList(exclusionRect));
    }

    private static class DisplayParams {
        public final int strokeWidth;
        public final int textSize;
        public final int radius;
        public final boolean autoRotate;

        public float x;
        public float y;

        public DisplayParams(int strokeWidth, int textSize, int radius,
                boolean autoRotate) {
            this.strokeWidth = strokeWidth;
            this.textSize = textSize;
            this.radius = radius;
            this.autoRotate = autoRotate;
        }
    }
}
