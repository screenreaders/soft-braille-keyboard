package com.dalton.braillekeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

public final class SupportReportSender {
    public static final String EXTRA_ADDITIONAL_DIAGNOSTICS =
            "com.dalton.braillekeyboard.EXTRA_ADDITIONAL_DIAGNOSTICS";

    private static volatile boolean initialized;

    public static final class ReportResult {
        public enum Mode {
            GITHUB,
            FAILED
        }

        public final Mode mode;

        ReportResult(Mode mode) {
            this.mode = mode;
        }
    }

    public static final class ReportData {
        public final String subject;
        public final String message;
        public final String name;
        public final String email;
        public final boolean includeDiagnostics;
        public final String additionalDiagnostics;

        public ReportData(String subject, String message, String name,
                String email, boolean includeDiagnostics,
                String additionalDiagnostics) {
            this.subject = subject == null ? "" : subject.trim();
            this.message = message == null ? "" : message.trim();
            this.name = name == null ? "" : name.trim();
            this.email = email == null ? "" : email.trim();
            this.includeDiagnostics = includeDiagnostics;
            this.additionalDiagnostics = additionalDiagnostics;
        }
    }

    private SupportReportSender() {
    }

    public static ReportResult submit(Context context, final ReportData data) {
        final String diagnostics = data.includeDiagnostics
                ? SupportDiagnostics.buildReport(context, data.message,
                        data.additionalDiagnostics)
                : null;
        boolean opened = openGitHubIssue(context, data, diagnostics);
        return new ReportResult(opened ? ReportResult.Mode.GITHUB
                : ReportResult.Mode.FAILED);
    }

    private static boolean openGitHubIssue(Context context, ReportData data,
            String diagnostics) {
        copyDiagnosticsToClipboard(context, diagnostics);
        GitHubReleaseChecker.RepoInfo repoInfo = GitHubReleaseChecker.parseRepoInfo(
                context.getString(R.string.info_url));
        if (repoInfo == null) {
            return false;
        }
        String issueUrl = "https://github.com/" + repoInfo.owner + "/"
                + repoInfo.repo + "/issues/new";
        StringBuilder body = new StringBuilder();
        body.append("Manual support report sent from the app.\n\n");
        body.append("Message:\n");
        body.append(data.message);
        body.append("\n\n");
        if (!TextUtils.isEmpty(data.name) || !TextUtils.isEmpty(data.email)) {
            body.append("Reporter:\n");
            body.append(TextUtils.isEmpty(data.name) ? "(not provided)"
                    : data.name);
            if (!TextUtils.isEmpty(data.email)) {
                body.append(" <");
                body.append(data.email);
                body.append(">");
            }
            body.append("\n\n");
        }
        body.append("Diagnostics were copied to the clipboard on the device.\n");
        Uri uri = Uri.parse(issueUrl).buildUpon()
                .appendQueryParameter("title",
                        "Manual report: " + data.subject)
                .appendQueryParameter("body", body.toString())
                .build();
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return false;
        }
        context.startActivity(intent);
        return true;
    }

    private static void copyDiagnosticsToClipboard(Context context,
            String diagnostics) {
        if (TextUtils.isEmpty(diagnostics)) {
            return;
        }
        ClipboardManager manager = (ClipboardManager) context.getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText(
                    context.getString(R.string.github_issue_clip_label),
                    diagnostics));
        }
    }
}
