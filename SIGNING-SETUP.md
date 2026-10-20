# Permanent Android updates

Production APKs must always use the same signing certificate and package ID (`com.staycalm.focus`). CI now pins the public SHA-256 fingerprint in `signing/certificate-sha256.txt` and assigns an increasing version code from the workflow run number. It refuses to publish temporary debug builds as releases.

## One-time setup from a phone

1. Open the private signing setup file supplied separately. Keep it private: it contains the key and password. Never put it in repository files, issues or comments.
2. Open [new Actions secret](https://github.com/avinashxthorn/STAYCALM/settings/secrets/actions/new).
3. Add `STAYCALM_KEYSTORE_BASE64`, copying its value from the private setup file.
4. Add `STAYCALM_KEYSTORE_PASSWORD`, copying its value from the same file.
5. Open [Android workflow](https://github.com/avinashxthorn/STAYCALM/actions/workflows/android-apk.yml), tap **Run workflow**, select **main**, and run it.
6. Once all checks pass, download `STAYCALM.apk` from the newest release. Future releases use this same key. Retain a secure backup of the private setup file; do not regenerate the key.

The GitHub connection used to prepare this change cannot create repository secrets, so the owner must complete steps 2–4. Missing secrets allow verification but block publishing with a clear error.

## Existing installations

Older CI builds generated temporary debug signing keys on separate runners. An APK's public certificate cannot recover its private signing key. If the installed app has one of those certificates and its original key was not retained, Android cannot accept a differently signed APK as an update. Export a full ZIP and confirm cloud progress sync before any one-time migration. Do not uninstall without your backup. The permanent key fixes subsequent updates, but cannot bypass Android's old certificate requirement.

A normal same-key update preserves app storage, settings, account state and local planner files. It does not call logout. Firebase may still require authentication if credentials are revoked independently. Uninstalling or clearing Android app data removes local data. Files are local-only; cloud sync remains limited to structured progress at the user's request.

## Verification

CI checks the configured key and built APK against the pinned public certificate, runs unit/lint and Android instrumentation, and installs two increasing-version APKs over each other using `adb install -r`. The install test compares package UID, account preferences and private planner file bytes. A separate package-replacement broadcast test checks that the app retains its account fixture and settings through recreation; it does not sign into the owner's production Firebase account.
