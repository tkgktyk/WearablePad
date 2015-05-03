package jp.tkgktyk.wearablepad;

import jp.tkgktyk.wearablepadlib.BaseApplication;

/**
 * Created by tkgktyk on 2015/05/02.
 */
public class MyApp extends BaseApplication {
    @Override
    protected boolean isDebug() {
        return BuildConfig.DEBUG;
    }

    @Override
    protected void onVersionUpdated(MyVersion next, MyVersion old) {
    }
}
