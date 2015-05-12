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

import android.content.res.Configuration;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import jp.tkgktyk.wearablepadlib.ParcelableUtil;
import jp.tkgktyk.wearablepadlib.TouchMessage;

/**
 * Created by tkgktyk on 2015/04/27.
 * <p/>
 * Basically use float point for scale (relative) value based on degree=0 (portraite),
 * and double point (x,y) for absolute value based on degree=0,
 * and int or short point (x,y) for rotated value.
 * Not need to transform for Cursor and Message position.
 */
public class WearableService extends WearableListenerService {
    private VirtualMouse mVirtualMouse = new VirtualMouse();

    @Override
    synchronized public void onMessageReceived(MessageEvent messageEvent) {
        MyApp.logD("onMessageReceived");
        MyApp.logD(messageEvent.getPath());
        TouchMessage message = ParcelableUtil.unmarshall(messageEvent.getData(), TouchMessage.CREATOR);
        mVirtualMouse.onMessageReceived(message);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mVirtualMouse.onCreate(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        /**
         * THis method is called only when Configuration is changed.
         * Configuration includes orientation but the orientation
         */
        super.onConfigurationChanged(newConfig);

        mVirtualMouse.onConfigurationChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mVirtualMouse.onDestroy();
    }

}
