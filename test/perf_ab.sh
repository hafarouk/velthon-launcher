#!/bin/bash
# Old against new on the same emulator: start times, the calendar page, smoothness, memory and CPU.
# Each version runs twice, in the order old, new, old, new, so a slow minute on the machine hits both.
mkdir -p rec
P=com.velthon.launcher
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
adb shell settings put global package_verifier_enable 0
# a calendar with a few events, the same for both versions
adb shell "content insert --uri 'content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=v&account_type=LOCAL' --bind account_name:s:v --bind account_type:s:LOCAL --bind name:s:v --bind calendar_displayName:s:Velthon --bind calendar_access_level:i:700 --bind ownerAccount:s:v --bind visible:i:1 --bind sync_events:i:1 --bind calendar_color:i:-16776961"
NOW=$(adb shell date +%s | tr -d '\r')
for k in 1 2 3 4 5 6 7 8 9 10 11 12; do
  S=$(( (NOW + k * 43200) * 1000 )); E=$(( S + 3600000 ))
  adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:1 --bind title:s:"'Event $k'" --bind dtstart:l:$S --bind dtend:l:$E --bind eventTimezone:s:UTC
done
med() { sort -n | awk '{a[NR]=$1} END {print (NR%2 ? a[(NR+1)/2] : int((a[NR/2]+a[NR/2+1])/2))}'; }
run() {   # $1 = old|new, $2 = round
  V=$1; R=rec/$V-$2.txt; : > $R
  adb uninstall $P >/dev/null 2>&1
  adb install -r -g $V.apk >/dev/null
  adb shell cmd package set-home-activity $P/.Launcher >/dev/null
  adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
  sleep 25   # first run: weather, Synaxarium, holidays download
  adb shell am start -W -n $P/.ChurchActivity >/dev/null; sleep 8; adb shell input keyevent KEYCODE_BACK; sleep 2
  for i in 1 2 3 4 5; do adb shell am force-stop $P; sleep 2
    adb shell am start -W -n $P/.Launcher | grep TotalTime | tr -dc '0-9\n' | grep . ; sleep 4; done | med | sed 's/^/cold_start_ms /' >> $R
  for i in 1 2 3 4 5; do adb shell am start -n com.android.settings/.Settings >/dev/null; sleep 3
    adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME | grep TotalTime | tr -dc '0-9\n' | grep . ; sleep 3; done | med | sed 's/^/warm_start_ms /' >> $R
  adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; sleep 3
  for i in 1 2 3 4 5; do adb logcat -c; adb shell am start -n $P/.ChurchActivity >/dev/null; sleep 4
    adb logcat -d | grep -m1 'Displayed com.velthon.launcher/.ChurchActivity' | sed -E 's/.*: \+//; s/ .*//' \
      | awk '{s=0; if (match($0,/[0-9]+s/)) s=substr($0,RSTART,RLENGTH-1)*1000; m=0; if (match($0,/[0-9]+ms/)) m=substr($0,RSTART,RLENGTH-2); print s+m}'
    adb shell input keyevent KEYCODE_BACK; sleep 2; done | med | sed 's/^/calendar_open_ms /' >> $R
  # smoothness: scrolling the calendar page, then the home screen
  adb shell am start -n $P/.ChurchActivity >/dev/null; sleep 4
  adb shell dumpsys gfxinfo $P reset >/dev/null
  for i in 1 2 3 4; do adb shell input swipe 540 1700 540 600 250; sleep 1; adb shell input swipe 540 600 540 1700 250; sleep 1; done
  adb shell dumpsys gfxinfo $P > rec/gfx.txt
  echo "calendar_frames $(grep -m1 'Total frames rendered' rec/gfx.txt | tr -dc '0-9')" >> $R
  echo "calendar_janky_pct $(grep -m1 'Janky frames' rec/gfx.txt | sed 's/.*(\(.*\)%).*/\1/')" >> $R
  echo "calendar_p50_ms $(grep -m1 '50th percentile' rec/gfx.txt | sed -E 's/.*: *([0-9]+)ms.*/\1/')" >> $R
  echo "calendar_p90_ms $(grep -m1 '90th percentile' rec/gfx.txt | sed -E 's/.*: *([0-9]+)ms.*/\1/')" >> $R
  adb shell dumpsys meminfo $P | grep -m1 'TOTAL PSS' | awk '{print "calendar_memory_kb", $3}' >> $R
  adb shell input keyevent KEYCODE_BACK; sleep 2
  adb shell dumpsys gfxinfo $P reset >/dev/null
  for i in 1 2 3; do
    adb shell input swipe 540 1700 540 700 250; sleep 1.2; adb shell input keyevent KEYCODE_BACK; sleep .8
    adb shell input swipe 540 700 540 1500 250; sleep 1.2; adb shell input keyevent KEYCODE_BACK; sleep .8
  done
  adb shell dumpsys gfxinfo $P > rec/gfx.txt
  echo "home_janky_pct $(grep -m1 'Janky frames' rec/gfx.txt | sed 's/.*(\(.*\)%).*/\1/')" >> $R
  echo "home_p50_ms $(grep -m1 '50th percentile' rec/gfx.txt | sed -E 's/.*: *([0-9]+)ms.*/\1/')" >> $R
  echo "home_p90_ms $(grep -m1 '90th percentile' rec/gfx.txt | sed -E 's/.*: *([0-9]+)ms.*/\1/')" >> $R
  sleep 20
  adb shell dumpsys meminfo $P | grep -m1 'TOTAL PSS' | awk '{print "home_memory_kb", $3}' >> $R
  pid=$(adb shell pidof $P | tr -d '\r')
  a=$(adb shell cat /proc/$pid/stat | awk '{print $14+$15}'); sleep 30; b=$(adb shell cat /proc/$pid/stat | awk '{print $14+$15}')
  echo "cpu_at_rest_pct $(awk -v a=$a -v b=$b 'BEGIN {printf "%.2f", (b-a)/30}')" >> $R   # clock ticks are 1/100 s
  echo "holidays_cached $(adb shell cat /data/data/$P/shared_prefs/velthon.xml 2>/dev/null | grep -c 'hol_at_')" >> $R
  cat $R
}
run old 1; run new 1; run old 2; run new 2
ls -la old.apk new.apk | awk '{print $5, $9}' > rec/apk_size.txt
python3 - <<'PY' > rec/summary.txt
import collections
v = {}
for name in ('old', 'new'):
    d = collections.defaultdict(list)
    for r in (1, 2):
        for line in open('rec/%s-%d.txt' % (name, r)):
            k, _, x = line.strip().partition(' ')
            try: d[k].append(float(x))
            except ValueError: pass
    v[name] = {k: sum(x) / len(x) for k, x in d.items()}
sizes = dict(reversed(l.split()) for l in open('rec/apk_size.txt'))
print('%-20s %12s %12s %9s' % ('measure', 'v3.71 (old)', 'v3.73 (new)', 'change'))
for k in v['old']:
    a, b = v['old'][k], v['new'].get(k, float('nan'))
    ch = '' if a == 0 else '%+.0f%%' % ((b - a) / a * 100)
    print('%-20s %12.1f %12.1f %9s' % (k, a, b, ch))
a, b = int(sizes['old.apk']), int(sizes['new.apk'])
print('%-20s %12d %12d %9s' % ('apk_size_bytes', a, b, '%+.1f%%' % ((b - a) / a * 100)))
PY
cat rec/summary.txt
