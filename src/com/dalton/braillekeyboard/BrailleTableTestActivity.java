package com.dalton.braillekeyboard;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.googlecode.eyesfree.braille.translate.TableInfo;
import com.googlecode.eyesfree.braille.translate.TranslationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class BrailleTableTestActivity extends Activity
        implements BrailleParser.BrailleParserListener {
    private BrailleParser brailleParser;
    private final List<TableInfo> tables = new ArrayList<TableInfo>();
    private int currentTableIndex;

    private TextView currentTableView;
    private TextView statusView;
    private EditText sourceTextView;
    private TextView forwardResultView;
    private EditText cellsInputView;
    private TextView backwardResultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_braille_table_test);
        setTitle(R.string.braille_table_test_title);

        currentTableView = (TextView) findViewById(R.id.braille_table_test_current);
        statusView = (TextView) findViewById(R.id.braille_table_test_status);
        sourceTextView = (EditText) findViewById(R.id.braille_table_test_source);
        forwardResultView = (TextView) findViewById(R.id.braille_table_test_forward_result);
        cellsInputView = (EditText) findViewById(R.id.braille_table_test_cells_input);
        backwardResultView = (TextView) findViewById(R.id.braille_table_test_backward_result);

        brailleParser = new BrailleParser(this, this);
        statusView.setText(R.string.braille_table_test_waiting);
        updateTableUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (brailleParser != null) {
            brailleParser.destroy();
            brailleParser = null;
        }
    }

    @Override
    public void onTranslatorReady(int status) {
        if (status != BrailleParser.STATUS_OK || brailleParser == null) {
            statusView.setText(R.string.braille_table_test_error);
            return;
        }
        tables.clear();
        List<TableInfo> allTables = brailleParser.getTables(
                BrailleParser.BrailleType.ALL);
        if (allTables != null) {
            tables.addAll(allTables);
        }
        Collections.sort(tables, new java.util.Comparator<TableInfo>() {
            @Override
            public int compare(TableInfo left, TableInfo right) {
                return formatTableLabel(left).compareToIgnoreCase(
                        formatTableLabel(right));
            }
        });
        currentTableIndex = findCurrentTableIndex();
        updateTableUi();
        statusView.setText(tables.isEmpty()
                ? getString(R.string.braille_table_test_no_tables)
                : getString(R.string.braille_table_test_ready));
    }

    public void onPreviousTable(View view) {
        if (tables.isEmpty()) {
            return;
        }
        currentTableIndex = currentTableIndex <= 0
                ? tables.size() - 1 : currentTableIndex - 1;
        updateTableUi();
    }

    public void onNextTable(View view) {
        if (tables.isEmpty()) {
            return;
        }
        currentTableIndex = (currentTableIndex + 1) % tables.size();
        updateTableUi();
    }

    public void onTranslateForward(View view) {
        TableInfo table = getCurrentTable();
        if (table == null || brailleParser == null) {
            statusView.setText(R.string.braille_table_test_waiting);
            return;
        }
        CharSequence source = sourceTextView == null ? null : sourceTextView.getText();
        if (TextUtils.isEmpty(source)) {
            forwardResultView.setText(R.string.braille_table_test_empty_input);
            return;
        }
        TranslationResult translation = brailleParser.translateText(this, source,
                source.length(), table.getId());
        byte[] cells = translation == null ? null : translation.getCells();
        if (cells == null || cells.length == 0) {
            forwardResultView.setText(R.string.braille_table_test_no_forward_result);
            return;
        }
        forwardResultView.setText(getString(
                R.string.braille_table_test_forward_result,
                formatCells(cells), cells.length));
    }

    public void onTranslateBackward(View view) {
        TableInfo table = getCurrentTable();
        if (table == null || brailleParser == null) {
            statusView.setText(R.string.braille_table_test_waiting);
            return;
        }
        String rawInput = cellsInputView == null ? null
                : cellsInputView.getText().toString();
        Byte[] cells = parseCellInput(rawInput);
        if (cells == null || cells.length == 0) {
            backwardResultView.setText(R.string.braille_table_test_invalid_cells);
            return;
        }
        String text = brailleParser.backTranslate(this, cells, table.getId());
        if (TextUtils.isEmpty(text)) {
            backwardResultView.setText(R.string.braille_table_test_no_backward_result);
            return;
        }
        backwardResultView.setText(getString(
                R.string.braille_table_test_backward_result, text));
    }

    private void updateTableUi() {
        TableInfo table = getCurrentTable();
        if (currentTableView == null) {
            return;
        }
        currentTableView.setText(table == null
                ? getString(R.string.braille_table_test_no_tables)
                : getString(R.string.braille_table_test_current_value,
                        currentTableIndex + 1, tables.size(),
                        formatTableLabel(table)));
    }

    private TableInfo getCurrentTable() {
        if (tables.isEmpty() || currentTableIndex < 0
                || currentTableIndex >= tables.size()) {
            return null;
        }
        return tables.get(currentTableIndex);
    }

    private int findCurrentTableIndex() {
        if (brailleParser == null || tables.isEmpty()) {
            return 0;
        }
        TableInfo current = brailleParser.getTable(this);
        if (current == null) {
            return 0;
        }
        for (int i = 0; i < tables.size(); i++) {
            if (TextUtils.equals(current.getId(), tables.get(i).getId())) {
                return i;
            }
        }
        return 0;
    }

    private String formatTableLabel(TableInfo table) {
        if (table == null) {
            return "";
        }
        Locale locale = table.getLocale() == null ? Locale.ROOT : table.getLocale();
        StringBuilder builder = new StringBuilder();
        String displayName = locale.getDisplayName();
        if (!TextUtils.isEmpty(displayName)) {
            builder.append(displayName);
        } else {
            builder.append(table.getId());
        }
        if (!TextUtils.isEmpty(table.getVariant())) {
            builder.append(" / ");
            builder.append(table.getVariant());
        }
        builder.append(" / ");
        builder.append(table.isEightDot()
                ? getString(R.string.grade_computer)
                : getString(R.string.grade_table, table.getGrade()));
        builder.append(" / ");
        builder.append(table.getId());
        return builder.toString();
    }

    private String formatCells(byte[] cells) {
        List<String> parts = new ArrayList<String>();
        for (byte cell : cells) {
            int mask = cell & 0xFF;
            StringBuilder builder = new StringBuilder();
            for (int dot = 1; dot <= 8; dot++) {
                if ((mask & (1 << (dot - 1))) != 0) {
                    builder.append(dot);
                }
            }
            parts.add(builder.length() == 0 ? "0" : builder.toString());
        }
        return TextUtils.join(" ", parts);
    }

    private Byte[] parseCellInput(String rawInput) {
        if (TextUtils.isEmpty(rawInput)) {
            return null;
        }
        String[] tokens = rawInput.trim().split("\\s+");
        List<Byte> cells = new ArrayList<Byte>();
        for (String token : tokens) {
            String digits = token == null ? "" : token.replaceAll("[^1-8]", "");
            if (TextUtils.isEmpty(digits)) {
                continue;
            }
            int mask = 0;
            for (int i = 0; i < digits.length(); i++) {
                int dot = Character.digit(digits.charAt(i), 10);
                if (dot < 1 || dot > 8) {
                    continue;
                }
                mask |= 1 << (dot - 1);
            }
            cells.add(Byte.valueOf((byte) mask));
        }
        return cells.isEmpty() ? null : cells.toArray(new Byte[cells.size()]);
    }
}
