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

package jp.tkgktyk.wearablepadrlib;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.ImageView;

import com.google.common.collect.Lists;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import jp.tkgktyk.wearablepadlib.BaseApplication;
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

    private static final int MAX_DISTANCE = 50;

    public static final String KEY_LAST_CURSOR_X = "key_last_cursor_x";
    public static final String KEY_LAST_CURSOR_Y = "key_last_cursor_y";
    private static final float DEFAULT_CURSOR_POSITION = 0.5f;

    private Context mContext;
    private Settings mSettings;

    private PointF mCursor = new PointF();
    private PointF mSwipeCursor = new PointF();

    private Point mDisplaySize = new Point();
    private float mMaxDistance;
    private WindowManager mWindowManager;
    private ImageView mCursorView;
    private WindowManager.LayoutParams mLayoutParams;

    private Process mSuProcess;
    private DataOutputStream mSuStdin;
    private FileOutputStream mInputSubsystem;

    private int mScreenRotation;

    private Runnable mUpdateViewLayout = new Runnable() {
        @Override
        public void run() {
            synchronized (VirtualMouse.this) {
                if (mCursorView != null && mLayoutParams != null) {
                    mWindowManager.updateViewLayout(mCursorView, mLayoutParams);
                }
            }
        }
    };

    private Point getRotatedPointForInputSubsystem(PointF point) {
        float x = 0.0f;
        float y = 0.0f;
        BaseApplication.logD("rotation = " + mScreenRotation);
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
                BaseApplication.logD();
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
        final Point rotated = getRotatedPointForInputSubsystem(point);
        BaseApplication.logD(rotated.toString());
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
        BaseApplication.logD("onMessageReceived");
        BaseApplication.logD("event: " + message.event + ", x: " + message.x + ", y: " + message.y);

        if (mInputSubsystem == null) {
            BaseApplication.logD("mInputSubsystem is null");
            return;
        }

        ArrayList<byte[]> cmds = Lists.newArrayList();
        switch (message.getMaskedEvent()) {
            case TouchMessage.EVENT_SHOW_CURSOR:
                mCursorView.post(new Runnable() {
                    @Override
                    public void run() {
                        BaseApplication.logD();
                        mCursorView.setAlpha(1.0f);
                        mUpdateViewLayout.run();
                    }
                });
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
                BaseApplication.logD("taps = " + taps);
                for (int i = 0; i < taps; ++i) {
                    performTap(cmds);
                }
//                updateCursorView();
                break;
            case TouchMessage.EVENT_ACTION_SYSTEM_UI:
                switch (message.getActionValue()) {
                    case TouchMessage.SYSTEM_UI_BACK:
                        suAsync("input keyevent " + KeyEvent.KEYCODE_BACK);
                        break;
                    case TouchMessage.SYSTEM_UI_TASKS:
                        suAsync("input keyevent " + KeyEvent.KEYCODE_APP_SWITCH);
                        break;
                    case TouchMessage.SYSTEM_UI_HOME:
                        suAsync("input keyevent " + KeyEvent.KEYCODE_HOME);
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
                final float scale = 0.4f;
                final Point point2 = new Point(point1);
                switch (message.getActionValue()) {
                    case TouchMessage.SWIPE_LEFT_TO_RIGHT:
                        point2.x = clamp(point1.x + (int) (mDisplaySize.x * scale), mDisplaySize.x);
                        break;
                    case TouchMessage.SWIPE_TOP_TO_BOTTOM:
                        point2.y = clamp(point1.y + (int) (mDisplaySize.y * scale), mDisplaySize.y);
                        break;
                    case TouchMessage.SWIPE_RIGHT_TO_LEFT:
                        point2.x = clamp(point1.x - (int) (mDisplaySize.x * scale), mDisplaySize.x);
                        break;
                    case TouchMessage.SWIPE_BOTTOM_TO_TOP:
                        point2.y = clamp(point1.y - (int) (mDisplaySize.y * scale), mDisplaySize.y);
                        break;
                }
                suAsync(String.format("input swipe %d %d %d %d",
                        point1.x, point1.y, point2.x, point2.y));
                break;
        }
        try {
            for (byte[] cmd : cmds) {
                BaseApplication.logD("cmd.length = " + cmd.length);
                StringBuilder sb = new StringBuilder(cmd.length * 2);
                for (byte b : cmd) {
                    sb.append(String.format("%02x", b & 0xff));
                }
                BaseApplication.logD(sb.toString());
                mInputSubsystem.write(cmd);
            }
        } catch (IOException e) {
            BaseApplication.logE(e);
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
        BaseApplication.logD("headerSize = " + headerSize);
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
        mCursorView.post(mUpdateViewLayout);
    }

    private void setLayoutParams() {
        final Point rotated = getRotatedPointForCursor(mCursor);
        mLayoutParams.x = rotated.x - mCursorView.getWidth() / 2;
        mLayoutParams.y = rotated.y - mCursorView.getHeight() / 2;
    }

    public VirtualMouse(Context context, Settings settings) {
        mContext = context;
        BaseApplication.logD("onCreate");

        mSettings = settings;

        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mMaxDistance = mContext.getResources().getDisplayMetrics().density * MAX_DISTANCE;

        mWindowManager.getDefaultDisplay().getRealSize(mDisplaySize);
        // call after loading display size
        initCursorView();
        updateScreenRotation();

        openSuProcess();
        openInputSubsystem();
    }

    public void onConfigurationChanged() {
        /**
         * This method is called only when Configuration is changed.
         * Configuration includes orientation but the orientation is only portrait or landscape.
         * If change portrait <-> landscape, this method is called. However If rotate device
         * 180 degree, isn't called because the orientation isn't changed, only degree is changed).
         */
        BaseApplication.logD();

        updateScreenRotation();
    }

    synchronized private void updateScreenRotation() {
        mWindowManager.getDefaultDisplay().getRealSize(mDisplaySize);
        mScreenRotation = mWindowManager.getDefaultDisplay().getRotation();
        BaseApplication.logD("rotation = " + mScreenRotation);
        updateCursorView();
    }

    private void initCursorView() {
        // restore cursor position
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mCursor.x = prefs.getFloat(KEY_LAST_CURSOR_X, DEFAULT_CURSOR_POSITION);
        mCursor.y = prefs.getFloat(KEY_LAST_CURSOR_Y, DEFAULT_CURSOR_POSITION);
        // make cursor
        mCursorView = new ImageView(mContext);
        mCursorView.setImageResource(android.R.drawable.ic_delete);
        mCursorView.setAlpha(0.0f);
        mLayoutParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT);
        mLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        setLayoutParams();
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

    private void openSuProcess() {
        try {
            mSuProcess = Runtime.getRuntime().exec("su");
        } catch (IOException e) {
            BaseApplication.logE(e);
            BaseApplication.showToast(R.string.cannot_execute_su);
        }
        if (mSuProcess != null) {
            mSuStdin = new DataOutputStream(mSuProcess.getOutputStream());
        }
    }

    private void suAsync(String command) {
        try {
            mSuStdin.writeBytes(command + "\n");
            mSuStdin.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeSuProcess() {
        if (mSuStdin != null) {
            try {
                mSuStdin.writeBytes("exit\n");
                mSuStdin.flush();
                mSuStdin.close();
                mSuProcess.waitFor();
            } catch (IOException e) {
                BaseApplication.logE(e);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void suSync(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            process.waitFor();
        } catch (IOException e) {
            BaseApplication.logE(e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void openInputSubsystem() {
        try {
            suSync("supolicy --live \"allow appdomain input_device dir { ioctl read getattr search open }\" \"allow appdomain input_device chr_file { ioctl read write getattr lock append open }\"");
            // Owner of Input Subsystem is root, is not system.
            // Therefore cannot access without changing permission even if system app.
            suSync("chmod 666 " + mSettings.inputSubsystem);
            mInputSubsystem = new FileOutputStream(mSettings.inputSubsystem);
            suAsync("chmod 660 " + mSettings.inputSubsystem);
        } catch (IOException e) {
            BaseApplication.showToast(R.string.cannot_access_input_subsystem);
            e.printStackTrace();
            suAsync("chmod 660 " + mSettings.inputSubsystem);
            suAsync("supolicy --live \"deny appdomain input_device dir { ioctl read getattr search open }\" \"deny appdomain input_device chr_file { ioctl read write getattr lock append open }\"");
        }
    }

    private void closeInputSubsystem() {
        if (mInputSubsystem != null) {
            try {
                mInputSubsystem.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mInputSubsystem = null;
            suAsync("supolicy --live \"deny appdomain input_device dir { ioctl read getattr search open }\" \"deny appdomain input_device chr_file { ioctl read write getattr lock append open }\"");
        }
    }

    public void onDestroy() {
        BaseApplication.logD("onDestroy");

        removeCursor();

        closeInputSubsystem();
        closeSuProcess();
    }

}
