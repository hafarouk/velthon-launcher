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
adb shell input swipe 200 1300 900 1310 250; sleep 3; shot 10_page2
adb shell input swipe 900 1300 200 1310 250; sleep 3; shot 11_back_page1
adb shell pm grant $P android.permission.READ_CALENDAR 2>/dev/null
adb shell input swipe 900 1300 200 1310 250; sleep 9; shot 12_church
dump; cp rec/s.xml rec/church.xml
adb shell input swipe 300 900 300 1400 300; sleep 2; shot 12b_church_scrolled
# one page: the church day, then your next events, then the month with feasts and events
adb shell input swipe 540 1700 540 500 300; sleep 2; shot 12c_church_events
adb shell input swipe 540 1700 540 900 300; sleep 1.5; shot 12d_calendar_week_events
dump; grep -c "Prayer meeting" rec/s.xml > rec/next_events.txt
c=$(center "›"); [ -n "$c" ] && adb shell input tap $c; sleep 2; shot 12e_calendar_next_week
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input swipe 540 1000 540 1700 250; sleep 2; shot 13_quick
adb shell input swipe 540 1500 540 600 250; sleep 2
adb shell input swipe 540 1700 540 800 250; sleep 3; shot 14_search
adb shell input keyevent KEYCODE_BACK; sleep 2; shot 15_drawer
dump; cp rec/s.xml rec/drawer.xml
c=$(grep -o 'content-desc="Calendar"[^>]*bounds="[^"]*"' rec/s.xml | head -1 | sed 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\1 \2 \3 \4/' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'); [ -z "$c" ] && c=$(center "Calendar")
if [ -n "$c" ]; then set -- $c; adb shell input motionevent DOWN $1 $2; sleep 1.2; adb shell input motionevent UP $1 $2; sleep 2; shot 16_app_menu
  dump; c2=$(centerp "Add to VIP"); [ -n "$c2" ] && adb shell input tap $c2; sleep 2; fi
adb shell input swipe 900 1700 200 1710 250; sleep 2; shot 17_drawer_next_cat
adb shell input swipe 900 1700 200 1710 250; sleep 2; shot 18_drawer_next_cat2
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input swipe 540 1000 540 1700 250; sleep 2; shot 19_quick_with_vip
adb shell input keyevent KEYCODE_BACK; sleep 1
# the phone's text size
adb shell appops set $P WRITE_SETTINGS allow
adb shell settings put system font_scale 1.3; sleep 6; adb shell input keyevent KEYCODE_HOME; sleep 3; shot 26_font_large
adb shell settings put system font_scale 1.0; sleep 4
# living weather: tap the sky, then rain, snow, storm
adb shell input keyevent KEYCODE_HOME; sleep 2; shot 30_sun_a; sleep 1; shot 30_sun_b
adb shell input tap 540 330; sleep .5; shot 31_poke
for code in 63 73 95; do
  adb shell am force-stop $P; sleep 1
  adb shell "sed -i -e 's/<int name=\"wx_code\" value=\"[0-9]*\"/<int name=\"wx_code\" value=\"'$code'\"/' $F"
  adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME; sleep 7; shot 32_wx_$code
  adb shell input tap 540 330; sleep .4; shot 33_poke_$code
