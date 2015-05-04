# Wearable Pad
*****
## Control Android phone/tablet from Wear
There are some apps to control your PC from Android Wear via Android phone/tablet.
However I couldn't find apps to control Android phone from Android wear.
This is just a challenge make that.

[Movie on Youtube](https://youtu.be/G9Vo8Ck-Mno)

## Solution
Android has Input SubSystem, /dev/input/eventX the same as Linux, processing user input from input devices including touch screen.
We can emulate user input by injecting input event to Input SubSystem, but normal user has no permissions and that is protected by SELinux.
Therefore we need **root permission** and **changing SELinux policy**.

### Changing SELinux policy
Use `setenforce` or `supolicy` command to do that.
I refered to
[stackoverflow:Q27496968](http://stackoverflow.com/questions/27496968/inject-touch-screen-events-android-5-0-dev-input-eventx).

### Select touch screen from eventX
Android device has some /dev/input/eventX, X is number from 0, so you need to know which X links to your touch screen.
The number of touch screen is depending on device.
The following page is helpful for X:
[LMT Launcher's thread on XDA](http://forum.xda-developers.com/showthread.php?t=1330150)

For Nexus4, touch screen is /dev/input/event2, and RatioX = 200%, and RatioY = 200%.

## Download
Here is a apk:
[Wearable Pad APK on my Google Drive](https://drive.google.com/file/d/0B3ROJmhB_rAydkZCNzRSTEdnQmc/view?usp=sharing)

## License
Copyright 2015 Takagi Katsuyuki

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and limitations under the License.
