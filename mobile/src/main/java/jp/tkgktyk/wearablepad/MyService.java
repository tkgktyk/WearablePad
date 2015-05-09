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
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageView;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import eu.chainfire.libsuperuser.Shell;
import jp.tkgktyk.wearablepadlib.ParcelableUtil;
import jp.tkgktyk.wearablepadlib.TouchMessage;

/**
 * Created by tkgktyk on 2015/04/27.
 */
public class MyService extends WearableListenerService {
    private static final short EV_ABS = 3;
    private static final short EV_SYN = 0;

    private static final short ABS_MT_POSITION_X = 53;
    private static final short ABS_MT_POSITION_Y = 54;
    private static final short ABS_MT_TRACKING_ID = 57;

    private static final short SYN_REPORT = 0;
    private static final String KEY_LAST_CURSOR_X = "last_cursor_x";
    private static final String KEY_LAST_CURSOR_Y = "last_cursor_y";

    private PowerManager.WakeLock mWakeLock;
    private Settings mSettings;

    private int mCursorX;
    private int mCursorY;
    private int mSwipeCursorX;
    private int mSwipeCursorY;

    private Point mDisplaySize;
    private WindowManager mWindowManager;
    private ImageView mCursorView;
    private Point mCursorSize;
    private WindowManager.LayoutParams mLayoutParams;

    private FileOutputStream mInputDevice;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        MyApp.logD("onMessageReceived");
        MyApp.logD(messageEvent.getPath());
        TouchMessage message = ParcelableUtil.unmarshall(messageEvent.getData(), TouchMessage.CREATOR);
        MyApp.logD("event: " + message.event + ", x: " + message.x + ", y: " + message.y);

        if (mInputDevice == null) {
            return;
        }

        ArrayList<byte[]> cmds = Lists.newArrayList();
        switch (message.getMaskedEvent()) {
            case TouchMessage.EVENT_SHOW_CURSOR:
                mCursorView.setVisibility(View.VISIBLE);
                updateCursorView();
                break;
            case TouchMessage.EVENT_START_DRAG:
                cmds.add(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, (int) System.currentTimeMillis()));
                mSwipeCursorX = mCursorX;
                mSwipeCursorY = mCursorY;
                // drop
            case TouchMessage.EVENT_DRAG:
                mSwipeCursorX = clampX(mSwipeCursorX + message.x * mSettings.speed);
                mSwipeCursorY = clampY(mSwipeCursorY + message.y * mSettings.speed);
                cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_X,
                        Math.round(mSwipeCursorX * mSettings.ratioX)
                ));
                cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_Y,
                        Math.round(mSwipeCursorY * mSettings.ratioY)
                ));
                cmds.add(makeEvent(EV_SYN, SYN_REPORT, 0));
//                updateCursorView();
                break;
            case TouchMessage.EVENT_MOVE:
                mCursorX = clampX(mCursorX + message.x * mSettings.speed);
                mCursorY = clampY(mCursorY + message.y * mSettings.speed);
                updateCursorView();
                break;
            case TouchMessage.EVENT_END_STROKE:
                cmds.add(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, 0xFFFFFFFF));
                cmds.add(makeEvent(EV_SYN, SYN_REPORT, 0));
                break;
            case TouchMessage.EVENT_PRESS:
                cmds.add(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, (int) System.currentTimeMillis()));
                cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_X,
                        Math.round(mCursorX * mSettings.ratioX)
                ));
                cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_Y,
                        Math.round(mCursorY * mSettings.ratioY)
                ));
                cmds.add(makeEvent(EV_SYN, SYN_REPORT, 0));
                updateCursorView();
                break;
            case TouchMessage.EVENT_ACTION_TAP:
                final int count = message.getTapCount();
                for (int i = 0; i < count; ++i) {
                    performTap(cmds);
                }
                updateCursorView();
                break;
            case TouchMessage.EVENT_ACTION_BACK:
                Shell.SU.run("input keyevent " + KeyEvent.KEYCODE_BACK);
                break;
            case TouchMessage.EVENT_ACTION_TASKS:
                Shell.SU.run("input keyevent " + KeyEvent.KEYCODE_APP_SWITCH);
                break;
            case TouchMessage.EVENT_ACTION_HOME:
                Shell.SU.run("input keyevent " + KeyEvent.KEYCODE_HOME);
                break;
            case TouchMessage.EVENT_ACTION_EXIT:
                // not working
