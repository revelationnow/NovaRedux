#!/usr/bin/env bash
# Runs the screenshot tour (app/src/androidTest/.../ScreenshotTour.kt) on the
# emulator the Screens workflow started, then prints every screenshot into the
# job log as base64 between SCREEN-BEGIN and SCREEN-END lines, so it can be
# read back from the log alone. Fails if the tour fails or the app crashed.
#
# TOUR_ARGS holds extra instrumentation arguments, e.g.
#   -e pref.b.dock_enabled false -e shots home,drawer
set -euo pipefail

app=io.github.revelationnow.novaredux.debug
apk=app/build/outputs/apk/debug/app-debug.apk
tests=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
out=screens

adb install -r -g "$apk"
adb install -r "$tests"
# NovaRedux Connect, when built, for web suggestions in the search shot.
connect=connect/build/outputs/apk/debug/connect-debug.apk
if [ -f "$connect" ]; then adb install -r "$connect"; fi
adb shell cmd role add-role-holder --user 0 android.app.role.HOME "$app" \
    || echo "Could not make $app the home app; the tour starts it by class"
adb logcat -c

read -r -a extra <<< "${TOUR_ARGS:-}"
adb shell am instrument -w -r "${extra[@]}" -e class com.supernova.launcher.ScreenshotTour \
    "$app.test/androidx.test.runner.AndroidJUnitRunner" | tee tour.txt

mkdir -p "$out"
adb pull "/sdcard/Android/data/$app/files/screens/." "$out/" || echo "No screenshots to pull"

for png in "$out"/*.png; do
    [ -e "$png" ] || continue
    name=$(basename "$png" .png)
    convert "$png" -resize 540x -quality 72 "$out/$name.jpg"
    echo "SCREEN-BEGIN $name"
    base64 -w 4000 "$out/$name.jpg"
    echo "SCREEN-END $name"
done

failed=0
if ! grep -q "^OK (" tour.txt; then
    echo "::error::The screenshot tour failed; see the instrumentation output above."
    failed=1
fi
crashes=$(adb logcat -d -b crash | grep -A 40 -E "Process: $app" || true)
if [ -n "$crashes" ]; then
    echo "::error::$app crashed during the tour."
    echo "$crashes"
    failed=1
fi
exit "$failed"
