package com.dalton.braillekeyboard;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BrailleNodeUtils {
    private BrailleNodeUtils() {
    }

    static boolean isHeading(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && node.isHeading()) {
            return true;
        }
        AccessibilityNodeInfo.CollectionItemInfo itemInfo = node.getCollectionItemInfo();
        return itemInfo != null && itemInfo.isHeading();
    }

    static boolean isLandmark(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        String viewId = node.getViewIdResourceName();
        if (TextUtils.isEmpty(viewId)) {
            return false;
        }
        String normalized = viewId.toLowerCase(Locale.ROOT);
        return normalized.contains("toolbar")
                || normalized.contains("appbar")
                || normalized.contains("navigation")
                || normalized.contains("header")
                || normalized.contains("footer")
                || normalized.contains("main")
                || normalized.contains("search");
    }

    static boolean isDialogOrPane(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence className = node.getClassName();
        String cls = className == null ? "" : className.toString();
        if (cls.contains("Dialog")) {
            return true;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && !TextUtils.isEmpty(node.getPaneTitle());
    }

    static boolean isTable(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        AccessibilityNodeInfo.CollectionInfo collectionInfo = node.getCollectionInfo();
        return collectionInfo != null
                && collectionInfo.getRowCount() > 1
                && collectionInfo.getColumnCount() > 1;
    }

    static boolean isListLike(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence className = node.getClassName();
        String cls = className == null ? "" : className.toString();
        if (cls.contains("ListView") || cls.contains("RecyclerView")
                || cls.contains("GridView")) {
            return true;
        }
        AccessibilityNodeInfo.CollectionInfo collectionInfo = node.getCollectionInfo();
        return collectionInfo != null
                && (collectionInfo.getRowCount() > 1
                        || collectionInfo.getColumnCount() > 1);
    }

    static boolean isPager(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence className = node.getClassName();
        String cls = className == null ? "" : className.toString();
        return cls.contains("ViewPager") || cls.contains("Pager");
    }

    static boolean isFormField(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence className = node.getClassName();
        String cls = className == null ? "" : className.toString();
        return node.isEditable()
                || cls.contains("EditText")
                || cls.contains("Spinner")
                || cls.contains("CheckBox")
                || cls.contains("RadioButton")
                || cls.contains("Switch")
                || cls.contains("ToggleButton");
    }

    static boolean isLink(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        String viewId = node.getViewIdResourceName();
        if (!TextUtils.isEmpty(viewId)
                && viewId.toLowerCase(Locale.ROOT).contains("link")) {
            return true;
        }
        CharSequence className = node.getClassName();
        String cls = className == null ? "" : className.toString();
        return cls.contains("Link")
                || (cls.contains("TextView") && node.isClickable()
                        && !TextUtils.isEmpty(node.getText()));
    }

    static boolean isImage(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence className = node.getClassName();
        String cls = className == null ? "" : className.toString();
        return cls.contains("ImageView");
    }

    static boolean isTab(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence className = node.getClassName();
        String cls = className == null ? "" : className.toString();
        if (cls.contains("Tab")) {
            return true;
        }
        String viewId = node.getViewIdResourceName();
        return !TextUtils.isEmpty(viewId)
                && viewId.toLowerCase(Locale.ROOT).contains("tab");
    }

    static boolean isSlider(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence className = node.getClassName();
        String cls = className == null ? "" : className.toString();
        if (cls.contains("SeekBar") || cls.contains("Slider")) {
            return true;
        }
        return node.getRangeInfo() != null && node.isFocusable();
    }

    static boolean isProgressIndicator(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence className = node.getClassName();
        String cls = className == null ? "" : className.toString();
        return cls.contains("ProgressBar") || node.getRangeInfo() != null;
    }

    static boolean isLiveRegionEvent(AccessibilityNodeInfo node, AccessibilityEvent event) {
        if (node == null || event == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && node.getLiveRegion() != View.ACCESSIBILITY_LIVE_REGION_NONE) {
            return true;
        }
        return event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && (event.getContentChangeTypes()
                        & AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT) != 0;
    }

    static CharSequence getHintText(AccessibilityNodeInfo node) {
        if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return node.getHintText();
        }
        return null;
    }

    static boolean hasAction(AccessibilityNodeInfo node, int action) {
        if (node == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
            if (actions == null) {
                return false;
            }
            for (AccessibilityNodeInfo.AccessibilityAction candidate : actions) {
                if (candidate != null && candidate.getId() == action) {
                    return true;
                }
            }
            return false;
        }
        return (node.getActions() & action) != 0;
    }

    static CharSequence buildCollectionDescription(Context context,
            AccessibilityNodeInfo node) {
        if (context == null || node == null) {
            return null;
        }
        AccessibilityNodeInfo.CollectionInfo collectionInfo = node.getCollectionInfo();
        AccessibilityNodeInfo.CollectionItemInfo itemInfo = node.getCollectionItemInfo();
        List<CharSequence> parts = new ArrayList<CharSequence>();
        if (collectionInfo != null) {
            int rows = collectionInfo.getRowCount();
            int columns = collectionInfo.getColumnCount();
            if (rows > 0 || columns > 0) {
                parts.add(context.getString(R.string.braille_service_collection_template,
                        Math.max(rows, 0), Math.max(columns, 0)));
            }
        }
        if (itemInfo != null) {
            parts.add(context.getString(R.string.braille_service_position_template,
                    Math.max(0, itemInfo.getRowIndex()) + 1,
                    Math.max(0, itemInfo.getColumnIndex()) + 1));
            if (itemInfo.isHeading()) {
                parts.add(context.getString(R.string.braille_role_heading));
            }
        }
        return parts.isEmpty() ? null : TextUtils.join(", ", parts);
    }

    static CharSequence buildRangeDescription(Context context,
            AccessibilityNodeInfo node) {
        if (context == null || node == null || node.getRangeInfo() == null) {
            return null;
        }
        AccessibilityNodeInfo.RangeInfo rangeInfo = node.getRangeInfo();
        int current = Math.round(rangeInfo.getCurrent());
        int max = Math.round(rangeInfo.getMax());
        if (max <= 0) {
            return null;
        }
        return context.getString(R.string.braille_service_progress_template, current, max);
    }
}
