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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.common.collect.Queues;

import java.util.concurrent.BlockingQueue;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import jp.tkgktyk.wearablepad.util.TouchpadView;
import jp.tkgktyk.wearablepadlib.ParcelableUtil;
import jp.tkgktyk.wearablepadlib.TouchMessage;

public class MainActivity extends Activity {

    private static final int TAP_COUNT_TO_GRAB = 3;
    private static final int REQUEST_EXTRA_ACTION = 1;

    private TouchpadView.OnTouchpadEventListener mOnTouchpadEventListener
            = new TouchpadView.OnTouchpadEventListener() {
        private boolean mInGrabMode = false;

        @Override
        public void onStart(int x, int y) {
            MyApp.logD("onStart: x=" + x + ", y=" + y);
            postMessage(TouchMessage.EVENT_SHOW_CURSOR);
        }

        @Override
        public void onStopAsTap(int tapCount, int x, int y) {
            MyApp.logD("onStopAsTap: count=" + tapCount + ", x=" + x + ", y=" + y);
            if (!mInGrabMode && tapCount == TAP_COUNT_TO_GRAB) {
                performHapticFeedback(mTouchpadView);
                postMessage(TouchMessage.EVENT_START_DRAG, 0, 0);
                mInGrabMode = true;
            } else if (mInGrabMode) {
                postMessage(TouchMessage.EVENT_END_STROKE);
                mInGrabMode = false;
            } else {
                postActionMessage(TouchMessage.EVENT_ACTION_TAP, tapCount);
            }
        }

        @Override
        public void onStop(int tapCount, int x, int y) {
            MyApp.logD("onStop: count=" + tapCount + ", x=" + x + ", y=" + y);
            if (!mInGrabMode) {
                postMessage(TouchMessage.EVENT_END_STROKE);
            } else {
                // keep pressing
            }
        }


        @Override
        public void onStartScroll(int tapCount, int dx, int dy) {
            MyApp.logD("onStartScroll: count=" + tapCount + ", dx=" + dx + ", dy=" + dy);
            if (mInGrabMode) {
                postMessage(TouchMessage.EVENT_DRAG, dx, dy);
            } else if (tapCount == 0) {
                postMessage(TouchMessage.EVENT_MOVE, dx, dy);
            } else if (tapCount > 1) {
                postActionMessage(TouchMessage.EVENT_ACTION_TAP, tapCount - 1);
                postMessage(TouchMessage.EVENT_START_DRAG, dx, dy);
            } else {
                // tapCount == 1
                postMessage(TouchMessage.EVENT_START_DRAG, dx, dy);
            }
        }

        @Override
        public void onScroll(int tapCount, int dx, int dy) {
            MyApp.logD("onScroll: count=" + tapCount + ", dx=" + dx + ", dy=" + dy);
            if (mInGrabMode) {
                postMessage(TouchMessage.EVENT_DRAG, dx, dy);
            } else if (tapCount == 0) {
                postMessage(TouchMessage.EVENT_MOVE, dx, dy);
            } else {
                postMessage(TouchMessage.EVENT_DRAG, dx, dy);
            }
        }

        @Override
        public void onLongPress(int tapCount, int x, int y) {
            MyApp.logD("onLongPress: count=" + tapCount + ", x=" + x + ", y=" + y);
            performHapticFeedback(mTouchpadView);
            if (tapCount != 0) {
                startActivityForResult(new Intent(MainActivity.this, ExtraActionActivity.class),
                        REQUEST_EXTRA_ACTION);
            } else {
                postMessage(TouchMessage.EVENT_PRESS);
            }
        }
    };

    @InjectView(R.id.touchpad)
    TouchpadView mTouchpadView;
    @InjectView(R.id.left_button)
    ImageButton mLeftButton;
    @InjectView(R.id.top_button)
    ImageButton mTopButton;
    @InjectView(R.id.right_button)
    ImageButton mRightButton;
    @InjectView(R.id.bottom_button)
    ImageButton mBottomButton;

    @OnClick({R.id.left_button, R.id.top_button, R.id.right_button, R.id.bottom_button})
    void postSwipeMessage(ImageButton button) {
        performHapticFeedback(button);
        byte value = TouchMessage.ACTION_VALUE_NONE;
        switch (button.getId()) {
            case R.id.left_button:
                value = TouchMessage.SWIPE_RIGHT_TO_LEFT;
                break;
            case R.id.top_button:
                value = TouchMessage.SWIPE_BOTTOM_TO_TOP;
                break;
            case R.id.right_button:
                value = TouchMessage.SWIPE_LEFT_TO_RIGHT;
                break;
            case R.id.bottom_button:
                value = TouchMessage.SWIPE_TOP_TO_BOTTOM;
                break;
        }
        postActionMessage(TouchMessage.EVENT_ACTION_SWIPE, value);
    }

