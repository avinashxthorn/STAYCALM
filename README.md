# STAYCALM · Bloom 3.1

A native Android focus companion in a cohesive pink theme. Kotlin, Jetpack Compose, Material 3. Android 8.0+.

## Features

- Circular focus timer with animated progress, 15/25/45-minute presets and custom 5–180-minute durations.
- Separate short-break mode with 5/10/15-minute presets. Breaks do not count toward focus progress.
- Pause/resume, persisted sessions, completion summary, and confirmation before abandoning a session.
- Session intentions, recent history, daily goal progress, seven-day chart, and consecutive-day streak.
- Monthly planner with task editing, completion, exact-time reminders (when Android permits), and one-tap focus on a task.
- The Focus screen's "What deserves your attention?" field automatically saves one updatable entry into today's Planner.
- App blocking cannot be turned off while a focus session is running or paused: a dialog explains why the toggle stays on.
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

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Planner reminders use exact Android alarms when permitted; on Android 12+ enable Alarms & reminders, and on Android 13+ enable app notifications. If exact alarms are not permitted, the fallback can be delayed. Existing reminders are re-armed after a restart, update, clock/time-zone change or granting precise-alarm access; device battery policies may still interfere. The timer reconciles its deadline on reopening.

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


## Focus app blocking (v3.7.1)

STAYCALM can **optionally** stop Instagram (`com.instagram.android`), Instagram Lite (`com.instagram.lite`) and YouTube (`com.google.android.youtube`) from opening while an unfinished focus session exists (including a paused session). Break sessions are unaffected. On natural completion or an allowed End session, all three apps work normally; their data, notifications and installation are never altered.

**Setup:** In STAYCALM Settings, leave **Block distracting apps** enabled, select **Set up app blocking**, and explicitly grant **Usage Access** and **Display over other apps** in Android Settings. Focus sessions will not start while app blocking is enabled and those permissions are missing; turn off Block distracting apps to focus without blocking. During an unfinished focus session, a full-screen STAYCALM overlay appears over the three blocked apps with a button to return to STAYCALM.

**Privacy and Android limits:** No Accessibility service is declared. Android Usage Access does provide broad app usage events, but STAYCALM processes them on-device solely to identify the foreground app, overlays only the three listed app packages, and never reads screen text, reads messages, or transmits activity. Website blocking is not included. The user can revoke either permission at any time. A non-device-owner app cannot guarantee blocking if permissions are revoked, the phone force-stops STAYCALM, or the OS terminates the foreground service. Always leave Google Play Protect enabled.
