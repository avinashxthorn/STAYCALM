# Staycalm AI 4.3

Staycalm AI automatically generates focus insights, next steps, break advice and personalized notification text. The app uses only Staycalm AI branding and has no provider-key setup screen.

## First-time profile

The name-entry screen collects a focus area (study, work, personal or other), focus topic, future goal, optional skills to improve, optional target date, preferred focus time and daily focus budget. Automatic coaching and AI personalized notifications have clear on/off controls and a cloud-processing description. Existing profiles are reused on upgrade; earlier explicit AI opt-outs remain off. These details can be edited later from AI settings without repeating onboarding.

## Automatic generation

The app refreshes after changes to the focus topic, planner, goals or completed sessions, with a debounce to avoid sending every keystroke. WorkManager also prepares coaching approximately every six hours when connected. Cached responses remain readable offline. Errors keep the previous report. Android can defer background work; the app cannot promise delivery at an exact time while force-stopped or restricted.

Every request goes to the protected service recorded in ai-service/service.json using the current learner's verified Firebase ID token. The provider credential exists only as the backend's encrypted STAYCALM_AI_API_KEY secret. It is never compiled into an APK, stored in app preferences or copied into a backup. Earlier on-device personal keys are deleted during migration.

The service verifies token signature, exact Firebase project, issuer, expiry and email verification before reading or writing account-scoped coaching. Context is bounded to the profile, recent sessions and planner details; files, audio, passwords, login credentials and chats are not sent to the AI provider. The identity token is verified by the service and is not forwarded to the model. Attempts are limited to six per account daily, one minute apart, and fifty shared attempts per UTC day. These app/server limits do not change provider billing: use a free-tier provider project.

## Notifications

The Settings page has an **AI personalized notifications** switch. AI notices use their own 1, 2, 3 or 6 hour interval and are independent of the older generic encouragement setting. With the AI switch on, there is no app-imposed nighttime or active-session suppression. Android notification permission, Do Not Disturb and background restrictions still apply. During an active session, advice refers to a break after the session rather than starting a conflicting timer. Switching AI notices off removes the delivered coach notice and stops AI notices; ordinary encouragement follows its separate setting.

## Planner and break actions

Suggested focus tasks open a review dialog before saving. The learner can edit title, date, time, duration and reminder. **Find free time** moves the proposed session beyond existing commitments. Saving validates dates, durations and overlapping intervals, including across midnight, then uses a stable suggestion ID to avoid duplicate additions. It rechecks the current planner when saving. Nothing executes model-provided commands.

Break suggestions can start a 5–15 minute break only while no unfinished session exists, or be added as a typed break task in Planner. Starting that task uses break mode. A running focus session is never silently cancelled. Suggestions can still be imperfect and should be adjusted to real priorities.

## Hosting and validation

The API is deployed as an owner-private Sites project. Mobile clients require the platform transport to accept requests without ChatGPT browser sign-in; each API request still requires its own verified Staycalm account token. Changing the platform audience requires the owner's explicit instruction. Do not embed a Sites service-access bearer token in the Android app.

Local checks: 39 JUnit tests pass; production Kotlin/Compose and instrumentation-test sources compile. Nine backend tests pass, including signed identity validation, malformed inputs, quotas and credential non-disclosure. GitHub Actions runs unit/lint, Android device flows, APK signing and update-retention checks. Live authenticated phone-to-service generation still requires the mobile-access audience to be enabled and available provider quota.
