# STAYCALM · Design & Engineering Specification

STAYCALM is a private, offline Android study companion built around **one obvious primary action: begin focusing**.

## Design direction

- **Aesthetic:** quiet editorial, premium utilitarian, minimal movement, not kawaii.
- **Colors:** ink #211C26, secondary plum #3A2C40, warm off-white #F9F5F6, brand rose #C75C81, focus-success green #477B6E.
- **Typography:** Manrope, bundled at build time under SIL OFL; large 30sp hero header and 48sp timer figures. Uppercase labels are secondary and never the primary heading.
- **Composition:** place the most important state in a single immersive dark session surface. Put duration and action together. Turtle and audio become supporting panels, not distractions.
- **Interaction:** high-contrast 58dp primary touch target, buttons never masquerading as labels, animation only where state changes.
- **Accessibility:** clear textual state alongside color, readable label hierarchy, avoid essential meaning conveyed by animation alone, device font scaling via sp.

## Inspiration studied

1. [Android Material 3 guidelines](https://developer.android.com/develop/ui/compose/designsystems/material3), type ramps and accessible surface roles.
2. [Micheal-Jiaming/Pomodoro-timer-Android](https://github.com/Micheal-Jiaming/Pomodoro-timer-Android), offline timer design with a deadline-derived countdown rather than decrementing a ticking counter.
3. [MartynasCirvinskas/FocusBee](https://github.com/MartynasCirvinskas/FocusBee), Kotlin TimerViewModel, foreground notification architecture, separate settings persistence.
4. [adnanrangrej/Focus-Modes-App](https://github.com/adnanrangrej/Focus-Modes-App), architecture and app-blocking OEM limitations.
5. [Android alarm scheduling guidance](https://developer.android.com/develop/background-work/services/alarms) and [Android 15 DND changes](https://developer.android.com/about/versions/15/behavior-changes-15).

References are for study; **no third-party focus app source is copied into STAYCALM**.

## Timer contract

Session state is persisted. Current remaining time is always computed from a monotonic elapsed-realtime end deadline on the same boot, with a wall-clock fallback after reboot. The render loop does not decrement a stored counter. Session history increments once after the first valid due transition. Pauses, abandoned sessions, and notification restoration are explicit paths.

Alarm timing: when the user grants the special exact-alarm permission, STAYCALM schedules exact completion alarms. Otherwise, Android's inexact scheduling can delay a background notification; the UI timer still calculates the correct remaining time when opened.

## Egg physics and motion

Six explicit silhouettes: untouched egg; fracture; separated top/lower shell revealing baby; hatchling; juvenile; adult. A spring with damping ratio and stiffness translates and rotates shell halves when transitioning into the hatch stage. The adult turtle uses restrained idle breathing. This is **procedural spring motion and vector geometry**, not a rigid-body physics engine.

## Sound

Seven bundled, documented ambient loops played via Android MediaPlayer in a foreground media playback service. Playback includes offline sound selection, looping, volume, audio focus request and media notification. System playback behavior must be validated on a real phone.

## Privacy and Android restrictions

Local SharedPreferences + JSON are used for the initial offline prototype. No `INTERNET` permission. Android notification-policy access is opt-in; DND is not an app blocker. Exact alarm special access is opt-in. No AI, no account, no cloud.

## Release gate

The source must pass GitHub Actions unit tests and compilation before an APK is presented as buildable. Battery, reboot, notification and media behavior also require a physical Android test device. See `QA-CHECKLIST.md`.
