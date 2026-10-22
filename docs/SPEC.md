# Project brief: "Špajza" — Android recipe app

Native Android app that suggests what to cook from the ingredients my household has at home: one section with recipes I can cook right now, one with recipes that need up to 3 extra items to buy. Each recipe shows macros, time and steps. Pantry and shopping list are shared and live-synced with my housemate. Everything is personalized to my profile, ratings and kitchen equipment. For now it's just for me, but build it so it could be published on Google Play later.

## How we work
- Talk to me in Slovak. Code, comments, commit messages and docs in English.
- First: save this brief verbatim as docs/SPEC.md and create a concise CLAUDE.md (stack, architecture rules, build/test commands, conventions). Keep CLAUDE.md current; re-read SPEC.md at the start of every phase.
- You run locally on my PC; the repo lives on GitHub. Check the environment (Android Studio / SDK, JDK 17+, adb). If something is missing, tell me exactly what to install.
- Guide me step by step through anything only I can do (Firebase console, GitHub secrets, SHA-1 fingerprints, USB debugging).
- Before coding, show me a short Phase 1 plan and wait for my OK.
- Work in milestones. After each: unit tests green, ./gradlew assembleDebug passes, commit + push, 3–5 line summary in Slovak. Stop after each phase and tell me what to test on the phone.
- Ask before decisions that are hard to change later (data model, Firestore structure). Don't build features outside the current phase.
- Use the latest stable library versions (verify them), managed in gradle/libs.versions.toml.

## Stack & architecture
- Kotlin, Jetpack Compose, Material 3 (warm food palette, light + dark), single AppCompatActivity (needed for per-app language), minSdk 26, latest stable compile/target SDK. Package: sk.matej.spajza. App name: Špajza.
- MVVM + unidirectional data flow: ViewModels expose StateFlow<UiState>; screens are stateless composables.
- Hilt, Room + KSP, DataStore, Navigation Compose (type-safe routes), kotlinx.serialization, Coil, WorkManager.
- Firebase: Auth (Google Sign-In via Credential Manager) + Cloud Firestore with offline persistence. The project must stay on the free Spark plan: no Cloud Functions, no Cloud Storage.
- Where data lives:
  - Recipe + ingredient catalog (large, read-only, bundled): Room, imported from assets on first launch and when the seed version changes. Never put the catalog in Firestore (read quotas).
  - User and household data (pantry, shopping list, custom recipes, custom ingredients, meal plans, ratings, cook history, profile): Firestore, observed as Flows; works offline, syncs later.
  - Device-only settings (language, notification time): DataStore.
- Packages: domain/ (pure Kotlin: models, use cases, engines; no Android or Firebase imports), data/ (Room, Firestore, repositories, seed importer, Open Food Facts client), ui/ (feature packages + components + theme). Repositories are interfaces; domain logic and ViewModels are tested with fakes.
- i18n: Slovak default (values/), English (values-en/), in-app language switch via the per-app language API. Catalog content is bilingual: every user-facing text field is a localized {sk, en} value.
- GitHub Actions: on every push run unit tests and build a debug APK; upload it as an artifact and to a rolling "latest-debug" pre-release. google-services.json and a dedicated debug keystore are NOT committed; CI decodes them from secrets so CI-built APKs share the SHA-1 registered in Firebase (required for Google Sign-In).

## Non-negotiable rules
1. Offline-first: after the first sign-in everything works offline (Firestore cache) and syncs when online.
2. Nutrition is ALWAYS computed by the app: grams × values per 100 g from the ingredient table. Never trust nutrition numbers from imports or external sources without recomputing.
3. Diet and allergen hard filters exist in the domain (AllergenGuard, DietFilter) and in the data model, but their UI stays hidden behind a feature flag for now.
4. Household members must never overwrite each other's changes: use FieldValue.increment for quantity changes and server timestamps; otherwise last-write-wins per field.
5. Firestore security rules from day one: a user can access only their own user document and households they are a member of. Invite codes expire; joining is validated by the rules.
6. Computed calorie targets never go below BMR; no shaming language.
7. Publishable later: original content only (no copied recipes or images), an About screen with attributions (Open Food Facts — ODbL, USDA FoodData Central), in-app account and data deletion, Firestore in an EU location.

