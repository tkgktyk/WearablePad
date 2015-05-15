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

import android.content.pm.ApplicationInfo;
import android.preference.PreferenceManager;

import eu.chainfire.libsuperuser.Shell;
import jp.tkgktyk.wearablepadlib.BaseApplication;

/**
 * Created by tkgktyk on 2015/05/02.
 */
public class MyApp extends BaseApplication {
    private static boolean mIsSystemApp;

    @Override
    public void onCreate() {
        super.onCreate();

        final int mask = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        mIsSystemApp = (getApplicationInfo().flags & mask) != 0;
        MyApp.logD("mIsSystemApp = " + mIsSystemApp);
    }

    @Override
    protected boolean isDebug() {
        return BuildConfig.DEBUG;
    }

    @Override
    protected void onVersionUpdated(MyVersion next, MyVersion old) {
        if (old.isOlderThan("0.2.1")) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .remove(VirtualMouse.KEY_LAST_CURSOR_X)
                    .remove(VirtualMouse.KEY_LAST_CURSOR_Y)
                    .commit();
        }
    }

    public static void run(String command) {
        if (mIsSystemApp) {
            Shell.SH.run(command);
        } else {
            Shell.SU.run(command);
        }
    }
}
