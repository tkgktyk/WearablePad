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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import jp.tkgktyk.wearablepadlib.ParcelableUtil;
import jp.tkgktyk.wearablepadlib.TouchMessage;

/**
 * Created by tkgktyk on 2015/05/13.
 */
public class BluetoothReceiverService extends Service {
    private BluetoothHelper mBluetoothHelper;
    private String mConnectedDeviceName;

    private VirtualMouse mVirtualMouse;

    public static void startService(Context context) {
        MyApp.logD();
        if (context == null) {
            return;
        }
        context.startService(new Intent(context, PrepareInputSubsystemIntentService.class));
    }

    public static void stopService(Context context) {
        MyApp.logD();
        if (context == null) {
            return;
        }
        context.stopService(new Intent(context, PrepareInputSubsystemIntentService.class));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // BT is always on in this service
        mBluetoothHelper = new BluetoothHelper(this, mHandler);

        mBluetoothHelper.start();
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
        }
        if (mBluetoothHelper != null) {
            mBluetoothHelper.stop();
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
                            MyApp.showToast(getString(R.string.connected_to_s1, mConnectedDeviceName));
                            if (mVirtualMouse != null) {
                                mVirtualMouse.onDestroy();
                                mVirtualMouse = null;
                            }
                            Context context = BluetoothReceiverService.this;
                            mVirtualMouse = new VirtualMouse(context, new Settings(context));
                            break;
                        case BluetoothHelper.STATE_CONNECTING:
                            // TODO: show connecting text
                            break;
                        case BluetoothHelper.STATE_LISTEN:
                        case BluetoothHelper.STATE_NONE:
                            // TODO: show not connected text
                            if (mVirtualMouse != null) {
                                mVirtualMouse.onDestroy();
                                mVirtualMouse = null;
                            }
                            break;
                    }
                    break;
                case BluetoothHelper.MESSAGE_WRITE:
                    // never reach
                    break;
                case BluetoothHelper.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    if (mVirtualMouse != null) {
                        TouchMessage message = ParcelableUtil
                                .unmarshall(readBuf, TouchMessage.CREATOR);
                        mVirtualMouse.onMessageReceived(message);
                    }
                    break;
                case BluetoothHelper.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(BluetoothHelper.DEVICE_NAME);
                    MyApp.showToast(getString(R.string.connected_to_s1, mConnectedDeviceName));
                    break;
                case BluetoothHelper.MESSAGE_TOAST:
                    MyApp.showToast(msg.getData().getString(BluetoothHelper.TOAST));
                    break;
            }
        }
    };
}
