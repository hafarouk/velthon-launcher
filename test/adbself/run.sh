#!/bin/bash
# Runs inside the emulator job: the TV app's own ADB client against a real adbd (port 5555 of the emulator).
set -x
cd "$(dirname "$0")"
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
javac -nowarn -d cls Main.java
PUB=$(java -cp cls Main keys pub)
echo "secure: $(adb shell getprop ro.adb.secure)"
echo "== 1: a key the TV has never seen: must be refused until allowed (expect a timeout or an error)"
timeout 20 java -cp cls Main keys run "echo NOPE" ; echo "exit $?"
echo "== 2: allow the key (what 'Always allow' does on the TV), then connect"
adb root; sleep 3; adb wait-for-device
adb shell "echo '$PUB' >> /data/misc/adb/adb_keys"
adb unroot; sleep 3; adb wait-for-device
OUT=$(timeout 60 java -cp cls Main keys run "pm grant com.android.shell android.permission.WRITE_SECURE_SETTINGS; settings put secure screensaver_components com.android.deskclock/.Screensaver; settings get secure screensaver_components; echo VELTHON_DONE")
echo "$OUT"
echo "$OUT" | grep -q VELTHON_DONE && echo "$OUT" | grep -q deskclock && echo "RESULT: PASS" || echo "RESULT: FAIL"
