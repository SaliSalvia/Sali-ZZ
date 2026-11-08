# Product Readiness Scorecard — Sali-ZZai

_Assessment date: 2026-11-08 · Static review of uploaded source; no successful Gradle build or device test was possible in this environment._

## Overall verdict

**MVP+ / early Beta — not yet production-verified.** The uploaded project has a clean native stack (Compose, MVVM, Room), an existing unit-test suite, and build workflow. This review added ZIP/resource bounds, hardened credential fallback and API transport, and made production signing opt-in. A Gradle build could not run here because the Gradle distribution host was unreachable; device, Play Store, and release-signing verification remain outstanding.

| Dimension | Score | Status |
| --- | --- | --- |
| UX/UI (Compose + Material 3, splash, adaptive icon) | 7/10 | Good |
| Automated tests | 5/10 | Model, ZIP and password-hashing tests exist; ViewModels/API/Room still lack integration coverage |
| CI/CD (debug APK + signed release path) | 6/10 | Workflow configured; successful remote run still required; unsigned release publication is prevented |
| Security & privacy | 7/10 | HTTPS-only API URL, redirects disabled, bounded ZIP/password verification, no plaintext key fallback; authentication remains local/demo and still needs an independent security review |
| i18n | 4/10 | Mixed EN/FA strings; no in-app language switch |
| Onboarding | 5/10 | Key entry flow exists; needs first-run polish |
| Analytics & observability | 2/10 | Deliberately none; no crash channel |
| Performance (R8 minification; resource shrinking disabled after a documented launch regression) | 7/10 | Good |
| App store readiness | 3/10 | Target SDK is 35 (new Play submissions/updates require API 36 since 2026-08-31); no store listing assets or completed Play Data Safety declaration; release signing and device validation remain outstanding |
| Device testing | 3/10 | No documented matrix; minSdk 26 low-end unverified |

## 30 / 60 / 90-day plan to Beta → Production

The version packaged with this hardening pass is `2.2.1` (`versionCode` 8). The toolchain uses Gradle 8.9, AGP 8.7.2, Kotlin 2.1.20, KSP2, and Room 2.7.2.

### Days 0–30 — Stability & release hygiene
1. Obtain a green GitHub Actions run for the expanded unit tests and APK build.
2. Add ViewModel tests for chat streaming (SSE parsing, error states, cancellation).
3. Create and protect a production keystore; verify the signed release APK on a physical device.
4. Add CHANGELOG.md; bump versionCode per release.
5. Documented device test matrix pass (see docs/DEVICE_TESTING_MATRIX.md).

### Days 31–60 — Quality & store readiness
1. Complete empty/loading/error/retry states for chat, image gen, discover, ZIP tree.
2. First-run onboarding polish (key entry with "get a key" deep link, validation feedback).
3. Store assets: icon pass, screenshots, EN + FA descriptions, data safety form from PRIVACY.md.
4. Lint step in CI; fix all fatal lint findings.

### Days 61–90 — Product polish
1. In-app language switch (EN/FA) with proper RTL pass — or full strings.xml localization.
2. Opt-in crash reporting evaluated with a privacy-first option.
3. Closed beta group, staged rollout plan.
4. Accessibility pass: TalkBack, font scaling, contrast, touch targets.
