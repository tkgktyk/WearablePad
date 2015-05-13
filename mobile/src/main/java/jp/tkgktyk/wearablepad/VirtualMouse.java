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
import android.graphics.PointF;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageView;

import com.google.common.collect.Lists;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import eu.chainfire.libsuperuser.Shell;
import jp.tkgktyk.wearablepadlib.TouchMessage;

/**
 * Created by tkgktyk on 2015/05/12.
 * <p/>
 * Basically use float point for scale (relative) value based on degree=0 (portraite),
 * and double point (x,y) for absolute value based on degree=0,
 * and int or short point (x,y) for rotated value. Need to transform for InputSubsystem by rotation,
 * but not need for Cursor and Message position.
 */
public class VirtualMouse {
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

    public static final String KEY_LAST_CURSOR_X = "key_last_cursor_x";
    public static final String KEY_LAST_CURSOR_Y = "key_last_cursor_y";
    private static final float DEFAULT_CURSOR_POSITION = 0.5f;

    private Context mContext;
    private PowerManager.WakeLock mWakeLock;
    private Settings mSettings;

    private PointF mCursor = new PointF();
    private PointF mSwipeCursor = new PointF();

    private Point mDisplaySize = new Point();
    private float mMaxDistance;
    private WindowManager mWindowManager;
    private ImageView mCursorView;
    private Point mCursorSize = new Point();
    private WindowManager.LayoutParams mLayoutParams;

    private FileOutputStream mInputDevice;

    private int mScreenRotation;

    private Point getRotatedPointForInputDevice(PointF point) {
        float x = 0.0f;
        float y = 0.0f;
        MyApp.logD("rotation = " + mScreenRotation);
        switch (mScreenRotation) {
            case Surface.ROTATION_0:
                x = point.x;
                y = point.y;
                break;
            case Surface.ROTATION_90:
                x = 1.0f - point.y;
                y = point.x;
                break;
            case Surface.ROTATION_180:
                x = 1.0f - point.x;
                y = 1.0f - point.y;
                break;
            case Surface.ROTATION_270:
                x = point.y;
                y = 1.0f - point.x;
                break;
            default:
                MyApp.logD();
        }
        final float sizeX = Math.min(mDisplaySize.x, mDisplaySize.y);
        final float sizeY = Math.max(mDisplaySize.x, mDisplaySize.y);
        return new Point(Math.round(x * sizeX * mSettings.ratioX),
                Math.round(y * sizeY * mSettings.ratioY));
    }

    private Point getRotatedPointForCursor(PointF point) {
        return new Point(Math.round(point.x * mDisplaySize.x),
                Math.round(point.y * mDisplaySize.y));
    }

