# Špajza — notes for Claude

Android app that suggests recipes from the household pantry. `docs/SPEC.md` is the source of truth:
re-read it at the start of every phase. Agreed hard-to-change decisions live in `docs/DECISIONS.md`
and override the spec where they differ. `docs/CONTENT.md` (coverage) and `docs/DATA_TODO.md`
(estimated nutrition) are generated. Keep this file current when the stack, commands or rules change.

## Working agreement
- Talk to the user in Slovak, using gender-neutral phrasing. Code, comments, commits and docs are in English.
- Milestones: unit tests green + `./gradlew assembleDebug` passes → commit + push → 3–5 line summary in
  Slovak. Stop after each phase with a list of things to test on the phone.
- Ask before hard-to-change decisions (data model, Firestore structure). Build only current-phase features.
- Library versions: latest stable, verified before use, declared only in `gradle/libs.versions.toml`.
  Entries marked `verify:gmaven` still need a check against Google Maven.
- Guide the user step by step through anything only they can do (Firebase console, GitHub secrets, SHA-1).
- Repo: github.com/kiss-m/spajza, commits go straight to `main`.

## Environment
- Claude works in an ephemeral cloud container: JDK 21, Gradle wrapper, no adb, no phone. APKs reach the
  phone through CI (rolling `latest-debug` pre-release). Push work before the session ends.
- Needed network hosts: `dl.google.com` (Android SDK + Google Maven), `fdc.nal.usda.gov` (USDA data).
- Maven Central sometimes answers 429; rerun the build.
- `app/google-services.json` and `keystore/` are never committed. `scripts/placeholder-google-services.sh`
  writes a placeholder so the app builds without Firebase; CI decodes the real files from the secrets
  `GOOGLE_SERVICES_JSON_B64` and `DEBUG_KEYSTORE_B64`.

## Stack
Kotlin · Jetpack Compose + Material 3 (warm palette, light + dark) · single `AppCompatActivity` (per-app
language) · minSdk 26, latest stable compile/target SDK · Hilt · Room 3 (`androidx.room3`) + KSP ·
DataStore · Navigation Compose with type-safe routes · kotlinx.serialization · Coil · WorkManager ·
Firebase Auth (Google Sign-In via Credential Manager) + Cloud Firestore with offline persistence.
Spark plan only: no Cloud Functions, no Cloud Storage. Package `sk.matej.spajza`, app name Špajza.

## Modules
- `:domain` (Kotlin/JVM, `sk.matej.spajza.domain`): models, engines, use cases, repository interfaces.
  No `android.*`, `androidx.*` or `com.google.firebase.*` imports — the module cannot see them.
- `:seed` (Kotlin/JVM, `sk.matej.spajza.data.seed`): seed JSON format, `CatalogBuilder` (validation,
  gram normalization, computed tags), content tests, coverage report.
- `:app` (Android, not created yet): rest of `data/` (Room, Firestore, repositories) and `ui/<feature>/`,
  `ui/components/`, `ui/theme/`.

## Architecture rules
- MVVM + UDF: ViewModels expose `StateFlow<UiState>`; screens are stateless composables taking state and
  event callbacks; a thin route composable wires the ViewModel.
- Data placement: catalog → Room, imported from `app/src/main/assets/seed/` on first launch and whenever
  `SeedSources.fingerprint()` changes (never Firestore). User/household data → Firestore, observed as
  Flows, works offline. Device-only settings → DataStore.
- Nutrition is always computed by `NutritionCalculator`: grams × per-100 g values. Never trust imported
  nutrition numbers.
- Concurrent household edits: `FieldValue.increment` for quantities, server timestamps, field-level
  updates. Pantry/shopping doc ids are deterministic (`i_<slug>`, `c_<customId>`), quantities are stored
  in the ingredient's `pantryUnit`.
- Diet/allergen filters live in domain and the model; their UI is behind a feature flag that stays off.
- Every tunable (scoring weights, tag thresholds) lives in one documented config object.
- Calorie targets never go below BMR; no shaming language.
- Strings: Slovak in `values/`, English in `values-en/`, no hardcoded UI text. Catalog text is `{sk, en}`.
- Original content only: no copied recipes, no stock photos (generated placeholders).

## Seed content
- Files: `app/src/main/assets/seed/{ingredients,recipes,substitutions}/*.json`; unknown JSON keys fail.
- Ingredients: stable slug ids, SK/CZ/EN synonyms incl. inflected forms (a normalized term may belong to
  one ingredient only), `pantryUnit` + `gramsPerPiece`/`density` for every unit recipes use, EU-14
  allergens consistent with diet flags, nutrition per 100 g with `"USDA FDC <id>"` or `"estimate"`.
  USDA values come from the SR Legacy dataset: the `USDA candidates` workflow on the `data/usda`
  branch (tools/usda/candidates.py) dumps it to that branch, since the container cannot reach
  fdc.nal.usda.gov. Estimates are typical label values and are listed in docs/DATA_TODO.md.
- Substitutions (`substitutions/*.json`): `from` is the recipe ingredient, `to` the substitute,
  `ratio` multiplies the grams.
- Recipes: original, bilingual, realistic amounts, doneness cues for meat and fish, salt amounts that are
  eaten are written out (`TO_TASTE` only for pasta water and similar), optional lines don't count toward
  nutrition. Steps use the singular imperative in Slovak.

## Commands
- `./gradlew test` — all JVM tests (domain + seed content tests)
- `./gradlew :seed:contentReport` — regenerate docs/CONTENT.md and docs/DATA_TODO.md
- `./gradlew assembleDebug`, `./gradlew lintDebug` — once `:app` exists
- Firestore rules: `firestore.rules` (deploy by pasting into the Firebase console). Proper emulator rules
  tests are Phase 3; until then check changes ad hoc with `@firebase/rules-unit-testing` in the scratchpad.

## Conventions
- Domain logic: tests first; ViewModels and domain tested with fakes, not Firebase mocks.
- Commit messages: imperative English subject, ≤ 72 chars, body explains why.
