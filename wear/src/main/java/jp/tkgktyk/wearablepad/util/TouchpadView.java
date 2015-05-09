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

package jp.tkgktyk.wearablepad.util;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Created by tkgktyk on 2015/05/08.
 */
public class TouchpadView extends View {
    private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();
    private static final int DOUBLE_TAP_TIMEOUT = ViewConfiguration.getDoubleTapTimeout();

    private int mTouchSlopSquare;
    private int mDoubleTapSlopSquare;

    private int mLastX;
    private int mLastY;
    private MotionEvent mPreviousUpEvent;
    private boolean mLongPressPending;
    private boolean mInLongPress;
    private boolean mIsBeingDragged;
    private int mTapCount;

    private final Handler mHandler = new Handler();
    private final Runnable mStopTouchEvent = new Runnable() {
        @Override
        public void run() {
            stopTouchEvent();
        }
    };
    private final Runnable mDispatchLongPress = new Runnable() {
        @Override
        public void run() {
            dispatchLongPress();
        }
    };

    private OnTouchpadEventListener mListener;

    public TouchpadView(Context context) {
        super(context);
        initTouchpad(context);
    }

    public TouchpadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initTouchpad(context);
    }

    public TouchpadView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initTouchpad(context);
    }

    private void initTouchpad(Context context) {
        ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlopSquare = square(vc.getScaledTouchSlop());
        mDoubleTapSlopSquare = square(vc.getScaledDoubleTapSlop());
    }

    private int square(int value) {
        return value * value;
    }

    public void setOnTouchpadEventListener(OnTouchpadEventListener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        final int action = event.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                mLastX = (int) event.getX();
                mLastY = (int) event.getY();

                mHandler.removeCallbacks(mStopTouchEvent);
                mHandler.removeCallbacks(mDispatchLongPress);
                mHandler.postAtTime(mDispatchLongPress, event.getDownTime()
                        + TAP_TIMEOUT + LONGPRESS_TIMEOUT);
                if (mTapCount == 0) {
                    mListener.onStart(mLastX, mLastY);
                }
                mLongPressPending = true;
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final int x = (int) event.getX();
                final int dx = x - mLastX;
                final int y = (int) event.getY();
                final int dy = y - mLastY;
                if (!mIsBeingDragged && !mInLongPress &&
                        square(x - mLastX) + square(y - mLastY) > mTouchSlopSquare) {
                    mHandler.removeCallbacks(mStopTouchEvent);
                    mHandler.removeCallbacks(mDispatchLongPress);
                    mIsBeingDragged = true;
                    mLongPressPending = false;
                    mLastX = x;
                    mLastY = y;
                    mListener.onStartScroll(mTapCount, dx, dy);
                } else if (mIsBeingDragged) {
                    mLastX = x;
                    mLastY = y;
                    mListener.onScroll(mTapCount, dx, dy);
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (mLongPressPending) {
                    if (mPreviousUpEvent == null ||
                            isConsideredDoubleTap(mPreviousUpEvent, event)) {
                        ++mTapCount;
                    }
                    mHandler.removeCallbacks(mStopTouchEvent);
                    mHandler.postDelayed(mStopTouchEvent, DOUBLE_TAP_TIMEOUT);
                    if (mPreviousUpEvent != null) {
                        mPreviousUpEvent.recycle();
                    }
                    mPreviousUpEvent = MotionEvent.obtain(event);
                } else {
                    stopTouchEvent();
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                stopTouchEvent();
                break;
            }
        }
        return true;
    }

    private void cancel() {
        mTapCount = 0;
        mHandler.removeCallbacks(mStopTouchEvent);
        mHandler.removeCallbacks(mDispatchLongPress);
        mInLongPress = false;
        mLongPressPending = false;
        mIsBeingDragged = false;
        if (mPreviousUpEvent != null) {
            mPreviousUpEvent.recycle();
            mPreviousUpEvent = null;
        }
    }

    private boolean isConsideredDoubleTap(MotionEvent lastUp, MotionEvent currentUp) {
        int deltaX = (int) lastUp.getX() - (int) currentUp.getX();
        int deltaY = (int) lastUp.getY() - (int) currentUp.getY();
        return (deltaX * deltaX + deltaY * deltaY < mDoubleTapSlopSquare);
    }

    private void dispatchLongPress() {
        mHandler.removeCallbacks(mStopTouchEvent);
        mInLongPress = true;
        mListener.onLongPress(mTapCount, mLastX, mLastY);
        mLongPressPending = false;
    }

    private void stopTouchEvent() {
        if (mLongPressPending) {
            mListener.onStopAsTap(mTapCount, mLastX, mLastY);
        } else {
            mListener.onStop(mTapCount, mLastX, mLastY);
        }
        cancel();
    }

    public interface OnTouchpadEventListener {
        void onStart(int x, int y);

        void onStop(int tapCount, int x, int y);

        void onStopAsTap(int tapCount, int x, int y);

        void onStartScroll(int tapCount, int dx, int dy);

        void onScroll(int tapCount, int dx, int dy);

        void onLongPress(int tapCount, int x, int y);
    }
}
