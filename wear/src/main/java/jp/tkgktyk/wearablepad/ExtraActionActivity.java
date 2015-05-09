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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.view.WearableListView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Strings;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * Created by tkgktyk on 2015/05/02.
 */
public class ExtraActionActivity extends Activity {

    public static final String ACTION_BACK = "back";
    public static final String ACTION_TASKS = "tasks";
    public static final String ACTION_HOME = "home";
    public static final String ACTION_EXIT = "exit";

    @InjectView(R.id.list_view)
    WearableListView mWearableListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extra_action);
        ButterKnife.inject(this);

        mWearableListView.setAdapter(new MyAdapter(this));
        mWearableListView.scrollToPosition(1);
        mWearableListView.setHasFixedSize(true);
        mWearableListView.setClickListener(new WearableListView.ClickListener() {
            @Override
            public void onClick(WearableListView.ViewHolder viewHolder) {
                String action = null;
                switch ((int) mWearableListView.getAdapter().getItemId(viewHolder.getPosition())) {
                    case R.string.action_tasks:
                        action = ACTION_TASKS;
                        break;
                    case R.string.action_back:
                        action = ACTION_BACK;
                        break;
                    case R.string.action_home:
                        action = ACTION_HOME;
                        break;
                    case R.string.action_exit:
                        action = ACTION_EXIT;
                        break;
                }
                if (!Strings.isNullOrEmpty(action)) {
                    setResult(RESULT_OK, new Intent(action));
                    finish();
                } else {
                    MyApp.showToast(R.string.invalid_action);
                }
            }

            @Override
            public void onTopEmptyRegionClick() {
            }
        });
    }

    private class MyAdapter extends WearableListView.Adapter {
        private final int[] mActionIds = {
                R.string.action_tasks,
                R.string.action_back,
                R.string.action_home,
                R.string.action_exit,
        };
        private final LayoutInflater mLayoutInflater;

        public MyAdapter(Context context) {
            super();
            mLayoutInflater = LayoutInflater.from(context);
        }

        @Override
        public WearableListView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = mLayoutInflater.inflate(android.R.layout.simple_list_item_1, viewGroup, false);
            return new MyViewHolder(v);
        }

        @Override
        public void onBindViewHolder(WearableListView.ViewHolder viewHolder, int position) {
            MyViewHolder holder = (MyViewHolder) viewHolder;
            holder.text.setText(mActionIds[position]);
        }

        @Override
        public int getItemCount() {
            return mActionIds.length;
        }

        @Override
        public long getItemId(int position) {
            return mActionIds[position];
        }
    }

    protected class MyViewHolder extends WearableListView.ViewHolder {
        @InjectView(android.R.id.text1)
        TextView text;

        public MyViewHolder(View v) {
            super(v);
            ButterKnife.inject(this, v);
        }

    }
}
