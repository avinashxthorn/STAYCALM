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
