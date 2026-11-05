# Security Policy — Sali-ZZai

## Supported versions

The main branch is the only supported version. The CI workflow builds debug and release APKs from main.

## What is in place

- **API key at rest**: EncryptedSharedPreferences (AES, Android Keystore-backed).
- **Server-less design**: the app only talks to Z.ai directly; there is no backend to attack.
- **ZIP handling**: extraction stays in app-private storage with skip-lists for dangerous directories and hard size caps (1 MB per file, 200 KB extracted text).
- **Release build**: R8 minification + resource shrinking enabled.

## Reporting a vulnerability

Open a **private security advisory** (Security tab → Report a vulnerability). Include:

1. App version (Settings → About, or versionName from the APK).
2. Steps to reproduce or a proof of concept.
3. Impact assessment.

Do not open public issues for exploitable vulnerabilities.

## Known hardening backlog (tracked for release readiness)

- Replace debug-key release signing with a protected production keystore before store distribution.
- Add ProGuard keep-rule review for Markwon/Coil reflection after R8 changes.
- Consider certificate pinning for the Z.ai API endpoint.
