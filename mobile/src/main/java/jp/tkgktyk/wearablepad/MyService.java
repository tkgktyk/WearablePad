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
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageView;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import com.google.common.collect.Lists;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    /**
     * Input SubSystem
     */
    // EVENT TYPE
    private static final short EV_ABS = 3;
    private static final short EV_SYN = 0;
    // VALUE TYPE
    private static final short ABS_MT_POSITION_X = 53;
    private static final short ABS_MT_POSITION_Y = 54;
    private static final short ABS_MT_TRACKING_ID = 57;

    private static final short SYN_REPORT = 0;

    private static final int MAX_DISTANCE = 100;

    private static final String KEY_LAST_CURSOR_X = "last_cursor_x";
    private static final String KEY_LAST_CURSOR_Y = "last_cursor_y";

    private PowerManager.WakeLock mWakeLock;
    private Settings mSettings;

    private int mCursorX;
    private int mCursorY;
    private int mSwipeCursorX;
    private int mSwipeCursorY;

    private Point mDisplaySize;
    private float mMaxDistance;
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
                mCursorView.post(new Runnable() {
                    @Override
                    public void run() {
                        mCursorView.setAlpha(1.0f);
                        mLayoutParams.alpha = 1.0f;
                        mCursorView.invalidate();
                        updateCursorView();
                    }
                });
                break;
            case TouchMessage.EVENT_START_DRAG:
                cmds.add(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, (int) System.currentTimeMillis()));
                mSwipeCursorX = mCursorX;
                mSwipeCursorY = mCursorY;
                cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_X,
                        Math.round(mSwipeCursorX * mSettings.ratioX)
                ));
                cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_Y,
                        Math.round(mSwipeCursorY * mSettings.ratioY)
                ));
                cmds.add(makeEvent(EV_SYN, SYN_REPORT, 0));
                // drop
            case TouchMessage.EVENT_DRAG:
                final int newX = clampX(mSwipeCursorX + message.x * mSettings.speed);
                final int newY = clampY(mSwipeCursorY + message.y * mSettings.speed);
                final int dx = newX - mSwipeCursorX;
                final int dy = newY - mSwipeCursorY;
                final int distance = (int) Math.round(Math.sqrt(dx * dx + dy * dy));
                final int n = (int) (distance / mMaxDistance) + 1; // ceil
                for (int i = 0; i < n; ++i) {
                    cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_X,
                            Math.round((mSwipeCursorX + dx * (i + 1) / n) * mSettings.ratioX)
                    ));
                    cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_Y,
                            Math.round((mSwipeCursorY + dy * (i + 1) / n) * mSettings.ratioY)
                    ));
                    cmds.add(makeEvent(EV_SYN, SYN_REPORT, 0));
                }
                mSwipeCursorX = newX;
                mSwipeCursorY = newY;
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
//                updateCursorView();
                break;
            case TouchMessage.EVENT_ACTION_TAP:
                final int taps = message.getActionValue();
                MyApp.logD("taps = " + taps);
                for (int i = 0; i < taps; ++i) {
                    performTap(cmds);
                }
//                updateCursorView();
                break;
            case TouchMessage.EVENT_ACTION_SYSTEM_UI:
                switch (message.getActionValue()) {
                    case TouchMessage.SYSTEM_UI_BACK:
                        Shell.SU.run("input keyevent " + KeyEvent.KEYCODE_BACK);
                        break;
                    case TouchMessage.SYSTEM_UI_TASKS:
                        Shell.SU.run("input keyevent " + KeyEvent.KEYCODE_APP_SWITCH);
                        break;
                    case TouchMessage.SYSTEM_UI_HOME:
                        Shell.SU.run("input keyevent " + KeyEvent.KEYCODE_HOME);
                        break;
                    case TouchMessage.SYSTEM_UI_STATUSBAR:
                        @SuppressWarnings("ResourceType") Object sbservice = getSystemService("statusbar");
                        try {
                            Class<?> statusbarManager = Class.forName("android.app.StatusBarManager");
                            Method showsb = statusbarManager.getMethod("expandNotificationsPanel");
                            showsb.invoke(sbservice);
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        } catch (NoSuchMethodException e) {
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                        break;
                }
                break;
            case TouchMessage.EVENT_ACTION_SWIPE:
                final int x1 = mCursorX;
                final int y1 = mCursorY;
                final int div = 3;
                int x2 = x1;
                int y2 = y1;
                switch (message.getActionValue()) {
                    case TouchMessage.SWIPE_LEFT_TO_RIGHT:
                        x2 = clampX(x1 + mDisplaySize.x / div);
                        break;
                    case TouchMessage.SWIPE_TOP_TO_BOTTOM:
                        y2 = clampY(y1 + mDisplaySize.y / div);
                        break;
                    case TouchMessage.SWIPE_RIGHT_TO_LEFT:
                        x2 = clampX(x1 - mDisplaySize.x / div);
                        break;
                    case TouchMessage.SWIPE_BOTTOM_TO_TOP:
                        y2 = clampY(y1 - mDisplaySize.y / div);
                        break;
                }
                Shell.SU.run(String.format("input swipe %d %d %d %d", x1, y1, x2, y2));
                break;
        }
        try {
            for (byte[] cmd : cmds) {
                MyApp.logD("cmd.length = " + cmd.length);
                StringBuilder sb = new StringBuilder(cmd.length * 2);
                for(byte b: cmd) {
                    sb.append(String.format("%02x", b & 0xff));
                }
                MyApp.logD(sb.toString());
                mInputDevice.write(cmd);
            }
        } catch (IOException e) {
            MyApp.logE(e);
        }
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
        final int headerSize = new NativeMethod().getInputEventHeaderSize();
        MyApp.logD("headerSize = " + headerSize);
        ByteBuffer buf = ByteBuffer
                .allocate(headerSize + (Short.SIZE * 2 + Integer.SIZE) / Byte.SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < headerSize; ++i) {
            buf.put((byte)0);
        }
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
        mMaxDistance = getResources().getDisplayMetrics().density * MAX_DISTANCE;

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
        mCursorView.setAlpha(0.0f);
        mLayoutParams.alpha = 0.0f;
        mWindowManager.addView(mCursorView, mLayoutParams);
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
