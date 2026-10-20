## Email accounts and recoverable backups (3.8.0)

STAYCALM now supports optional Firebase email/password accounts, opt-in per-device cloud copies for structured progress, and full ZIP export/import including local files. See [phone-friendly Firebase setup](FIREBASE-SETUP.md). Cloud features require your Firebase Android client configuration, enabled Email/Password sign-in and published owner-only Firestore rules. Builds without configuration remain local-only. SMS login is intentionally excluded because it requires billing.

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
- Local-first storage with optional email accounts and opt-in cloud recovery; no analytics trackers. Optional embedded YouTube and HTTPS podcast playback require internet; video playback connects to YouTube, RSS podcast feeds connect to their publishers.

## Build and download (v3.8.0)

**Mobile-friendly download:** After the latest GitHub Actions build succeeds, open [STAYCALM Releases](https://github.com/avinashxthorn/STAYCALM/releases/latest), expand **Assets**, and download **STAYCALM.apk**. This is a real, installable APK, **not** a ZIP file. Do not choose **Source code (zip)** or **Source code (tar.gz)**.

- The latest release's direct APK URL (once published) is [STAYCALM.apk](https://github.com/avinashxthorn/STAYCALM/releases/latest/download/STAYCALM.apk).
- **Alternative:** Open the latest successful [GitHub Actions run](https://github.com/avinashxthorn/STAYCALM/actions) and select **STAYCALM.apk** in its Artifacts section. The workflow uses `actions/upload-artifact@v7` with `archive: false`, which downloads this one APK **without** a ZIP/extraction step.
- Before publishing, CI runs unit tests and Android lint, compiles the app, checks the APK ZIP's internal integrity, verifies its Android signature with `apksigner`, verifies its application ID with `aapt`, and generates `STAYCALM.apk.sha256` for checking download integrity. An Android 15 emulator UI test also runs separately. No physical-device install is guaranteed by these checks.
- If the APK fails to install over an older STAYCALM, Android may be rejecting a **signing certificate mismatch**: debug builds produced by separate GitHub runners can use different temporary debug signing keys. An unchanged package ID requires the same signing certificate to update in place. **Do not uninstall the previous app without saving important planner and focus data**; uninstalling removes local records. A permanent fix requires configuring a securely held stable signing key.

The checked APK lives at `app/build/outputs/apk/debug/app-debug.apk` during CI; it is copied unchanged to `dist/STAYCALM.apk` for the direct download.

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


## Optional basic Instagram blocking (v3.7.5)

The strict Recents-swipe protection requested earlier was **removed** in v3.7.5.
STAYCALM no longer declares or asks for an Accessibility service, checks Recents gesture changes, or rapidly scans for app switching. YouTube is **not blocked**. You can open YouTube while a focus session is running, or use the visible embedded YouTube video player in the Focus screen; the focus countdown continues.

Only the older, **optional basic** overlay blocker remains for Instagram (`com.instagram.android`) and Instagram Lite (`com.instagram.lite`). It is **OFF for fresh installations**. To enable it, turn on Settings → **Block Instagram (optional)** and grant Android Usage Access and Display over other apps. No Accessibility permission is needed. An unfinished or paused focus session with optional Instagram blocking already enabled keeps that setting locked until the session ends, as before.

The basic blocker is not designed to survive Recents gestures, forced stops, revoked permissions or OEM restrictions. Android Settings remain available. No window contents, passwords, keystrokes, screenshots or other apps are monitored. Users who previously enabled the removed Accessibility service can disable it in Android Settings; it is no longer present in this app's manifest.

## Watch & Listen (v3.7.5)

The Focus page includes an optional **Watch & Listen** card:

1. **YouTube video/podcast video:** Paste a full HTTPS `youtube.com/watch?v=...` or `youtu.be/...` video link and choose **Show video player**. The official YouTube embed remains visible inside STAYCALM. The focus timer stays active; YouTube is allowed whether optional Instagram blocking is enabled or not. Playback requires an internet connection and the user pressing Play; the player stops when it is closed, when leaving the Focus page, or when STAYCALM goes into the background. Channel/profile pages and playlists are not supported by this first version.
2. **Background audio podcasts:** Paste a public **HTTPS RSS podcast feed** in the RSS field, tap **Find episodes**, then tap an episode. STAYCALM streams the RSS publisher's HTTPS audio file using its existing media playback foreground service, with notification/lock-screen play/pause and seek support. Audio can continue when the screen is off or while the user works elsewhere. Playback stops when the focus session is completed or cancelled. Podcast publishers determine episode availability and network requirements. You can still use local imported audio as before.

**YouTube policy:** YouTube's developer rules prohibit extracting or isolating YouTube audio and prohibit using an undisplayed embedded YouTube player for background playback. This feature does **not** offer background YouTube audio, downloads, audio extraction or video recording. Use independent podcast RSS audio for background listening; official YouTube Premium/background capabilities remain within YouTube's own product, not this embedded player. Play Store approval is not automatic.

**Privacy:** No account access or YouTube OAuth is requested. YouTube's official embedded player connects directly to Google's/YouTube's services and can send playback/device data under their privacy practices when loaded. RSS feeds and audio URLs are fetched directly from their publishers. STAYCALM stores pasted YouTube and RSS links only on the device, does not upload planner data, and never sends local study history to YouTube/podcast publishers.

**Limitations & QA:** Some YouTube videos disallow embedding, and WebView/embed attestation, restrictions or network conditions may prevent playback on particular devices. The app cannot ensure every link plays. HTTPS-only podcast sources and direct RSS audio enclosures are supported. Testing the installable APK and playback on physical Android devices is required; a source commit alone is not a verified working installer.


## Mini YouTube search & watch (v3.7.7)

Tap the **mini YouTube video icon next to Settings on the Focus screen** for instant access, or scroll to **Focus → Watch & Listen → Open mini YouTube · Search & Watch**. The button opens a mini browser inside STAYCALM. It loads **YouTube's official mobile website** (`https://m.youtube.com/`) and has a simple in-app search box. Type a topic, video or podcast, select **Search**, and choose a video from YouTube's own search results. No copied URL, developer API key or YouTube account connection is required for publicly accessible videos. Your focus timer continues running because STAYCALM remains in the foreground. Video playback, ads, recommendations and controls come from YouTube's site, not a custom player.

This is an **in-app website integration**, not an OAuth connector to a YouTube account and not the YouTube Data API. Users are taken to YouTube's service and its privacy terms when they open the mini browser. Android WebView/YouTube might refuse some streams or account sign-in; STAYCALM does not circumvent those restrictions or scrape search results.

**Video must stay visible:** Closing mini YouTube, switching away from STAYCALM, or locking the screen stops the WebView playback. Returning reloads the YouTube home or last search page; the user explicitly starts any video again. Third-party site links open with normal Android handlers. Use public podcast RSS audio in Watch & Listen if you want actual background/lock-screen listening. Strict Accessibility/Recents blocking is still removed, and the external YouTube app remains unblocked.
