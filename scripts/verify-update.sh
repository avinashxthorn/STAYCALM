#!/usr/bin/env bash
# Actual Android install -r, using the same debug key within this CI job.
set -euo pipefail
PKG=com.staycalm.focus
PROBE="$RUNNER_TEMP/staycalm-update-probe"
mkdir -p "$PROBE"
python3 - "$PROBE" <<'PY'
import hashlib, pathlib, sys
p=pathlib.Path(sys.argv[1])
name='account_'+hashlib.sha256(b'upgrade-probe').hexdigest()
(p/'prefname').write_text(name)
(p/'settings.xml').write_text('<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><boolean name="profile_ready" value="true"/><string name="profile_name">Update retention probe</string><boolean name="dark_theme" value="true"/><int name="daily_goal" value="150"/><int name="quote_interval" value="180"/><string name="logs">[]</string></map>')
(p/'planner-file').write_bytes(b'STAYCALM private planner update retention probe\n')
PY
PREFNAME="$(cat "$PROBE/prefname")"
ACCOUNT_HASH="${PREFNAME#account_}"
gradle --no-daemon :app:assembleDebug --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb push "$PROBE/settings.xml" /data/local/tmp/staycalm-update-settings.xml
adb push "$PROBE/planner-file" /data/local/tmp/staycalm-update-file
adb shell run-as "$PKG" mkdir -p shared_prefs "files/accounts/$ACCOUNT_HASH/journal"
adb shell run-as "$PKG" cp /data/local/tmp/staycalm-update-settings.xml "shared_prefs/$PREFNAME.xml"
adb shell run-as "$PKG" cp /data/local/tmp/staycalm-update-file "files/accounts/$ACCOUNT_HASH/journal/probe"
FIRST_UID="$(adb shell dumpsys package "$PKG" | sed -n 's/.*userId=\([0-9]*\).*/\1/p' | head -1 | tr -d '\r')"
STAYCALM_VERSION_CODE="$((STAYCALM_VERSION_CODE + 1))" gradle --no-daemon :app:assembleDebug --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
SECOND_UID="$(adb shell dumpsys package "$PKG" | sed -n 's/.*userId=\([0-9]*\).*/\1/p' | head -1 | tr -d '\r')"
test -n "$FIRST_UID" && test "$FIRST_UID" = "$SECOND_UID"
adb exec-out run-as "$PKG" cat "shared_prefs/$PREFNAME.xml" > "$PROBE/retained-settings.xml"
adb exec-out run-as "$PKG" cat "files/accounts/$ACCOUNT_HASH/journal/probe" > "$PROBE/retained-file"
cmp "$PROBE/settings.xml" "$PROBE/retained-settings.xml"
cmp "$PROBE/planner-file" "$PROBE/retained-file"
echo "Android update passed: package UID, settings and private planner file retained without uninstall."
