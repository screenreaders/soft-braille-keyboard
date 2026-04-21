package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public final class RemoteReportUploader {
    public static final class UploadResult {
        public final boolean success;
        public final String serverFile;
        public final String message;

        UploadResult(boolean success, String serverFile, String message) {
            this.success = success;
            this.serverFile = serverFile;
            this.message = message;
        }
    }

    private static final String ENDPOINT = "https://report.asteja.eu/";
    private static final String REPORT_TOKEN = "47dc28661ef7d1ac07400827508b6aa9";
    private static final String PREF_CLIENT_ID = "REMOTE_REPORT_CLIENT_ID";
    private static final int CONNECT_TIMEOUT_MS = 7000;
    private static final int READ_TIMEOUT_MS = 15000;

    private RemoteReportUploader() {
    }

    public static UploadResult uploadTextReport(Context context, String title,
            String body, String kind) {
        if (TextUtils.isEmpty(body)) {
            return new UploadResult(false, null, "empty report body");
        }
        byte[] payload;
        try {
            payload = body.getBytes("UTF-8");
        } catch (Exception e) {
            payload = body.getBytes();
        }
        return uploadPayload(context, payload, "text/plain; charset=utf-8",
                title, kind, null);
    }

    public static UploadResult uploadZipReport(Context context, File file,
            String title, String kind) {
        if (file == null || !file.exists() || !file.isFile()) {
            return new UploadResult(false, null, "missing report zip");
        }
        HttpURLConnection connection = null;
        BufferedInputStream input = null;
        BufferedOutputStream output = null;
        try {
            connection = openConnection(context, "application/zip", title,
                    kind, file.getName());
            output = new BufferedOutputStream(connection.getOutputStream());
            input = new BufferedInputStream(new FileInputStream(file));
            pipe(input, output);
            return parseResponse(connection);
        } catch (IOException e) {
            return new UploadResult(false, null, "upload failed");
        } finally {
            closeQuietly(input);
            closeQuietly(output);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static UploadResult uploadPayload(Context context, byte[] payload,
            String contentType, String title, String kind, String fileName) {
        HttpURLConnection connection = null;
        BufferedOutputStream output = null;
        try {
            connection = openConnection(context, contentType, title, kind,
                    fileName);
            output = new BufferedOutputStream(connection.getOutputStream());
            writeAndFlush(output, payload);
            return parseResponse(connection);
        } catch (IOException e) {
            return new UploadResult(false, null, "upload failed");
        } finally {
            closeQuietly(output);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpURLConnection openConnection(Context context,
            String contentType, String title, String kind, String fileName)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT)
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestProperty("X-Report-Token", REPORT_TOKEN);
        connection.setRequestProperty("X-Client-Token", ensureClientId(context));
        connection.setRequestProperty("X-Report-Title",
                TextUtils.isEmpty(title) ? "Soft Braille Keyboard report"
                        : title);
        connection.setRequestProperty("X-Report-Kind",
                TextUtils.isEmpty(kind) ? "general" : kind);
        connection.setRequestProperty("X-Report-App",
                BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME);
        if (!TextUtils.isEmpty(fileName)) {
            connection.setRequestProperty("X-Report-Filename", fileName);
        }
        return connection;
    }

    private static UploadResult parseResponse(HttpURLConnection connection)
            throws IOException {
        int code = connection.getResponseCode();
        byte[] response = readAllBytes(selectResponseStream(connection, code));
        String text = response == null ? "" : new String(response, "UTF-8");
        if (code < 200 || code >= 300) {
            return new UploadResult(false, null,
                    TextUtils.isEmpty(text) ? "server rejected report" : text);
        }
        try {
            JSONObject json = new JSONObject(text);
            return new UploadResult(true,
                    json.optString("file",
                            json.optString("stored_file", null)),
                    json.optString("status", "ok"));
        } catch (Exception e) {
            return new UploadResult(true, null, "ok");
        }
    }

    private static java.io.InputStream selectResponseStream(
            HttpURLConnection connection, int code) throws IOException {
        return code >= 200 && code < 300 ? connection.getInputStream()
                : connection.getErrorStream();
    }

    private static String ensureClientId(Context context) {
        if (context == null) {
            return UUID.randomUUID().toString();
        }
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context.getApplicationContext());
        String existing = preferences.getString(PREF_CLIENT_ID, "");
        if (!TextUtils.isEmpty(existing)) {
            return existing;
        }
        String id = UUID.randomUUID().toString();
        preferences.edit().putString(PREF_CLIENT_ID, id).apply();
        return id;
    }

    private static byte[] readAllBytes(java.io.InputStream stream)
            throws IOException {
        if (stream == null) {
            return null;
        }
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        try {
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            closeQuietly(stream);
            closeQuietly(output);
        }
    }

    private static void writeAndFlush(BufferedOutputStream output, byte[] payload)
            throws IOException {
        output.write(payload);
        output.flush();
    }

    private static void pipe(BufferedInputStream input,
            BufferedOutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Ignore close failure.
        }
    }
}
