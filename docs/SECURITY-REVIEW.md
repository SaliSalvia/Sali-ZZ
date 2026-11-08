# Security hardening and release gates

This document records the security boundaries of the current local-first Android
application. It is not a claim of certification or a substitute for a mobile
penetration test.

## Changes in this hardening branch

- Credential storage fails closed if Android Keystore-backed encrypted
  preferences cannot be opened. The app no longer silently falls back to
  plaintext SharedPreferences for API credentials.
- ZIP processing enforces a maximum of 10,000 entries, 25 MiB per expanded
  entry, 100 MiB total expanded content, and 1,024 characters per entry path.
  Absolute paths, parent-directory traversal, backslash paths, and empty path
  segments are rejected.
- CI uses read-only repository permissions for build/test work. Release
  publishing is isolated in a job with `contents: write`.
- A public release requires a release APK signed with the configured
  repository keystore. A debug-signed APK is never accepted by the publish job.
- Regression tests cover ZIP path traversal and excessive entry counts.

## Authentication model: important limitations

The app is currently local-first and has no trusted application backend.
Consequently, the local account database is a profile/session convenience,
not a remotely enforceable identity system.

- **Email OTP:** the current implementation generates the code on-device and
  exposes it in the app's local inbox. It does not send an email and does not
  prove control of the mailbox. Do not describe this as email verification or
  production passwordless authentication.
- **Google sign-in:** Credential Manager obtains a Google ID token, but this
  client currently decodes its JWT payload to read profile fields. Payload
  decoding alone is not signature verification. Before using Google identity
  to authorize remote data or privileged operations, validate the token's
  signature and claims (issuer, audience, expiry, and nonce) on a trusted
  backend. The current local-only flow must not be treated as backend
  authorization.
- **Local passwords:** PBKDF2 password hashing protects locally stored
  password verifiers; it does not make a local-only account database suitable
  for cross-device identity, account recovery, or server authorization.

## Release blockers / operator actions

1. Configure all four GitHub Actions secrets:
   `SALI_KEYSTORE_BASE64`, `SALI_KEYSTORE_PASSWORD`,
   `SALI_KEY_ALIAS`, and `SALI_KEY_PASSWORD`.
2. Keep the release keystore and its passwords backed up outside the repo.
   Never paste signing material into issues, source files, or chat.
3. Run the complete CI workflow and inspect test, lint, debug build, and
   signed release build results.
4. Verify the release APK signature with Android SDK `apksigner
   verify --print-certs`; confirm it is not signed by the Android debug key.
5. Test upgrades from the previous production APK, database migrations,
   API streaming cancellation/error paths, and supported Android versions.
6. Before advertising email verification or secure Google-backed accounts,
   implement a trusted authentication service and end-to-end tests.

## Security validation status

The changes in this branch have been applied to source control, but no claim
of successful Gradle compilation, device testing, API integration testing,
or independent penetration testing is made until the GitHub Actions run and
the required device/release checks have completed.
