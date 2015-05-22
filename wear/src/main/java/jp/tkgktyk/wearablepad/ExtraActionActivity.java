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

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import com.google.common.base.Strings;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

/**
 * Created by tkgktyk on 2015/05/02.
 */
public class ExtraActionActivity extends WearableActivity {

    public static final String ACTION_BACK = "back";
    public static final String ACTION_TASKS = "tasks";
    public static final String ACTION_HOME = "home";
    public static final String ACTION_STATUSBAR = "statusbar";
    public static final String ACTION_EXIT = "exit";

    @InjectView(R.id.back_button)
    ImageButton mBackButton;
    @InjectView(R.id.home_button)
    ImageButton mHomeButton;
    @InjectView(R.id.tasks_button)
    ImageButton mTasksButton;
    @InjectView(R.id.statusbar_button)
    Button mStatusbarButton;
    @InjectView(R.id.exit_button)
    Button mExitButton;

    @OnClick({R.id.back_button, R.id.home_button, R.id.tasks_button,
            R.id.statusbar_button, R.id.exit_button})
    void performButtonClick(View button) {
        String action = null;
        switch (button.getId()) {
            case R.id.back_button:
                action = ACTION_BACK;
                break;
            case R.id.home_button:
                action = ACTION_HOME;
                break;
            case R.id.tasks_button:
                action = ACTION_TASKS;
                break;
            case R.id.statusbar_button:
                action = ACTION_STATUSBAR;
                break;
            case R.id.exit_button:
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extra_action);
        ButterKnife.inject(this);

        setAmbientEnabled();
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);

        finish();
    }
}