//                stopSelf();
                break;
        }
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            for (byte[] cmd : cmds) {
                mInputDevice.write(cmd);
//                fos.write((byte) '\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        stopwatch.stop();
        MyApp.logD("time: " + stopwatch);
    }

    private void performTap(ArrayList<byte[]> cmds) {
        // down
        cmds.add(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, (int) System.currentTimeMillis()));
        cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_X,
                Math.round(mCursorX * mSettings.ratioX)
        ));
        cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_Y,
                Math.round(mCursorY * mSettings.ratioY)
        ));
        cmds.add(makeEvent(EV_SYN, SYN_REPORT, 0));
        // up
        cmds.add(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, 0xFFFFFFFF));
        cmds.add(makeEvent(EV_SYN, SYN_REPORT, 0));
    }

    private int clampX(float newPosition) {
        if (newPosition < 0) {
            return 0;
        }
        if (newPosition >= mDisplaySize.x) {
            return mDisplaySize.x - 1;
        }
        return Math.round(newPosition);
    }

    private int clampY(float newPosition) {
        if (newPosition < 0) {
            return 0;
        }
        if (newPosition >= mDisplaySize.y) {
            return mDisplaySize.y - 1;
        }
        return Math.round(newPosition);
    }

    private byte[] makeEvent(short type, short code, int value) {
        ByteBuffer buf = ByteBuffer.allocate(4 + 4 + 2 + 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0);
        buf.putInt(0);
        buf.putShort(type);
        buf.putShort(code);
        buf.putInt(value);
        return buf.array();
    }

    private void updateCursorView() {
        setLayoutParams();
        mCursorView.post(new Runnable() {
            @Override
            public void run() {
                mWindowManager.updateViewLayout(mCursorView, mLayoutParams);
            }
        });
    }

    private void setLayoutParams() {
        mLayoutParams.x = Math.round((mCursorX - mDisplaySize.x / 2)) + mCursorSize.x;
        mLayoutParams.y = Math.round((mCursorY - mDisplaySize.y / 2)) + mCursorSize.y;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        MyApp.logD("onCreate");

        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        PowerManager.ON_AFTER_RELEASE, "MyWakeLock");
        mWakeLock.acquire();

        mSettings = new Settings(this);

        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mDisplaySize = new Point();
        mWindowManager.getDefaultDisplay().getRealSize(mDisplaySize);

        // call after loading display size
        initCursorView();

        try {
            mInputDevice = new FileOutputStream(mSettings.device);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initCursorView() {
        // restore cursor position
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mCursorX = prefs.getInt(KEY_LAST_CURSOR_X, mDisplaySize.x / 2);
        mCursorY = prefs.getInt(KEY_LAST_CURSOR_Y, mDisplaySize.y / 2);
        // make cursor
        mCursorSize = new Point();
        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        setLayoutParams();
        mCursorView = new ImageView(this);
        mCursorView.setImageResource(android.R.drawable.ic_delete);
        mCursorView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mCursorView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mCursorSize.set(mCursorView.getWidth() * 0, mCursorView.getHeight() / 3);
                updateCursorView();
            }
        });
        mCursorView.setVisibility(View.GONE);
        mWindowManager.addView(mCursorView, mLayoutParams);
        mCursorView.setVisibility(View.VISIBLE);
    }

    private void removeCursor() {
        if (mCursorView != null) {
            mWindowManager.removeView(mCursorView);
            mCursorView = null;
            mLayoutParams = null;
            // store cursor position
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putInt(KEY_LAST_CURSOR_X, mCursorX)
                    .putInt(KEY_LAST_CURSOR_Y, mCursorY)
                    .apply();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        MyApp.logD("onDestroy");

        removeCursor();

        if (mInputDevice != null) {
            try {
                mInputDevice.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        mWakeLock.release();
    }

}
