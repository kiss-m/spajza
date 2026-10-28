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
  Google Maven versions are checked with the `Google Maven versions` workflow (edit its artifact list,
  push to an `m1/**` branch or run it manually, read the notices).
- Guide the user step by step through anything only they can do (Firebase console, GitHub secrets, SHA-1).
- Repo: github.com/kiss-m/spajza, no pull requests. Work that needs CI to compile (`:app`) goes through a
  short-lived branch (`m3/app`, …) that is fast-forwarded into `main` once CI is green.

## Environment
- Claude works in an ephemeral cloud container: JDK 21, Gradle wrapper, no Android SDK, no adb, no phone.
  APKs reach the phone through CI (rolling `latest-debug` pre-release). Push work before the session ends.
- Needed network hosts: `dl.google.com` (Android SDK + Google Maven), `fdc.nal.usda.gov` (USDA data).
  While they are blocked, `~/.gradle/gradle.properties` holds `spajza.jvmOnly=true`: `settings.gradle.kts`
  then leaves `:app` out and local builds cover only `:domain` and `:seed`. `:app` is built by CI.
- Reading CI from the container: job logs are not reachable, but `scripts/ci-annotate.sh` turns Gradle
  failures, compiler errors and failed tests into annotations. Fetch them with `curl` from
  `api.github.com/repos/kiss-m/spajza/actions/runs?branch=<branch>` → the run's jobs → each job's
  `check_run_url` + `/annotations`. Pure-Kotlin parts of `:app` (mappers, `CookFeed`, `RecipeDetailBuilder`,
  `BodyDraft`, `NumberFormat`) can be compiled and tested locally in a scratch JVM project with stub Room
  annotations, so keep such logic in files without Android or Compose imports.
- Maven Central sometimes answers 429; rerun the build.
- `app/google-services.json` and `keystore/` are never committed. `scripts/placeholder-google-services.sh`
  writes a placeholder so the app builds without Firebase; CI writes the real files from the single
  secret `SPAJZA_SECRETS` (JSON with `google_services_json` and `debug_keystore_base64`, prepared by
  Claude so the owner pastes one value) and checks them in `scripts/ci-write-secrets.sh`. The dedicated debug key (PKCS12, alias `androiddebugkey`, password
  `android`) has SHA-1 `CD:17:72:E2:5D:D0:A0:A3:9C:77:11:14:58:FC:86:E9:C2:72:58:BC`; it was generated
  for the owner and lives only inside that secret. The owner is not technical: they need click-by-click
  steps and no command line.
- Firebase project `spajza-f6fa6` (Spark plan): Firestore `(default)` in eur3, Google sign-in enabled,
  Android app `sk.matej.spajza` with the debug SHA-1, rules from `firestore.rules`. It was set up through
  the Firebase Management, Firestore and Rules APIs with a temporary `gcloud auth login
  --no-launch-browser` of the owner (run gcloud with `CLOUDSDK_AUTH_ACCESS_TOKEN` unset; revoke the login
  when done). Enabling the Google provider has no public API: the owner clicks it in the console.

## Stack
Kotlin · Jetpack Compose + Material 3 (warm palette, light + dark) · single `AppCompatActivity` (per-app
language) · minSdk 26, latest stable compile/target SDK · Hilt · Room 3 (`androidx.room3`) + KSP ·
DataStore · Navigation Compose with type-safe routes · kotlinx.serialization · Coil · WorkManager ·
Firebase Auth (Google Sign-In via Credential Manager) + Cloud Firestore with offline persistence.
Spark plan only: no Cloud Functions, no Cloud Storage. Package `sk.matej.spajza`, app name Špajza.

## Modules
- `:domain` (Kotlin/JVM, `sk.matej.spajza.domain`): models, engines, use cases, repository interfaces,
  `SessionManager` and `HomeData` (the shared live household data). No `android.*`, `androidx.*` or
  `com.google.firebase.*` imports — the module cannot see them. Fakes and fixtures live in its
  `testFixtures` source set and are shared with the app's tests.
- `:seed` (Kotlin/JVM, `sk.matej.spajza.data.seed`): seed JSON format, `CatalogBuilder` (validation,
  gram normalization, computed tags), content tests, coverage report.
- `:app` (Android, `sk.matej.spajza`): `data/` (Room catalog, Firebase, repository implementations),
  `di/` (Hilt modules), `ui/<feature>/`, `ui/components/`, `ui/theme/`, `ui/navigation/`. Navigation keys
  are `@Serializable` `…Destination` types; `…Route` composables wire a ViewModel to a stateless `…Screen`.

## Architecture rules
- MVVM + UDF: ViewModels expose `StateFlow<UiState>`; screens are stateless composables taking state and
  event callbacks; a thin route composable wires the ViewModel.
- Data placement: catalog → Room, imported from `app/src/main/assets/seed/` on first launch and whenever
  `SeedSources.fingerprint()` changes (never Firestore). User/household data → Firestore, observed as
  Flows, works offline. Device-only settings → DataStore. Own recipes (Firestore) join the catalog in
  memory as `own:<id>` through `HomeData.catalog`, so screens treat them like catalog recipes (D12).
- Nutrition is always computed by `NutritionCalculator`: grams × per-100 g values. Never trust imported
  nutrition numbers.
- Concurrent household edits: `FieldValue.increment` for quantities, server timestamps, field-level
  updates, `arrayUnion`/`arrayRemove` for staples and equipment. Pantry/shopping doc ids are deterministic
  (`i_<slug>`, `c_<customId>`), quantities are stored in the ingredient's `pantryUnit`. Firestore writes
  are never awaited (offline-first); only account deletion and joining a household (a transaction the
  rules check) wait for the server. Field formats: docs/DECISIONS.md D9–D12.
- Domain code runs on Android 8+: no JDK APIs newer than Java 8 that Android lacks (`LocalDate.EPOCH`,
  `List.removeFirst()`, …).
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
  nutrition. Steps use the singular imperative in Slovak and are detailed enough to cook from without
  experience: cut sizes, vessel and heat level, times, sensory and doneness cues, mistakes to avoid, serving
  and storage for meal prep, timers on waiting steps (at least 4 steps, ~150–250 characters each; content test).

## Commands
- `./gradlew test` — all unit tests (domain, seed content tests and, with the Android SDK, `:app`)
- `./gradlew :seed:contentReport` — regenerate docs/CONTENT.md and docs/DATA_TODO.md
- `./gradlew :app:assembleDebug`, `./gradlew :app:lintDebug` — need the Android SDK (CI)
- Firestore rules: `firestore.rules`, deployed with the Rules API (create a ruleset, then update the
  `cloud.firestore` release) during an owner gcloud login, or pasted into the console by the owner.
  Proper emulator rules tests are Phase 3; until then check changes ad hoc with
  `@firebase/rules-unit-testing` in the scratchpad.

## Conventions
- Domain logic: tests first; ViewModels and domain tested with fakes, not Firebase mocks.
- `:app` unit tests use `org.junit.jupiter.api.Test` (`kotlin.test.Test` does not resolve on Android).
- Commit messages: imperative English subject, ≤ 72 chars, body explains why.
