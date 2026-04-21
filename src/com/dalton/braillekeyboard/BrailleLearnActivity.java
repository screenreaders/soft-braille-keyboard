package com.dalton.braillekeyboard;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.googlecode.eyesfree.braille.translate.TableInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class BrailleLearnActivity extends Activity
        implements BrailleParser.BrailleParserListener {
    private enum LessonOrder {
        RANDOM,
        SEQUENTIAL
    }

    private enum LessonMode {
        SYMBOL_TO_DOTS,
        DOTS_TO_SYMBOL
    }

    private enum LessonCategory {
        LETTERS,
        NUMBERS,
        PUNCTUATION
    }

    private static final class LessonItem {
        final String symbol;
        final int dotsMask;

        LessonItem(String symbol, int dotsMask) {
            this.symbol = symbol;
            this.dotsMask = dotsMask;
        }
    }

    private final Random random = new Random();
    private final Button[] dotButtons = new Button[8];
    private final Button[] choiceButtons = new Button[4];

    private BrailleParser brailleParser;
    private TextView modeView;
    private TextView categoryView;
    private TextView progressView;
    private TextView tableView;
    private TextView promptView;
    private TextView dotsView;
    private TextView statusView;
    private TextView scoreView;
    private TextView keyboardResultView;
    private EditText keyboardInputView;
    private View dotsContainer;
    private View choiceContainer;

    private List<LessonItem> lessonItems = Collections.emptyList();
    private LessonMode currentMode = LessonMode.SYMBOL_TO_DOTS;
    private LessonOrder currentOrder = LessonOrder.RANDOM;
    private LessonCategory currentCategory = LessonCategory.LETTERS;
    private LessonItem currentItem;
    private final List<LessonItem> currentChoices = new ArrayList<LessonItem>();
    private int currentIndex;
    private int selectedDotsMask;
    private int correctAnswers;
    private int attemptedAnswers;
    private int streak;
    private int bestStreak;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_braille_learn);
        setTitle(R.string.braille_learn_title);
        bindViews();
        bindKeyboardHelper();
        brailleParser = new BrailleParser(this, this);
        refreshViews();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (brailleParser != null) {
            brailleParser.destroy();
            brailleParser = null;
        }
    }

    public void onCycleLessonCategory(View view) {
        LessonCategory[] values = LessonCategory.values();
        currentCategory = values[(currentCategory.ordinal() + 1) % values.length];
        rebuildLessonItems(true);
    }

    public void onCycleLessonMode(View view) {
        LessonMode[] values = LessonMode.values();
        currentMode = values[(currentMode.ordinal() + 1) % values.length];
        rebuildLessonItems(true);
    }

    public void onCycleLessonOrder(View view) {
        LessonOrder[] values = LessonOrder.values();
        currentOrder = values[(currentOrder.ordinal() + 1) % values.length];
        rebuildLessonItems(true);
    }

    public void onShowTextKeyboard(View view) {
        keyboardInputView.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        keyboardInputView.requestFocus();
    }

    public void onShowNumberKeyboard(View view) {
        keyboardInputView.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        keyboardInputView.requestFocus();
    }

    public void onShowSymbolKeyboard(View view) {
        keyboardInputView.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        keyboardInputView.requestFocus();
    }

    public void onToggleDot(View view) {
        if (currentMode != LessonMode.SYMBOL_TO_DOTS) {
            return;
        }
        Object tag = view.getTag();
        if (!(tag instanceof String)) {
            return;
        }
        try {
            int dot = Integer.parseInt((String) tag);
            int bit = 1 << (dot - 1);
            selectedDotsMask ^= bit;
            refreshViews();
        } catch (NumberFormatException e) {
            // Ignore malformed layout tag.
        }
    }

    public void onChooseSymbol(View view) {
        if (currentMode != LessonMode.DOTS_TO_SYMBOL || currentItem == null) {
            statusView.setText(R.string.braille_learn_waiting);
            return;
        }
        Object tag = view.getTag();
        if (!(tag instanceof String)) {
            return;
        }
        int index;
        try {
            index = Integer.parseInt((String) tag);
        } catch (NumberFormatException e) {
            return;
        }
        if (index < 0 || index >= currentChoices.size()) {
            return;
        }
        LessonItem selected = currentChoices.get(index);
        attemptedAnswers++;
        if (selected != null && TextUtils.equals(selected.symbol, currentItem.symbol)) {
            correctAnswers++;
            streak++;
            bestStreak = Math.max(bestStreak, streak);
            statusView.setText(getString(R.string.braille_learn_correct_choice,
                    currentItem.symbol, formatDots(currentItem.dotsMask)));
            nextChallenge();
        } else {
            streak = 0;
            statusView.setText(getString(R.string.braille_learn_incorrect_choice,
                    currentItem.symbol, formatDots(currentItem.dotsMask)));
        }
        refreshScore();
        refreshViews();
    }

    public void onCheckAnswer(View view) {
        if (currentItem == null) {
            statusView.setText(R.string.braille_learn_waiting);
            return;
        }
        if (currentMode != LessonMode.SYMBOL_TO_DOTS) {
            statusView.setText(R.string.braille_learn_pick_choice);
            return;
        }
        attemptedAnswers++;
        if (selectedDotsMask == currentItem.dotsMask) {
            correctAnswers++;
            streak++;
            bestStreak = Math.max(bestStreak, streak);
            statusView.setText(getString(R.string.braille_learn_correct,
                    currentItem.symbol, formatDots(currentItem.dotsMask)));
            nextChallenge();
        } else {
            streak = 0;
            statusView.setText(getString(R.string.braille_learn_incorrect,
                    currentItem.symbol, formatDots(currentItem.dotsMask)));
        }
        refreshScore();
        refreshViews();
    }

    public void onShowHint(View view) {
        if (currentItem == null) {
            statusView.setText(R.string.braille_learn_waiting);
            return;
        }
        if (currentMode == LessonMode.SYMBOL_TO_DOTS) {
            statusView.setText(getString(R.string.braille_learn_hint,
                    currentItem.symbol, formatDots(currentItem.dotsMask)));
        } else {
            statusView.setText(getString(R.string.braille_learn_hint_choice,
                    currentItem.symbol, formatDots(currentItem.dotsMask)));
        }
    }

    public void onNextChallenge(View view) {
        nextChallenge();
    }

    public void onClearDots(View view) {
        selectedDotsMask = 0;
        refreshViews();
    }

    @Override
    public void onTranslatorReady(int status) {
        rebuildLessonItems(true);
    }

    private void rebuildLessonItems(boolean resetStatus) {
        lessonItems = buildLessonItems();
        if (lessonItems.isEmpty()) {
            resetEmptyLessonState(resetStatus);
        } else {
            initializeLessonState(resetStatus);
        }
        selectedDotsMask = 0;
        refreshViews();
    }

    private void nextChallenge() {
        if (lessonItems.isEmpty()) {
            resetCurrentChallenge();
            refreshViews();
            return;
        }
        if (currentOrder == LessonOrder.SEQUENTIAL) {
            currentIndex = (currentIndex + 1) % lessonItems.size();
        } else {
            currentIndex = random.nextInt(lessonItems.size());
        }
        currentItem = lessonItems.get(currentIndex);
        rebuildChoices();
        selectedDotsMask = 0;
        refreshViews();
    }

    private void rebuildChoices() {
        currentChoices.clear();
        if (currentItem == null || lessonItems.isEmpty()) {
            return;
        }
        currentChoices.add(currentItem);
        List<LessonItem> pool = new ArrayList<LessonItem>(lessonItems);
        Collections.shuffle(pool, random);
        for (LessonItem item : pool) {
            if (currentChoices.size() >= choiceButtons.length) {
                break;
            }
            if (item == null || TextUtils.equals(item.symbol, currentItem.symbol)) {
                continue;
            }
            currentChoices.add(item);
        }
        Collections.shuffle(currentChoices, random);
    }

    private List<LessonItem> buildLessonItems() {
        if (brailleParser == null) {
            return Collections.emptyList();
        }
        java.util.LinkedHashMap<String, Integer> items =
                new java.util.LinkedHashMap<String, Integer>();
        TableInfo table = brailleParser.getTable(this);
        Locale tableLocale = table == null || table.getLocale() == null
                ? Locale.getDefault() : table.getLocale();
        for (String candidate : getCategoryCandidates(tableLocale)) {
            if (TextUtils.isEmpty(candidate)) {
                continue;
            }
            byte[] cells = brailleParser.translateTextToBrailleCells(this,
                    candidate);
            Integer dotsMask = extractSingleCellMask(cells);
            if (dotsMask == null) {
                continue;
            }
            String key = normalizeLessonSymbol(candidate);
            if (!items.containsKey(key)) {
                items.put(key, dotsMask);
            }
        }
        if (items.isEmpty()) {
            appendBackTranslatedLessonItems(items);
        }
        List<LessonItem> result = new ArrayList<LessonItem>(items.size());
        List<String> keys = new ArrayList<String>(items.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            result.add(new LessonItem(key, items.get(key).intValue()));
        }
        return result;
    }

    private void appendBackTranslatedLessonItems(
            java.util.LinkedHashMap<String, Integer> items) {
        for (int mask = 1; mask <= 0xFF; mask++) {
            String translated = brailleParser.backTranslate(this,
                    new Byte[] { Byte.valueOf((byte) mask) });
            if (TextUtils.isEmpty(translated) || translated.length() != 1) {
                continue;
            }
            char symbol = translated.charAt(0);
            if (!matchesCategory(symbol)) {
                continue;
            }
            String key = normalizeLessonSymbol(String.valueOf(symbol));
            if (!items.containsKey(key)) {
                items.put(key, Integer.valueOf(mask));
            }
        }
    }

    private List<String> getCategoryCandidates(Locale tableLocale) {
        String language = tableLocale == null ? "" : tableLocale.getLanguage();
        String symbols;
        switch (currentCategory) {
        case LETTERS:
            symbols = getLetterCandidates(language);
            break;
        case NUMBERS:
            symbols = "1234567890";
            break;
        case PUNCTUATION:
            symbols = ".,;:!?'-\"()/[]{}@#%&*+=<>\\_";
            break;
        default:
            symbols = "";
            break;
        }
        List<String> candidates = new ArrayList<String>(symbols.length());
        for (int i = 0; i < symbols.length();) {
            int codePoint = symbols.codePointAt(i);
            candidates.add(new String(Character.toChars(codePoint)));
            i += Character.charCount(codePoint);
        }
        return candidates;
    }

    private String getLetterCandidates(String language) {
        String base = "abcdefghijklmnopqrstuvwxyz";
        if (TextUtils.isEmpty(language)) {
            return base;
        }
        if ("pl".equals(language)) {
            return base + "ąćęłńóśźż";
        }
        if ("cs".equals(language) || "sk".equals(language)) {
            return base + "áäčďéěíĺľňóôŕšťúýž";
        }
        if ("de".equals(language)) {
            return base + "äöüß";
        }
        if ("es".equals(language)) {
            return base + "áéíñóúü";
        }
        if ("fr".equals(language)) {
            return base + "àâçéèêëîïôùûüÿ";
        }
        if ("it".equals(language)) {
            return base + "àèéìíîòóùú";
        }
        if ("pt".equals(language)) {
            return base + "áâãàçéêíóôõúü";
        }
        if ("hr".equals(language)) {
            return base + "čćđšž";
        }
        if ("ru".equals(language)) {
            return "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
        }
        return base;
    }

    private Integer extractSingleCellMask(byte[] cells) {
        if (cells == null || cells.length != 1) {
            return null;
        }
        return Integer.valueOf(cells[0] & 0xFF);
    }

    private String normalizeLessonSymbol(String symbol) {
        if (TextUtils.isEmpty(symbol)) {
            return "";
        }
        if (symbol.length() == 1 && Character.isLetter(symbol.charAt(0))) {
            return String.valueOf(Character.toLowerCase(symbol.charAt(0)));
        }
        return symbol;
    }

    private boolean matchesCategory(char symbol) {
        switch (currentCategory) {
        case LETTERS:
            return Character.isLetter(symbol);
        case NUMBERS:
            return Character.isDigit(symbol);
        case PUNCTUATION:
            return ".,;:!?'-\"()/".indexOf(symbol) >= 0;
        default:
            return false;
        }
    }

    private void refreshViews() {
        refreshHeaderViews();
        refreshModeViews();
        refreshDotButtons();
        refreshChoiceButtons();
        refreshScore();
        updateKeyboardHelper();
    }

    private void bindViews() {
        modeView = (TextView) findViewById(R.id.braille_learn_mode);
        categoryView = (TextView) findViewById(R.id.braille_learn_category);
        progressView = (TextView) findViewById(R.id.braille_learn_progress);
        tableView = (TextView) findViewById(R.id.braille_learn_table);
        promptView = (TextView) findViewById(R.id.braille_learn_prompt);
        dotsView = (TextView) findViewById(R.id.braille_learn_selected_dots);
        statusView = (TextView) findViewById(R.id.braille_learn_status);
        scoreView = (TextView) findViewById(R.id.braille_learn_score);
        keyboardInputView = (EditText) findViewById(
                R.id.braille_learn_keyboard_input);
        keyboardResultView = (TextView) findViewById(
                R.id.braille_learn_keyboard_result);
        dotsContainer = findViewById(R.id.braille_learn_dots_container);
        choiceContainer = findViewById(R.id.braille_learn_choices);
        bindIndexedButtons(dotButtons, "braille_learn_dot_");
        bindIndexedButtons(choiceButtons, "braille_learn_choice_");
    }

    private void bindIndexedButtons(Button[] buttons, String idPrefix) {
        for (int i = 0; i < buttons.length; i++) {
            int id = getResources().getIdentifier(idPrefix + (i + 1), "id",
                    getPackageName());
            buttons[i] = (Button) findViewById(id);
        }
    }

    private void bindKeyboardHelper() {
        if (keyboardInputView == null) {
            return;
        }
        keyboardInputView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateKeyboardHelper();
            }
        });
    }

    private void resetEmptyLessonState(boolean resetStatus) {
        currentItem = null;
        currentChoices.clear();
        currentIndex = 0;
        if (resetStatus) {
            statusView.setText(R.string.braille_learn_empty);
        }
    }

    private void initializeLessonState(boolean resetStatus) {
        Collections.shuffle(lessonItems, random);
        currentIndex = 0;
        currentItem = lessonItems.get(currentIndex);
        rebuildChoices();
        if (resetStatus) {
            statusView.setText(R.string.braille_learn_ready);
        }
    }

    private void resetCurrentChallenge() {
        currentItem = null;
        currentChoices.clear();
        selectedDotsMask = 0;
    }

    private void refreshHeaderViews() {
        modeView.setText(getModeLabel());
        categoryView.setText(getCategoryLabel());
        progressView.setText(getProgressLabel());
        tableView.setText(getCurrentTableLabel());
        promptView.setText(buildPromptLabel());
    }

    private void refreshModeViews() {
        boolean symbolToDots = currentMode == LessonMode.SYMBOL_TO_DOTS;
        dotsView.setText(getString(R.string.braille_learn_selected_dots_value,
                formatDots(selectedDotsMask)));
        dotsContainer.setVisibility(symbolToDots ? View.VISIBLE : View.GONE);
        dotsView.setVisibility(symbolToDots ? View.VISIBLE : View.GONE);
        choiceContainer.setVisibility(symbolToDots ? View.GONE : View.VISIBLE);
    }

    private void refreshDotButtons() {
        for (int i = 0; i < dotButtons.length; i++) {
            Button button = dotButtons[i];
            if (button == null) {
                continue;
            }
            boolean selected = (selectedDotsMask & (1 << i)) != 0;
            button.setText(getString(R.string.braille_learn_dot_button,
                    i + 1, selected
                            ? getString(R.string.braille_learn_dot_on)
                            : getString(R.string.braille_learn_dot_off)));
            button.setSelected(selected);
        }
    }

    private void refreshChoiceButtons() {
        for (int i = 0; i < choiceButtons.length; i++) {
            Button button = choiceButtons[i];
            if (button == null) {
                continue;
            }
            if (i < currentChoices.size()) {
                LessonItem item = currentChoices.get(i);
                button.setEnabled(currentMode == LessonMode.DOTS_TO_SYMBOL
                        && currentItem != null);
                button.setVisibility(View.VISIBLE);
                button.setText(item.symbol.toUpperCase(Locale.getDefault()));
            } else {
                button.setVisibility(View.GONE);
            }
        }
    }

    private void refreshScore() {
        scoreView.setText(getString(R.string.braille_learn_score,
                correctAnswers, attemptedAnswers, streak, bestStreak));
    }

    private String getModeLabel() {
        switch (currentMode) {
        case SYMBOL_TO_DOTS:
            return getString(R.string.braille_learn_mode_symbol_to_dots);
        case DOTS_TO_SYMBOL:
            return getString(R.string.braille_learn_mode_dots_to_symbol);
        default:
            return "";
        }
    }

    private String getProgressLabel() {
        if (lessonItems.isEmpty()) {
            return getString(R.string.braille_learn_progress, 0, 0);
        }
        return getString(R.string.braille_learn_progress, currentIndex + 1,
                lessonItems.size()) + " | " + getOrderLabel();
    }

    private String getOrderLabel() {
        switch (currentOrder) {
        case SEQUENTIAL:
            return getString(R.string.braille_learn_order_sequential);
        case RANDOM:
        default:
            return getString(R.string.braille_learn_order_random);
        }
    }

    private String buildPromptLabel() {
        if (currentItem == null) {
            return getString(R.string.braille_learn_prompt_empty);
        }
        if (currentMode == LessonMode.DOTS_TO_SYMBOL) {
            return getString(R.string.braille_learn_prompt_from_dots,
                    formatDots(currentItem.dotsMask));
        }
        return getString(R.string.braille_learn_prompt,
                currentItem.symbol.toUpperCase(Locale.getDefault()));
    }

    private String getCategoryLabel() {
        switch (currentCategory) {
        case LETTERS:
            return getString(R.string.braille_learn_category_letters);
        case NUMBERS:
            return getString(R.string.braille_learn_category_numbers);
        case PUNCTUATION:
            return getString(R.string.braille_learn_category_punctuation);
        default:
            return "";
        }
    }

    private String getCurrentTableLabel() {
        if (brailleParser == null) {
            return getString(R.string.braille_learn_table_waiting);
        }
        TableInfo table = brailleParser.getTable(this);
        if (table == null || table.getLocale() == null) {
            return getString(R.string.braille_learn_table_waiting);
        }
        String label = table.getLocale().getDisplayLanguage();
        String country = table.getLocale().getDisplayCountry();
        if (!TextUtils.isEmpty(country)) {
            label += " (" + country + ")";
        }
        return getString(R.string.braille_learn_table_value, label,
                table.isEightDot()
                        ? getString(R.string.grade_computer)
                        : getString(R.string.grade_table, table.getGrade()));
    }

    private String formatDots(int dotsMask) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if ((dotsMask & (1 << i)) != 0) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(i + 1);
            }
        }
        return builder.length() == 0
                ? getString(R.string.braille_learn_no_dots)
                : builder.toString();
    }

    private void updateKeyboardHelper() {
        if (keyboardResultView == null || keyboardInputView == null) {
            return;
        }
        CharSequence value = keyboardInputView.getText();
        if (TextUtils.isEmpty(value)) {
            keyboardResultView.setText(
                    R.string.braille_learn_keyboard_waiting);
            return;
        }
        String symbol = lastInputSymbol(value);
        if (TextUtils.isEmpty(symbol)) {
            keyboardResultView.setText(
                    R.string.braille_learn_keyboard_waiting);
            return;
        }
        byte[] cells = brailleParser == null ? null
                : brailleParser.translateTextToBrailleCells(this, symbol);
        String shown = symbol.toUpperCase(Locale.getDefault());
        if (cells == null || cells.length == 0) {
            keyboardResultView.setText(getString(
                    R.string.braille_learn_keyboard_missing, shown));
            return;
        }
        keyboardResultView.setText(getString(
                R.string.braille_learn_keyboard_result, shown,
                formatDotsSequence(symbol, cells)));
    }

    private String lastInputSymbol(CharSequence value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String text = value.toString();
        int end = text.length();
        int start = Character.offsetByCodePoints(text, end, -1);
        return text.substring(start, end);
    }

    private String formatDotsSequence(String symbol, byte[] cells) {
        if (cells == null || cells.length == 0) {
            return getString(R.string.braille_learn_no_dots);
        }
        if (cells.length == 1) {
            return formatDots(cells[0] & 0xFF);
        }
        String prefixDescription = describeSequencePrefix(symbol, cells);
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(prefixDescription)) {
            builder.append(prefixDescription);
            builder.append(" -> ");
        }
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(i + 1);
            builder.append(": ");
            builder.append(formatDots(cells[i] & 0xFF));
        }
        return builder.toString();
    }

    private String describeSequencePrefix(String symbol, byte[] cells) {
        if (TextUtils.isEmpty(symbol) || cells == null || cells.length < 2
                || brailleParser == null) {
            return null;
        }
        String simple = lastInputSymbol(symbol);
        if (TextUtils.isEmpty(simple)) {
            return null;
        }
        char ch = simple.charAt(0);
        if (Character.isUpperCase(ch)) {
            byte[] lowercase = brailleParser.translateTextToBrailleCells(this,
                    String.valueOf(Character.toLowerCase(ch)));
            if (endsWithCells(cells, lowercase)) {
                return getString(R.string.braille_learn_prefix_capital);
            }
        }
        if (Character.isDigit(ch)) {
            return getString(R.string.braille_learn_prefix_number);
        }
        return getString(R.string.braille_learn_prefix_multi_cell);
    }

    private static boolean endsWithCells(byte[] cells, byte[] suffix) {
        if (cells == null || suffix == null || suffix.length == 0
                || suffix.length >= cells.length) {
            return false;
        }
        int start = cells.length - suffix.length;
        for (int i = 0; i < suffix.length; i++) {
            if (cells[start + i] != suffix[i]) {
                return false;
            }
        }
        return true;
    }
}
