/*
 * Copyright (C) 2020 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings;

import android.content.Intent;
import android.os.Bundle;
import android.os.SELinux;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;
import androidx.preference.ListPreference;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.lineageos.settings.R;
import org.lineageos.settings.dirac.DiracUtils;
import org.lineageos.settings.speaker.ClearSpeakerActivity;
import org.lineageos.settings.SuShell;
import org.lineageos.settings.SuTask;

public class DeviceSettingsFragment extends PreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "XiaomiParts";

    private static final String PREF_DIRAC = "dirac_pref";
    private static final String PREF_HEADSET = "dirac_headset_pref";
    private static final String PREF_PRESET = "dirac_preset_pref";
    private static final String PREF_CLEAR_SPEAKER = "clear_speaker_settings";

    private static final String SELINUX_CATEGORY = "selinux";
    private static final String PREF_SELINUX_MODE = "selinux_mode";
    private static final String PREF_SELINUX_PERSISTENCE = "selinux_persistence";

    private SwitchPreference mDiracPref;

    private ListPreference mHeadsetPref;
    private ListPreference mPresetPref;

    private Preference mClearSpeakerPref;

    private DiracUtils mDiracUtils;

    private SwitchPreference mSelinuxMode;
    private SwitchPreference mSelinuxPersistence;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.device_settings);

        mDiracUtils = new DiracUtils(getContext());

        boolean enhancerEnabled = mDiracUtils.isDiracEnabled();

        mDiracPref = (SwitchPreference) findPreference(PREF_DIRAC);
        mDiracPref.setOnPreferenceChangeListener(this);
        mDiracPref.setChecked(enhancerEnabled);

        mHeadsetPref = (ListPreference) findPreference(PREF_HEADSET);
        mHeadsetPref.setOnPreferenceChangeListener(this);
        mHeadsetPref.setEnabled(enhancerEnabled);

        mPresetPref = (ListPreference) findPreference(PREF_PRESET);
        mPresetPref.setOnPreferenceChangeListener(this);
        mPresetPref.setEnabled(enhancerEnabled);

        mClearSpeakerPref = (Preference) findPreference(PREF_CLEAR_SPEAKER);
        mClearSpeakerPref.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity().getApplicationContext(), ClearSpeakerActivity.class);
            startActivity(intent);
            return true;
        });
        // SELinux
        Preference selinuxCategory = findPreference(SELINUX_CATEGORY);
        mSelinuxMode = (SwitchPreference) findPreference(PREF_SELINUX_MODE);
        mSelinuxMode.setChecked(SELinux.isSELinuxEnforced());
        mSelinuxMode.setOnPreferenceChangeListener(this);

        mSelinuxPersistence =
        (SwitchPreference) findPreference(PREF_SELINUX_PERSISTENCE);
        mSelinuxPersistence.setOnPreferenceChangeListener(this);
        mSelinuxPersistence.setChecked(getContext()
        .getSharedPreferences("selinux_pref", Context.MODE_PRIVATE)
        .contains(PREF_SELINUX_MODE));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
            case PREF_DIRAC:
                mDiracUtils.setEnabled((boolean) newValue);
                setDiracEnabled((boolean) newValue);
                return true;
            case PREF_HEADSET:
                mDiracUtils.setHeadsetType(Integer.parseInt(newValue.toString()));
                return true;
            case PREF_PRESET:
                mDiracUtils.setLevel(String.valueOf(newValue));
                return true;

            case PREF_SELINUX_MODE:
                if (preference == mSelinuxMode) {
		              boolean enabled = (Boolean) newValue;
                  new SwitchSelinuxTask(getActivity()).execute(enabled);
                  setSelinuxEnabled(enabled, mSelinuxPersistence.isChecked());
                  return true;
                } else if (preference == mSelinuxPersistence) {
                  setSelinuxEnabled(mSelinuxMode.isChecked(), (Boolean) newValue);
                  return true;
                }

                break;

             default:
                return false;
        }
        return true;
    }

    private void setDiracEnabled(boolean enabled) {
        mDiracPref.setChecked(enabled);
        mHeadsetPref.setEnabled(enabled);
        mPresetPref.setEnabled(enabled);
    }

    private void setSelinuxEnabled(boolean status, boolean persistent) {
      SharedPreferences.Editor editor = getContext()
          .getSharedPreferences("selinux_pref", Context.MODE_PRIVATE).edit();
      if (persistent) {
        editor.putBoolean(PREF_SELINUX_MODE, status);
      } else {
        editor.remove(PREF_SELINUX_MODE);
      }
      editor.apply();
      mSelinuxMode.setChecked(status);
    }

    private class SwitchSelinuxTask extends SuTask<Boolean> {
      public SwitchSelinuxTask(Context context) {
        super(context);
      }
      @Override
      protected void sudoInBackground(Boolean... params) throws SuShell.SuDeniedException {
        if (params.length != 1) {
          return;
        }
        if (params[0]) {
          SuShell.runWithSuCheck("setenforce 1");
        } else {
          SuShell.runWithSuCheck("setenforce 0");
        }
      }

      @Override
      protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        if (!result) {
          // Did not work, so restore actual value
          setSelinuxEnabled(SELinux.isSELinuxEnforced(), mSelinuxPersistence.isChecked());
        }
      }
    }
}
