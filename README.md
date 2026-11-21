# Velthon Launcher

An Android phone launcher with a daily Bible verse over a living, weather-driven window scene,
a Coptic church calendar, and a lock-screen window. By VELTHON Design & Development.

Package `com.velthon.launcher` · current version **3.24** (versionCode 47, in `app/apktool.yml`).

## Layout
| Path | What |
|---|---|
| `src/com/velthon/launcher/` | All Java source (plain Android SDK, API 28 jar, newer APIs via reflection) |
| `app/` | apktool project: manifest, resources, assets (sky pictures, logo, verses) |
| `i18n/ui.txt` | Screen words: English · Arabic · French · Spanish · German · Portuguese (built into `L.java`) |
| `tools/` | `Dis.java` (dex → smali) and `apksign.py` (zip-align + v2 signing) |
| `test/test.sh` | Emulator walk-through: home, swipes, drawer, weather, lock screen; screenshots to `test/rec/` |

## Build
```
ANDROID_JAR=…/android-28/android.jar APKTOOL=…/apktool.jar D8=d8 ./build.sh velthon-launcher.apk
```
Without `KEYSTORE`/`KEY_P12`/`KEY_PASS` the APK is signed with a throw-away debug key (fine for testing,
but it can't update a phone that has the release build). Release keys are never stored in this repository.

## Tests
Every push to `main` builds the APK and runs `test/test.sh` on an Android 11 emulator
(GitHub Actions). The APK and screenshots are attached to the run as artifacts.
