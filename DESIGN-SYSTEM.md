# STAYCALM · Focus edition 4.3

A ChatGPT-inspired *visual design language*, not an OpenAI-branded app. STAYCALM retains its own name, icon, functions and local/cloud data.

## Colors

- Light: neutral paper background `#F8F9FA`, white cards `#FFFFFF`, charcoal type `#202124`, and evergreen primary `#0E7564`.
- Dark: near-black background `#171717`, charcoal cards `#242424`, muted gray copy `#A5ACA9`, and a bright green accent `#53D1B5`.
- Supporting neutrals keep inputs, calendar rows, history metrics and settings readable without decorative pink.
- Tinted green cards and round active navigation indicators convey progress. The app continues to use bundled Manrope and Material 3.
- Native splash, launcher colors and Android system bars are coordinated with the light/dark palette.

## Animation

- Main navigation slides horizontally with a crossfade (280 ms enter, 200 ms exit) and system motion scaling.
- The circular timer interpolates smoothly, with a quiet green breathing glow while active.
- The main start/pause/resume button animates its text, onboarding page dots expand/contract, and Insight weekly bars interpolate.
- The turtle remains a vector illustration with subtle floating, breathing and spring growth motion; its shell and flippers are now evergreen/mint.
- Motion is declarative Compose animation; it does not alter the countdown's monotonic timing, alarm scheduling or background services.

## Preserved behavior

Timer, short breaks, reward rules, optional blocking, notes/files, YouTube and RSS audio, reminders, Firebase accounts, structured cloud sync, local full ZIP backups and upgrade package ID remain unchanged. Navigation and colors do not change stored settings.

## Timer and alarms

New sessions store Android's boot count. Within the same boot, elapsed realtime determines remaining duration; after a reboot wall clock deadline is a fallback. Alarms and notification permissions remain Android-controlled. See README for the current full app behavior.

## References

- https://developer.android.com/develop/ui/compose/animation/composables-modifiers
- https://developer.android.com/develop/background-work/services/alarms
