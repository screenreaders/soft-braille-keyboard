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

    public static final class ReportResult {
        public enum Mode {
            SERVER,
            GITHUB,
            FAILED
        }

        public final Mode mode;

        ReportResult(Mode mode) {
            this.mode = mode;
        }
    }

    public static final class ReportData {
        public enum ReportType {
            GENERAL,
            BRAILLE_DISPLAY
        }

        public final String subject;
        public final String message;
        public final String name;
        public final String email;
        public final boolean includeDiagnostics;
        public final String additionalDiagnostics;
        public final ReportType reportType;

        public ReportData(String subject, String message, String name,
                String email, boolean includeDiagnostics,
                String additionalDiagnostics, ReportType reportType) {
            this.subject = subject == null ? "" : subject.trim();
            this.message = message == null ? "" : message.trim();
            this.name = name == null ? "" : name.trim();
            this.email = email == null ? "" : email.trim();
            this.includeDiagnostics = includeDiagnostics;
            this.additionalDiagnostics = additionalDiagnostics;
            this.reportType = reportType == null ? ReportType.GENERAL
                    : reportType;
        }
    }

    private SupportReportSender() {
    }

    public static ReportResult submit(Context context, final ReportData data) {
        final String diagnostics = buildDiagnostics(context, data);
        String title = buildReportTitle(data);
        String remoteBody = buildRemoteBody(data, title, diagnostics);
        RemoteReportUploader.UploadResult uploadResult
                = RemoteReportUploader.uploadTextReport(context, title,
                        remoteBody,
                        data.reportType == ReportData.ReportType.BRAILLE_DISPLAY
                                ? "braille-display" : "bug-report");
        if (uploadResult.success) {
            return new ReportResult(ReportResult.Mode.SERVER);
        }
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
        boolean hardware = data.reportType == ReportData.ReportType.BRAILLE_DISPLAY;
        String issueBody = SupportReportTextUtils.buildGitHubBody(data);
        Uri.Builder builder = Uri.parse(issueUrl).buildUpon()
                .appendQueryParameter("title",
                        (hardware ? "[hardware] " : "[bug] ") + data.subject)
                .appendQueryParameter("body", issueBody)
                .appendQueryParameter("labels",
                        hardware ? "hardware,needs-triage"
                                : "bug,needs-triage");
        if (hardware) {
            builder.appendQueryParameter("template", "hardware-report.yml");
        } else {
            builder.appendQueryParameter("template", "bug-report.yml");
        }
        Uri uri = builder.build();
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return false;
        }
        context.startActivity(intent);
        return true;
    }

    private static String buildDiagnostics(Context context, ReportData data) {
        return data.includeDiagnostics
                ? SupportDiagnostics.buildReport(context, data.message,
                        data.additionalDiagnostics)
                : null;
    }

    private static String buildReportTitle(ReportData data) {
        return SupportReportTextUtils.buildReportTitle(data);
    }

    private static String buildRemoteBody(ReportData data, String title,
            String diagnostics) {
        return SupportReportTextUtils.buildRemoteBody(data, title, diagnostics);
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
