# Contributing to Sali-ZZai

## Setup

1. Clone and open in Android Studio.
2. Toolchain: JDK 17, Gradle 8.9, Kotlin 2.0, compileSdk 35, minSdk 26.
3. Run: `./gradlew assembleDebug` · Tests: `./gradlew testDebugUnitTest`

## Ground rules

1. **Tests required** for new logic (util, data, ViewModel). Pure-JVM code gets plain JUnit tests; see app/src/test for the pattern.
2. **No secrets in source.** The Z.ai key is entered by the user at runtime; never commit real keys or .env files.
3. **Small PRs**, one concern each, short imperative commit messages (fix:/feat:/docs:/test:/chore:).
4. **Architecture**: MVVM — feature/ packages own screens + ViewModels, data/ owns api/db/prefs, util/ owns helpers. Keep ZIP extraction limits (1 MB/file, 200 KB text) intact — they are safety bounds.
5. **UI**: Compose + Material 3 only; no XML layouts for new screens.
6. **Secrets handling unchanged**: any change to EncryptedSharedPreferences usage or network layer needs explicit justification in the PR.

## Bugs & security

Use the issue templates. For vulnerabilities see SECURITY.md — never public issues.
