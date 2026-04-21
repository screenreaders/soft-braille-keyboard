package com.dalton.braillekeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class TextTransferUtils {
    private TextTransferUtils() {
    }

    static ClipboardManager getClipboardManager(Context context) {
        return context == null ? null
                : (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    static CharSequence getClipboardText(Context context, ClipboardManager clipboard) {
        if (context == null || clipboard == null || !clipboard.hasPrimaryClip()) {
            return null;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() <= 0) {
            return null;
        }
        ClipData.Item item = clip.getItemAt(0);
        return item == null ? null : item.coerceToText(context);
    }

    static boolean writeTextToUri(Context context, Uri uri, String text) {
        if (context == null || uri == null || text == null) {
            return false;
        }
        OutputStream stream = null;
        try {
            stream = context.getContentResolver().openOutputStream(uri);
            if (stream == null) {
                return false;
            }
            stream.write(text.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            closeQuietly(stream);
        }
    }

    static String readTextFromUri(Context context, Uri uri) {
        if (context == null || uri == null) {
            return null;
        }
        InputStream stream = null;
        try {
            stream = context.getContentResolver().openInputStream(uri);
            if (stream == null) {
                return null;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return null;
        } finally {
            closeQuietly(stream);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            // Ignore cleanup failure after transfer.
        }
    }
}
