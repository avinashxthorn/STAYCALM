# Version 3.9: planner file recovery and simple login

Existing project owners: **publish the updated `firestore.rules` from this repository before syncing planner attachments**. The earlier rules allow structured backups but deny the new owner-only `users/{uid}/plannerFiles/{fileId}/chunks/{index}` path. Your existing email/password setup and project config stay the same.

After updating the app, use **Your account → Sync now** once on the phone containing the original planner attachments. Wait for success before removing local files or changing phones. Old 3.8 backups cannot recover files that were never uploaded. New recovery copies reference only completed, checksum-verified file uploads.

Planner file bytes are stored as 512 KiB Firestore binary chunks under each verified account. Uploads checkpoint every eight chunks and resume after interruption. Limits are 80 MiB per file and 160 MiB of current planner files per device recovery copy. The entire project's free Firestore quota is still shared: 1 GiB stored plus daily operations and monthly network quotas. Spark remains usable without billing, but exceeding quotas pauses cloud access; it does not provide unlimited file hosting. Completed chunks and older device copies are retained until the owner removes them through the console. Imported audio stays in full ZIP exports.

In **Firestore → Indexes → Automatic → Add exemption**, keep `backups` / `payload` disabled, and optionally add `chunks` / `data` with every index disabled. The checked-in `firestore.indexes.json` defines both. These exemptions avoid unnecessary index storage; do not disable the `updatedAt` index used to find recent recovery copies.

The initial flow is now: **three intro slides → Get started → Login or Sign up → automatic cloud recovery on a fresh verified login → profile setup if needed → home**. Existing local work is never automatically replaced on login. A failed fresh-install restore stays on a recovery screen with Retry and an explicit Continue without restoring option. Profile and account settings show Login successful. Logout retains local data and stops automatic cloud uploads.

Cloud restore downloads every referenced planner file from the server into staging, verifies expected length and SHA-256, and only then installs file lists and structured progress. Missing, partial or corrupted files abort restore before replacing existing preferences. A local cache is used to open attachments offline; deleting/uninstalling that cache no longer loses successfully synced planner files. Cloud copies are recovery checkpoints, not live concurrent editing across phones.

Before installing a differently signed build, **export a full ZIP from the old app and save it outside the phone**. The existing build workflow uses temporary debug signing keys, so an update may require a separately planned migration; never uninstall while unsynced planner files are still only local. Full ZIP imports preserve existing planner files so they can be synced from the new app after login.

---

# STAYCALM accounts and backup — phone setup

The code is ready for your own Firebase project. The GitHub connection does not authorize access to your Google/Firebase account; the project owner must complete the console steps below. No desktop-only ChatGPT connector or Android Studio is needed for these console steps. If a console panel is cramped on mobile, use Chrome's Desktop site layout (this does not unlock desktop-only ChatGPT plugins).

## 1. Create or select a free Firebase project

Open https://console.firebase.google.com in your phone browser and sign in. Create/select a project and keep it on the **Spark** plan. Do not add a payment method or enable SMS/Phone authentication. Google Analytics is not required.

Email/password authentication and Standard Cloud Firestore have no-cost allowances. At the time of implementation, Firestore's free database allowance is 1 GiB stored, 50,000 document reads/day, 20,000 writes/day and 20,000 deletes/day; network limits also apply. Free quota is shared by the whole project, not reserved for this app. Exceeding Spark quotas can make cloud operations unavailable until quota resets. The app keeps local data and reports failed/pending backups. Nothing promises free unlimited storage or permanent recovery.

Sources: https://firebase.google.com/pricing and https://firebase.google.com/docs/firestore/quotas

## 2. Register the Android app

In Project settings → General → Your apps, add an Android app:

- Package name: **com.staycalm.focus** (exactly)
- Nickname: STAYCALM (optional)
- SHA certificate: not required for email/password login

Download **google-services.json**. This Android client configuration contains non-secret project identifiers; it is different from a service-account private key. You can attach this client config in the chat so it can be added to the build. Never send a service-account JSON file, password, or private key.

Alternatively, add the entire google-services.json text as a GitHub Actions repository secret named **FIREBASE_ANDROID_CONFIG**, at repository Settings → Secrets and variables → Actions → New repository secret. The APK workflow reads this secret without printing it. Local builds can place it at `app/google-services.json`, which is git-ignored. No Firebase admin credentials are required in the app or workflow.

Source: https://firebase.google.com/docs/android/setup

## 3. Enable email login

