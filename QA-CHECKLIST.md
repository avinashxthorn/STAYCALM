# Version 4.0 account privacy regression checks

- Log out from Insights/Settings and confirm only Login/intro is reachable, including after Activity recreation/Back.
- Account A adds a planner file and history; account B must see none of A's files, logs, profile or sync status. Return to A and verify its private cache remains.
- Restore a completed history snapshot and check session IDs/intention/minutes, rewards and the Insights screen.
- A running or paused session prevents logout; finish or End session first.
- Sign up -> verification -> refresh token -> cloud recovery -> profile/home. Wrong credentials/reset/network errors remain clear without revealing account existence.
- Sync and log out must remain signed in if cloud upload fails. Log out without syncing retains only that account's local cache.
- Upgrade a signed-in 3.9 installation with planner and audio files and confirm migration to the same UID; do not assign an old signed-out installation's data to a different login.

---

# Bloom 3.0 validation

## Automated gates

- JVM: evolution boundaries, timer drift, pause clamping, reboot fallback, wall-clock changes, display rounding, streak duplicates/gaps/year boundaries.
- Android lint and debug APK assembly.
- Android 15 emulator: launch, pink screen screenshots, planner add/start, focus pause/resume/end, persisted timer after Activity recreation, break mode, atomic/idempotent completion, no focus credit for breaks.

Reports and screenshots are uploaded by the main build workflow. A workflow is only verified when its run completes successfully.

## Device checks still useful

- Test large font settings, screen rotation, TalkBack, and reduced animation settings on the user's handset.
- Allow notifications, deny notifications, and toggle precise-alarm access. Validate background completion under the handset's battery-saving modes.
- Check DND restoration, interrupted playback, wired/Bluetooth audio, and the sound notification stop action.
- Test reminder delivery after restart and editing a future reminder into the past.
- Listen for acceptable soundscape loop boundaries.

No finite set of checks can guarantee an app has no defects on every Android device.


## v3.5 rewards and timer notification
- [ ] Complete 59/60/120/180-minute focus sessions: +0/+1/+2/+3 points, exactly once.
- [ ] Three focus cancellations are free; the fourth deducts one point or is refused at zero balance.
- [ ] Pause/restart/recreate and change mode/duration/task attempts cannot reset an active session.
- [ ] Concurrent completion and cancellation callbacks cannot double-credit or double-charge.
- [ ] Cancelling an already-due session completes it without a charge; breaks never change rewards or allowances.
- [ ] Locked silent focus and break countdowns appear, restore after dismissal, unlock immediately and disappear on pause/end/completion.
- [ ] Notification permission denial does not crash; notification settings link opens Android controls.


## v3.6 opt-in uninstall protection
- [ ] Denied Device Admin activation leaves the timer unstarted.
- [ ] Approved protection applies to focus and break sessions and persists through pause/recreation.
- [ ] Having points and attempting to change the protection switch do not release an unfinished session.
- [ ] Completion and successful free/paid cancellation remove Device Admin; blocked cancellation does not.
- [ ] A new protected session requires approval again after administrator removal finishes.
- [ ] Android Settings remains accessible for administrator deactivation; no destructive policies are requested.

## v3.7.2 planner and blocker regression checks

- Save a planner task with a reminder a few minutes ahead; grant Notifications and Alarms & reminders, lock the screen, and verify an alert arrives near its chosen minute.
- Deny exact-alarm access and check the app's warning and inexact delivery fallback (timing may be delayed).
- Edit the task date/time and verify only the new reminder fires. Mark it complete or delete it and ensure the old notification does not fire.
- Reboot and change the system clock or time zone with an upcoming task and verify re-arming.
- Type a focus-page intention and check that exactly one entry appears on today's planner date, updates while editing, and survives reopening.
- Start focus with Block Apps enabled, then try switching it off; verify a modal alert appears, the switch remains On, and blocking continues through pause/resume.
- Complete or allowably end the session and verify the switch can then be turned Off.

## v3.7.5 Remove strict blocking and allow YouTube

- Confirm no Accessibility service or BIND_ACCESSIBILITY_SERVICE declarations exist, and Settings never prompts for Accessibility access.
- Confirm optional Instagram blocker defaults OFF on a clean installation. Existing users who enabled it may turn it off before starting a session.
- When Instagram blocking is OFF, start a focus session without Usage Access or overlay permission; timer starts normally.
- When optional Instagram blocking is ON, Usage Access and overlay are required, and only Instagram/Instagram Lite are covered; in-app or external YouTube is NOT covered.
- Start focus, play an in-app YouTube video, interact with it and let the timer count down; pausing or resuming the focus session must not close or block the visible video.
- Go to the launcher, use Recents and return to the Focus screen; no Accessibility/strict Recents interception is active.
- Verify the old optional Instagram-blocking toggle still cannot be disabled mid-session once started with it enabled, and can be disabled after the session ends.

## v3.7.5 Watch & Listen QA

