# Staycalm AI

Staycalm 4.2 adds a Gemini study coach to **Insights → Staycalm AI**. It generates three study insights, seven dated study sessions and seven rotating coaching notifications. Each session can be added to the existing Planner with a reminder and edited there. Nothing is added automatically.

## Enable on your phone

1. Create a personal Gemini API key in [Google AI Studio](https://aistudio.google.com/apikey). Replace any key previously pasted into a chat or public page.
2. In **Insights → Coach settings**, enter the key, future study goal, weaker topics and optional exam date.
3. Enable AI after reading the data-sharing description, then save. The first plan is generated immediately. Enable coaching notifications if wanted, and choose their interval under the existing encouragement settings. Android notification permission is also required.

This version uses personal keys entered on the device. The owner's shared key is **not** bundled into the APK or committed to GitHub. Each user supplies their own key. For a centrally funded public app, deploy a separately authenticated backend or configure Firebase AI Logic/App Check before switching to a shared project. Do not add a shared key as a BuildConfig field, resource, Remote Config value or GitHub build secret compiled into the APK.

## Data and storage

With AI enabled, requests send the profile name, subject, current focus topic, stated goals/weak topics/exam date, daily study budget, up to 40 sessions from the last 14 days, and up to 30 planner entries within seven days either side of today. Journal attachments, chat, audio, Firebase tokens, passwords and file contents are not sent. Google’s free-tier data policies apply; the free tier may use inputs and outputs to improve Google products.

Keys are encrypted using an account-specific Android Keystore AES-GCM key. Ciphertext lives in `noBackupFilesDir`, outside both Android backups and Staycalm's local/cloud exports. A normal signed in-place update preserves it. On a new device or reinstall, enter the key and grant consent again. Goals can be included in user-initiated backups; consent, generated coaching, API credentials and request counters are excluded. Disconnect disables requests and AI nudges, clears the generated cache, deletes the encrypted key and cancels background work. No request logs contain credentials or model bodies.

## Requests, limits and notifications

The REST client calls `gemini-3.5-flash-lite:generateContent` over HTTPS with an API-key header, JSON output schema, bounded response size and network timeouts. It refuses redirects. Model output is validated for dates, lengths and session duration before saving. It cannot execute actions or modify the planner. Failed refreshes preserve the previous report and show a clear offline, authorization or quota error.

There is a local six-attempt daily limit and a one-minute minimum interval shared by foreground and background requests. Unchanged coaching is cached for 12 hours. These are app-side quota-saving measures, not a billing guarantee; use a free-tier Google project without paid billing. Google controls eligibility, pricing and quotas. The app never upgrades a project or switches to a paid model automatically.

Opt-in WorkManager jobs refresh coaching approximately every 12 hours with network access. Existing motivation alarms deliver cached AI nudges at the user's configured interval, only from 08:00 through 21:59, while no focus/break timer is running. Context changes or messages older than 24 hours use the existing clearly titled generic encouragement and schedule a background refresh. AI text is titled **Staycalm AI · Your study coach**. Android may delay background work or alarms. Signing out cancels work; returning to an account schedules its own coaching. The account and consent version are checked again before accepting an in-flight response.

## Validation

Unit tests cover seven-day dates, JSON cache round-trip, malformed/incomplete output, daily budget bounds and credential/consent exclusion from backups. Device tests cover encrypted key storage, account isolation and no network access without AI consent. Existing Android UI, planner alarms, APK signing and install-over-existing update checks remain in the release workflow. Live Gemini generation requires a user-entered valid key and available quota; it is not exercised by CI.

Local verification for this change: all production Android/Compose sources and all instrumentation-test sources compiled against Android API 35 with Kotlin/Compose compiler 2.0.21; all 33 JUnit tests passed. GitHub Actions also checks unit tests, lint, APK integrity, Android UI flows and install-over-existing-app retention before publishing a release. Live Gemini generation requires a user-entered valid key and is not exercised by CI.

References: [API keys](https://ai.google.dev/gemini-api/docs/api-key), [models](https://ai.google.dev/gemini-api/docs/models), [structured output](https://ai.google.dev/gemini-api/docs/structured-output), [pricing](https://ai.google.dev/gemini-api/docs/pricing).
