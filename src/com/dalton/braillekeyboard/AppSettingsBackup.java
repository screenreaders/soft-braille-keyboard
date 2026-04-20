package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class AppSettingsBackup {
    private AppSettingsBackup() {
    }

    public static String exportPreferences(Context context) throws JSONException {
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        Map<String, ?> all = preferences.getAll();
        JSONObject root = new JSONObject();
        root.put("format", 1);
        root.put("package", context.getPackageName());
        JSONObject values = new JSONObject();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            JSONObject item = new JSONObject();
            Object value = entry.getValue();
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
                JSONArray array = new JSONArray();
                for (Object setValue : (Set<?>) value) {
                    if (setValue != null) {
                        array.put(String.valueOf(setValue));
                    }
                }
                item.put("value", array);
            } else {
                item.put("type", "string");
                item.put("value", value == null ? JSONObject.NULL : value);
            }
            values.put(entry.getKey(), item);
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
            String type = item.optString("type", "string");
            if ("boolean".equals(type)) {
                editor.putBoolean(key, item.optBoolean("value"));
                restored++;
            } else if ("int".equals(type)) {
                editor.putString(key, String.valueOf(item.optInt("value")));
                restored++;
            } else if ("long".equals(type)) {
                editor.putLong(key, item.optLong("value"));
                restored++;
            } else if ("float".equals(type)) {
                editor.putFloat(key, (float) item.optDouble("value"));
                restored++;
            } else if ("string_set".equals(type)) {
                java.util.HashSet<String> set = new java.util.HashSet<String>();
                JSONArray array = item.optJSONArray("value");
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        String value = array.optString(i, null);
                        if (value != null) {
                            set.add(value);
                        }
                    }
                }
                editor.putStringSet(key, set);
                restored++;
            } else {
                editor.putString(key, item.optString("value", ""));
                restored++;
            }
        }
        editor.apply();
        return restored;
    }
}