Firebase console → Build → Authentication → Get started → Sign-in method → Email/Password → Enable → Save. Leave Email link and Phone disabled. Review the verification and password-reset templates under Authentication → Templates. Use a strong password policy. Firebase stores password credentials; STAYCALM never writes passwords into Firestore or backup files.

## 4. Create and secure the database

Firebase console → Build → Firestore Database → Create database:

1. Choose **Standard edition**, the **(default)** database, and production mode.
2. Choose a region close to your users; the location cannot easily be changed later.
3. Open Rules, replace the editor contents with this repository's **firestore.rules**, then Publish.
4. In Indexes → Single field, add an exemption for collection group `backups`, field `payload`, with ascending, descending and array indexes disabled. This prevents large JSON snapshots from being indexed. The equivalent CLI configuration is in firestore.indexes.json.

The rules allow only a signed-in user with a verified email to read/write their own `users/{uid}/backups/{deviceId}` documents. All other client paths are denied. Keep the rules restrictive; do not switch to public test rules to fix an error. If you reuse an existing database, integrate these rules with its existing rules before publishing rather than replacing unrelated app policies.

The included `firebase.json` can also deploy rules and indexes with an authenticated Firebase CLI: `firebase deploy --only firestore --project YOUR_PROJECT_ID`. There is no CLI project ID committed to this repo so it cannot accidentally deploy to someone else's project.

## 5. Build the configured APK

After the Android configuration is supplied, run GitHub → STAYCALM → Actions → STAYCALM · Android APK → Run workflow → main. Builds without config remain local-only and clearly say cloud setup is needed. Download the **STAYCALM.apk** artifact/release, not a Source code ZIP. A successful Android build verifies compilation and local behavior; a real project login/restore test is still needed after console setup.

**Preserve your current installation:** the existing workflow uses runner-generated debug signing keys. A different signing certificate cannot update an installed app. Do not uninstall an old installation containing important data just to bypass “App not installed.” This change cannot recover private data from an already uninstalled older build. A stable signing key (configured separately) is needed for reliable future updates.

## 6. Sign up and test recovery

- Open Settings → Account & backup (or Log in / Restore on the welcome screen).
- Sign up with email/password. Open the verification email and return to Check verification.
- On your original phone, choose Enable backup for this device and confirm. Wait for the first successful backup.
- Create a test plan, complete a short session, then Back up now. Confirm a document appears in Firestore under your account UID.
- On another installation/device, log in with the **same account**. Choose Find cloud backups to restore, select the original device's copy, and confirm. Restore must be done with no active/paused session.
- Verify plans, completed history, rewards and profile. Restore replaces local structured data; it does not merge two devices. Enable automatic backup again after checking the restored data.
- Test Export full backup and Restore from backup file with a test PDF and imported audio file before relying on it. Keep the exported ZIP outside the phone (for example in Google Drive). The ZIP contains private data and is not encrypted by the app.
- Confirm a second account cannot find the first account's backups. The CI Firestore emulator tests also cover cross-account access, verified-email requirements and document validation.

## What version 3.8 backed up (historical)

| Data | Firebase cloud copy | Full ZIP export |
| --- | --- | --- |
| Planner entries/reminders, focus history, rewards | Yes | Yes |
| Profile and optional unverified phone number | Yes | Yes |
| Theme, study goal, motivation settings, media links | Yes | Yes |
| Imported PDFs/journal attachments and audio files | No | Yes |
| Passwords, login tokens, live timer, Android permission grants | Never | Never |

Automatic backup is opt-in and uses network-constrained WorkManager jobs about 20 seconds after changes, plus a six-hour safety check. Android battery restrictions and network availability can delay it. Completed uploads are acknowledged before showing a successful backup time. Unchanged snapshots are skipped. Each installation owns one cloud document, avoiding one phone silently replacing another phone's recovery copy. Each snapshot is capped at 750 KB; a clear error requests file export if structured history exceeds it. Old device copies remain in Firestore until the owner deletes them through the console. Device labels and payload fields are not indexed unnecessarily.

Logging in never uploads existing local data without explicit consent. Logout stops future automatic uploads but keeps local and existing cloud data. Do not enable upload for another account unless you intend to copy this phone's data to that account. Cloud restore turns automatic backup off; it keeps local PDF/audio files and file lists. Full ZIP restore replaces file lists as well. Restore does not start timers or grant Android permissions. Restored past reminders are not fired as new alarms.

Cloud copies are recovery checkpoints, not real-time cross-device sync or indefinite revision history. A user can still lose changes that were never uploaded/exported. Firestore backups/PITR and Cloud Storage are not enabled, keeping paid services outside this setup.
