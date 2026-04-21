package com.dalton.braillekeyboard;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class BrailleNotesActivity extends Activity {
    private static final String NOTES_DIR_NAME = "braille-notes";
    private static final int REQUEST_EXPORT_NOTE = 11;
    private static final int REQUEST_IMPORT_NOTE = 12;

    private EditText titleView;
    private EditText notesView;
    private TextView listView;
    private TextView statusView;

    private File notesDir;
    private File[] noteFiles = new File[0];
    private int currentNoteIndex = -1;
    private boolean suppressAutosave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_braille_notes);
        setTitle(R.string.braille_notes_title);

        titleView = (EditText) findViewById(R.id.braille_notes_title_input);
        notesView = (EditText) findViewById(R.id.braille_notes_content);
        listView = (TextView) findViewById(R.id.braille_notes_list);
        statusView = (TextView) findViewById(R.id.braille_notes_status);
        notesDir = new File(getFilesDir(), NOTES_DIR_NAME);
        if (!notesDir.exists()) {
            notesDir.mkdirs();
        }

        notesView.addTextChangedListener(new TextWatcher() {
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
                autosave();
            }
        });

        refreshNoteList();
        if (noteFiles.length == 0) {
            createNewNoteInternal(defaultNoteName(), true);
        } else {
            loadNoteAt(0, false);
        }
    }

    public void onSaveNotes(View view) {
        if (saveCurrentNote()) {
            statusView.setText(getString(R.string.braille_notes_saved,
                    getCurrentFile().getAbsolutePath()));
        } else {
            statusView.setText(R.string.braille_notes_save_failed);
        }
    }

    public void onLoadNotes(View view) {
        if (currentNoteIndex >= 0) {
            loadNoteAt(currentNoteIndex, true);
        }
    }

    public void onShareNotes(View view) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, currentNoteName());
        intent.putExtra(Intent.EXTRA_TEXT, notesView.getText().toString());
        Intent chooser = Intent.createChooser(intent,
                getString(R.string.braille_notes_share));
        if (chooser.resolveActivity(getPackageManager()) != null) {
            startActivity(chooser);
        }
    }

    public void onExportNoteToFile(View view) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, currentNoteName() + ".txt");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_EXPORT_NOTE);
        }
    }

    public void onImportNoteFromFile(View view) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_IMPORT_NOTE);
        }
    }

    public void onClearNotes(View view) {
        notesView.setText("");
        if (saveCurrentNote()) {
            statusView.setText(R.string.braille_notes_cleared);
        } else {
            statusView.setText(R.string.braille_notes_save_failed);
        }
    }

    public void onPreviousNote(View view) {
        if (noteFiles.length == 0) {
            return;
        }
        int next = currentNoteIndex <= 0 ? noteFiles.length - 1
                : currentNoteIndex - 1;
        loadNoteAt(next, true);
    }

    public void onNextNote(View view) {
        if (noteFiles.length == 0) {
            return;
        }
        int next = (currentNoteIndex + 1) % noteFiles.length;
        loadNoteAt(next, true);
    }

    public void onCreateNote(View view) {
        String title = textOf(titleView);
        if (TextUtils.isEmpty(title)) {
            title = defaultNoteName();
        }
        createNewNoteInternal(title, true);
    }

    public void onDeleteNote(View view) {
        File current = getCurrentFile();
        if (current == null) {
            return;
        }
        if (current.delete()) {
            refreshNoteList();
            if (noteFiles.length == 0) {
                createNewNoteInternal(defaultNoteName(), true);
            } else {
                loadNoteAt(Math.max(0, Math.min(currentNoteIndex,
                        noteFiles.length - 1)), true);
            }
            statusView.setText(R.string.braille_notes_deleted);
        } else {
            statusView.setText(R.string.braille_notes_save_failed);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQUEST_EXPORT_NOTE) {
            exportNoteToUri(data.getData());
        } else if (requestCode == REQUEST_IMPORT_NOTE) {
            importNoteFromUri(data.getData());
        }
    }

    private void autosave() {
        if (suppressAutosave) {
            return;
        }
        if (saveCurrentNote()) {
            File file = getCurrentFile();
            if (file != null) {
                showStatus(R.string.braille_notes_autosaved,
                        file.getAbsolutePath());
            }
        }
    }

    private void createNewNoteInternal(String title, boolean showStatus) {
        String safeTitle = sanitizeFileName(title);
        if (TextUtils.isEmpty(safeTitle)) {
            safeTitle = defaultNoteName();
        }
        File candidate = new File(notesDir, safeTitle + ".txt");
        int suffix = 2;
        while (candidate.exists()) {
            candidate = new File(notesDir, safeTitle + "-" + suffix + ".txt");
            suffix++;
        }
        suppressAutosave = true;
        titleView.setText(stripExtension(candidate.getName()));
        notesView.setText("");
        suppressAutosave = false;
        if (saveFile(candidate, "")) {
            refreshNoteList();
            loadNoteByName(candidate.getName(), showStatus);
        }
    }

    private void refreshNoteList() {
        File[] listed = notesDir.listFiles();
        if (listed == null) {
            noteFiles = new File[0];
        } else {
            Arrays.sort(listed, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    long delta = o2.lastModified() - o1.lastModified();
                    if (delta == 0) {
                        return o1.getName().compareToIgnoreCase(o2.getName());
                    }
                    return delta > 0 ? 1 : -1;
                }
            });
            noteFiles = listed;
        }
        updateListView();
    }

    private void updateListView() {
        if (noteFiles.length == 0) {
            listView.setText(R.string.braille_notes_empty);
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < noteFiles.length; i++) {
            if (i == currentNoteIndex) {
                builder.append("* ");
            }
            builder.append(stripExtension(noteFiles[i].getName()));
            builder.append('\n');
        }
        listView.setText(builder.toString().trim());
    }

    private void loadNoteAt(int index, boolean showStatus) {
        if (index < 0 || index >= noteFiles.length) {
            return;
        }
        currentNoteIndex = index;
        File file = noteFiles[index];
        suppressAutosave = true;
        titleView.setText(stripExtension(file.getName()));
        notesView.setText(readFile(file));
        suppressAutosave = false;
        updateListView();
        if (showStatus) {
            showStatus(R.string.braille_notes_loaded, file.getAbsolutePath());
        }
    }

    private void loadNoteByName(String fileName, boolean showStatus) {
        for (int i = 0; i < noteFiles.length; i++) {
            if (noteFiles[i].getName().equals(fileName)) {
                loadNoteAt(i, showStatus);
                return;
            }
        }
    }

    private boolean saveCurrentNote() {
        File current = getCurrentFile();
        String desiredTitle = getDesiredNoteTitle();
        if (current == null) {
            current = new File(notesDir, desiredTitle + ".txt");
        }
        File target = resolveSaveTarget(current, desiredTitle);
        boolean result = saveFile(target, notesView.getText().toString());
        refreshNoteList();
        loadNoteByName(target.getName(), false);
        return result;
    }

    private String getDesiredNoteTitle() {
        String desiredTitle = sanitizeFileName(textOf(titleView));
        return TextUtils.isEmpty(desiredTitle) ? defaultNoteName() : desiredTitle;
    }

    private File resolveSaveTarget(File current, String desiredTitle) {
        String desiredFileName = desiredTitle + ".txt";
        if (current == null || desiredFileName.equals(current.getName())) {
            return current;
        }
        File target = new File(notesDir, desiredFileName);
        int suffix = 2;
        while (target.exists() && !target.equals(current)) {
            target = new File(notesDir, desiredTitle + "-" + suffix + ".txt");
            suffix++;
        }
        return current.renameTo(target) ? target : current;
    }

    private boolean saveFile(File file, String content) {
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file);
            stream.write(content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // Ignore close failure after note save.
                }
            }
        }
    }

    private String readFile(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        try {
            return readStream(new FileInputStream(file), file.length());
        } catch (IOException e) {
            showStatus(R.string.braille_notes_load_failed);
            return "";
        }
    }

    private void exportNoteToUri(Uri uri) {
        try {
            java.io.OutputStream stream = getContentResolver().openOutputStream(uri);
            if (stream == null) {
                showStatus(R.string.braille_notes_export_failed);
                return;
            }
            writeStream(stream, notesView.getText().toString());
            showStatus(R.string.braille_notes_exported, uri.toString());
        } catch (IOException e) {
            showStatus(R.string.braille_notes_export_failed);
        }
    }

    private void importNoteFromUri(Uri uri) {
        try {
            java.io.InputStream stream = getContentResolver().openInputStream(uri);
            if (stream == null) {
                showStatus(R.string.braille_notes_import_failed);
                return;
            }
            String text = readStream(stream, -1);
            String name = uri.getLastPathSegment();
            if (TextUtils.isEmpty(name)) {
                name = defaultNoteName();
            }
            createNewNoteInternal(stripExtension(name), false);
            suppressAutosave = true;
            notesView.setText(text);
            suppressAutosave = false;
            saveCurrentNote();
            showStatus(R.string.braille_notes_imported, uri.toString());
        } catch (IOException e) {
            showStatus(R.string.braille_notes_import_failed);
        }
    }

    private void showStatus(int messageId, Object... args) {
        if (statusView == null) {
            return;
        }
        statusView.setText(args == null || args.length == 0 ? getString(messageId)
                : getString(messageId, args));
    }

    private static void writeStream(java.io.OutputStream stream, String text)
            throws IOException {
        try {
            stream.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Ignore close failure after note write.
            }
        }
    }

    private static String readStream(java.io.InputStream stream, long expectedSize)
            throws IOException {
        try {
            if (expectedSize >= 0 && expectedSize <= Integer.MAX_VALUE) {
                byte[] data = new byte[(int) expectedSize];
                int read = stream.read(data);
                return read <= 0 ? "" : new String(data, 0, read,
                        StandardCharsets.UTF_8);
            }
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Ignore close failure after note read.
            }
        }
    }

    private File getCurrentFile() {
        return currentNoteIndex >= 0 && currentNoteIndex < noteFiles.length
                ? noteFiles[currentNoteIndex] : null;
    }

    private String currentNoteName() {
        File current = getCurrentFile();
        return current == null ? getString(R.string.braille_notes_title)
                : stripExtension(current.getName());
    }

    private String defaultNoteName() {
        return "note-" + new SimpleDateFormat("yyyyMMdd-HHmmss",
                Locale.US).format(new Date());
    }

    private static String sanitizeFileName(String input) {
        if (input == null) {
            return "";
        }
        String safe = input.trim().replaceAll("[\\\\/:*?\"<>|]+", "-");
        safe = safe.replaceAll("\\s+", " ");
        return safe;
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String textOf(EditText view) {
        return view == null || view.getText() == null
                ? "" : view.getText().toString().trim();
    }
}
