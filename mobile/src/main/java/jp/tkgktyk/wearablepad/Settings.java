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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.StringRes;

/**
 * Created by tkgktyk on 2015/05/02.
 */

public class Settings {
    public final String device;
    public final float ratioX;
    public final float ratioY;
    public final float speed;
    private Context mContext;

    public Settings(Context context) {
        mContext = context;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        device = prefs.getString(key(R.string.key_input_device),
                context.getString(R.string.default_input_device));
        ratioX = getPercent(prefs, R.string.key_input_device_ratio_x,
                R.string.default_input_device_ratio_x);
        ratioY = getPercent(prefs, R.string.key_input_device_ratio_y,
                R.string.default_input_device_ratio_y);

        speed = getPercent(prefs, R.string.key_cursor_speed, R.string.default_cursor_speed);

        mContext = null;
    }

    private String key(@StringRes int keyId) {
        return mContext.getString(keyId);
    }

    private float getPercent(SharedPreferences prefs, @StringRes int keyId,
                             @StringRes int defaultId) {
        return Integer.parseInt(
                prefs.getString(key(keyId), mContext.getString(defaultId))) / 100f;
    }
}
