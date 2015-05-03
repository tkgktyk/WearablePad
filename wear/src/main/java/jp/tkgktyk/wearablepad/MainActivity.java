package jp.tkgktyk.wearablepad;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

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
import jp.tkgktyk.wearablepadlib.ParcelableUtil;
import jp.tkgktyk.wearablepadlib.TouchMessage;

public class MainActivity extends Activity {

    private static final int REQUEST_EXTRA_ACTION = 1;
    private GestureDetector.SimpleOnGestureListener mOnGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            MyApp.logD("onDoubleTap");
            startActivityForResult(new Intent(MainActivity.this, ExtraActionActivity.class),
                    REQUEST_EXTRA_ACTION);
            return true;
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            MyApp.logD("onDoubleTapEvent");
            return false;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            MyApp.logD("onDown");
//            MyApp.logD(e.toString());
//            final TouchMessage message = new TouchMessage();
//            message.event = TouchMessage.EVENT_DOWN;
//            message.x = getX(e);
//            message.y = getY(e);
//            postMessage(message);
//            return true;
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            MyApp.logD("onFling");
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            MyApp.logD("onLongPress");
            MyApp.logD(e.toString());
            final TouchMessage message = new TouchMessage();
            message.event = TouchMessage.EVENT_START_DRAG;
            message.x = getX(e);
            message.y = getY(e);
            postMessage(message);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            MyApp.logD("onScroll");
//            MyApp.logD(e1.toString());
//            MyApp.logD(e2.toString());
//            final TouchMessage message = new TouchMessage();
//            message.event = TouchMessage.EVENT_MOVE;
//            message.x = floatToShort(distanceX);
//            message.y = floatToShort(distanceY);
//            postMessage(message);
//            return true;
            return false;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            MyApp.logD("onSingleTapConfirmed");
            MyApp.logD(e.toString());
            final TouchMessage message = new TouchMessage();
            message.event = TouchMessage.EVENT_SINGLE_TAP;
            message.x = getX(e);
            message.y = getY(e);
            postMessage(message);
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            MyApp.logD("onSingleTapUp");
            return false;
        }
    };
    @InjectView(R.id.screen)
    View mScreen;
    private View.OnTouchListener mOnTouchListener = new View.OnTouchListener() {
        private float mLastX;
        private float mLastY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (!mGestureDetector.onTouchEvent(event)) {
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN: {
                        mLastX = event.getX();
                        mLastY = event.getY();
                        final TouchMessage message = new TouchMessage();
                        message.event = TouchMessage.EVENT_DOWN;
                        message.x = floatToShort(mLastX);
                        message.y = floatToShort(mLastY);
                        postMessage(message);
                        break;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        final float x = event.getX();
                        final float y = event.getY();
                        final float dx = mLastX - x;
                        final float dy = mLastY - y;
                        mLastX = x;
                        mLastY = y;
                        final TouchMessage message = new TouchMessage();
                        message.event  =TouchMessage.EVENT_MOVE;
                        message.x = floatToShort(dx);
                        message.y = floatToShort(dy);
                        postMessage(message);
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        final TouchMessage message = new TouchMessage();
                        message.event = TouchMessage.EVENT_UP;
                        message.x = getX(event);
                        message.y = getY(event);
                        postMessage(message);
                        break;
                    }
                }
            }
            return true;
        }

    };
    private GoogleApiClient mClient;
    private NodeApi.GetConnectedNodesResult mNodes;
    private BlockingQueue<TouchMessage> mEvents;
    private GestureDetector mGestureDetector;
    private boolean mRunning;

    private short getX(MotionEvent event) {
        return floatToShort(event.getX());
    }

    private short getY(MotionEvent event) {
        return floatToShort(event.getY());
    }

    private short floatToShort(float value) {
        return (short) Math.round(value);
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

        mGestureDetector = new GestureDetector(this, mOnGestureListener);
        mScreen.setOnTouchListener(mOnTouchListener);

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
                    mNodes = Wearable.NodeApi.getConnectedNodes(mClient).await();
                    while (mRunning) {
                        send(mEvents.take());
                        MyApp.logD("queue size = " + mEvents.size());
                    }
                } catch (InterruptedException e) {
                }
            }

            private void send(TouchMessage message) {
                Log.d("MyService", "event: " + message.event + ", x: " + message.x + ", y: " + message.y);
                final byte[] data = ParcelableUtil.marshall(message);
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
                    final TouchMessage message = new TouchMessage();
                    final String action = data.getAction();
                    if (Objects.equal(action, ExtraActionActivity.ACTION_BACK)) {
                        message.event = TouchMessage.EVENT_BACK;
                    } else if (Objects.equal(action, ExtraActionActivity.ACTION_DOUBLE_TAP)) {
                        message.event = TouchMessage.EVENT_DOUBLE_TAP;
                    } else if (Objects.equal(action, ExtraActionActivity.ACTION_TASKS)) {
                        message.event = TouchMessage.EVENT_TASKS;
                    } else if (Objects.equal(action, ExtraActionActivity.ACTION_HOME)) {
                        message.event = TouchMessage.EVENT_HOME;
                    } else if (Objects.equal(action, ExtraActionActivity.ACTION_EXIT)) {
                        message.event = TouchMessage.EVENT_EXIT;
                        finish();
                    }
                    postMessage(message);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