## Data model
Catalog (Room):
- Ingredient: id (stable slug), name {sk, en}, synonyms (SK/CZ/EN incl. common inflected forms, used for search and parsing), category, nutrition per 100 g (kcal, protein, carbs, fat, fiber, sugar, salt), gramsPerPiece?, densityGPerMl?, allergens (EU 14), diet flags (meat, fish, animal product, dairy, egg, gluten, pork), isDefaultStaple, typicalShelfLifeDays?, priceTier (CHEAP/MID/EXPENSIVE), spicy, nutritionSource ("USDA FDC <id>" or "estimate").
- Recipe: id, title {sk, en}, description {sk, en}, cuisine, mealTypes, totalTimeMin, activeTimeMin, difficulty 1–3, servings, required equipment, authored tags (STUDENT, MEAL_PREP, ONE_POT, ...), pairsWith (optional recipe ids).
- RecipeIngredient: recipeId, ingredientId, amount, unit, grams (normalized at import), role (MAIN/SECONDARY/SEASONING), optional, note {sk, en}.
- RecipeStep: recipeId, index, text {sk, en}, timerSeconds?.
- Substitution: ingredientId -> substituteId, ratio, note {sk, en}.
- Computed tags, derived at import, never authored: VEGAN, VEGETARIAN (from ingredient flags), HIGH_PROTEIN, BULK, LIGHT (from per-serving macro thresholds), CHEAP (from price tiers), QUICK (≤ 20 min). All thresholds in one config object.

Firestore:
- users/{uid}: displayName, profile (sex, birthYear, heightCm, weightKg, activityLevel, goal LOSE/MAINTAIN/GAIN, kcalOverride?), liked/disliked ingredients, liked cuisines, max cooking time weekday/weekend (default 30/60), cooking skill, spicy tolerance, budget (default MID), default servings (default 1), mealPrep (default true), householdId.
- users/{uid}/ratings/{recipeId}: like/dislike, favorite, note.
- users/{uid}/cookHistory/{id}: recipeId, cookedAt, servings (used for preference learning; NOT a nutrition diary).
- households/{hid}: name, memberUids, inviteCode + expiry, staples (editable default list), kitchen equipment.
- households/{hid}/pantry/{id}: ingredientId or customIngredientId, quantity? + unit? (optional), expiresAt?, addedBy, updatedAt.
- households/{hid}/shopping/{id}: ingredientId or free text, amount?, checked, reasonRecipeIds, addedBy.
- households/{hid}/recipes/{id}: our own recipes (manual / URL / photo import), same shape as catalog recipes, single language.
- households/{hid}/customIngredients/{id}: from barcode or manual entry, nutrition per 100 g.
- households/{hid}/mealPlans/{weekId}: day -> recipe ids + servings.
- Units: g, kg, ml, l, ks, lyžica 15 ml, lyžička 5 ml, hrnček 250 ml, štipka ~0.5 g, podľa chuti 0 g (EN: pcs, tbsp, tsp, cup, pinch, to taste). UnitConverter normalizes to grams; the importer fails loudly on a missing conversion.

## Recipe catalog content (the app must feel full without me adding anything)
- You write ORIGINAL recipes (never copied from websites) in batches into app/src/main/assets/seed/recipes/<category>.json; the importer reads all files. Every recipe is bilingual SK + EN, with realistic amounts, times and steps, incl. doneness cues for meat and fish.
- Coverage matrix: world cuisines (Slovak/Czech, Italian, French, Spanish, Greek/Mediterranean, Middle Eastern, Indian, Chinese, Japanese, Korean, Thai, Vietnamese, Mexican, American, ...) × types (student, cheap, vegan, vegetarian, high-protein, bulking, light/diet, meal prep) × meal types (breakfast, lunch, dinner, snack, dessert). Keep a coverage report in docs/CONTENT.md.
- Targets: Phase 1 → 150 recipes + ~250 ingredients, Phase 2 → 400+, Phase 3 → 800+.
- Ingredient nutrition from USDA FoodData Central (store the FDC id). Local foods without a match (tvaroh, bryndza, kyslá smotana, ...) use typical label values marked "estimate" and are listed in docs/DATA_TODO.md.
- Content tests: all ids resolve, all units convert, both languages present, kcal per serving > 0, sanity bounds on macros, no duplicate ids or near-duplicate titles.

## Domain logic (pure Kotlin, unit-tested; write tests first)
- NutritionCalculator: totals, per serving, servings scaling.
- TargetCalculator: Mifflin–St Jeor BMR × activity factor (1.2 / 1.375 / 1.55 / 1.725 / 1.9) = TDEE; LOSE −20 %, MAINTAIN 0, GAIN +10 %; floor = BMR; kcalOverride wins (gentle warning if below BMR). Macros use fixed defaults (protein 1.8 g/kg, fat 30 % of kcal, carbs = rest), not configurable in the UI.
- RecipeMatcher:
  - hard filters: required equipment the household doesn't have, recipes I disliked, disliked MAIN/SECONDARY ingredients (+ the hidden diet/allergen filters);
  - staples count as available; a substitute in the pantry counts 0.8; a pantry quantity lower than needed counts as partial;
  - score in this priority order: 1) ingredient coverage (MAIN 3, SECONDARY 2, SEASONING 1); 2) my ratings (liked recipes and similar ones by cuisine/tags/main ingredients rank higher, cook history counts too); 3) macro fit to my goal (protein per 100 kcal always helps; LOSE prefers lower kcal per serving, GAIN higher; reference ≈ 1/3 of the daily target); 4) time (within my weekday/weekend limit, quicker is better); 5) bonus for using items expiring within 3 days. Small adjustments for skill, spiciness and budget. All weights in one documented config object;
  - output: READY (nothing missing) and TO_BUY (1–3 missing, listed with price tier); hide the rest.
