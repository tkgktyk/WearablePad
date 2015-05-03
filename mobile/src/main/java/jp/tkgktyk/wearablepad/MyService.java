package jp.tkgktyk.wearablepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.preference.PreferenceManager;
import android.util.Log;
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
    private static final String TAG = MyService.class.getSimpleName();

    private static final short EV_ABS = 3;
    private static final short EV_SYN = 0;

    private static final short ABS_MT_POSITION_X = 53;
    private static final short ABS_MT_POSITION_Y = 54;
    private static final short ABS_MT_TRACKING_ID = 57;

    private static final short SYN_REPORT = 0;
    private static final String KEY_LAST_CURSOR_X = "last_cursor_x";
    private static final String KEY_LAST_CURSOR_Y = "last_cursor_y";

    private Settings mSettings;

    private int mCursorX;
    private int mCursorY;
    private boolean mIsBeingDragged;
    private Point mDisplaySize;
    private WindowManager mWindowManager;
    private ImageView mCursorView;
    private Point mCursorSize;
    private WindowManager.LayoutParams mLayoutParams;

    private FileOutputStream mInputDevice;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d("MyService", "onMessageReceived");
        Log.d("MyService", messageEvent.getPath());
        TouchMessage message = ParcelableUtil.unmarshall(messageEvent.getData(), TouchMessage.CREATOR);
        Log.d("MyService", "event: " + message.event + ", x: " + message.x + ", y: " + message.y);

        if (mInputDevice == null) {
            return;
        }

        ArrayList<byte[]> cmds = Lists.newArrayList();
        switch (message.event) {
            case TouchMessage.EVENT_DOWN:
                break;
            case TouchMessage.EVENT_START_DRAG:
                mIsBeingDragged = true;
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
            case TouchMessage.EVENT_MOVE:
                if (message.x != 0) {
                    mCursorX = clampX(mCursorX - message.x * mSettings.speed);
                    if (mIsBeingDragged) {
                        cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_X,
                                Math.round(mCursorX * mSettings.ratioX)
                        ));
                    }
                }
                if (message.y != 0) {
                    mCursorY = clampY(mCursorY - message.y * mSettings.speed);
                    if (mIsBeingDragged) {
                        cmds.add(makeEvent(EV_ABS, ABS_MT_POSITION_Y,
                                Math.round(mCursorY * mSettings.ratioY)
                        ));
                    }
                }
                if (mIsBeingDragged) {
                    cmds.add(makeEvent(EV_SYN, SYN_REPORT, 0));
                }
                updateCursorView();
                break;
            case TouchMessage.EVENT_UP:
                if (mIsBeingDragged) {
                    cmds.add(makeEvent(EV_ABS, ABS_MT_TRACKING_ID, 0xFFFFFFFF));
                    cmds.add(makeEvent(EV_SYN, SYN_REPORT, 0));
                }
                mIsBeingDragged = false;
                break;
            case TouchMessage.EVENT_SINGLE_TAP:
                performSingleTap(cmds);
                updateCursorView();
                break;
            case TouchMessage.EVENT_DOUBLE_TAP:
                performSingleTap(cmds);
                performSingleTap(cmds);
                updateCursorView();
                break;
            case TouchMessage.EVENT_BACK:
                Shell.SU.run("input keyevent " + KeyEvent.KEYCODE_BACK);
                break;
            case TouchMessage.EVENT_TASKS:
                Shell.SU.run("input keyevent " + KeyEvent.KEYCODE_APP_SWITCH);
                break;
            case TouchMessage.EVENT_HOME:
                Shell.SU.run("input keyevent " + KeyEvent.KEYCODE_HOME);
                break;
            case TouchMessage.EVENT_EXIT:
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
        Log.d("watchpad", "time: " + stopwatch);
    }

    private void performSingleTap(ArrayList<byte[]> cmds) {
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
        Log.d(TAG, "onCreate");

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
        Log.d(TAG, "onDestroy");

        removeCursor();

        if (mInputDevice != null) {
            try {
                mInputDevice.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
