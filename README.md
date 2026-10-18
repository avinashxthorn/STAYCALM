# STAYCALM 2.0

A locally stored, privacy-first Android focus studio. Designed for deep work, not screen time.

## Design
- Manrope type, inky plum (#26232B), premium rose (#C45E83), ivory surfaces, clear contrast and restrained motion.
- Native Jetpack Compose Material 3, accessible text and large hit targets.
- Custom vector turtle / egg-hatching story. Six stages with motion based on springs, crack geometry, and scaling. The turtle grows from *total completed minutes*, not just opening the screen.
- Offline seven-sound field-recording library: rain, ocean, birds, stream, fireplace, wind, thunder. Adjustable volume and loop playback.

## Functional scope
- Focus sessions with custom time, pause/resume, elapsed-realtime clock and recoverable persisted state.
- AlarmManager completion alerts (inexact delivery under Doze; UI timer remains correct).
- Optional Android Do Not Disturb while focusing, with user-granted Notification Policy Access and correct Android 15 implicit-rule semantics.
- Fully local monthly planner: add, edit, complete and remove tasks with date/time, time budgets and reminders; task reminders are inexact Android alarms.
- Focus history with daily/weekly insights and a local daily goal.
- Does not request internet permission, does not run cloud sync, and does not contain an AI coach.

## Build a debug APK
1. Open **Actions → STAYCALM · Android APK → newest successful run → Artifacts**.
2. Download **STAYCALM-v2-debug-apk**, extract, and install `app-debug.apk`.
3. For local command-line builds with Android SDK 35 and Java 17: `bash scripts/fetch-assets.sh && gradle :app:assembleDebug`.

The audio and font are downloaded at **build time**, bundled in the APK, and never streamed at runtime. See `ASSET-LICENSES.md`. Internet access is needed for building, not for using the app.

## Platform caveats
- Apps cannot silently block every notification: DND needs user authorization, and OS exceptions/priority contacts still apply.
- Android may defer background alarms in Doze; exact delivery requires additional user-granted special access and is deliberately not requested.
- Users can force-stop the app; Android cancels pending alarms when an app is force-stopped. Reopening reconciles stored sessions.
- MediaPlayer runs in a foreground media playback service when ambient sound is playing, and must be started by user interaction.

## Verification
CI checks `testDebugUnitTest` and `assembleDebug`. Runtime notification, DND, audio, lifecycle and animation testing still need a real Android device.
