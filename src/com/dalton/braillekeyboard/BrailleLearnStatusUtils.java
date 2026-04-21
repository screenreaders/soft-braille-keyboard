package com.dalton.braillekeyboard;

import android.content.Context;

final class BrailleLearnStatusUtils {
    private BrailleLearnStatusUtils() {
    }

    static String buildCorrectChoiceMessage(Context context, String symbol,
            int dotsMask) {
        return context.getString(R.string.braille_learn_correct_choice, symbol,
                BrailleLearnUiUtils.formatDots(context, dotsMask));
    }

    static String buildIncorrectChoiceMessage(Context context, String symbol,
            int dotsMask) {
        return context.getString(R.string.braille_learn_incorrect_choice, symbol,
                BrailleLearnUiUtils.formatDots(context, dotsMask));
    }

    static String buildCorrectDotsMessage(Context context, String symbol,
            int dotsMask) {
        return context.getString(R.string.braille_learn_correct, symbol,
                BrailleLearnUiUtils.formatDots(context, dotsMask));
    }

    static String buildIncorrectDotsMessage(Context context, String symbol,
            int dotsMask) {
        return context.getString(R.string.braille_learn_incorrect, symbol,
                BrailleLearnUiUtils.formatDots(context, dotsMask));
    }

    static String buildHintMessage(Context context, boolean symbolToDots,
            String symbol, int dotsMask) {
        return context.getString(symbolToDots ? R.string.braille_learn_hint
                : R.string.braille_learn_hint_choice, symbol,
                BrailleLearnUiUtils.formatDots(context, dotsMask));
    }
}
