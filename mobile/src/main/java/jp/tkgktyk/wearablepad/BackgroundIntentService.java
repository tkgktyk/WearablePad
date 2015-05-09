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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import java.io.FileOutputStream;
import java.io.IOException;

import eu.chainfire.libsuperuser.Shell;

/**
 * Created by tkgktyk on 2015/05/02.
 */
public class BackgroundIntentService extends IntentService {
    public BackgroundIntentService() {
        super(BackgroundIntentService.class.getSimpleName());
    }

    public static void launchService(Context context) {
        if (context == null) {
            return;
        }
        context.startService(new Intent(context, BackgroundIntentService.class));
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Settings settings = new Settings(this);

        try {
            new FileOutputStream(settings.device).close();
        } catch (IOException e) {
            // SELinux permissive only for /dev/input
            Shell.SU.run("supolicy --live \"allow appdomain input_device dir { ioctl read getattr search open }\" \"allow appdomain input_device chr_file { ioctl read write getattr lock append open }\"");
            try {
                new FileOutputStream(settings.device).close();
            } catch (IOException e1) {
                Shell.SU.run("chmod 666 " + settings.device);
            }
        }
    }
}
