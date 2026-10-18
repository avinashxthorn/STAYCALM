# STAYCALM · Focus, naturally

Native offline Android focus timer with an executive-style rose/charcoal UI, evolving turtle, session history, task planner, procedural ambient soundscapes, and opt-in Do Not Disturb.

### Download APK
Open the repository **Actions → Build Android APK → newest successful run → Artifacts → STAYCALM-debug-apk**. Unzip the downloaded artifact to install `app-debug.apk`. This is a debug build, not a signed production release.

### Design
Rose accents #D56888, ink #29212A, parchment #FCF7F8. No pastel mascots, ads, accounts, AI coach, network API or cloud sync.

### Focus mechanics
Choose 15/25/45/60 minutes. Progress is stored on-device using SharedPreferences. Only completed sessions count toward turtle growth. A fresh turtle starts in an egg and moves through cracks, hatching, hatchling and adult at milestone thresholds.

### Notification control
The user explicitly grants Android Notification Policy Access and enables focus-mode DND. STAYCALM restores the prior interruption filter when focus ends. Manufacturer/device behavior varies. Do Not Disturb is not an app-specific blocker, and important alarms or exemptions may still be delivered according to system settings.

### Audio
Built-in Rain, Ocean, Forest, White Noise, Brown Noise and Night Breeze are procedural soundscapes (no copyrighted audio). The foreground playback service is used only while sounds are active.

### Build
Requires JDK 17, Android SDK 35 and Gradle 8.11.1. Run `gradle assembleDebug`, or use the GitHub Actions workflow.