    private void performPress(PointF point, ArrayList<byte[]> cmds) {
        final Point rotated = getRotatedPointForInputDevice(point);
        MyApp.logD(rotated.toString());
        cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_X, rotated.x));
        cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_Y, rotated.y));
        cmds.add(makeEvent(EV_SYN, SYN_REPORT, 0));
    }

    private void performTap(ArrayList<byte[]> cmds) {
        // down
        cmds.add(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, (int) System.currentTimeMillis()));
        performPress(mCursor, cmds);
        // up
        cmds.add(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, 0xFFFFFFFF));
        cmds.add(makeEvent(EV_SYN, SYN_REPORT, 0));
    }

    synchronized public void onMessageReceived(TouchMessage message) {
        MyApp.logD("onMessageReceived");
        MyApp.logD("event: " + message.event + ", x: " + message.x + ", y: " + message.y);

        if (mInputDevice == null) {
            return;
        }

        ArrayList<byte[]> cmds = Lists.newArrayList();
        switch (message.getMaskedEvent()) {
            case TouchMessage.EVENT_SHOW_CURSOR:
//                mCursorView.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        mCursorView.setAlpha(1.0f);
//                        mLayoutParams.alpha = 1.0f;
//                        mCursorView.invalidate();
//                        updateCursorView();
//                    }
//                });
                break;
            case TouchMessage.EVENT_START_DRAG:
                cmds.add(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, (int) System.currentTimeMillis()));
                mSwipeCursor.x = mCursor.x;
                mSwipeCursor.y = mCursor.y;
                performPress(mSwipeCursor, cmds);
                // drop
            case TouchMessage.EVENT_DRAG:
                final float newX = clamp(mSwipeCursor.x + message.x * mSettings.speed / mDisplaySize.x);
                final float newY = clamp(mSwipeCursor.y + message.y * mSettings.speed / mDisplaySize.y);
                final double dx = (newX - mSwipeCursor.x) * mDisplaySize.x; // absolute
                final double dy = (newY - mSwipeCursor.y) * mDisplaySize.y; // absolute
                final double distance = Math.sqrt(dx * dx + dy * dy);
                final int n = (int) (distance / mMaxDistance) + 1; // ceil
                for (int i = 0; i < n; ++i) {
                    mSwipeCursor.x += dx / n / mDisplaySize.x;
                    mSwipeCursor.y += dy / n / mDisplaySize.y;
                    performPress(mSwipeCursor, cmds);
                }
                mSwipeCursor.x = newX;
                mSwipeCursor.y = newY;
                performPress(mSwipeCursor, cmds);
//                updateCursorView();
                break;
            case TouchMessage.EVENT_MOVE:
                mCursor.x = clamp(mCursor.x + message.x * mSettings.speed / mDisplaySize.x);
                mCursor.y = clamp(mCursor.y + message.y * mSettings.speed / mDisplaySize.y);
                updateCursorView();
                break;
            case TouchMessage.EVENT_END_STROKE:
                cmds.add(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, 0xFFFFFFFF));
                cmds.add(makeEvent(EV_SYN, SYN_REPORT, 0));
                break;
            case TouchMessage.EVENT_PRESS:
                cmds.add(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, (int) System.currentTimeMillis()));
                performPress(mCursor, cmds);
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
                        @SuppressWarnings("ResourceType") Object sbservice
                                = mContext.getSystemService("statusbar");
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
                final Point point1 = getRotatedPointForCursor(mCursor);
                final int div = 3;
                final Point point2 = new Point(point1);
                switch (message.getActionValue()) {
                    case TouchMessage.SWIPE_LEFT_TO_RIGHT:
                        point2.x = clamp(point1.x + mDisplaySize.x / div, mDisplaySize.x);
                        break;
                    case TouchMessage.SWIPE_TOP_TO_BOTTOM:
                        point2.y = clamp(point1.y + mDisplaySize.y / div, mDisplaySize.y);
                        break;
                    case TouchMessage.SWIPE_RIGHT_TO_LEFT:
                        point2.x = clamp(point1.x - mDisplaySize.x / div, mDisplaySize.x);
                        break;
                    case TouchMessage.SWIPE_BOTTOM_TO_TOP:
                        point2.y = clamp(point1.y - mDisplaySize.y / div, mDisplaySize.y);
                        break;
                }
                Shell.SU.run(String.format("input swipe %d %d %d %d",
                        point1.x, point1.y, point2.x, point2.y));
                break;
        }
        try {
            for (byte[] cmd : cmds) {
                MyApp.logD("cmd.length = " + cmd.length);
                StringBuilder sb = new StringBuilder(cmd.length * 2);
                for (byte b : cmd) {
                    sb.append(String.format("%02x", b & 0xff));
                }
                MyApp.logD(sb.toString());
                mInputDevice.write(cmd);
            }
        } catch (IOException e) {
            MyApp.logE(e);
        }
    }

    private float clamp(float scale) {
        if (scale < 0.0f) {
            return 0.0f;
        }
        if (scale > 1.0f) {
            return 1.0f;
        }
        return scale;
    }

    private int clamp(int rotatedValue, int max) {
        if (rotatedValue < 0) {
            return 0;
        }
        if (rotatedValue > max) {
            return max;
        }
        return rotatedValue;
    }

    private byte[] makeEvent(short type, short code, int value) {
        final int headerSize = new NativeMethod().getInputEventHeaderSize();
        MyApp.logD("headerSize = " + headerSize);
        ByteBuffer buf = ByteBuffer
                .allocate(headerSize + (Short.SIZE * 2 + Integer.SIZE) / Byte.SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < headerSize; ++i) {
            buf.put((byte) 0);
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
        final Point rotated = getRotatedPointForCursor(mCursor);
        mLayoutParams.x = rotated.x - mDisplaySize.x / 2 + mCursorSize.x;
        mLayoutParams.y = rotated.y - mDisplaySize.y / 2 + mCursorSize.y;
    }

    public VirtualMouse(Context context, Settings settings) {
        mContext = context;
        MyApp.logD("onCreate");

        mSettings = settings;

        reloadResources();
    }

    public void onConfigurationChanged() {
        /**
         * This method is called only when Configuration is changed.
         * Configuration includes orientation but the orientation is only portrait or landscape.
         * If change portrait <-> landscape, this method is called. However If rotate device
         * 180 degree, isn't called because the orientation isn't changed (but degree is changed).
         */
        MyApp.logD();

        reloadResources();
    }

    synchronized private void reloadResources() {
        initWakeLock();

        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mMaxDistance = mContext.getResources().getDisplayMetrics().density * MAX_DISTANCE;

        mWindowManager.getDefaultDisplay().getRealSize(mDisplaySize);
        // call after loading display size
        initCursorView();
        // call after init cursor
        mScreenRotation = mWindowManager.getDefaultDisplay().getRotation();
        MyApp.logD("rotation = " + mScreenRotation);
        updateCursorView();

        openInputDevice();
    }

    private void initWakeLock() {
        releaseWakeLock();
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        PowerManager.ON_AFTER_RELEASE, "MyWakeLock");
        mWakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    private void initCursorView() {
        removeCursor();
        // restore cursor position
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mCursor.x = prefs.getFloat(KEY_LAST_CURSOR_X, DEFAULT_CURSOR_POSITION);
        mCursor.y = prefs.getFloat(KEY_LAST_CURSOR_Y, DEFAULT_CURSOR_POSITION);
        // make cursor
        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        setLayoutParams();
        mCursorView = new ImageView(mContext);
        mCursorView.setImageResource(android.R.drawable.ic_delete);
        mCursorView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mCursorView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mCursorSize.set(mCursorView.getWidth() * 0, mCursorView.getHeight() / 3);
                updateCursorView();
            }
        });
//        mCursorView.setAlpha(0.0f);
//        mLayoutParams.alpha = 0.0f;
        mWindowManager.addView(mCursorView, mLayoutParams);
    }

    private void removeCursor() {
        if (mCursorView != null) {
            mWindowManager.removeView(mCursorView);
            mCursorView = null;
            mLayoutParams = null;
            // store cursor position
            PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                    .putFloat(KEY_LAST_CURSOR_X, mCursor.x)
                    .putFloat(KEY_LAST_CURSOR_Y, mCursor.y)
                    .apply();
        }
    }

    private void openInputDevice() {
        closeInputDevice();
        try {
            mInputDevice = new FileOutputStream(mSettings.device);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeInputDevice() {
        if (mInputDevice != null) {
            try {
                mInputDevice.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mInputDevice = null;
        }
    }

    public void onDestroy() {
        MyApp.logD("onDestroy");

        removeCursor();

        closeInputDevice();

        mWakeLock.release();
    }

}
