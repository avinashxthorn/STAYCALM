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
- Local-only storage, no accounts or analytics. Optional embedded YouTube and HTTPS podcast playback require internet; video playback connects to YouTube, RSS podcast feeds connect to their publishers.

## Build and download

Pushes to `main` run `.github/workflows/android-apk.yml`. The workflow downloads licensed offline assets, runs JVM tests and Android lint, builds an installable debug APK, then exercises session and planner flows on an Android 15 emulator.

In the successful Actions run, download **STAYCALM-v3.7.4-watch-listen-debug-apk**, extract the ZIP, and install `app-debug.apk`. The **STAYCALM-verification** artifact contains check reports and emulator screenshots.

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


## Focus app blocking (v3.7.3)

STAYCALM can **optionally** stop Instagram (`com.instagram.android`), Instagram Lite (`com.instagram.lite`) and YouTube (`com.google.android.youtube`) from opening while an unfinished focus session exists (including a paused session). Break sessions are unaffected. On natural completion or an allowed End session, all three apps work normally; their data, notifications and installation are never altered.

**Setup:** In STAYCALM Settings, leave **Block distracting apps** enabled and select **Set up app blocking**. Grant **Usage Access**, **Display over other apps**, and (after a separate, affirmative in-app disclosure) **STAYCALM focused app blocking** under Android **Accessibility** settings. STAYCALM does not silently grant any permissions. New focus sessions with Block Apps enabled will not start until all three are granted. The separate Accessibility service listens for changes in the visible app package, including when switching through the Recents screen, and sends the package name to the local timer service. Usage Access polling at 350ms provides a fallback. The shield persists through transient launcher/SystemUI events and is reattached if needed when a blocked Activity resumes. The three blocked apps are covered by a full-screen overlay with a button back to STAYCALM. A paused focus session keeps them blocked, and turning off blocking in-app is prevented until session completion/cancellation.

**Privacy and Android limits:** STAYCALM is not an accessibility tool. The opt-in Accessibility service declares window state/change events only, with `canRetrieveWindowContent=false` and `isAccessibilityTool=false`. It checks package names only during an unfinished focus session, with no accessibility nodes, text, passwords, gestures, screenshots, internet uploads, telemetry or persisted browsing history. Android Usage Access may expose broad app usage events but is processed only on-device. No other apps, websites, launcher or Android Settings are intentionally blocked. System settings remain available to disable the service or revoke permissions.

Android permits ordinary app overlays only over app windows, not above protected system UI. An unprivileged non-device-owner app cannot guarantee that blocked-app previews disappear from Recents, that overlays are never hidden by the OS/OEM, or that blocking survives permissions being revoked, force-stop, battery kills, or other system-level intervention. The user must be allowed to manage accessibility settings. For a Play Store release, declare Accessibility use in Play Console, provide the in-app disclosure/consent experience in the review video and listing, and complete required permission reviews. Never claim STAYCALM is a verified accessibility tool. Keep Google Play Protect enabled.

**Device QA:** Verify Instagram, Instagram Lite, and YouTube through launcher, Recents swipe-up/down, task-switching, pausing and resuming, and completion on real Android devices. Check both permission revocation and return-to-STAYCALM paths. The Android emulator workflow does not prove all OEM Recents animations behave identically.


## Watch & Listen (v3.7.4)

The Focus page includes an optional **Watch & Listen** card:

1. **YouTube video/podcast video:** Paste a full HTTPS `youtube.com/watch?v=...` or `youtu.be/...` video link and choose **Show video player**. The official YouTube embed remains visible inside STAYCALM. The focus timer and selected distracting-app blocking stay active. Playback requires an internet connection and the user pressing Play; the player stops when it is closed, when leaving the Focus page, or when STAYCALM goes into the background. Channel/profile pages and playlists are not supported by this first version.
2. **Background audio podcasts:** Paste a public **HTTPS RSS podcast feed** in the RSS field, tap **Find episodes**, then tap an episode. STAYCALM streams the RSS publisher's HTTPS audio file using its existing media playback foreground service, with notification/lock-screen play/pause and seek support. Audio can continue when the screen is off or while the user works elsewhere. Playback stops when the focus session is completed or cancelled. Podcast publishers determine episode availability and network requirements. You can still use local imported audio as before.

**YouTube policy:** YouTube's developer rules prohibit extracting or isolating YouTube audio and prohibit using an undisplayed embedded YouTube player for background playback. This feature does **not** offer background YouTube audio, downloads, audio extraction or video recording. Use independent podcast RSS audio for background listening; official YouTube Premium/background capabilities remain within YouTube's own product, not this embedded player. Play Store approval is not automatic.

**Privacy:** No account access or YouTube OAuth is requested. YouTube's official embedded player connects directly to Google's/YouTube's services and can send playback/device data under their privacy practices when loaded. RSS feeds and audio URLs are fetched directly from their publishers. STAYCALM stores pasted YouTube and RSS links only on the device, does not upload planner data, and never sends local study history to YouTube/podcast publishers.

**Limitations & QA:** Some YouTube videos disallow embedding, and WebView/embed attestation, restrictions or network conditions may prevent playback on particular devices. The app cannot ensure every link plays. HTTPS-only podcast sources and direct RSS audio enclosures are supported. Testing the installable APK and playback on physical Android devices is required; a source commit alone is not a verified working installer.
