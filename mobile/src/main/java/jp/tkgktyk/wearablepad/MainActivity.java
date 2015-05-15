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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import butterknife.ButterKnife;
import butterknife.InjectView;
import jp.tkgktyk.wearablepad.util.SwitchPreference;

/**
 * Created by tkgktyk on 2015/04/27.
 */
public class MainActivity extends AppCompatActivity {
    @InjectView(R.id.toolbar)
    Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_toolbar);

        ButterKnife.inject(this);
        setSupportActionBar(mToolbar);

        if (savedInstanceState == null) {
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, new SettingsFragment())
                    .commit();
        }
    }

    public static class BaseFragment extends PreferenceFragment {

        protected Preference findPreference(@StringRes int id) {
            return findPreference(getString(id));
        }

        protected void showListSummary(@StringRes int id) {
            showListSummary(id, null);
        }

        protected void showListSummary(@StringRes int id,
                                       @Nullable final Preference.OnPreferenceChangeListener extraListener) {
            ListPreference list = (ListPreference) findPreference(id);
            list.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setListSummary((ListPreference) preference, (String) newValue);
                    if (extraListener != null) {
                        return extraListener.onPreferenceChange(preference, newValue);
                    }
                    return true;
                }
            });
            // pre-perform
            list.getOnPreferenceChangeListener().onPreferenceChange(list, list.getValue());
        }

        private void setListSummary(ListPreference pref, String value) {
            int index = pref.findIndexOfValue(value);
            CharSequence entry;
            if (index != -1) {
                entry = pref.getEntries()[index];
            } else {
                entry = getString(R.string.not_selected);
            }
            pref.setSummary(getString(R.string.current_s1, entry));
        }

        protected void showTextSummary(@StringRes int id) {
            showTextSummary(id, null);
        }

        protected void showTextSummary(@StringRes int id, @Nullable final String suffix) {
            EditTextPreference et = (EditTextPreference) findPreference(id);
            et.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String value = (String) newValue;
                    if (!Strings.isNullOrEmpty(suffix)) {
                        value += suffix;
                    }
                    preference.setSummary(getString(R.string.current_s1, value));
                    return true;
                }
            });
            et.getOnPreferenceChangeListener().onPreferenceChange(et,
                    et.getText());
        }

        protected void setUpSwitch(@StringRes int id, final OnSwitchChangedListener listener) {
            final SwitchPreference sw = (SwitchPreference) findPreference(id);
            sw.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean enabled = (Boolean) newValue;
                    listener.onChanged(sw, enabled);
                    return true;
                }
            });
            sw.getOnPreferenceChangeListener().onPreferenceChange(sw, sw.isChecked());
        }

        protected interface OnSwitchChangedListener {
            void onChanged(SwitchPreference sw, boolean enabled);
        }

        protected void openActivity(@StringRes int id, final Class<?> cls) {
            openActivity(id, cls, null);
        }

        protected void openActivity(@StringRes int id, final Class<?> cls, final ExtendsPutter putter) {
            Preference pref = findPreference(id);
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent activity = new Intent(preference.getContext(), cls);
                    if (putter != null) {
                        putter.putExtends(activity);
                    }
                    startActivity(activity);
                    return true;
                }
            });
        }

        protected interface ExtendsPutter {
            void putExtends(Intent activityIntent);
        }
    }

    public static class SettingsFragment extends BaseFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            boolean locked = lockTransferMode();
            addPreferencesFromResource(R.xml.pref_settings);

            updatePreferences();
            if (locked) {
                findPreference(R.string.key_transfer_mode_transfer_enabled).setEnabled(false);
                findPreference(R.string.key_transfer_mode_receiver_enabled).setEnabled(false);
            }
        }

        private void updatePreferences() {
            // Input Subsystem
            showListSummary(R.string.key_input_subsystem);
            showTextSummary(R.string.key_input_subsystem_ratio_x, getString(R.string.unit_percent));
            showTextSummary(R.string.key_input_subsystem_ratio_y, getString(R.string.unit_percent));
            // Cursor
            showTextSummary(R.string.key_cursor_speed, getString(R.string.unit_percent));
            // Transfer Mode
            setUpSwitch(R.string.key_transfer_mode_receiver_enabled, new OnSwitchChangedListener() {
                @Override
                public void onChanged(SwitchPreference sw, boolean enabled) {
                    if (enabled) {
                        BluetoothReceiverService.startService(getActivity());
                    } else {
                        BluetoothReceiverService.stopService(getActivity());
                    }
                }
            });
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
            // update summary
            showListSummary(R.string.key_transfer_mode_destination);
        }

        private boolean lockTransferMode() {
            try {
                ApplicationInfo ai = getActivity().getPackageManager()
                        .getApplicationInfo(getActivity().getPackageName(), 0);
                ZipFile zf = new ZipFile(ai.sourceDir);
                ZipEntry ze = zf.getEntry("classes.dex");
                long time = ze.getTime();
                zf.close();
                long elapsed = System.currentTimeMillis() - time;
                if (elapsed >= (long) (60 * 60 * 24 * 30) * 1000) {
                    // lock transfer mode
                    PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                            .putBoolean(getString(R.string.key_transfer_mode_transfer_enabled), false)
                            .putBoolean(getString(R.string.key_transfer_mode_receiver_enabled), false)
                            .commit();
                    return true;
                }
            } catch (Exception e) {
            }
            return false;
        }
    }
}
