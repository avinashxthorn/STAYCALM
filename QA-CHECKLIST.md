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
