package com.dalton.braillekeyboard;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SupportReportActivity extends Activity {
    public static final String EXTRA_REPORT_TYPE =
            "com.dalton.braillekeyboard.EXTRA_REPORT_TYPE";

    private EditText subjectView;
    private EditText messageView;
    private EditText nameView;
    private EditText emailView;
    private CheckBox diagnosticsView;
    private TextView statusView;
    private String additionalDiagnostics;
    private SupportReportSender.ReportData.ReportType reportType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support_report);
        subjectView = (EditText) findViewById(R.id.report_subject);
        messageView = (EditText) findViewById(R.id.report_message);
        nameView = (EditText) findViewById(R.id.report_name);
        emailView = (EditText) findViewById(R.id.report_email);
        diagnosticsView = (CheckBox) findViewById(R.id.report_include_diagnostics);
        statusView = (TextView) findViewById(R.id.report_status);
        additionalDiagnostics = getIntent().getStringExtra(
                SupportReportSender.EXTRA_ADDITIONAL_DIAGNOSTICS);
        String type = getIntent().getStringExtra(EXTRA_REPORT_TYPE);
        reportType = "braille_display".equals(type)
                ? SupportReportSender.ReportData.ReportType.BRAILLE_DISPLAY
                : SupportReportSender.ReportData.ReportType.GENERAL;
        updateStatus();
    }

    public void onSendReport(View view) {
        String subject = textOf(subjectView);
        String message = textOf(messageView);
        if (TextUtils.isEmpty(subject)) {
            subjectView.setError(getString(R.string.report_issue_subject_required));
            subjectView.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(message)) {
            messageView.setError(getString(R.string.report_issue_message_required));
            messageView.requestFocus();
            return;
        }
        SupportReportSender.ReportResult result = SupportReportSender.submit(this,
                new SupportReportSender.ReportData(subject, message,
                        textOf(nameView), textOf(emailView),
                        diagnosticsView.isChecked(), additionalDiagnostics,
                        reportType));
        if (result.mode == SupportReportSender.ReportResult.Mode.GITHUB) {
            Toast.makeText(this, R.string.report_issue_sent_github,
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Toast.makeText(this, R.string.report_issue_failed,
                Toast.LENGTH_LONG).show();
    }

    private void updateStatus() {
        statusView.setText(R.string.report_issue_github_only);
        if (reportType == SupportReportSender.ReportData.ReportType.BRAILLE_DISPLAY
                && TextUtils.isEmpty(subjectView.getText())) {
            subjectView.setText(R.string.report_braille_issue_default_subject);
            subjectView.setSelection(subjectView.getText().length());
        }
    }

    private static String textOf(EditText view) {
        return view == null || view.getText() == null
                ? "" : view.getText().toString().trim();
    }
}
