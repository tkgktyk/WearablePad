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
