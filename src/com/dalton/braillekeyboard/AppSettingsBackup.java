package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class AppSettingsBackup {
    private AppSettingsBackup() {
    }

    public static String exportPreferences(Context context) throws JSONException {
        return exportPreferences(context, null);
    }

    public static String exportPreferences(Context context, Set<String> excludedKeys)
            throws JSONException {
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        Map<String, ?> all = preferences.getAll();
        JSONObject root = new JSONObject();
        root.put("format", 1);
        root.put("package", context.getPackageName());
        JSONObject values = new JSONObject();
        Set<String> excluded = excludedKeys == null
                ? new HashSet<String>() : new HashSet<String>(excludedKeys);
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            if (excluded.contains(entry.getKey())) {
                continue;
            }
            values.put(entry.getKey(), serializePreferenceValue(entry.getValue()));
        }
        root.put("values", values);
        return root.toString(2);
    }

    public static int importPreferences(Context context, String payload)
            throws JSONException {
        if (payload == null || payload.trim().isEmpty()) {
            return 0;
        }
        JSONObject root = new JSONObject(payload);
        JSONObject values = root.optJSONObject("values");
        if (values == null) {
            return 0;
        }
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        int restored = 0;
        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject item = values.optJSONObject(key);
            if (item == null) {
                continue;
            }
            restored += restorePreferenceValue(editor, key, item) ? 1 : 0;
        }
        editor.apply();
        return restored;
    }

    private static JSONObject serializePreferenceValue(Object value)
            throws JSONException {
        JSONObject item = new JSONObject();
        if (value instanceof Boolean) {
            item.put("type", "boolean");
            item.put("value", ((Boolean) value).booleanValue());
        } else if (value instanceof Integer) {
            item.put("type", "int");
            item.put("value", ((Integer) value).intValue());
        } else if (value instanceof Long) {
            item.put("type", "long");
            item.put("value", ((Long) value).longValue());
        } else if (value instanceof Float) {
            item.put("type", "float");
            item.put("value", ((Float) value).doubleValue());
        } else if (value instanceof Set) {
            item.put("type", "string_set");
            item.put("value", serializeStringSet((Set<?>) value));
        } else {
            item.put("type", "string");
            item.put("value", value == null ? JSONObject.NULL : value);
        }
        return item;
    }

    private static JSONArray serializeStringSet(Set<?> values)
            throws JSONException {
        JSONArray array = new JSONArray();
        for (Object setValue : values) {
            if (setValue != null) {
                array.put(String.valueOf(setValue));
            }
        }
        return array;
    }

    private static boolean restorePreferenceValue(SharedPreferences.Editor editor,
            String key, JSONObject item) {
        String type = item.optString("type", "string");
        if ("boolean".equals(type)) {
            editor.putBoolean(key, item.optBoolean("value"));
            return true;
        }
        if ("int".equals(type)) {
            editor.putInt(key, item.optInt("value"));
            return true;
        }
        if ("long".equals(type)) {
            editor.putLong(key, item.optLong("value"));
            return true;
        }
        if ("float".equals(type)) {
            editor.putFloat(key, (float) item.optDouble("value"));
            return true;
        }
        if ("string_set".equals(type)) {
            editor.putStringSet(key, deserializeStringSet(item.optJSONArray("value")));
            return true;
        }
        editor.putString(key, item.optString("value", ""));
        return true;
    }

    private static Set<String> deserializeStringSet(JSONArray array) {
        Set<String> set = new HashSet<String>();
        if (array == null) {
            return set;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, null);
            if (value != null) {
                set.add(value);
            }
        }
        return set;
    }
}
