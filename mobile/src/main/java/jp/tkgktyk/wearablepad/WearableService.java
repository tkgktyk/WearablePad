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
import android.os.Handler;
import android.os.Message;
import android.support.annotation.StringRes;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import jp.tkgktyk.wearablepad.util.ServiceNotification;
import jp.tkgktyk.wearablepadlib.ParcelableUtil;
import jp.tkgktyk.wearablepadlib.TouchMessage;

/**
 * Created by tkgktyk on 2015/04/27.
 */
public class WearableService extends WearableListenerService {

    private Settings mSettings;

    private VirtualMouse mVirtualMouse;

    private BluetoothHelper mBluetoothHelper;

    private ServiceNotification mServiceNotification;

    @Override
    synchronized public void onMessageReceived(MessageEvent messageEvent) {
        MyApp.logD("onMessageReceived");
        MyApp.logD(messageEvent.getPath());

        if (mVirtualMouse != null) {
            TouchMessage message = ParcelableUtil.unmarshall(messageEvent.getData(), TouchMessage.CREATOR);
            mVirtualMouse.onMessageReceived(message);
        }
        if (mBluetoothHelper != null) {
            // transfer over bluetooth
            mBluetoothHelper.write(messageEvent.getData());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mSettings = new Settings(this);
        if (mSettings.transferEnabled) {
            // BT is always on in this service
            mBluetoothHelper = new BluetoothHelper(this, mHandler);

            mBluetoothHelper.start();
            mBluetoothHelper.connect(mSettings.getTransferAddress());
            mServiceNotification = new ServiceNotification(this, R.string.transfer);
            MyApp.showToast(R.string.start_transfer_service);
        } else {
            mVirtualMouse = new VirtualMouse(this, mSettings);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        /**
         * THis method is called only when Configuration is changed.
         * Configuration includes orientation but the orientation
         */
        super.onConfigurationChanged(newConfig);

        if (mVirtualMouse != null) {
            mVirtualMouse.onConfigurationChanged();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mVirtualMouse != null) {
            mVirtualMouse.onDestroy();
            mVirtualMouse = null;
        }
        if (mBluetoothHelper != null) {
            mBluetoothHelper.stop();
            mBluetoothHelper = null;
        }

        if (mServiceNotification != null) {
            mServiceNotification.stop();
            mServiceNotification = null;
        }
    }

    void updateNotification(@StringRes int textId) {
        if (mServiceNotification != null) {
            mServiceNotification.updateText(textId);
        }
    }

    private void updateNotification(String text) {
        if (mServiceNotification != null) {
            mServiceNotification.updateText(text);
        }
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BluetoothHelper.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothHelper.STATE_CONNECTED:
                            updateNotification(
                                    getString(R.string.connected_to_s1, mSettings.destination));
                            break;
                        case BluetoothHelper.STATE_CONNECTING:
                            updateNotification(R.string.connecting);
                            break;
                        case BluetoothHelper.STATE_LISTEN:
                        case BluetoothHelper.STATE_NONE:
                            updateNotification(R.string.not_connected);
                            break;
                    }
                    break;
                case BluetoothHelper.MESSAGE_WRITE:
                    // no feedback
                    break;
                case BluetoothHelper.MESSAGE_READ:
                    // never reach
                    break;
                case BluetoothHelper.MESSAGE_DEVICE_NAME:
                    // no feedback
                    break;
                case BluetoothHelper.MESSAGE_TOAST:
//                    updateNotification(msg.getData().getString(BluetoothHelper.TOAST));
                    break;
            }
        }
    };
}