done
# a clear night with the moon (the location moved to where it is night now)
adb shell am force-stop $P; sleep 1
adb shell "sed -i -e 's/<int name=\"wx_code\" value=\"[0-9]*\"/<int name=\"wx_code\" value=\"0\"/' -e 's/<long name=\"lat\" value=\"[-0-9]*\"/<long name=\"lat\" value=\"4629137466983448576\"/' -e 's/<long name=\"lon\" value=\"[-0-9]*\"/<long name=\"lon\" value=\"4636033603912859648\"/' $F"
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME; sleep 8; shot 34_night
# the lock screen, then home again after unlocking (everything must be visible)
adb shell appops set $P SYSTEM_ALERT_WINDOW allow
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_HOME; sleep 2; shot 39_before_lock
adb shell locksettings set-disabled false >/dev/null 2>&1
adb shell input keyevent KEYCODE_SLEEP; sleep 5
adb shell input keyevent KEYCODE_WAKEUP; sleep .2; shot 40a_wake; sleep .4; shot 40b_wake; sleep 3; shot 40_lock
adb shell input swipe 540 1800 540 500 200; sleep 3; shot 41_home_after_unlock
adb shell input swipe 200 1300 900 1310 250; sleep 2; adb shell input swipe 900 1300 200 1310 250; sleep 3; shot 42_after_page_swipes
adb shell input keyevent KEYCODE_SLEEP; sleep 4; adb shell input keyevent KEYCODE_WAKEUP; sleep 2
adb shell input swipe 900 1500 200 1500 150; sleep 3; shot 43_unlock_by_side_swipe
# lock while another app is open (the case that failed on the phone)
adb shell appops set $P SYSTEM_ALERT_WINDOW deny
adb shell monkey -p com.google.android.deskclock -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 3; shot 50_other_app
adb shell input keyevent KEYCODE_SLEEP; sleep 6
adb shell input keyevent KEYCODE_WAKEUP; sleep 4; shot 51_lock_from_app
adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" > rec/lock2.txt
adb shell dumpsys notification | grep -iE "velthon_lock|fullscreen|NotificationRecord.*velthon" | head -5 >> rec/lock2.txt
# a call / screen-off in the middle of closing the app list must not leave an invisible cover
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input swipe 540 1700 540 800 200; sleep 1.5
adb shell "input keyevent KEYCODE_BACK; input keyevent KEYCODE_SLEEP"; sleep 3
adb shell input keyevent KEYCODE_WAKEUP; sleep 3; adb shell input swipe 540 1800 540 500 200; sleep 4; shot 55_after_interrupted_close
adb shell input swipe 540 1700 540 800 200; sleep 1.5
adb shell "input keyevent KEYCODE_HOME; input keyevent KEYCODE_SLEEP"; sleep 3
adb shell input keyevent KEYCODE_WAKEUP; sleep 3; adb shell input swipe 540 1800 540 500 200; sleep 4; shot 56_after_interrupted_close2
adb shell input swipe 540 1000 540 1700 250; sleep 2; shot 57_still_responds
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell run-as $P cat shared_prefs/velthon.xml | grep -A14 lock_log > rec/locklog.txt
adb shell am start -n $P/.SettingsActivity >/dev/null; sleep 3
for k in 1 2 3 4 5 6 7 8; do adb shell input swipe 540 1700 540 500 200; sleep .6; done; sleep 1; shot 66_settings_bottom
adb shell input swipe 540 700 540 1500 300; sleep 1; shot 67_settings_lock
adb shell input swipe 540 700 540 1500 300; sleep 1; shot 68_settings_lock2
# French and Spanish: the verse, the church line and the whole Coptic page follow the chosen language
for lg in fr es; do
  adb shell am force-stop $P; setpref langs $lg; adb shell am start -n $P/.Launcher >/dev/null; sleep 5; shot 90_home_$lg
  adb shell am start -n $P/.ChurchActivity >/dev/null; sleep 4; shot 91_church_$lg
  adb shell input swipe 540 1700 540 700 300; sleep 1.5; shot 92_church_week_$lg
  adb shell input keyevent KEYCODE_BACK; sleep 1
