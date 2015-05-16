/*
 * Copyright 2015 Takagi Katsuyuki
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

package jp.tkgktyk.wearablepad;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.support.v7.widget.Toolbar;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Set;

import butterknife.ButterKnife;
import butterknife.InjectView;
import jp.tkgktyk.wearablepadrlib.BaseSettingsActivity;

/**
 * Created by tkgktyk on 2015/04/27.
 */
public class SettingsActivity extends BaseSettingsActivity {
    @InjectView(R.id.toolbar)
    Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ButterKnife.inject(this);
        setSupportActionBar(mToolbar);

        if (savedInstanceState == null) {
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends BaseFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_settings);

            updatePreferences();
        }

        private void updatePreferences() {
            // Input Subsystem
            showListSummary(R.string.key_input_subsystem);
            showTextSummary(R.string.key_input_subsystem_ratio_x, getString(R.string.unit_percent));
            showTextSummary(R.string.key_input_subsystem_ratio_y, getString(R.string.unit_percent));
            // Cursor
            showTextSummary(R.string.key_cursor_speed, getString(R.string.unit_percent));
            // Transfer Mode
            // About
            Preference about = findPreference(R.string.key_about);
            about.setSummary(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
        }

        @Override
        public void onResume() {
            super.onResume();

            loadPairedDevices();
        }

        private void loadPairedDevices() {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> pairedDevices = ba.getBondedDevices();
            ArrayList<String> entries = Lists.newArrayList();
            ArrayList<String> entryValues = Lists.newArrayList();
            if (pairedDevices.isEmpty()) {
                entries.add(getString(R.string.none));
                entryValues.add("");
            } else {
                for (BluetoothDevice device : pairedDevices) {
                    String entry = device.getName() + " / " + device.getAddress();
                    entries.add(entry);
                    entryValues.add(entry);
                }
            }
            ListPreference destination = (ListPreference)
                    findPreference(R.string.key_transfer_mode_destination);
            destination.setEntries(entries.toArray(new String[0]));
            destination.setEntryValues(entryValues.toArray(new String[0]));
            destination.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    MyApp.showToast(R.string.note_transfer_mode_destination);
                    return true;
                }
            });
            // update summary
            showListSummary(R.string.key_transfer_mode_destination);
        }
    }
}
