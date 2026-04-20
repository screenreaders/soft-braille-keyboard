package com.dalton.braillekeyboard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.eyesfree.braille.translate.TableInfo;

import java.util.ArrayList;
import java.util.List;

public class BrailleProfilesActivity extends Activity
        implements BrailleParser.BrailleParserListener {
    private BrailleParser brailleParser;
    private Spinner profileSpinner;
    private EditText profileNameInput;
    private TextView currentProfileView;
    private TextView currentSettingsView;
    private ArrayAdapter<String> profileAdapter;
    private final List<String> profileNames = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_braille_profiles);
        setTitle(R.string.braille_profiles_title);

        profileSpinner = (Spinner) findViewById(R.id.braille_profile_spinner);
        profileNameInput = (EditText) findViewById(R.id.braille_profile_name_input);
        currentProfileView = (TextView) findViewById(R.id.braille_profile_active_status);
        currentSettingsView = (TextView) findViewById(
                R.id.braille_profile_current_settings);

        profileAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, profileNames);
        profileAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(profileAdapter);

        brailleParser = new BrailleParser(this, this);
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (brailleParser != null) {
            brailleParser.destroy();
            brailleParser = null;
        }
    }

    @Override
    public void onTranslatorReady(int status) {
        refreshUi();
    }

    public void onSaveCurrentProfile(View view) {
        String name = profileNameInput == null ? null
                : profileNameInput.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, R.string.braille_profiles_name_required,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (BrailleUserProfiles.saveCurrentProfile(this, name)) {
            Toast.makeText(this, getString(
                    R.string.braille_profiles_saved, name), Toast.LENGTH_LONG)
                    .show();
            refreshUi();
            setSelectedProfile(name);
        } else {
            Toast.makeText(this, R.string.braille_profiles_save_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    public void onApplySelectedProfile(View view) {
        String name = getSelectedProfileName();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, R.string.braille_profiles_none,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (BrailleUserProfiles.applyProfile(this, name)) {
            if (brailleParser != null) {
                brailleParser.setTranslator(this);
            }
            Toast.makeText(this, getString(
                    R.string.braille_profiles_applied, name), Toast.LENGTH_LONG)
                    .show();
            refreshUi();
        } else {
            Toast.makeText(this, R.string.braille_profiles_apply_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    public void onDeleteSelectedProfile(View view) {
        String name = getSelectedProfileName();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, R.string.braille_profiles_none,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (BrailleUserProfiles.deleteProfile(this, name)) {
            Toast.makeText(this, getString(
                    R.string.braille_profiles_deleted, name), Toast.LENGTH_LONG)
                    .show();
            refreshUi();
        } else {
            Toast.makeText(this, R.string.braille_profiles_delete_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    public void onCycleToNextProfile(View view) {
        BrailleUserProfiles.Profile profile = BrailleUserProfiles
                .cycleToNextProfile(this);
        if (profile == null) {
            Toast.makeText(this, R.string.braille_profiles_none,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (brailleParser != null) {
            brailleParser.setTranslator(this);
        }
        Toast.makeText(this, getString(R.string.braille_profiles_applied,
                profile.name), Toast.LENGTH_LONG).show();
        refreshUi();
        setSelectedProfile(profile.name);
    }

    public void onOpenBrailleSettings(View view) {
        startActivity(new Intent(this, PreferenceIME.class));
    }

    private void refreshUi() {
        refreshProfileList();
        bindCurrentProfile();
        bindCurrentSettings();
    }

    private void refreshProfileList() {
        String activeProfile = BrailleUserProfiles.getActiveProfileName(this);
        profileNames.clear();
        for (BrailleUserProfiles.Profile profile
                : BrailleUserProfiles.getProfiles(this)) {
            profileNames.add(profile.name);
        }
        profileAdapter.notifyDataSetChanged();
        if (!TextUtils.isEmpty(activeProfile)) {
            setSelectedProfile(activeProfile);
        } else if (!profileNames.isEmpty()) {
            profileSpinner.setSelection(0);
        }
    }

    private void bindCurrentProfile() {
        String activeProfile = BrailleUserProfiles.getActiveProfileName(this);
        currentProfileView.setText(TextUtils.isEmpty(activeProfile)
                ? getString(R.string.braille_profiles_active_none)
                : getString(R.string.braille_profiles_active_value,
                        activeProfile));
        if (profileNameInput != null && TextUtils.isEmpty(profileNameInput.getText())) {
            profileNameInput.setText(activeProfile == null ? "" : activeProfile);
        }
    }

    private void bindCurrentSettings() {
        BrailleParser.BrailleType brailleType = brailleParser == null
                ? BrailleParser.BrailleType.LITERARY
                : brailleParser.getBrailleType(this);
        String typeLabel = brailleType == BrailleParser.BrailleType.COMPUTER
                ? getString(R.string.grade_computer)
                : getString(R.string.grade_literary);
        TableInfo activeTable = brailleParser == null ? null
                : brailleParser.getTable(this);
        String activeTableId = activeTable == null
                ? getString(R.string.no_braille_table)
                : activeTable.getId();
        String literaryId = Options.getStringPreference(this,
                R.string.pref_braille_literary_table_key,
                getString(R.string.pref_braille_table_auto));
        String computerId = Options.getStringPreference(this,
                R.string.pref_braille_computer_table_key,
                getString(R.string.pref_braille_table_auto));
        currentSettingsView.setText(getString(
                R.string.braille_profiles_current_settings_template,
                typeLabel, activeTableId, literaryId, computerId));
    }

    private void setSelectedProfile(String name) {
        if (TextUtils.isEmpty(name)) {
            return;
        }
        for (int i = 0; i < profileNames.size(); i++) {
            if (name.equalsIgnoreCase(profileNames.get(i))) {
                profileSpinner.setSelection(i);
                return;
            }
        }
    }

    private String getSelectedProfileName() {
        Object item = profileSpinner == null ? null
                : profileSpinner.getSelectedItem();
        return item == null ? null : String.valueOf(item);
    }
}
