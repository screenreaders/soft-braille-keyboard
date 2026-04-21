package com.dalton.braillekeyboard;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.googlecode.eyesfree.braille.translate.TableInfo;
import com.googlecode.eyesfree.braille.translate.TranslationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public class BrailleTableTestActivity extends Activity
        implements BrailleParser.BrailleParserListener {
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 800L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing()) {
                startTranslatorLoad(true);
            }
        }
    };

    private BrailleParser brailleParser;
    private final List<TableInfo> tables = new ArrayList<TableInfo>();
    private int currentTableIndex;
    private int retryAttempts;
    private boolean loadingTranslator = true;

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
        bindViews();

        statusView.setText(R.string.braille_table_test_waiting);
        startTranslatorLoad(false);
        updateTableUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (loadingTranslator || tables.isEmpty()) {
            startTranslatorLoad(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(retryRunnable);
        if (brailleParser != null) {
            brailleParser.destroy();
            brailleParser = null;
        }
    }

    @Override
    public void onTranslatorReady(int status) {
        loadingTranslator = false;
        handler.removeCallbacks(retryRunnable);
        if (brailleParser == null) {
            return;
        }

        tables.clear();
        if (status == BrailleParser.STATUS_OK
                || status == BrailleParser.STATUS_TABLE_ERROR) {
            loadAvailableTables();
        }

        if (!tables.isEmpty()) {
            currentTableIndex = findCurrentTableIndex();
            updateTableUi();
            statusView.setText(R.string.braille_table_test_ready);
            retryAttempts = 0;
            return;
        }

        if (retryAttempts < MAX_RETRY_ATTEMPTS) {
            retryAttempts++;
            loadingTranslator = true;
            statusView.setText(R.string.braille_table_test_waiting);
            updateTableUi();
            handler.postDelayed(retryRunnable, RETRY_DELAY_MS);
            return;
        }

        updateTableUi();
        statusView.setText(status == BrailleParser.STATUS_OK
                || status == BrailleParser.STATUS_TABLE_ERROR
                ? R.string.braille_table_test_no_tables
                : R.string.braille_table_test_error);
    }

    public void onReloadTables(View view) {
        retryAttempts = 0;
        startTranslatorLoad(true);
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
                ? getString(loadingTranslator
                        ? R.string.braille_table_test_loading_tables
                        : R.string.braille_table_test_no_tables)
                : getString(R.string.braille_table_test_current_value,
                        currentTableIndex + 1, tables.size(),
                        BrailleTableUiUtils.formatDetailedLabel(this, table)));
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

    private void startTranslatorLoad(boolean forceRecreate) {
        if (forceRecreate && brailleParser != null) {
            brailleParser.destroy();
            brailleParser = null;
        }
        if (brailleParser == null) {
            brailleParser = new BrailleParser(this, this);
        }
        loadingTranslator = true;
        statusView.setText(R.string.braille_table_test_waiting);
        updateTableUi();
    }

    private void bindViews() {
        currentTableView = (TextView) findViewById(R.id.braille_table_test_current);
        statusView = (TextView) findViewById(R.id.braille_table_test_status);
        sourceTextView = (EditText) findViewById(R.id.braille_table_test_source);
        forwardResultView = (TextView) findViewById(
                R.id.braille_table_test_forward_result);
        cellsInputView = (EditText) findViewById(R.id.braille_table_test_cells_input);
        backwardResultView = (TextView) findViewById(
                R.id.braille_table_test_backward_result);
    }

    private void loadAvailableTables() {
        addTables(brailleParser.getTables(BrailleParser.BrailleType.ALL));
        if (tables.isEmpty()) {
            addTables(brailleParser.getTables(BrailleParser.BrailleType.LITERARY));
            addTables(brailleParser.getTables(BrailleParser.BrailleType.COMPUTER));
        }
        Collections.sort(tables, new java.util.Comparator<TableInfo>() {
            @Override
            public int compare(TableInfo left, TableInfo right) {
                return BrailleTableUiUtils.formatDetailedLabel(
                        BrailleTableTestActivity.this, left).compareToIgnoreCase(
                        BrailleTableUiUtils.formatDetailedLabel(
                                BrailleTableTestActivity.this, right));
            }
        });
    }

    private void addTables(List<TableInfo> source) {
        if (source == null) {
            return;
        }
        for (TableInfo table : source) {
            if (table != null && !containsTableId(table.getId())) {
                tables.add(table);
            }
        }
    }

    private boolean containsTableId(String tableId) {
        for (TableInfo existing : tables) {
            if (existing != null && TextUtils.equals(existing.getId(), tableId)) {
                return true;
            }
        }
        return false;
    }
}
