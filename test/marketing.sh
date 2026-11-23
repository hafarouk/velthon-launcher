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
# ---------------------------------------------------------------- how-to screens for the commercial (each with its layout dump)
grab() { shot "$1"; adb shell uiautomator dump /sdcard/m.xml >/dev/null 2>&1; adb pull /sdcard/m.xml "rec/$1.xml" >/dev/null 2>&1; cp "rec/$1.xml" rec/s.xml; }
tapc() { local c; c=$(center "$1"); [ -z "$c" ] && c=$(centerp "$1"); [ -n "$c" ] && adb shell input tap $c; }
findrow() {   # scroll Settings until a row with this text is on screen
  for k in 1 2 3 4 5 6 7 8 9 10 11 12; do dump; c=$(center "$1"); [ -n "$c" ] && { set -- $c; [ "$2" -gt 300 ] && [ "$2" -lt 1900 ] && return 0; }; adb shell input swipe 540 1600 540 1100 300; sleep .8; done; return 1; }
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME; sleep 4
grab m00_home
# the weather: tap it for warnings and the full forecast
c=$(centerp "°"); [ -n "$c" ] && adb shell input tap $c; sleep 2; grab m01_weather_sheet; adb shell input keyevent KEYCODE_BACK; sleep 1
# swipe right: the Coptic page
adb shell input swipe 900 1300 200 1310 250; sleep 6; grab m02_coptic; adb shell input keyevent KEYCODE_BACK; sleep 2
# swipe up: the app list; long-press an app: its menu (Add to VIP)
adb shell input swipe 540 1700 540 800 250; sleep 3; adb shell input keyevent KEYCODE_BACK; sleep 1; grab m03_drawer
c=$(center "Clock"); if [ -n "$c" ]; then set -- $c; adb shell input motionevent DOWN $1 $2; sleep 1.3; adb shell input motionevent UP $1 $2; sleep 2; grab m04_app_menu
  tapc "Add to VIP"; sleep 2; fi
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input swipe 540 1000 540 1700 250; sleep 2; grab m05_vip_panel; adb shell input keyevent KEYCODE_BACK; sleep 1
# Settings: verse language, icon style, dock icons, text colour, the lock screen
adb shell am start -n $P/.SettingsActivity >/dev/null; sleep 3; grab m10_settings_top
findrow "Verse language" && { grab m11_verse_language_row; tapc "Verse language"; sleep 2; grab m12_language_sheet; tapc "French"; sleep 2; grab m13_language_chosen; }
findrow "Icon style" && { grab m14_icon_style_row; tapc "Icon style"; sleep 2; grab m15_icon_style_sheet; tapc "Colour glass (full-colour icon inside the glass)"; sleep 2; grab m16_icon_style_chosen; }
findrow "Dock icons" && { tapc "Dock icons"; sleep 2; grab m17_dock_sheet; adb shell input keyevent KEYCODE_BACK; sleep 1; }
findrow "Text colour" && { tapc "Text colour"; sleep 2; grab m18_text_colour_sheet; adb shell input keyevent KEYCODE_BACK; sleep 1; }
findrow "Velthon lock screen" && { grab m19_lock_row; tapc "Velthon lock screen"; sleep 2; grab m20_lock_sheet; adb shell input keyevent KEYCODE_BACK; sleep 1; }
adb shell input keyevent KEYCODE_HOME; sleep 3; grab m21_home_french_colour
adb shell input keyevent KEYCODE_POWER; sleep 3; adb shell input keyevent KEYCODE_WAKEUP; sleep 4; grab m22_lock