- PurchaseSuggester: greedy weighted max coverage. Candidates = missing ingredients of recipes with ≤ 3 missing that pass the hard filters. Repeatedly pick the ingredient with the highest gain (scores of recipes that become READY + partial credit for recipes that get closer, adjusted by price tier), add it to a virtual pantry, up to 3 items. Output: items + the recipes they unlock. Also a single-recipe mode.
- CookingDeduction: after "Uvaril som", subtract the used grams (scaled by servings) from pantry items that have a quantity, remove items that reach 0, never touch staples or items without a quantity; returns an undo action.
- IngredientSearch (diacritics-insensitive, synonyms, prefix match), IngredientLineParser ("200 g kuracích pŕs" -> amount, unit, ingredient match with a confidence score; handles Slovak/Czech inflection via synonyms + prefix matching), AllergenGuard, DietFilter, UnitConverter.

## Screens (bottom nav: Čo uvariť · Špajza · Nákup · Profil; Plán added in Phase 3)
- Onboarding: Google sign-in → household is created (joining by invite code comes in Phase 2) → profile (sex, age, height, weight, activity, goal → computed kcal + macros, kcal editable) → likes/dislikes, time, skill, spiciness → kitchen equipment → staples (editable default list). Everything after sign-in is skippable.
- Čo uvariť: two sections, "Môžeš uvariť hneď" and "Treba dokúpiť" (max 3 items). Cards show name, kcal + B/S/T per serving and cooking time; "Treba dokúpiť" cards also list the missing items. A "Dokúp a odomkni" card shows the PurchaseSuggester result. Category chips (cuisines + types: študentské, lacné, vegánske, vegetariánske, high-protein, objemovka, diétne, ...) and a meal-type filter apply to both sections. Search across the whole catalog, including recipes I can't make yet.
- Špajza: search with autocomplete + quick-add chips by category, optional quantity and expiry date, list grouped by category, expiry badges, swipe to delete, who added each item.
- Recipe detail: macros per serving (kcal, B/S/T, fiber), servings stepper (scales amounts and macros), ingredients marked have / missing / substitute available, suggestions for substitutions and add-ons ("Máš aj X — hodí sa sem", pairsWith), "Pridať chýbajúce do nákupu", steps, favorite, like/dislike, "Začať variť", "Uvaril som" (logs cook history, auto-deducts from the pantry, snackbar with "Späť").
- Cooking mode: one step per screen, large text, screen stays on, step timers.
- Nákup: shared and live-synced, grouped by category, check off, "Kúpené → do špajze" (optional quantity/expiry prompt), share as text.
- Profil: profile fields, computed vs. overridden kcal and macros, likes/dislikes, kitchen equipment, staples, household (members, invite code, leave), language SK/EN, notifications, About (attributions), delete account and data.
- No stock photos: tasteful generated placeholders (color + cuisine/category icon). Helpful empty states everywhere.

## Phases
- Phase 1 — core. M1: project setup, CI, Firebase (Auth + Firestore in an EU location + security rules), Room catalog + importer + content tests, SK/EN. M2: domain logic + tests. M3: all screens above. Content: 150 recipes. Done when I can sign in, add 5 ingredients and immediately see both sections with correct macros, get "Dokúp" suggestions, cook a recipe in cooking mode and see the pantry deducted (with undo), and switch the app to English.
- Phase 2 — household & smart input: invite/join household with a live shared pantry and shopping list; barcode scanning (ML Kit) + Open Food Facts lookup (custom User-Agent) → custom ingredients; receipt photo → on-device OCR (ML Kit Text Recognition) → matched items for me to confirm → pantry; expiry notifications (WorkManager, once a day, expiry only); our own recipes: manual editor, import from URL (schema.org Recipe JSON-LD, also via Android "Share" from the browser), import from a photo (on-device OCR → editor where I mark ingredients and steps). Content: 400+ recipes.
- Phase 3 — planning: weekly meal plan (Plán tab) with one combined shopping list minus what's already in the pantry; my own photos on recipes (stored on the device only; cloud photos would need the paid Blaze plan); better preference learning; Firestore rules tests with the emulator. Content: 800+ recipes.
- Later, only when I say so: AI via the Claude API through a backend proxy (never an API key in the app) for generating recipes on demand, recognizing ingredients from a fridge photo and smarter imports. Keep an AiRecipeService interface stub so it plugs in without refactoring.

Start now: save the brief, create CLAUDE.md, check the environment and show me the Phase 1 plan.
