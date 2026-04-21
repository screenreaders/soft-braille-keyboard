/*
 * Copyright (C) 2026 The Soft Braille Keyboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dalton.braillekeyboard;

import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.Display;

import java.util.ArrayList;
import java.util.List;

final class BrailleAccessibilityRenderUtils {
    private BrailleAccessibilityRenderUtils() {
    }

    static CharSequence buildRenderedText(Context context,
            AccessibilityNodeInfo node, AccessibilityEvent event) {
        if (node == null) {
            return context.getString(R.string.braille_service_no_focus);
        }
        if (node.isPassword()) {
            return context.getString(R.string.braille_service_password_field);
        }
        CharSequence label = node.getContentDescription();
        CharSequence value = node.getText();
        boolean valueFromEvent = false;
        if (TextUtils.isEmpty(value) && event != null && event.getText() != null
                && !event.getText().isEmpty()) {
            value = TextUtils.join(" ", event.getText());
            valueFromEvent = true;
        }
        List<CharSequence> parts = new ArrayList<CharSequence>();
        CharSequence liveRegionAnnouncement = buildLiveRegionAnnouncement(context,
                node, event);
        addPart(parts, liveRegionAnnouncement);
        addPart(parts, describeNodeRole(context, node));
        addPart(parts, buildPrimaryLabelAndValue(context, node, label, value,
                valueFromEvent, liveRegionAnnouncement));
        addPart(parts, buildSecondaryMetadata(context, node));
        if (parts.isEmpty()) {
            if (node.isEditable()) {
                return context.getString(R.string.braille_service_empty_field);
            }
            CharSequence className = node.getClassName();
            return TextUtils.isEmpty(className)
                    ? context.getString(R.string.braille_service_no_focus)
                    : className;
        }
        return TextUtils.join(". ", parts);
    }

    static int getCursorPosition(AccessibilityNodeInfo node,
            CharSequence renderedText) {
        if (node == null || renderedText == null) {
            return 0;
        }
        int selection = node.getTextSelectionStart();
        if (selection >= 0) {
            CharSequence text = node.getText();
            if (!TextUtils.isEmpty(text)) {
                String rendered = renderedText.toString();
                int offset = rendered.lastIndexOf(text.toString());
                if (offset >= 0) {
                    int inText = Math.min(selection, text.length());
                    return Math.min(rendered.length(), offset + inText);
                }
            }
            if (selection <= renderedText.length()) {
                return selection;
            }
        }
        return renderedText.length();
    }

    static String buildStatusText(Context context, int connectionState,
            String address, String tableOverride) {
        String tableText = tableOverride == null
                ? context.getString(R.string.braille_profile_table_global)
                : tableOverride;
        return context.getString(R.string.braille_service_status_template,
                formatConnectionState(context, connectionState),
                address == null
                        ? context.getString(R.string.braille_profile_no_device)
                        : address,
                tableText);
    }

    static String interpretInputEvent(Context context, BrailleInputEvent event) {
        if (event == null) {
            return context.getString(R.string.braille_command_waiting);
        }
        String command = BrailleInputEvent.commandToString(event.getCommand());
        switch (event.getCommand()) {
        case BrailleInputEvent.CMD_NAV_PAN_LEFT:
            return command + ": "
                    + context.getString(R.string.braille_command_pan_left);
        case BrailleInputEvent.CMD_NAV_PAN_RIGHT:
            return command + ": "
                    + context.getString(R.string.braille_command_pan_right);
        case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
            return command + ": "
                    + context.getString(R.string.braille_command_focus_previous);
        case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
            return command + ": "
                    + context.getString(R.string.braille_command_focus_next);
        case BrailleInputEvent.CMD_NAV_LINE_PREVIOUS:
            return command + ": "
                    + context.getString(R.string.braille_command_line_previous);
        case BrailleInputEvent.CMD_NAV_LINE_NEXT:
            return command + ": "
                    + context.getString(R.string.braille_command_line_next);
        case BrailleInputEvent.CMD_SCROLL_BACKWARD:
            return command + ": "
                    + context.getString(R.string.braille_command_scroll_backward);
        case BrailleInputEvent.CMD_SCROLL_FORWARD:
            return command + ": "
                    + context.getString(R.string.braille_command_scroll_forward);
        case BrailleInputEvent.CMD_NAV_TOP:
            return command + ": "
                    + context.getString(R.string.braille_command_nav_top);
        case BrailleInputEvent.CMD_NAV_BOTTOM:
            return command + ": "
                    + context.getString(R.string.braille_command_nav_bottom);
        case BrailleInputEvent.CMD_SECTION_NEXT:
            return command + ": "
                    + context.getString(R.string.braille_command_section_next);
        case BrailleInputEvent.CMD_SECTION_PREVIOUS:
            return command + ": "
                    + context.getString(R.string.braille_command_section_previous);
        case BrailleInputEvent.CMD_CONTROL_NEXT:
            return command + ": "
                    + context.getString(R.string.braille_command_control_next);
        case BrailleInputEvent.CMD_CONTROL_PREVIOUS:
            return command + ": "
                    + context.getString(R.string.braille_command_control_previous);
        case BrailleInputEvent.CMD_LIST_NEXT:
            return command + ": "
                    + context.getString(R.string.braille_command_list_next);
        case BrailleInputEvent.CMD_LIST_PREVIOUS:
            return command + ": "
                    + context.getString(R.string.braille_command_list_previous);
        case BrailleInputEvent.CMD_ROUTE:
            return context.getString(R.string.braille_command_route_template,
                    event.getArgument());
        case BrailleInputEvent.CMD_BRAILLE_KEY:
            return context.getString(R.string.braille_command_dots_template,
                    formatDots(context, event.getArgument()));
        case BrailleInputEvent.CMD_KEY_DEL:
            return command + ": "
                    + context.getString(R.string.braille_command_delete_backward);
        case BrailleInputEvent.CMD_KEY_FORWARD_DEL:
            return command + ": "
                    + context.getString(R.string.braille_command_delete_forward);
        case BrailleInputEvent.CMD_KEY_ENTER:
            return command + ": "
                    + context.getString(R.string.braille_command_insert_newline);
        case BrailleInputEvent.CMD_GLOBAL_BACK:
            return command + ": " + context.getString(R.string.braille_command_back);
        case BrailleInputEvent.CMD_GLOBAL_HOME:
            return command + ": " + context.getString(R.string.braille_command_home);
        case BrailleInputEvent.CMD_GLOBAL_RECENTS:
            return command + ": "
                    + context.getString(R.string.braille_command_recents);
        case BrailleInputEvent.CMD_GLOBAL_NOTIFICATIONS:
            return command + ": "
                    + context.getString(R.string.braille_command_notifications);
        case BrailleInputEvent.CMD_TOGGLE_BRAILLE_GRADE:
            return command + ": "
                    + context.getString(R.string.braille_command_toggle_grade);
        default:
            return command;
        }
    }

    static String formatDots(Context context, int dotsMask) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if ((dotsMask & (1 << i)) != 0) {
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(i + 1);
            }
        }
        return builder.length() == 0 ? context.getString(R.string.blank)
                : builder.toString();
    }

    private static String formatConnectionState(Context context, int state) {
        switch (state) {
        case Display.STATE_CONNECTED:
            return context.getString(R.string.braille_status_connected);
        case Display.STATE_ERROR:
            return context.getString(R.string.braille_status_error);
        case Display.STATE_NOT_CONNECTED:
        default:
            return context.getString(R.string.braille_status_disconnected);
        }
    }

    private static String describeNodeRole(Context context,
            AccessibilityNodeInfo node) {
        List<String> roles = new ArrayList<String>();
        CharSequence className = node.getClassName();
        String cls = className == null ? "" : className.toString();
        if (BrailleNodeUtils.isHeading(node)) {
            roles.add(context.getString(R.string.braille_role_heading));
        }
        if (BrailleNodeUtils.isLandmark(node)) {
            roles.add(context.getString(R.string.braille_role_landmark));
        }
        if (BrailleNodeUtils.isTable(node)) {
            roles.add(context.getString(R.string.braille_role_table));
        } else if (BrailleNodeUtils.isListLike(node)) {
            roles.add(context.getString(R.string.braille_role_list));
        } else if (node.getCollectionInfo() != null) {
            roles.add(context.getString(R.string.braille_role_collection));
        }
        if (BrailleNodeUtils.isPager(node)) {
            roles.add(context.getString(R.string.braille_role_pager));
        }
        if (node.isEditable()) {
            roles.add(context.getString(R.string.braille_role_edit_text));
        } else if (BrailleNodeUtils.isFormField(node)) {
            roles.add(context.getString(R.string.braille_role_form_field));
        }
        if (BrailleNodeUtils.isLink(node)) {
            roles.add(context.getString(R.string.braille_role_link));
        }
        if (BrailleNodeUtils.isImage(node)) {
            roles.add(context.getString(R.string.braille_role_image));
        }
        if (BrailleNodeUtils.isTab(node)) {
            roles.add(context.getString(R.string.braille_role_tab));
        }
        if (BrailleNodeUtils.isSlider(node)) {
            roles.add(context.getString(R.string.braille_role_slider));
        } else if (BrailleNodeUtils.isProgressIndicator(node)) {
            roles.add(context.getString(R.string.braille_role_progress));
        }
        if (node.isCheckable()) {
            roles.add(node.isChecked()
                    ? context.getString(R.string.braille_role_checked)
                    : context.getString(R.string.braille_role_unchecked));
        }
        if (cls.contains("Button")) {
            roles.add(context.getString(R.string.braille_role_button));
        } else if (cls.contains("WebView")) {
            roles.add(context.getString(R.string.braille_role_webview));
        } else if (node.isClickable()) {
            roles.add(context.getString(R.string.braille_role_control));
        }
        return TextUtils.join(", ", roles);
    }

    private static CharSequence buildPrimaryLabelAndValue(Context context,
            AccessibilityNodeInfo node, CharSequence label, CharSequence value,
            boolean valueFromEvent, CharSequence liveRegionAnnouncement) {
        if (node == null) {
            return null;
        }
        if (valueFromEvent && !TextUtils.isEmpty(liveRegionAnnouncement)
                && !TextUtils.isEmpty(value)
                && liveRegionAnnouncement.toString().contains(value.toString())) {
            value = null;
        }
        if (!TextUtils.isEmpty(label) && !TextUtils.isEmpty(value)
                && !TextUtils.equals(label, value)) {
            return label + ": " + value;
        }
        if (!TextUtils.isEmpty(value)) {
            return value;
        }
        if (!TextUtils.isEmpty(label)) {
            return label;
        }
        CharSequence hint = BrailleNodeUtils.getHintText(node);
        if (!TextUtils.isEmpty(hint)) {
            return context.getString(R.string.braille_service_hint_template, hint);
        }
        if (node.isEditable()) {
            return context.getString(R.string.braille_service_empty_field);
        }
        return null;
    }

    private static CharSequence buildSecondaryMetadata(Context context,
            AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        List<CharSequence> metadata = new ArrayList<CharSequence>();
        if (!node.isEnabled()) {
            metadata.add(context.getString(R.string.braille_state_disabled));
        }
        if (node.isSelected()) {
            metadata.add(context.getString(R.string.braille_state_selected));
        }
        if (BrailleNodeUtils.hasAction(node,
                AccessibilityNodeInfo.ACTION_COLLAPSE)) {
            metadata.add(context.getString(R.string.braille_state_expanded));
        } else if (BrailleNodeUtils.hasAction(node,
                AccessibilityNodeInfo.ACTION_EXPAND)) {
            metadata.add(context.getString(R.string.braille_state_collapsed));
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
                && node.isScreenReaderFocusable()) {
            metadata.add(context.getString(
                    R.string.braille_state_reader_focusable));
        }
        CharSequence paneTitle = android.os.Build.VERSION.SDK_INT
                >= android.os.Build.VERSION_CODES.P ? node.getPaneTitle() : null;
        if (!TextUtils.isEmpty(paneTitle)) {
            metadata.add(context.getString(R.string.braille_service_pane_template,
                    paneTitle));
        }
        CharSequence error = node.getError();
        if (!TextUtils.isEmpty(error)) {
            metadata.add(context.getString(R.string.braille_service_error_template,
                    error));
        }
        CharSequence collection = BrailleNodeUtils.buildCollectionDescription(
                context, node);
        if (!TextUtils.isEmpty(collection)) {
            metadata.add(collection);
        }
        CharSequence progress = BrailleNodeUtils.buildRangeDescription(context,
                node);
        if (!TextUtils.isEmpty(progress)) {
            metadata.add(progress);
        }
        return metadata.isEmpty() ? null : TextUtils.join(", ", metadata);
    }

    private static CharSequence buildLiveRegionAnnouncement(Context context,
            AccessibilityNodeInfo node, AccessibilityEvent event) {
        if (!BrailleNodeUtils.isLiveRegionEvent(node, event) || event == null
                || event.getText() == null || event.getText().isEmpty()) {
            return null;
        }
        CharSequence announcement = TextUtils.join(" ", event.getText());
        return TextUtils.isEmpty(announcement)
                ? null
                : context.getString(R.string.braille_service_live_region_template,
                        announcement);
    }

    private static void addPart(List<CharSequence> parts, CharSequence value) {
        if (!TextUtils.isEmpty(value)) {
            parts.add(value);
        }
    }
}