- Paste `https://www.youtube.com/watch?v=M7lc1UVf-VE` during active focus; verify the official player is visible and the countdown continues. Check Shorts and youtu.be video URLs; reject fake domains, playlists and non-HTTPS links.
- Try a YouTube video that disallows embedding and confirm this is handled by the embedded YouTube player (STAYCALM does not download or bypass it).
- Switch to Planner/Settings, background the app and lock the screen; confirm the embedded YouTube player does **not** continue in the background, and reload on returning to Focus requires an explicit Play tap.
- With optional Instagram blocking active, verify the embedded YouTube video plays during focus and external YouTube is also allowed; no strict Recents interception occurs.
- Paste a public HTTPS audio RSS feed, retrieve episodes, choose one; verify background audio, media notification, headphone controls, pause/resume/stop and seek.
- End or complete an active focus session; verify the podcast media service stops promptly and notification is removed.
- Confirm local/imported ambient audio still loads, loops and plays; changing ambient audio stops previous podcast stream.
- Test airplane mode, malformed podcast URLs, HTTPS failures, RSS feeds without enclosures, autoplay restrictions and Android 15 media playback permissions.
- Verify Android manifest declares INTERNET but no new account, device-storage or broad media permissions; note podcast RSS URLs shared with their publishers and YouTube requests shared with YouTube.

## v3.7.6 APK download and Android install validation

- In CI, run unit tests, lint and assemble; verify app-debug.apk is nonempty, passes ZIP integrity and apksigner checks, and has application ID com.staycalm.focus.
- Download STAYCALM.apk from GitHub Releases under Assets, not Source code (zip) or an HTML page. It should open in Android's APK installer without extraction.
- Download the unarchived STAYCALM.apk from the latest green GitHub Actions run (upload-artifact@v7 archive:false). Check that the extension remains .apk and no ZIP extractor is used.
- Compare SHA-256 with the STAYCALM.apk.sha256 release asset to detect incomplete/mobile-browser downloads.
- Try a clean installation on Android 15. For upgrades from earlier debug builds, test signing-certificate mismatch reporting and avoid deleting user data without backup.
- Confirm Release publication uses GITHUB_TOKEN content write permission and that a denied Release publish does not suppress the direct Actions APK.

## v3.7.7 In-app mini YouTube search QA

- From Focus, open Watch & Listen and tap Open mini YouTube. Confirm the mini browser displays YouTube's official mobile page without asking for a developer key or a pasted video URL.
- Search in the native search box for a lecture or podcast, press keyboard Search or the search icon. Verify correctly encoded YouTube search results URL and selection of a search result in the mini browser.
- Verify YouTube's own video player, ads, links, subtitles and controls remain unmodified. External links use normal Android handlers rather than an unrestricted STAYCALM webview.
- During a focus session, let a video play and verify timer continues counting. Test with optional Instagram-only blocking ON and OFF; YouTube should be accessible.
- Leave STAYCALM, lock the screen or dismiss the dialog; video/audio must stop and cannot be played invisibly. Returning starts from YouTube home/search, not playing a hidden YouTube video.
- Open mini browser while a pasted video is playing; the old embed must be removed so two videos never play concurrently.
- Verify HTTP URLs, spoofed youtube.com hosts, data/file schemes cannot navigate within the mini browser.
- Test poor connectivity, WebView blocked content, sign-in limitations, and video embed restrictions on the target Android phone. Android installation of the debug APK can fail over older builds with different signing keys.

- Verify mini YouTube also opens from the one-tap video icon in the Focus header. Closing it returns to the same focus timer without resetting the session.


## Account and recovery (3.8.0)
- Without Firebase config, Account & backup explains setup; local app and file export still work.
- Real configured project: sign up, verify email, check verification, log out, log in, reset password.
- Opt in, wait for confirmed upload, change a plan, background app and confirm automatic upload.
- Offline changes stay local; reconnect and verify retry and last successful backup time.
- Sign in on a new device; do not enable upload before restoring original-device copy.
- Restore plans/history/rewards/profile with no active timer; confirm reminders reschedule.
- Active or paused timer blocks cloud and file restores.
- Logging out and switching accounts never automatically uploads old local data.
- PDFs and imported audio are explicitly excluded from cloud; full ZIP round trip preserves them.
- Invalid/truncated/path-traversing ZIP and future/oversized JSON fail before replacing preferences.
- Never uninstall an existing build just to bypass a debug signing mismatch.


## Version 3.9 login and planner cloud files
- Fresh install: no login button appears above slides; three pages and Get started lead to simple email/password login.
- Login/Sign up changes only the short auth form. No export/restore controls appear before authentication.
- Verified login on an empty fresh install recovers latest profile, planner, history and cloud attachment bytes before home.
- Interrupted fresh-install recovery remains retryable after restarting the Activity. Existing local work is never silently replaced.
- Profile and account manager show Login successful and the signed-in email.
- Update Firebase rules, sync an existing PDF, remove the source local file in a test installation, and recover identical bytes from the cloud.
- Partial chunks/checksum failure never become a successful backup or replace existing planner/preferences.
- Upload retries resume after the last atomic eight-chunk checkpoint. Complete files skip subsequent byte writes.
- Verify cross-account and unverified access to file manifests and bytes is denied.
- Confirm all limits are explicit; Spark quota failures retain local data and show pending sync.
