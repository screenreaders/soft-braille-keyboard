package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONException;

import java.io.IOException;

final class MainActivityBackupUtils {
    private MainActivityBackupUtils() {
    }

    static Intent buildExportIntent(Context context) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,
                context.getString(R.string.app_settings_backup_file_name));
        return intent;
    }

    static Intent buildImportIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        return intent;
    }

    static void exportPreferencesToUri(Context context, Uri uri)
            throws IOException, JSONException {
        if (uri == null) {
            throw new IOException("Missing export uri");
        }
        String payload = AppSettingsBackup.exportPreferences(context);
        if (!TextTransferUtils.writeTextToUri(context, uri, payload)) {
            throw new IOException("Failed to write export payload");
        }
    }

    static int importPreferencesFromUri(Context context, Uri uri)
            throws IOException, JSONException {
        if (uri == null) {
            throw new IOException("Missing import uri");
        }
        String payload = TextTransferUtils.readTextFromUri(context, uri);
        if (payload == null) {
            throw new IOException("Failed to read import payload");
        }
        return AppSettingsBackup.importPreferences(context, payload);
    }
}
