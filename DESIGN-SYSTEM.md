# STAYCALM · Bloom edition

A native Compose interface with a pink-only accent palette. The focus screen puts the timer and its primary action first, followed by an intention, daily progress, offline sound, and the growing companion.

## Visual system

- Background: blush white `#FFF8FA`; cards: white and pale rose `#FCE8EE`.
- Primary controls: deep rose `#AC345F`; text: plum rose `#542536`; secondary text: `#88616F`.
- Manrope is bundled offline and applied to every Material typography role, including dialogs.
- Large numeric timer uses tabular figures. Timer labels round up to avoid immediately losing a displayed second.
- Rounded cards use 18–32 dp radii; primary actions are 58 dp high; duration controls are 48 dp high.
- Circular timer uses minute marks, a moving progress point, and a 300 ms interpolation between once-per-second deadline samples. Pages crossfade in 220 ms. Native Compose animations follow the system motion scale.
- Companion artwork remains vector-based and uses rose shell, flippers, and accents. Evolution is based only on completed focus minutes.

## Behavior

Focus and break modes use the same persisted timer. Breaks never add focus minutes or evolve the companion. Selecting a mode or duration is disabled during running or partially completed sessions. Ending a session requires confirmation.

Intentions are stored locally and copied into completed session history. Planner tasks can start a focus session directly when the timer is idle. Completing a session does not automatically mark its task done: studying a task for a session does not necessarily finish the task.

Completion persists a message and the reset timer atomically with the log; reopening the app shows the summary. A short break is offered but starts only when the user taps Start break.

The streak counts distinct local dates and remains active until the end of the day following the latest completed session. Future dates do not inflate it.

## Timer and alarms

New sessions store Android's boot count. Within the same boot, elapsed realtime determines the remaining duration even if the wall clock changes. After reboot, the wall-clock deadline is the fallback. Session alarms use elapsed realtime; exact scheduling requires Android permission and otherwise falls back to inexact delivery. The on-screen timer is not based on counting UI ticks.

Planner reminders cancel their previous alarm before rescheduling, including when a date is moved into the past. Notifications require user permission. The ongoing session notification includes Pause. Pausing ambient audio stops its foreground service; playback can be restarted from the app.

## Reference

- Android Compose motion: https://developer.android.com/develop/ui/compose/animation/composables-modifiers
- Android alarms and permissions: https://developer.android.com/develop/background-work/services/alarms
