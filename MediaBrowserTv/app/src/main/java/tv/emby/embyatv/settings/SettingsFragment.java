package tv.emby.embyatv.settings;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Fragment;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.widget.TextView;

import org.acra.ACRA;

import java.io.IOException;

import tv.emby.embyatv.startup.LogonCredentials;
import tv.emby.embyatv.R;
import tv.emby.embyatv.TvApp;
import tv.emby.embyatv.util.Utils;


/**
 * A simple {@link Fragment} subclass.
 */
public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set version info
        TextView ver = (TextView) getActivity().findViewById(R.id.settings_version_info);
        ver.setText(Utils.VersionString() + " " + TvApp.getApplication().getRegistrationString());

        // hide these options for now - will be removed if all is okay
        PreferenceCategory cat = (PreferenceCategory) findPreference("pref_playback_category");
        cat.removePreference(findPreference("pref_enable_vlc"));
        cat.removePreference(findPreference("pref_allow_vlc_transcode"));
        cat.removePreference(findPreference("pref_vlc_max_res"));
        cat.removePreference(findPreference("pref_net_buffer"));
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); ++i) {
            Preference preference = getPreferenceScreen().getPreference(i);
            if (preference instanceof PreferenceGroup) {
                PreferenceGroup preferenceGroup = (PreferenceGroup) preference;
                for (int j = 0; j < preferenceGroup.getPreferenceCount(); ++j) {
                    updatePreference(preferenceGroup.getPreference(j));
                }
            } else {
                updatePreference(preference);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        //If logon setting is changed to this user, save credentials
        if (key.equals("pref_login_behavior")) {
            ListPreference listPreference = (ListPreference) findPreference(key);
            if (listPreference.getValue().equals("1")) {
                try {
                    Utils.SaveLoginCredentials(new LogonCredentials(TvApp.getApplication().getApiClient().getServerInfo(), TvApp.getApplication().getCurrentUser()), "tv.mediabrowser.login.json");
                } catch (IOException e) {
                    e.printStackTrace();
                    ACRA.getErrorReporter().handleException(e);
                }
            }
        }

        updatePreference(findPreference(key));
    }

    private void updatePreference(Preference preference) {
        if (preference instanceof ListPreference) {
            ListPreference listPreference = (ListPreference) preference;
            if (listPreference.getKey().equals("pref_login_behavior")) {
                CheckBoxPreference pwPrompt = (CheckBoxPreference) findPreference("pref_auto_pw_prompt");
                pwPrompt.setEnabled(TvApp.getApplication().getConfiguredAutoCredentials().getUserDto().getHasPassword());
                String extra = "";
                if (listPreference.getValue().equals("1")) {
                    if (!TvApp.getApplication().getConfiguredAutoCredentials().getUserDto().getId().equals(TvApp.getApplication().getCurrentUser().getId())) {
                        listPreference.setEnabled(false);
                        pwPrompt.setEnabled(false);
                        extra = getActivity().getString(R.string.lbl_paren_login_as)+TvApp.getApplication().getConfiguredAutoCredentials().getUserDto().getName()+getActivity().getString(R.string.lbl_to_change_paren);
                    }
                    listPreference.setSummary(getActivity().getString(R.string.lbl_login_as) + TvApp.getApplication().getConfiguredAutoCredentials().getUserDto().getName() + extra);
                } else {
                    listPreference.setSummary(listPreference.getEntry());
                    pwPrompt.setEnabled(false);
                }
            } else {
                listPreference.setSummary(listPreference.getEntry());
            }
        }

        if (preference instanceof CheckBoxPreference) {
            CheckBoxPreference cb = (CheckBoxPreference) preference;
            if (cb.getKey().equals("pref_enable_vlc")) {
                // enable other vlc only options
                ListPreference buffer = (ListPreference) findPreference("pref_net_buffer");
                buffer.setEnabled(cb.isChecked());
                ListPreference audio = (ListPreference) findPreference("pref_audio_option");
                audio.setEnabled(cb.isChecked());
                ListPreference res = (ListPreference) findPreference("pref_vlc_max_res");
                res.setEnabled(cb.isChecked());
                CheckBoxPreference trans = (CheckBoxPreference) findPreference("pref_allow_vlc_transcode");
                trans.setEnabled(cb.isChecked());
            }
        }
    }
}

