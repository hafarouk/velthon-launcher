# Quick check of the launcher on one emulated phone type (run once per device in the device matrix).
# Env: NAV=gestural|threebutton  SCALE=<font scale>  LOCALE=<e.g. ar-EG>  NAME=<device label>
set -x
mkdir -p rec
P=com.velthon.launcher
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
adb root >/dev/null 2>&1; sleep 3
adb shell settings put global package_verifier_enable 0
if [ -n "$LOCALE" ]; then
  adb shell "setprop persist.sys.locale $LOCALE; setprop ctl.restart zygote"; sleep 25
  until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done; sleep 5
fi
[ -n "$NAV" ] && adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.$NAV
[ -n "$SCALE" ] && adb shell settings put system font_scale $SCALE
adb install -r -g app.apk > rec/install.txt 2>&1
adb logcat -c
adb shell cmd package set-home-activity $P/.Launcher
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
sleep 15
read W H <<EOF2
$(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
EOF2
X() { echo $(( W * $1 / 100 )); }; Y() { echo $(( H * $1 / 100 )); }
shot() { adb exec-out screencap -p > rec/$1.png; }
dump() { adb shell uiautomator dump /sdcard/s.xml >/dev/null 2>&1; adb pull /sdcard/s.xml rec/$1.xml >/dev/null 2>&1; }
echo "$NAME ${W}x${H} density=$(adb shell wm density | tr -d '\r' | tail -1) sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r') nav=$NAV scale=$SCALE locale=$LOCALE" > rec/device.txt
shot 01_home; dump home
# app list, then search with the keyboard up
adb shell input swipe $(X 50) $(Y 85) $(X 50) $(Y 30) 250; sleep 3; shot 02_drawer; dump drawer
adb shell input tap $(X 50) $(Y 24); sleep 3; shot 03_search_keyboard; dump search
adb shell input text "set"; sleep 2; shot 04_search_typed
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 2
# recent apps and VIP (swipe down), then the Coptic calendar (swipe left)
adb shell input swipe $(X 50) $(Y 35) $(X 50) $(Y 80) 250; sleep 2; shot 05_quick
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input swipe $(X 85) $(Y 55) $(X 15) $(Y 55) 250; sleep 2
adb shell input swipe $(X 85) $(Y 55) $(X 15) $(Y 55) 250; sleep 6; shot 06_church; dump church
adb shell input keyevent KEYCODE_BACK; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 2
# settings
adb shell am start -n $P/.SettingsActivity; sleep 3; shot 07_settings
for k in 0 1 2 3 4 5 6 7 8; do dump settings_$k; adb shell input swipe $(X 50) $(Y 80) $(X 50) $(Y 40) 300; sleep .8; done
adb shell input keyevent KEYCODE_HOME; sleep 2
# lock screen: sleep, wake
adb shell input keyevent KEYCODE_SLEEP; sleep 3
adb shell input keyevent KEYCODE_WAKEUP; sleep 4; shot 08_lock; dump lock
adb shell input swipe $(X 50) $(Y 88) $(X 50) $(Y 30) 200; sleep 3; shot 09_after_unlock
# the screen saver (as when charging)
adb shell settings put secure screensaver_components $P/.VerseDream
adb shell am start -n com.android.systemui/.Somnambulator >/dev/null 2>&1; sleep 6; shot 10_screensaver
adb shell input keyevent KEYCODE_HOME; sleep 2
# rotate request (the launcher stays portrait) and back home
adb shell settings put system user_rotation 1; sleep 2; shot 11_rotated; adb shell settings put system user_rotation 0; sleep 2
# results
adb logcat -d > rec/logcat.txt
grep -A25 -E "FATAL EXCEPTION|ANR in $P" rec/logcat.txt | grep -B2 -A25 "$P" > rec/crash.txt || true
python3 "$(dirname "$0")/layout_check.py" $P rec/*.xml > rec/layout.txt 2>&1
{ cat rec/device.txt; echo "crash lines: $(wc -l < rec/crash.txt)"; echo "layout problems: $(wc -l < rec/layout.txt)"; } > rec/summary.txt
cat rec/summary.txt
