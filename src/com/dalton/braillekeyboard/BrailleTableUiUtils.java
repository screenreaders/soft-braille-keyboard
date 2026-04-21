package com.dalton.braillekeyboard;

import android.content.Context;
import android.text.TextUtils;

import com.googlecode.eyesfree.braille.translate.TableInfo;

import java.util.Locale;

final class BrailleTableUiUtils {
    private BrailleTableUiUtils() {
    }

    static String formatDetailedLabel(Context context, TableInfo table) {
        if (context == null || table == null) {
            return "";
        }
        Locale locale = table.getLocale() == null ? Locale.ROOT : table.getLocale();
        StringBuilder builder = new StringBuilder();
        String displayName = locale.getDisplayName();
        if (!TextUtils.isEmpty(displayName)) {
            builder.append(displayName);
        } else {
            builder.append(table.getId());
        }
        if (!TextUtils.isEmpty(table.getVariant())) {
            builder.append(" / ");
            builder.append(table.getVariant());
        }
        builder.append(" / ");
        builder.append(table.isEightDot()
                ? context.getString(R.string.grade_computer)
                : context.getString(R.string.grade_table, table.getGrade()));
        builder.append(" / ");
        builder.append(table.getId());
        return builder.toString();
    }

    static String formatWizardLabel(Context context, TableInfo table) {
        if (context == null || table == null) {
            return "";
        }
        Locale locale = table.getLocale() == null ? Locale.getDefault() : table.getLocale();
        String language = locale.getDisplayLanguage(locale);
        String country = locale.getDisplayCountry(locale);
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(language)) {
            builder.append(language);
        }
        if (!TextUtils.isEmpty(country)) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(country);
        }
        if (!table.isEightDot()) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(context.getString(R.string.grade_table, table.getGrade()));
        } else {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(context.getString(R.string.grade_computer));
        }
        builder.append(" / ").append(table.getId());
        return builder.toString();
    }
}
