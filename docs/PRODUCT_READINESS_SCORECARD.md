# Product Readiness Scorecard — Sali-ZZai

_Assessment date: 2026-11-02 · Focus (per product review): stability, quality, app-store readiness, UX, device testing_

## Overall verdict

**MVP+ / early Beta.** A clean, well-structured native client with a modern stack (Compose, MVVM, Room). The biggest product gaps: no test suite existed until now (first unit tests added in this pass), release APK still debug-signed, no store assets, no privacy docs (now added).

| Dimension | Score | Status |
| --- | --- | --- |
| UX/UI (Compose + Material 3, splash, adaptive icon) | 7/10 | Good |
| Automated tests | 3/10 | ZipExtractor covered now; ViewModels/api/Room untested |
| CI/CD (debug + release APK, artifact, tag releases) | 6/10 | Works; tests just added; no lint step |
| Security & privacy (EncryptedSharedPreferences, no tracking) | 7/10 | Good; policies now added |
| i18n | 4/10 | Mixed EN/FA strings; no in-app language switch |
| Onboarding | 5/10 | Key entry flow exists; needs first-run polish |
| Analytics & observability | 2/10 | Deliberately none; no crash channel |
| Performance (R8 + shrinkResources) | 7/10 | Good |
| App store readiness | 3/10 | No listing assets, debug-signed release, data safety not filled |
| Device testing | 3/10 | No documented matrix; minSdk 26 low-end unverified |

## 30 / 60 / 90-day plan to Beta → Production

### Days 0–30 — Stability & release hygiene
1. Green CI with unit tests (done in this pass: ZipExtractorTest + CI test step).
2. Add ViewModel tests for chat streaming (SSE parsing, error states, cancellation).
3. Create a protected production keystore; sign releases with it (stop debug-key releases).
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
