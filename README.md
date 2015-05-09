# Wearable Pad
*****
## Control Android phone/tablet from Wear
There are some apps to control your PC from Android Wear via Android phone/tablet.
However I couldn't find apps to control Android phone from Android wear.
This is just a challenge to do that.

[Movie on Youtube](https://youtu.be/G9Vo8Ck-Mno)

## Solution
Android has Input SubSystem, /dev/input/eventX the same as Linux, processing user input from input devices including touch screen.
We can emulate user input by injecting input event to Input SubSystem, but normal user has no permissions and that is protected by SELinux.
Therefore we need **root permission** and **changing SELinux policy**.

### Changing SELinux policy
Use `setenforce` or `supolicy` command.
I referred to
[stackoverflow:Q27496968](http://stackoverflow.com/questions/27496968/inject-touch-screen-events-android-5-0-dev-input-eventx).

### Select touch screen from eventX
Android device has some /dev/input/eventX, X is number from 0, so you need to know which X links to your touch screen.
The number of touch screen is depending on device.
The following page is helpful for X:
[LMT Launcher's thread on XDA](http://forum.xda-developers.com/showthread.php?t=1330150)

For Nexus4, touch screen is /dev/input/event2, and Ratio X = 200%, and Ratio Y = 200%.

## Pad UI
**IMPORTANT:**
tap (down and up your finger) and longpress to exit this app or perform extra actions.

For v0.2.0 or later

|Handheld|Wear||
|---|---|---|
|Tap|Tap||
|Taps (Up to 15)|Taps||
|Move Cursor|Swipe||
|Swipe|Tap + Swipe|Swipe after single tap|
|Tap + Swipe|Double Tap + Swipe||
|Taps + Swipe|Tap + Taps + Swipe|One more tap|
|Longpress|Long-Longpress|Keep pressing|

Before v0.2.0

|Handheld|Wear|
|---|---|
|Tap|Tap|
|Move Cursor|Swipe|
|Swipe|Longpress + Swipe|
|Tap + Swipe|Double Tap + Swipe|
|Taps + Swipe|Tap + Taps + Swipe|
|Longpress|Long-Longpress|

## Download
On my Google Drive:
[Wearable Pad APKs](https://drive.google.com/folderview?id=0B3ROJmhB_rAyflRVUkkxOW1JWWdYdmtydXdzeEdjaUt0Q25vRC1RbFlmZjZnUVlSbDdaUWs&usp=sharing)
