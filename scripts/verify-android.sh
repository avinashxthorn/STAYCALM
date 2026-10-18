#!/usr/bin/env bash
# Preserve the test exit code while collecting screenshots on success or failure.
set +e
gradle --no-daemon :app:connectedDebugAndroidTest --console=plain
result=$?
adb pull /data/local/tmp/staycalm-screenshots ui-screenshots
exit "$result"
