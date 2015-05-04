# Wearable Pad
*****
# Control your Android phone from paired Android Wear
There are some apps to control your PC from Android Wear. 
However I couldn't find apps to control Android phone from Android wear. 
This is just a challenge make that.

# Solution
Android has Input SubSystem, /dev/input/eventX the same as Linux, processing user input from input devices including touch screen.
We can emulate user input by injecting input event to Input SubSystem, but normal user has no permissions and that is protected by SELinux.
Therefore we need **root permission** and **changing SELinux policy**.

## Changing SELinux policy
Use `setenforce` or `supolicy` command to do that.
Refer to

    http://stackoverflow.com/questions/27496968/inject-touch-screen-events-android-5-0-dev-input-eventx.

# Download
Here is an shared apk:

    https://drive.google.com/file/d/0B3ROJmhB_rAydkZCNzRSTEdnQmc/view?usp=sharing

# License
Copyright (C) 2015 Takagi Katsuyuki

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and limitations under the License.