    private GoogleApiClient mClient;
    private NodeApi.GetConnectedNodesResult mNodes;
    private BlockingQueue<TouchMessage> mMessages;
    private boolean mRunning;

    private void postActionMessage(byte action, int value) {
        postMessage(TouchMessage.makeActionEvent(action, value));
    }

    private void postMessage(byte event) {
        postMessage(event, 0, 0);
    }

    private void postMessage(byte event, int x, int y) {
        final TouchMessage message = new TouchMessage();
        message.event = event;
        message.x = (short) x;
        message.y = (short) y;
        postMessage(message);
    }

    private void postMessage(TouchMessage message) {
        mMessages.add(message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);

        mRunning = true;

        mTouchpadView.setOnTouchpadEventListener(mOnTouchpadEventListener);

        initClient();
        initMessageQueue();

        final TouchMessage message = new TouchMessage();
        postMessage(message);
    }

    private void initClient() {
        mClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        Log.d("MyFragment", "onConnected");
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Log.d("MyFragment", "onConnectionSuspended");
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Log.d("MyFragment", "onConnectionFailed");
                    }
                })
                .addApi(Wearable.API)
                .build();
        mClient.connect();

    }

    private void initMessageQueue() {
        mMessages = Queues.newLinkedBlockingQueue();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (mRunning) {
                        final TouchMessage message = mMessages.take();
                        final int count = mMessages.size();
                        MyApp.logD("queue size = " + count);
                        TouchMessage lastMessage;
                        if (mMessages.size() != 0) {
                            if (message.event == TouchMessage.EVENT_MOVE) {
                                lastMessage = compressAndSend(count, TouchMessage.EVENT_MOVE, message);
                            } else if (message.event == TouchMessage.EVENT_DRAG) {
                                lastMessage = compressAndSend(count, TouchMessage.EVENT_DRAG, message);
                            } else {
                                send(message);
                                lastMessage = message;
                            }
                        } else {
                            send(message);
                            lastMessage = message;
                        }
                        switch (lastMessage.event) {
                            case TouchMessage.EVENT_MOVE:
                            case TouchMessage.EVENT_START_DRAG:
                            case TouchMessage.EVENT_DRAG:
                                SystemClock.sleep(100);
                                break;
                        }
                    }
                } catch (InterruptedException e) {
                    MyApp.logE(e);
                }
            }

            private TouchMessage compressAndSend(int count, int event, TouchMessage baseMessage)
                    throws InterruptedException {
                TouchMessage message2 = null;
                for (int i = 0; i < count; ++i) {
                    message2 = mMessages.take();
                    if (message2.event == event) {
                        baseMessage.x += message2.x;
                        baseMessage.y += message2.y;
                        message2 = null;
                        MyApp.logD("compress scroll event#" + i + ": " + event);
                    } else {
                        break;
                    }
                }
                if (message2 == null) {
                    send(baseMessage);
                    return baseMessage;
                } else {
                    send(baseMessage);
                    send(message2);
                    return message2;
                }
            }

            private void send(TouchMessage message) {
                Log.d("MyService", "event: " + message.event + ", x: " + message.x + ", y: " + message.y);
                final byte[] data = ParcelableUtil.marshall(message);
                mNodes = Wearable.NodeApi.getConnectedNodes(mClient).await();
                for (Node node : mNodes.getNodes()) {
                    MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
                            mClient,
                            node.getId(),
                            "",
                            data)
                            .await();
                    if (!result.getStatus().isSuccess()) {
                        Log.d("onTouchEvent", "isSuccess is false");
                    }
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRunning = false;
        mClient.disconnect();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_EXTRA_ACTION:
                if (resultCode == RESULT_OK) {
                    switch (data.getAction()) {
                        case ExtraActionActivity.ACTION_BACK:
                            postActionMessage(TouchMessage.EVENT_ACTION_SYSTEM_UI,
                                    TouchMessage.SYSTEM_UI_BACK);
                            break;
                        case ExtraActionActivity.ACTION_TASKS:
                            postActionMessage(TouchMessage.EVENT_ACTION_SYSTEM_UI,
                                    TouchMessage.SYSTEM_UI_TASKS);
                            break;
                        case ExtraActionActivity.ACTION_HOME:
                            postActionMessage(TouchMessage.EVENT_ACTION_SYSTEM_UI,
                                    TouchMessage.SYSTEM_UI_HOME);
                            break;
                        case ExtraActionActivity.ACTION_STATUSBAR:
                            postActionMessage(TouchMessage.EVENT_ACTION_SYSTEM_UI,
                                    TouchMessage.SYSTEM_UI_STATUSBAR);
                            break;
                        case ExtraActionActivity.ACTION_EXIT:
                            finish();
                            break;
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void performHapticFeedback(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }
}
