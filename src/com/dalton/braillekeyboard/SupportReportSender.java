package com.dalton.braillekeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import io.sentry.Attachment;
import io.sentry.IScope;
import io.sentry.ScopeCallback;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.UserFeedback;
import io.sentry.android.core.SentryAndroid;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;

import java.nio.charset.Charset;
import java.util.Locale;

public final class SupportReportSender {
    public static final String EXTRA_ADDITIONAL_DIAGNOSTICS =
            "com.dalton.braillekeyboard.EXTRA_ADDITIONAL_DIAGNOSTICS";

    private static volatile boolean initialized;

    public static final class ReportResult {
        public enum Mode {
            REMOTE,
            GITHUB_FALLBACK,
            FAILED
        }

        public final Mode mode;
        public final SentryId eventId;

        ReportResult(Mode mode, SentryId eventId) {
            this.mode = mode;
            this.eventId = eventId;
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

    public static synchronized void initialize(Context context) {
        if (initialized || TextUtils.isEmpty(BuildConfig.SENTRY_DSN)) {
            initialized = true;
            return;
        }
        final Context appContext = context.getApplicationContext();
        SentryAndroid.init(appContext, options -> {
            options.setDsn(BuildConfig.SENTRY_DSN);
            options.setRelease(BuildConfig.APPLICATION_ID + "@"
                    + BuildConfig.VERSION_NAME + "+" + BuildConfig.VERSION_CODE);
            options.setEnvironment(BuildConfig.DEBUG ? "debug" : "release");
            options.setAttachThreads(true);
            options.setSendDefaultPii(false);
        });
        Sentry.configureScope(new ScopeCallback() {
            @Override
            public void run(IScope scope) {
                scope.setTag("application", "soft-braille-keyboard");
                scope.setTag("app_version", BuildConfig.VERSION_NAME);
            }
        });
        initialized = true;
    }

    public static boolean isRemoteReportingConfigured() {
        return !TextUtils.isEmpty(BuildConfig.SENTRY_DSN) && Sentry.isEnabled();
    }

    public static ReportResult submit(Context context, final ReportData data) {
        initialize(context);
        final String diagnostics = data.includeDiagnostics
                ? SupportDiagnostics.buildReport(context, data.message,
                        data.additionalDiagnostics)
                : null;

        if (!isRemoteReportingConfigured()) {
            boolean opened = openGitHubFallback(context, data, diagnostics);
            return new ReportResult(opened ? ReportResult.Mode.GITHUB_FALLBACK
                    : ReportResult.Mode.FAILED, null);
        }

        final SentryId[] eventId = new SentryId[1];
        Sentry.withScope(new ScopeCallback() {
            @Override
            public void run(IScope scope) {
                scope.setTag("report_source", "manual_feedback");
                scope.setTag("has_diagnostics",
                        data.includeDiagnostics ? "true" : "false");
                scope.setTag("report_subject",
                        sanitizeTagValue(data.subject));
                scope.setExtra("report_message", data.message);
                if (!TextUtils.isEmpty(data.email)) {
                    scope.setExtra("report_email", data.email);
                }
                if (!TextUtils.isEmpty(data.name)) {
                    User user = new User();
                    user.setName(data.name);
                    user.setEmail(data.email);
                    scope.setUser(user);
                }
                if (!TextUtils.isEmpty(diagnostics)) {
                    scope.addAttachment(new Attachment(
                            diagnostics.getBytes(Charset.forName("UTF-8")),
                            "support-diagnostics.txt", "text/plain"));
                }
                eventId[0] = Sentry.captureMessage(
                        "Manual report: " + data.subject, SentryLevel.WARNING);
                UserFeedback userFeedback = new UserFeedback(eventId[0],
                        emptyToNull(data.name), emptyToNull(data.email),
                        emptyToNull(data.message));
                Sentry.captureUserFeedback(userFeedback);
            }
        });
        return new ReportResult(ReportResult.Mode.REMOTE, eventId[0]);
    }

    private static boolean openGitHubFallback(Context context, ReportData data,
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

    private static String sanitizeTagValue(String input) {
        if (TextUtils.isEmpty(input)) {
            return "manual-report";
        }
        String normalized = input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-");
        return normalized.length() > 200
                ? normalized.substring(0, 200) : normalized;
    }

    private static String emptyToNull(String value) {
        return TextUtils.isEmpty(value) ? null : value;
    }
}
