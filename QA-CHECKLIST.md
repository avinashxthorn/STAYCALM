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

## v3.7.3 Recents gesture bypass checks

- Before a blocked focus session, explicitly opt in to the separate Accessibility foreground-window detection disclosure. Verify denying consent does not open Android Settings or start a protected focus session.
- Enable all three settings (Usage Access, Display over other apps, Accessibility service). Start focus and open Instagram, Instagram Lite, and YouTube from the home screen. Confirm each shows the shield.
- While shielded, swipe into Recents and immediately swipe back down to the blocked app; repeat at different speeds 10+ times and verify its content cannot be interacted with after the transition settles.
- Switch from the blocked app to launcher and back, then from a nonblocked app back into a blocked app. Ensure the shield is recreated on return.
- Pause focus and repeat Recents navigation; blocking remains until natural completion or allowed End session. Test that the settings switch still rejects an Off attempt.
- Use the shield's Open STAYCALM button; confirm the app opens without trapping the user under its own overlay.
- Disable Accessibility access mid-session using Android Settings and confirm this remains possible; Usage Access/overlay continue as a best-effort fallback and Settings is never blocked.
- Confirm no network use, screen-content reads, screenshots or recorded browsing history. Document OEM-specific behavior and never claim system-level lockdown.


## v3.7.4 Watch & Listen QA

- Paste `https://www.youtube.com/watch?v=M7lc1UVf-VE` during active focus; verify the official player is visible and the countdown continues. Check Shorts and youtu.be video URLs; reject fake domains, playlists and non-HTTPS links.
- Try a YouTube video that disallows embedding and confirm this is handled by the embedded YouTube player (STAYCALM does not download or bypass it).
- Switch to Planner/Settings, background the app and lock the screen; confirm the embedded YouTube player does **not** continue in the background, and reload on returning to Focus requires an explicit Play tap.
- With the focus blocker active, try the recent-apps swipe trick; the embedded player does not whitelist the external YouTube app, which remains blocked.
- Paste a public HTTPS audio RSS feed, retrieve episodes, choose one; verify background audio, media notification, headphone controls, pause/resume/stop and seek.
- End or complete an active focus session; verify the podcast media service stops promptly and notification is removed.
- Confirm local/imported ambient audio still loads, loops and plays; changing ambient audio stops previous podcast stream.
- Test airplane mode, malformed podcast URLs, HTTPS failures, RSS feeds without enclosures, autoplay restrictions and Android 15 media playback permissions.
- Verify Android manifest declares INTERNET but no new account, device-storage or broad media permissions; note podcast RSS URLs shared with their publishers and YouTube requests shared with YouTube.
