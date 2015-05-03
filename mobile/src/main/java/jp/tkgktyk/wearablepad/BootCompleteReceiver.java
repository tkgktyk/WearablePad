package jp.tkgktyk.wearablepad;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by tkgktyk on 2015/05/02.
 */
public class BootCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        BackgroundIntentService.launchService(context);
    }
}
