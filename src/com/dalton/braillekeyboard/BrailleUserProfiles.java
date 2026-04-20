package com.dalton.braillekeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BrailleUserProfiles {
    private static final String PREF_PROFILES_JSON = "brailleUserProfilesJson";
    private static final String PREF_ACTIVE_PROFILE = "brailleUserActiveProfile";

    public static final class Profile {
        public final String name;
        public final String payload;

        Profile(String name, String payload) {
            this.name = name;
            this.payload = payload;
        }
    }

    private BrailleUserProfiles() {
    }

    public static List<Profile> getProfiles(Context context) {
        List<Profile> profiles = new ArrayList<Profile>();
        if (context == null) {
            return profiles;
        }
        String payload = getPreferences(context).getString(PREF_PROFILES_JSON, null);
        if (TextUtils.isEmpty(payload)) {
            return profiles;
        }
        try {
            JSONArray array = new JSONArray(payload);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", "").trim();
                String snapshot = item.optString("payload", "");
                if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(snapshot)) {
                    profiles.add(new Profile(name, snapshot));
                }
            }
        } catch (JSONException e) {
            return new ArrayList<Profile>();
        }
        Collections.sort(profiles, new Comparator<Profile>() {
            @Override
            public int compare(Profile left, Profile right) {
                return left.name.compareToIgnoreCase(right.name);
            }
        });
        return profiles;
    }

    public static boolean saveCurrentProfile(Context context, String name) {
        if (context == null || TextUtils.isEmpty(name)) {
            return false;
        }
        String trimmedName = name.trim();
        if (trimmedName.length() == 0) {
            return false;
        }
        try {
            String payload = AppSettingsBackup.exportPreferences(context,
                    buildExcludedKeys());
            if (TextUtils.isEmpty(payload)) {
                return false;
            }
            List<Profile> profiles = getProfiles(context);
            Profile current = new Profile(trimmedName, payload);
            boolean replaced = false;
            for (int i = 0; i < profiles.size(); i++) {
                if (trimmedName.equalsIgnoreCase(profiles.get(i).name)) {
                    profiles.set(i, current);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                profiles.add(current);
            }
            persistProfiles(context, profiles);
            setActiveProfileName(context, trimmedName);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    public static boolean applyProfile(Context context, String name) {
        if (context == null || TextUtils.isEmpty(name)) {
            return false;
        }
        Profile profile = findProfile(context, name);
        if (profile == null) {
            return false;
        }
        try {
            int restored = AppSettingsBackup.importPreferences(context,
                    profile.payload);
            if (restored <= 0) {
                return false;
            }
            setActiveProfileName(context, profile.name);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    public static boolean deleteProfile(Context context, String name) {
        if (context == null || TextUtils.isEmpty(name)) {
            return false;
        }
        List<Profile> profiles = getProfiles(context);
        boolean removed = false;
        for (int i = profiles.size() - 1; i >= 0; i--) {
            if (name.equalsIgnoreCase(profiles.get(i).name)) {
                profiles.remove(i);
                removed = true;
            }
        }
        if (!removed) {
            return false;
        }
        persistProfiles(context, profiles);
        if (TextUtils.equals(getActiveProfileName(context), name)) {
            if (profiles.isEmpty()) {
                clearActiveProfileName(context);
            } else {
                setActiveProfileName(context, profiles.get(0).name);
            }
        }
        return true;
    }

    public static Profile cycleToNextProfile(Context context) {
        List<Profile> profiles = getProfiles(context);
        if (context == null || profiles.isEmpty()) {
            return null;
        }
        String active = getActiveProfileName(context);
        int nextIndex = 0;
        if (!TextUtils.isEmpty(active)) {
            for (int i = 0; i < profiles.size(); i++) {
                if (active.equalsIgnoreCase(profiles.get(i).name)) {
                    nextIndex = (i + 1) % profiles.size();
                    break;
                }
            }
        }
        Profile next = profiles.get(nextIndex);
        return applyProfile(context, next.name) ? next : null;
    }

    public static Profile findProfile(Context context, String name) {
        if (context == null || TextUtils.isEmpty(name)) {
            return null;
        }
        for (Profile profile : getProfiles(context)) {
            if (name.equalsIgnoreCase(profile.name)) {
                return profile;
            }
        }
        return null;
    }

    public static String getActiveProfileName(Context context) {
        if (context == null) {
            return null;
        }
        try {
            return getPreferences(context).getString(PREF_ACTIVE_PROFILE, null);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public static void setActiveProfileName(Context context, String name) {
        if (context == null) {
            return;
        }
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putString(PREF_ACTIVE_PROFILE, name);
        editor.apply();
    }

    public static void clearActiveProfileName(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.remove(PREF_ACTIVE_PROFILE);
        editor.apply();
    }

    private static void persistProfiles(Context context, List<Profile> profiles) {
        JSONArray array = new JSONArray();
        for (Profile profile : profiles) {
            JSONObject item = new JSONObject();
            try {
                item.put("name", profile.name);
                item.put("payload", profile.payload);
                array.put(item);
            } catch (JSONException e) {
                // Skip malformed profile serialization and keep the rest.
            }
        }
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putString(PREF_PROFILES_JSON, array.toString());
        editor.apply();
    }

    private static SharedPreferences getPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static Set<String> buildExcludedKeys() {
        Set<String> excluded = new HashSet<String>();
        excluded.add(PREF_PROFILES_JSON);
        excluded.add(PREF_ACTIVE_PROFILE);
        excluded.add("brailleDisplayNamedProfilesJson");
        excluded.add("brailleDisplayNamedActiveProfile");
        return excluded;
    }
}
