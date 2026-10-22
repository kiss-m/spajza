# Špajza — notes for Claude

Android app that suggests recipes from the household pantry. `docs/SPEC.md` is the source of truth:
re-read it at the start of every phase. Content coverage lives in `docs/CONTENT.md`, nutrition data gaps
in `docs/DATA_TODO.md`. Keep this file current when the stack, commands or rules change.

## Working agreement
- Talk to the user in Slovak, using gender-neutral phrasing. Code, comments, commits and docs are in English.
- Milestones: unit tests green + `./gradlew assembleDebug` passes → commit + push → 3–5 line summary in
  Slovak. Stop after each phase with a list of things to test on the phone.
- Ask before hard-to-change decisions (data model, Firestore structure, Firestore location). Build only
  features of the current phase.
- Library versions: latest stable, verified before use, declared only in `gradle/libs.versions.toml`.
- Guide the user step by step through anything only they can do (Firebase console, GitHub secrets, SHA-1).

## Environment
- Claude works in an ephemeral cloud container: JDK 21, no adb, no phone. Builds that reach the phone
  come from CI (rolling `latest-debug` pre-release). Push work before the session ends.
- Needed network hosts: `dl.google.com` (Android SDK + Google Maven), `fdc.nal.usda.gov` (USDA data).
- `app/google-services.json` and the debug keystore are never committed. Local container builds use a
  dummy `google-services.json` (Firebase calls fail, the build and unit tests work); CI decodes the real
  ones from GitHub secrets.

## Stack
Kotlin · Jetpack Compose + Material 3 (warm palette, light + dark) · single `AppCompatActivity` (per-app
language) · minSdk 26, latest stable compile/target SDK · Hilt · Room + KSP · DataStore · Navigation
Compose with type-safe routes · kotlinx.serialization · Coil · WorkManager · Firebase Auth (Google
Sign-In via Credential Manager) + Cloud Firestore with offline persistence.
Spark plan only: no Cloud Functions, no Cloud Storage. Package `sk.matej.spajza`, app name Špajza.

## Architecture rules
- `domain/`: pure Kotlin models, use cases, engines and repository interfaces. No `android.*`,
  `androidx.*` or `com.google.firebase.*` imports.
- `data/`: Room catalog, Firestore, repository implementations, seed importer, Open Food Facts client.
- `ui/<feature>/`, `ui/components/`, `ui/theme/`.
- MVVM + UDF: ViewModels expose `StateFlow<UiState>`; screens are stateless composables taking state and
  event callbacks; a thin route composable wires the ViewModel.
- Data placement: catalog → Room, imported from `app/src/main/assets/seed/` on first launch and when the
  seed version changes (never Firestore). User/household data → Firestore, observed as Flows, works
  offline. Device-only settings → DataStore.
- Nutrition is always computed: grams × per-100 g values. Never trust imported nutrition numbers.
- Concurrent household edits: `FieldValue.increment` for quantities, server timestamps, field-level
  updates (never overwrite a whole document another member may have changed).
- Diet/allergen filters (AllergenGuard, DietFilter) live in domain and the model; their UI is behind a
  feature flag that stays off.
- Every tunable (scoring weights, tag thresholds) lives in one documented config object.
- Calorie targets never go below BMR; no shaming language.
- Strings: Slovak in `values/`, English in `values-en/`, no hardcoded UI text. Catalog text is a
  localized `{sk, en}` value.
- Original content only: no copied recipes, no stock photos (generated placeholders).

## Commands
(available once the Gradle project exists, M1)
- `./gradlew testDebugUnitTest` — unit tests, including catalog content tests
- `./gradlew assembleDebug` — debug APK
- `./gradlew lintDebug` — Android lint

## Conventions
- Domain logic: tests first; ViewModels and domain tested with fakes, not Firebase mocks.
- Commit messages: imperative English subject, ≤ 72 chars, body explains why.
