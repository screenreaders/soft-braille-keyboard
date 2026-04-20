package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ScreenReportExporter {
    private ScreenReportExporter() {
    }

    public static File createReportDirectory(Context context) {
        File root = new File(context.getFilesDir(), "screen-reports");
        if (!root.exists()) {
            root.mkdirs();
        }
        String name = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date());
        File dir = new File(root, "report-" + name);
        dir.mkdirs();
        return dir;
    }

    public static void writeTextFile(File file, String content)
            throws IOException {
        FileOutputStream stream = new FileOutputStream(file);
        try {
            stream.write((content == null ? "" : content)
                    .getBytes(StandardCharsets.UTF_8));
            stream.flush();
        } finally {
            stream.close();
        }
    }

    public static File zipDirectory(File directory) throws IOException {
        File zipFile = new File(directory.getParentFile(), directory.getName()
                + ".zip");
        ZipOutputStream zipStream = new ZipOutputStream(
                new FileOutputStream(zipFile));
        try {
            addDirectoryToZip(directory, directory, zipStream);
        } finally {
            zipStream.close();
        }
        return zipFile;
    }

    private static void addDirectoryToZip(File root, File current,
            ZipOutputStream zipStream) throws IOException {
        File[] files = current.listFiles();
        if (files == null) {
            return;
        }
        byte[] buffer = new byte[4096];
        for (File file : files) {
            if (file.isDirectory()) {
                addDirectoryToZip(root, file, zipStream);
                continue;
            }
            String relativePath = root.toURI().relativize(file.toURI()).getPath();
            zipStream.putNextEntry(new ZipEntry(relativePath));
            FileInputStream input = new FileInputStream(file);
            try {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    zipStream.write(buffer, 0, read);
                }
            } finally {
                input.close();
                zipStream.closeEntry();
            }
        }
    }

    public static void shareReport(Context context, File zipFile) {
        if (context == null || zipFile == null || !zipFile.exists()) {
            return;
        }
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", zipFile);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(Intent.createChooser(intent,
                context.getString(R.string.screen_report_share)));
    }

    public static String sanitizeFileSegment(String value) {
        if (value == null) {
            return "screen";
        }
        String normalized = value.trim().toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-");
        return normalized.length() == 0 ? "screen" : normalized;
    }
}
