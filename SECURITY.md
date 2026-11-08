# Security Policy — Sali-ZZai

## Supported versions

The default branch is the supported development line. CI always builds a debug APK; a production-signed release APK is built only when a valid signing keystore is configured.

## What is in place

- **API key at rest**: EncryptedSharedPreferences (AES, Android Keystore-backed).
- **Server-less design**: the app only talks to Z.ai directly; there is no backend to attack.
- **API transport**: API Base URLs must use HTTPS; OkHttp redirects are disabled so bearer credentials are not automatically forwarded to another host.
- **ZIP handling**: compressed input is capped at 25 MiB, entry count at 2,000, expanded entry size at 10 MiB, and total expanded data at 50 MiB. Traversal, absolute, excessively deep, and ambiguous paths are rejected.
- **Password hashing**: PBKDF2-HMAC-SHA256 with 600,000 iterations; stored parameters and lengths are bounded during verification.
- **Credential storage**: EncryptedSharedPreferences is used with Android Keystore. If recovery fails, credentials remain in process memory instead of falling back to plaintext disk storage.
- **Release build**: R8 minification is enabled. A production-signed APK is emitted only when a valid private signing keystore and all required secrets are configured.

## Reporting a vulnerability

Open a **private security advisory** (Security tab → Report a vulnerability). Include:

1. App version (Settings → About, or versionName from the APK).
2. Steps to reproduce or a proof of concept.
3. Impact assessment.

Do not open public issues for exploitable vulnerabilities.

## Known hardening backlog (tracked for release readiness)

- Add a backend before presenting Google or OTP as production account authentication; the current profiles are local-only and OTP is a local demo.
- Add ProGuard keep-rule review for Markwon/Coil reflection after R8 changes.
- Evaluate certificate pinning only with a certificate rotation and recovery plan.


## Attachment limits

The picker processes at most 8 documents per selection, with a 25 MiB maximum per document and a 50 MiB aggregate budget per selection. Stream reads stop when a limit is exceeded; the entire document is never read into memory without a bound.
