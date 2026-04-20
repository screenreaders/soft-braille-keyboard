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
import java.util.List;

public final class BrailleDisplayNamedProfiles {
    private static final String PREF_PROFILES_JSON = "brailleDisplayNamedProfilesJson";
    private static final String PREF_ACTIVE_PROFILE = "brailleDisplayNamedActiveProfile";

    public static final class Profile {
        public final String name;
        public final String bundle;

        Profile(String name, String bundle) {
            this.name = name;
            this.bundle = bundle;
        }
    }

    private BrailleDisplayNamedProfiles() {
    }

    public static List<Profile> getProfiles(Context context) {
        List<Profile> result = new ArrayList<Profile>();
        if (context == null) {
            return result;
        }
        String payload = getPreferences(context).getString(PREF_PROFILES_JSON, null);
        if (TextUtils.isEmpty(payload)) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(payload);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", "").trim();
                String bundle = item.optString("bundle", "");
                if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(bundle)) {
                    result.add(new Profile(name, bundle));
                }
            }
        } catch (JSONException e) {
            return new ArrayList<Profile>();
        }
        Collections.sort(result, new Comparator<Profile>() {
            @Override
            public int compare(Profile left, Profile right) {
                return left.name.compareToIgnoreCase(right.name);
            }
        });
        return result;
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
            String bundle = BrailleDisplayPreferences.exportProfileBundle(context);
            if (TextUtils.isEmpty(bundle)) {
                return false;
            }
            List<Profile> profiles = getProfiles(context);
            Profile replacement = new Profile(trimmedName, bundle);
            boolean replaced = false;
            for (int i = 0; i < profiles.size(); i++) {
                if (trimmedName.equalsIgnoreCase(profiles.get(i).name)) {
                    profiles.set(i, replacement);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                profiles.add(replacement);
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
            BrailleDisplayPreferences.ImportResult result =
                    BrailleDisplayPreferences.importProfileBundle(context,
                            profile.bundle);
            if (!result.hadContent) {
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
                item.put("bundle", profile.bundle);
                array.put(item);
            } catch (JSONException e) {
                // Skip malformed serialization and keep other profiles.
            }
        }
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putString(PREF_PROFILES_JSON, array.toString());
        editor.apply();
    }

    private static SharedPreferences getPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
