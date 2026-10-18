# STAYCALM · Bloom 3.1

A native Android focus companion in a cohesive pink theme. Kotlin, Jetpack Compose, Material 3. Android 8.0+.

## Features

- Circular focus timer with animated progress, 15/25/45-minute presets and custom 5–180-minute durations.
- Separate short-break mode with 5/10/15-minute presets. Breaks do not count toward focus progress.
- Pause/resume, persisted sessions, completion summary, and confirmation before abandoning a session.
- Session intentions, recent history, daily goal progress, seven-day chart, and consecutive-day streak.
- Monthly planner with task editing, completion, reminders, and one-tap focus on a task.
- Six-stage pink turtle companion, earned through completed focus time.
- Four supplied offline tracks, personal audio imports, seeking, volume, previous/next, and lock-screen playback controls. Every track loops automatically and stops when the session completes or is ended.
- Optional Do Not Disturb and precise session alerts, subject to Android permissions.
- Local-only storage, no accounts or analytics.

## Build and download

Pushes to `main` run `.github/workflows/android-apk.yml`. The workflow downloads licensed offline assets, runs JVM tests and Android lint, builds an installable debug APK, then exercises session and planner flows on an Android 15 emulator.

In the successful Actions run, download **STAYCALM-Bloom-v3.1-debug-apk**, extract the ZIP, and install `app-debug.apk`. The **STAYCALM-verification** artifact contains check reports and emulator screenshots.

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


### First launch (v3.3)

New users see a three-page swipeable intro to focus sessions, their dated work archive,
progress and study encouragement. **Get started** opens a separate, local-only profile page
with a required name, an optional subject, a daily goal and optional motivation reminders.
Saving the profile opens the Focus tab. Existing profiles skip onboarding on subsequent launches.


### Rewards and cancellation rules (v3.5)

- Completing a focus session earns one point per full hour in that session (60m = 1, 120m = 2, 180m = 3). Shorter sessions do not accumulate toward a point; unfinished sessions and breaks earn none. Existing history is unchanged.
- Each installation gets three lifetime free focus cancellations. Later cancellations cost one point; a zero balance blocks cancellation. Break cancellation and pausing are free. A paused session must be resumed or cancelled before its mode, task or duration can change.
- Rewards, cancellation charges, allowance usage and session reset persist in one preferences transaction. A shared lock serializes Activity, alarm and service operations so duplicate completion/cancellation callbacks cannot credit or charge twice.
- Balances and allowances persist through restarts and app updates. Clearing app data or uninstalling clears these device-local records.
- Locked focus and break notifications use a dedicated foreground timer service, including silent sessions. Android 14+ permits individual swipes while unlocked, so locked notifications are restored on deletion and checked by the service. Turning the lock off makes the notification dismissible and stops restoration. Pause, cancellation and completion remove it. Android notification permissions, force-stop and manufacturer restrictions remain system-controlled.

Verification includes full-hour boundaries, zero-balance cancellation, three free cancellations, paused-session configuration protection, concurrent callbacks, persisted balances, deadline cancellation and silent focus/break notification lock toggles.


### Session uninstall protection (v3.6)

Enable **Settings → Protect session from uninstall** before starting. Android requests Device Admin consent for each new protected session. Direct uninstall is blocked while the administrator is active, including when paused. Completion or an allowed End session removes the administrator automatically; having points alone does not remove it. Denying activation leaves the session unstarted. Protection cannot be switched off inside STAYCALM until the session ends.

This is optional, uses an empty device-admin policy list (no password, camera, lock or data-wipe powers), and does not request Accessibility. Android Settings can always deactivate the administrator. This is protection against direct uninstall, not a guarantee against deliberate system-level removal. A foreground timer remains active during protected running sessions even if notification locking is off. Permission must be granted again for the next protected session after automatic removal.
