#!/usr/bin/env bash
# Preserve the test exit code while collecting screenshots on success or failure.
set +e
gradle --no-daemon :app:connectedDebugAndroidTest --console=plain
result=$?
adb pull /sdcard/Android/data/com.staycalm.focus/files/screenshots ui-screenshots
exit "$result"