done
adb shell am force-stop $P; setpref langs en
# every settings screen, long values set (colour glass icons, automatic text colour): no name may be squeezed or covered
adb shell am force-stop $P; setpref icon_style colorglass; adb shell am start -n $P/.SettingsActivity >/dev/null; sleep 3
for k in 0 1 2 3 4 5 6 7 8 9 10; do adb shell uiautomator dump /sdcard/st.xml >/dev/null 2>&1; adb pull /sdcard/st.xml rec/settings_$k.xml >/dev/null 2>&1; adb shell input swipe 540 1700 540 900 300; sleep .8; done
python3 "$(dirname "$0")/layout_check.py" $P rec/settings_*.xml rec/home.xml rec/church.xml > rec/layout.txt 2>&1
echo "== layout check (empty = no squeezed or overlapping text)"; cat rec/layout.txt
adb logcat -d -s velthon-desk | tail -30 > rec/desk.txt
# themes: gold text, colour-glass icons, a coloured folder
adb shell input keyevent KEYCODE_HOME; sleep 1
setpref text_theme gold; adb shell am start -n $P/.Launcher >/dev/null; sleep 6; shot 80_text_gold
setpref text_theme -; setpref icon_style colorglass
adb shell am start -n $P/.Launcher >/dev/null; sleep 6; shot 82_icons_colorglass
adb shell input swipe 540 1700 540 800 250; sleep 2; shot 83_drawer_colorglass; adb shell input keyevent KEYCODE_BACK; sleep 1
setpref icon_style -
adb shell "cat $F" | grep -o 'fcolor[^<]*' > rec/fcolor.txt
folderkey=$(adb shell "cat $F" | grep -o 'folder:[0-9]*' | head -1)
adb shell am force-stop $P; sleep 1; adb shell cat $F > rec/p.xml
python3 - "$folderkey" <<'PY'
import sys
s=open('rec/p.xml').read(); k=sys.argv[1]
if k: s=s.replace('</map>','<int name="fcolor_%s" value="-1725009992" />\n</map>' % k)
open('rec/p.xml','w').write(s)
PY
adb push rec/p.xml /data/local/tmp/p.xml >/dev/null; adb shell "cat /data/local/tmp/p.xml > $F"
adb shell am start -n $P/.Launcher >/dev/null; sleep 6; shot 84_folder_blue
# ---------------- performance & load: start time, smoothness, memory, CPU at rest and in the background
adb shell input keyevent KEYCODE_HOME; sleep 2
{
echo "== cold start (3 runs)"
for i in 1 2 3; do adb shell am force-stop $P; sleep 2; adb shell am start -W -n $P/.Launcher | grep -E "TotalTime|WaitTime"; sleep 4; done
echo "== warm start (home pressed from another app, 3 runs)"
for i in 1 2 3; do adb shell am start -n com.android.settings/.Settings >/dev/null; sleep 3; adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME | grep -E "TotalTime|WaitTime"; sleep 3; done
echo "== smoothness while using it (swipes, app list, pages, panel)"
adb shell dumpsys gfxinfo $P reset >/dev/null
for i in 1 2 3; do
  adb shell input swipe 540 1700 540 700 250; sleep 1.2; adb shell input swipe 540 900 540 1700 250; sleep 1.2
  adb shell input keyevent KEYCODE_BACK; sleep .8
  adb shell input swipe 900 1300 200 1310 250; sleep 1.2; adb shell input swipe 200 1300 900 1310 250; sleep 1.2
  adb shell input swipe 540 700 540 1500 250; sleep 1.2; adb shell input keyevent KEYCODE_BACK; sleep .8
done
adb shell dumpsys gfxinfo $P | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Slow|Number Frozen"
echo "== baseline: the phone's own Settings app scrolling on this same emulator (software graphics)"
adb shell am start -n com.android.settings/.Settings >/dev/null; sleep 3
adb shell dumpsys gfxinfo com.android.settings reset >/dev/null
for i in 1 2 3 4 5 6; do adb shell input swipe 540 1600 540 600 250; sleep 1; adb shell input swipe 540 600 540 1600 250; sleep 1; done
adb shell dumpsys gfxinfo com.android.settings | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile"
adb shell input keyevent KEYCODE_HOME; sleep 3
echo "== where the launcher's time goes (CPU profile while using it)"
pid=$(adb shell pidof $P)
(adb shell simpleperf record -p $pid -e cpu-clock -f 1000 -g --duration 14 -o /data/local/tmp/perf.data > rec/simpleperf_record.txt 2>&1 &)
sleep 1
for i in 1 2; do
  adb shell input swipe 540 1700 540 700 250; sleep 1.2; adb shell input keyevent KEYCODE_BACK; sleep .8
  adb shell input swipe 900 1300 200 1310 250; sleep 1.2; adb shell input swipe 200 1300 900 1310 250; sleep 1.2
  adb shell input swipe 540 700 540 1500 250; sleep 1.2; adb shell input keyevent KEYCODE_BACK; sleep .8
done
sleep 4
sleep 3; cat rec/simpleperf_record.txt
adb shell simpleperf report -i /data/local/tmp/perf.data --sort comm,dso --percent-limit 2 2>&1 | head -40
adb shell simpleperf report -i /data/local/tmp/perf.data --sort symbol --percent-limit 1 2>&1 | head -60
echo "-- launcher's own code"
adb shell simpleperf report -i /data/local/tmp/perf.data --sort symbol --percent-limit 0.2 2>&1 | grep -i "velthon" | head -40
echo "== memory"
adb shell dumpsys meminfo $P | grep -E "TOTAL PSS|TOTAL RSS|Native Heap|Dalvik Heap|Graphics|TOTAL:" | head -8
echo "== CPU at rest on the home screen (after the 1-minute rest)"
sleep 70
adb shell top -b -n 3 -d 5 -o PID,%CPU,RES,NAME | grep -E "velthon" 
echo "== CPU while another app is in front (30 s)"
adb shell am start -n com.android.settings/.Settings >/dev/null; sleep 30
adb shell top -b -n 3 -d 5 -o PID,%CPU,RES,NAME | grep -E "velthon"
echo "== threads"
pid=$(adb shell pidof $P); adb shell ls /proc/$pid/task | wc -l
echo "== alarms and wake locks held"
adb shell dumpsys alarm | grep -c "$P"
adb shell dumpsys power | grep -i "$P" | head -5
echo "== app list reload under load: an app turned off and on 3 times (real package events)"
for i in 1 2 3; do adb shell pm disable-user --user 0 com.android.camera2 >/dev/null 2>&1; sleep 1; adb shell pm enable com.android.camera2 >/dev/null 2>&1; sleep 1; done
sleep 4; adb shell pidof $P >/dev/null && echo "launcher still running" || echo "LAUNCHER DIED"
adb logcat -d | grep -E "FATAL EXCEPTION|Process: $P" | head -5
adb shell input keyevent KEYCODE_HOME
} > rec/perf.txt 2>&1
adb logcat -d -s velthon-ink | tail -80 > rec/ink.txt
adb logcat -d -b crash > rec/crash.txt
adb logcat -d | grep -iE "velthon|AndroidRuntime" | tail -60 > rec/log.txt
adb logcat -d -s velthon-sill | tail -40 > rec/sill.txt
