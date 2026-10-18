# STAYCALM · Acceptance Checklist

## Automated verification
- [ ] GitHub Actions testDebugUnitTest green.
- [ ] GitHub Actions assembleDebug green.
- [ ] Tests cover monotonic countdown math, pause/resume, completed-session stage thresholds and progress bounds.
- [ ] Debug APK attached only after successful build.

## UI verification (Android 8-16)
- [ ] Screens fit on 360dp narrow devices and scale with accessibility text size.
- [ ] Focus state clearly differentiates idle, paused, running and completed.
- [ ] First tap begins focus, second tap pauses, abandoned time does not grow turtle.
- [ ] Every tappable item has its advertised action and correct icon/label.
- [ ] Contrast and tap targets are usable in bright sunlight and dark environments.
- [ ] Egg crack, shell separation, hatchling emergence, juvenile and adult visibly differ.
- [ ] Turtle progress changes only after confirmed session completion.

## Timer / scheduling
- [ ] Time does not drift when app is backgrounded for 10+ min.
- [ ] Pause and resume retain remaining time across activity re-creation.
- [ ] Notification shows the active countdown and is cleared when paused.
- [ ] Completed sessions increment exactly once after reopening or alarm delivery.
- [ ] Exact-alarm permission allowed, denied and revoked paths are checked.
- [ ] Screen lock, Doze, force-stop and reboot limitations are tested and documented.

## Sounds
- [ ] All seven offline recordings play without internet.
- [ ] Switching sound mid-session updates ongoing playback.
- [ ] Volume changes audible; Bluetooth headset and incoming call audio focus handled.
- [ ] Notification Stop controls actually stop playback.
- [ ] App navigation and orientation changes do not desynchronize the audio indicator.

## Planner
- [ ] Add, edit, delete, complete tasks; month navigation and date selection.
- [ ] Reminders scheduled and canceled correctly; no reminders for completed tasks.
- [ ] Midnight rollover, timezone change, and user-change time settings considered.
- [ ] User data remains local; no account needed.

## Notification controls
- [ ] DND permission explained and requested from system settings.
- [ ] Focus starts/restores DND only on authorized devices.
- [ ] Important exceptions follow system priority rules.
- [ ] DND exits on finish, pause, abandon and relevant lifecycle recovery.
