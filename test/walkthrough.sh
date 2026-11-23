set -x
mkdir -p rec
P=com.velthon.launcher
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
adb shell settings put global package_verifier_enable 0
adb install -r -g app.apk > rec/install.txt 2>&1
adb logcat -c
adb shell am start -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR 6 --ei android.intent.extra.alarm.MINUTES 45 --ez android.intent.extra.alarm.SKIP_UI true; sleep 3
# two calendar events for the lock screen
adb shell "content insert --uri 'content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=v&account_type=LOCAL' --bind account_name:s:v --bind account_type:s:LOCAL --bind name:s:v --bind calendar_displayName:s:Velthon --bind calendar_access_level:i:700 --bind ownerAccount:s:v --bind visible:i:1 --bind sync_events:i:1 --bind calendar_color:i:-16776961"
adb shell content query --uri content://com.android.calendar/calendars --projection _id:name > rec/cals.txt 2>&1
NOW=$(adb shell date +%s | tr -d '\r')
T1=$(( (NOW + 7200) * 1000 )); T2=$(( (NOW + 10800) * 1000 )); T3=$(( (NOW + 90000) * 1000 )); T4=$(( (NOW + 93600) * 1000 ))
adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:1 --bind title:s:"'Bible study at St. Mark'" --bind dtstart:l:$T1 --bind dtend:l:$T2 --bind eventTimezone:s:UTC
adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:1 --bind title:s:"'Divine Liturgy'" --bind dtstart:l:$T3 --bind dtend:l:$T4 --bind eventTimezone:s:UTC
T5=$(( (NOW - 1800) * 1000 )); T6=$(( (NOW + 3600) * 1000 ))
adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:1 --bind title:s:"'Prayer meeting'" --bind dtstart:l:$T5 --bind dtend:l:$T6 --bind eventTimezone:s:UTC
adb shell content query --uri content://com.android.calendar/events --projection title:dtstart > rec/events.txt 2>&1
adb shell cmd package set-home-activity $P/.Launcher
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
sleep 15
adb exec-out screencap -p > rec/01_home.png
# tilt the phone: the sky should move more than the sill
adb emu sensor set acceleration 0:9.81:0; sleep 2
true
adb emu sensor set acceleration 4.5:8.7:3; sleep 2; adb exec-out screencap -p > rec/02_tilted.png
true
adb emu sensor set acceleration 0:9.81:0; sleep 1
dump() { adb shell uiautomator dump /sdcard/s.xml >/dev/null 2>&1; adb pull /sdcard/s.xml rec/s.xml >/dev/null 2>&1; }
centerp() { grep -o "text=\"[^\"]*$1[^\"]*\"[^>]*bounds=\"[^\"]*\"" rec/s.xml | head -1 | sed 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\1 \2 \3 \4/' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'; }
center() { grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" rec/s.xml | head -1 | sed 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\1 \2 \3 \4/' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'; }
# set one saved setting (as root; "cat >" keeps the file's owner so the app can still read it)
setpref() {
  adb shell am force-stop $P; sleep 1
  adb shell cat $F > rec/p.xml
  python3 - "$1" "$2" <<'PY'
import re, sys
k, v = sys.argv[1], sys.argv[2]
s = open('rec/p.xml').read()
s = re.sub(r'\s*<string name="%s">[^<]*</string>' % re.escape(k), '', s)
if v != '-': s = s.replace('</map>', '<string name="%s">%s</string>\n</map>' % (k, v))
open('rec/p.xml', 'w').write(s)
PY
  adb push rec/p.xml /data/local/tmp/p.xml >/dev/null; adb shell "cat /data/local/tmp/p.xml > $F"
}
# put Clock, Calculator and a folder (Settings + Contacts + Files) on the home screen via the saved settings
adb root >/dev/null 2>&1; sleep 3
F=/data/data/$P/shared_prefs/velthon.xml
adb shell am force-stop $P; sleep 1
adb shell cat $F > rec/prefs.xml
adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --components > rec/acts.txt 2>&1
python3 - <<'PY'
import re
s=open('rec/prefs.xml').read()
acts=[l.strip() for l in open('rec/acts.txt') if '/' in l]
def key(pkgpart):
    for a in acts:
        if pkgpart in a:
            p,c=a.split('/'); c=p+c if c.startswith('.') else c
            return p+'/'+c
    return None
k={n:key(n) for n in ['deskclock','settings/','contacts','documentsui','camera2']}
print(k)
fold='\u0001'.join(['77','Tools',','.join(x for x in [k['contacts'],k['documentsui'],k['camera2']] if x)])
desk='0=folder:77;1=%s;2=%s;3=%s;4=%s'%(k['deskclock'],k['settings/'],k['contacts'],k['documentsui'])
s=re.sub(r'<string name="(desk|hfolders|widgets)">[^<]*</string>','',s)
s=s.replace('</map>','<string name="desk">'+desk+'</string>\n<string name="hfolders">'+fold+'</string>\n</map>')
open('rec/prefs_new.xml','w').write(s)
PY
adb push rec/prefs_new.xml /data/local/tmp/v.xml >/dev/null; adb shell "cat /data/local/tmp/v.xml > $F"
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME; sleep 8
adb exec-out screencap -p > rec/70_home_items.png; adb shell uiautomator dump /sdcard/h.xml >/dev/null 2>&1; adb pull /sdcard/h.xml rec/home.xml >/dev/null 2>&1; adb shell cat $F > rec/prefs_after.xml
# v3.5: gestures, page 2, Coptic page, recent/VIP panel, app list, widget move, text size
shot() { adb exec-out screencap -p > rec/$1.png; }
# ---------------------------------------------------------------- real screens for the commercial, nothing edited afterwards
grab() { shot "$1"; adb shell uiautomator dump /sdcard/m.xml >/dev/null 2>&1; adb pull /sdcard/m.xml "rec/$1.xml" >/dev/null 2>&1; cp "rec/$1.xml" rec/s.xml; }
tapc() { local c; c=$(center "$1"); [ -z "$c" ] && c=$(centerp "$1"); [ -n "$c" ] && adb shell input tap $c; }
findrow() { local want="$1" k c y
  for k in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16; do dump; c=$(center "$want")
    if [ -n "$c" ]; then y=${c#* }; [ "$y" -gt 300 ] && [ "$y" -lt 1900 ] && return 0; fi
    adb shell input swipe 540 1600 540 1100 300; sleep .8; done; return 1; }
home() { adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; sleep ${1:-5}; }
settime() { adb shell "date -u $1" >/dev/null; }      # MMDDhhmmYYYY.ss in UTC
# Toronto, a real morning (8:20 am), gesture navigation, the phone's own apps only
adb shell settings put global auto_time 0; adb shell settings put global auto_time_zone 0
adb shell service call alarm 3 s16 America/Toronto >/dev/null
settime 112313202026.00
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural; sleep 3
for p in com.android.chrome com.google.android.gm com.google.android.apps.maps com.google.android.youtube com.google.android.apps.youtube.music \
         com.google.android.apps.photos com.google.android.apps.docs com.google.android.videos com.google.android.googlequicksearchbox \
         com.google.android.music com.android.vending com.google.android.apps.googleassistant com.google.android.apps.wellbeing; do
  adb shell pm disable-user --user 0 $p >/dev/null 2>&1; done
python3 - <<'PY'
acts=[l.strip() for l in open('rec/acts.txt') if '/' in l]
def key(part):
    for a in acts:
        if part in a:
            p,c=a.split('/'); return p+'/'+(p+c if c.startswith('.') else c)
dock='|'.join(k for k in [key('dialer'),key('messaging'),key('camera2'),key('contacts'),key('documentsui'),key('deskclock')] if k)
open('rec/dock.txt','w').write(dock)
PY
setpref dock "$(cat rec/dock.txt)"
setpref city Toronto
adb shell "sed -i -e 's/<long name=\"lat\" value=\"[-0-9]*\"/<long name=\"lat\" value=\"4631340459036172196\"/' -e 's/<long name=\"lon\" value=\"[-0-9]*\"/<long name=\"lon\" value=\"-4588085523825101583\"/' $F"
setpref langs en
adb shell settings put system show_touches 1
home 15; home 10
rec() { adb shell "screenrecord --bit-rate 14000000 --time-limit 60 /sdcard/$1.mp4" & RP=$!; sleep 1.5; }
stop() { sleep 1; adb shell pkill -INT screenrecord; wait $RP 2>/dev/null; sleep 1; adb pull /sdcard/$1.mp4 rec/$1.mp4 >/dev/null 2>&1; ls -la rec/$1.mp4; }
# 1. home, morning: the living sky, the verse
rec w01_home; sleep 6; stop w01_home
# 2. swipe up: the app list, scroll; back; swipe down: recent apps and VIP
rec w02_apps
adb shell input swipe 540 1700 540 700 300; sleep 2.5; adb shell input keyevent KEYCODE_BACK; sleep 1.5
adb shell input swipe 540 1500 540 900 400; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input swipe 540 900 540 1700 300; sleep 3; adb shell input keyevent KEYCODE_BACK; sleep 1.5
stop w02_apps
# 3. swipe left: the Coptic page, scroll the week, next week
home 3
rec w03_coptic
adb shell input swipe 900 1300 200 1310 300; sleep 4
adb shell input swipe 540 1600 540 900 500; sleep 2.5
dump; c=$(center "›"); [ -n "$c" ] && { adb shell input tap $c; sleep 2.5; }
adb shell input keyevent KEYCODE_BACK; sleep 2
stop w03_coptic
# 4. Settings: verse language -> French; back home in French
adb shell am start -n $P/.SettingsActivity >/dev/null; sleep 3
rec w04_language
sleep 1; tapc "Verse language"; sleep 2; tapc "French"; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 5
stop w04_language
# 5. Settings: icon style -> colour glass; home
adb shell am start -n $P/.SettingsActivity >/dev/null; sleep 3
findrow "Icon style"
rec w05_icons
sleep 1; tapc "Icon style"; sleep 2; tapc "Colour glass (full-colour icon inside the glass)"; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 5
stop w05_icons
setpref langs en; home 8
# 6. the lock screen: screen off, on: Velthon's lock, then swipe to unlock
adb shell appops set $P SYSTEM_ALERT_WINDOW allow
adb shell locksettings set-disabled false >/dev/null 2>&1
adb shell input keyevent KEYCODE_SLEEP; sleep 4
rec w06_lock
adb shell input keyevent KEYCODE_WAKEUP; sleep 5; adb shell input swipe 540 1800 540 500 300; sleep 3
stop w06_lock
# 7. a still of the lock for the story scene
adb shell input keyevent KEYCODE_SLEEP; sleep 4; adb shell input keyevent KEYCODE_WAKEUP; sleep 1.5; grab w07_lock_still
adb shell date
