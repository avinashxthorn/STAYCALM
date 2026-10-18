# STAYCALM · Bloom 3.0

A native Android focus companion in a cohesive pink theme. Kotlin, Jetpack Compose, Material 3. Android 8.0+.

## Features

- Circular focus timer with animated progress, 15/25/45-minute presets and custom 5–180-minute durations.
- Separate short-break mode with 5/10/15-minute presets. Breaks do not count toward focus progress.
- Pause/resume, persisted sessions, completion summary, and confirmation before abandoning a session.
- Session intentions, recent history, daily goal progress, seven-day chart, and consecutive-day streak.
- Monthly planner with task editing, completion, reminders, and one-tap focus on a task.
- Six-stage pink turtle companion, earned through completed focus time.
- Seven bundled offline soundscapes, volume control, audio-focus handling, and notification stop action.
- Optional Do Not Disturb and precise session alerts, subject to Android permissions.
- Local-only storage, no accounts or analytics.

## Build and download

Pushes to `main` run `.github/workflows/android-apk.yml`. The workflow downloads licensed offline assets, runs JVM tests and Android lint, builds an installable debug APK, then exercises session and planner flows on an Android 15 emulator.

In the successful Actions run, download **STAYCALM-Bloom-v3-debug-apk**, extract the ZIP, and install `app-debug.apk`. The **STAYCALM-verification** artifact contains check reports and emulator screenshots.

This is a debug-signed build, not a Play Store release. CI-generated debug signing keys may differ between runs; an update can require removing the previously installed debug app, which erases its local data. Production distribution needs a stable private signing key.

### Local build

Requirements: JDK 17, Gradle 8.11.1, Android SDK 35/build tools 35.0.0.

```sh
bash scripts/fetch-assets.sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# With an Android 13+ emulator/device:
gradle :app:connectedDebugAndroidTest
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Android can delay notifications without precise-alarm permission; manufacturers may additionally restrict background execution. The timer reconciles its deadline on reopening.

See `DESIGN-SYSTEM.md`, `ASSET-LICENSES.md`, and `QA-CHECKLIST.md`.
