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
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.common.base.Objects;
import com.google.common.collect.Queues;

import java.util.concurrent.BlockingQueue;

import butterknife.ButterKnife;
import butterknife.InjectView;
import jp.tkgktyk.wearablepad.util.TouchpadView;
import jp.tkgktyk.wearablepadlib.ParcelableUtil;
import jp.tkgktyk.wearablepadlib.TouchMessage;

public class MainActivity extends Activity {

    private static final int REQUEST_EXTRA_ACTION = 1;
    private TouchpadView.OnTouchpadEventListener mOnTouchpadEventListener
            = new TouchpadView.OnTouchpadEventListener() {
        @Override
        public void onStart(int x, int y) {
            MyApp.logD("onStart: x=" + x + ", y=" + y);
            postMessage(TouchMessage.EVENT_SHOW_CURSOR);
        }

        @Override
        public void onStopAsTap(int tapCount, int x, int y) {
            MyApp.logD("onStopAsTap: count=" + tapCount + ", x=" + x + ", y=" + y);
            postTapMessage(tapCount);
        }

        @Override
        public void onStop(int tapCount, int x, int y) {
            MyApp.logD("onStop: count=" + tapCount + ", x=" + x + ", y=" + y);
            postMessage(TouchMessage.EVENT_END_STROKE);
        }

        @Override
        public void onStartScroll(int tapCount, int dx, int dy) {
            MyApp.logD("onStartScroll: count=" + tapCount + ", dx=" + dx + ", dy=" + dy);
            if (tapCount == 0) {
                postMessage(TouchMessage.EVENT_MOVE, dx, dy);
            } else if (tapCount > 1) {
                postTapMessage(tapCount - 1);
                postMessage(TouchMessage.EVENT_START_DRAG, dx, dy);
            } else {
                postMessage(TouchMessage.EVENT_START_DRAG, dx, dy);
            }
        }

        @Override
        public void onScroll(int tapCount, int dx, int dy) {
            MyApp.logD("onScroll: count=" + tapCount + ", dx=" + dx + ", dy=" + dy);
            if (tapCount == 0) {
                postMessage(TouchMessage.EVENT_MOVE, dx, dy);
            } else {
                postMessage(TouchMessage.EVENT_DRAG, dx, dy);
            }
        }

        @Override
        public void onLongPress(int tapCount, int x, int y) {
            MyApp.logD("onLongPress: count=" + tapCount + ", x=" + x + ", y=" + y);
            if (tapCount != 0) {
                startActivityForResult(new Intent(MainActivity.this, ExtraActionActivity.class),
                        REQUEST_EXTRA_ACTION);
            } else {
                postMessage(TouchMessage.EVENT_PRESS);
            }
        }
    };

    @InjectView(R.id.screen)
    TouchpadView mTouchpadView;

    private GoogleApiClient mClient;
    private NodeApi.GetConnectedNodesResult mNodes;
    private BlockingQueue<TouchMessage> mEvents;
    private boolean mRunning;

    private void postTapMessage(int tapCount) {
        postMessage(TouchMessage.makeTapEvent(tapCount), 0, 0);
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
        mEvents.add(message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);

        mRunning = true;

        mTouchpadView.setOnTouchpadEventListener(mOnTouchpadEventListener);

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

        mEvents = Queues.newLinkedBlockingQueue();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (mRunning) {
                        final TouchMessage message = mEvents.take();
                        final int count = mEvents.size();
                        MyApp.logD("queue size = " + count);
                        if (mEvents.size() != 0) {
                            if (message.event == TouchMessage.EVENT_MOVE) {
                                compressAndSend(count, TouchMessage.EVENT_MOVE, message);
                            } else if (message.event == TouchMessage.EVENT_DRAG) {
                                compressAndSend(count, TouchMessage.EVENT_DRAG, message);
                            }
                        } else {
                            send(message);
                        }
                    }
                } catch (InterruptedException e) {
                }
            }

            private void compressAndSend(int count, int event, TouchMessage baseMessage)
                    throws InterruptedException {
                TouchMessage message2 = null;
                for (int i = 0; i < count; ++i) {
                    message2 = mEvents.take();
                    if (message2.event == event) {
                        baseMessage.x += message2.x;
                        baseMessage.y += message2.y;
                        message2 = null;
                        MyApp.logD("compress scroll event#" + i + ": " + event);
                    } else {
                        break;
                    }
                }
                send(baseMessage);
                if (message2 != null) {
                    send(message2);
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
        final TouchMessage message = new TouchMessage();
        postMessage(message);
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
                    final String action = data.getAction();
                    byte event = TouchMessage.EVENT_UNKNOWN;
                    if (Objects.equal(action, ExtraActionActivity.ACTION_BACK)) {
                        event = TouchMessage.EVENT_ACTION_BACK;
                    } else if (Objects.equal(action, ExtraActionActivity.ACTION_TASKS)) {
                        event = TouchMessage.EVENT_ACTION_TASKS;
                    } else if (Objects.equal(action, ExtraActionActivity.ACTION_HOME)) {
                        event = TouchMessage.EVENT_ACTION_HOME;
                    } else if (Objects.equal(action, ExtraActionActivity.ACTION_EXIT)) {
                        event = TouchMessage.EVENT_ACTION_EXIT;
                        finish();
                    }
                    postMessage(event);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
