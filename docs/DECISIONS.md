# Decisions

Decisions that are hard to change later, agreed with the owner. Newest last.

## D1 — Firestore location: `eur3`
Multi-region Europe. Cannot be changed after the database is created.

## D2 — One pantry document per ingredient
- `households/{hid}/pantry/{itemId}` uses a deterministic id: `i_<ingredientSlug>` for catalog
  ingredients, `c_<customIngredientId>` for custom ones.
- Two members adding the same ingredient (also offline) end up in the same document; quantities merge
  through `FieldValue.increment`. `expiresAt` keeps the earliest date.
- `households/{hid}/shopping/{itemId}` uses the same ids for catalog ingredients (`reasonRecipeIds`
  merged with `arrayUnion`); free-text items get a random id.

## D3 — Fixed pantry unit per ingredient
- Catalog `Ingredient.pantryUnit` is one of `G`, `ML`, `PCS` (not in the original brief).
- Pantry quantities are stored only in that unit; the UI converts kg → g and l → ml on input, so
  increments from different phones always add the same unit.
- Cooking deduction converts recipe grams into the pantry unit via `densityGPerMl` / `gramsPerPiece`.

## D4 — Invites in a separate collection
- `invites/{code}`: `householdId`, `expiresAt`, `createdBy`. Signed-in users may `get` a single invite
  by its code but may not list the collection.
- Joining = adding your own uid to `households/{hid}.memberUids` while writing `joinCode`; the rules
  check that `invites/{joinCode}` exists, points to this household and has not expired.
- `households/{hid}` keeps `inviteCode` + `inviteExpiresAt` for display.
- Codes: 6 characters from an alphabet without look-alikes (no 0/O, 1/I/L), valid 48 h.
- Rules exist from Phase 1; the join UI arrives in Phase 2.

## D5 — Private GitHub repository, commits straight to `main`

## D6 — Gender-neutral copy
The "cooked it" action is labelled "Uvarené" (EN "Cooked") instead of "Uvaril som".

## D7 — Gradle modules
- `:domain` — Kotlin/JVM, package `sk.matej.spajza.domain`. The compiler enforces "no Android or
  Firebase imports".
- `:seed` — Kotlin/JVM, package `sk.matej.spajza.data.seed`: seed JSON format, parsing, gram
  normalization, computed tags and validation. The content tests run here against
  `app/src/main/assets/seed/` without an Android SDK.
- `:app` — Android: the rest of `data/` (Room, Firestore, repositories) and `ui/`.

## D8 — Room 3
`androidx.room3` (3.x, stable since July 2026, KSP + coroutines only) for the catalog database. The
catalog is read-only and rebuilt from the seed, so it never needs data migrations.

## D9 — Firestore field formats (Phase 1)
- `users/{uid}`: `displayName`, `householdId`, `onboardingDone`, nested maps `profile` (body data),
  `preferences` and `restrictions` (hidden diet/allergen filters), `createdAt`, `updatedAt`. Enums are
  stored by name; unknown names are ignored when reading, so older app versions survive newer data.
- `households/{hid}`: `name`, `memberUids`, `memberNames` (uid → display name, for "added by" labels and
  the member list; a leaving member removes their own entry), `staples`, `equipment`, timestamps.
- Pantry items: `ingredientId` or `customIngredientId`, optional `quantity` (pantry unit), optional
  `expiresAt` as an ISO date string (`2026-10-25`, no time zone), `addedBy`, `updatedAt`.
- Shopping items: `ingredientId`/`customIngredientId` or `text`, optional `amount`, `checked`,
  `reasonRecipeIds`, `addedBy`, timestamps. Re-adding a checked item resets its amount and reasons.
- `users/{uid}/ratings/{recipeId}`: `verdict` (`LIKE`/`DISLIKE`/null), `favorite`, `note`.
- `users/{uid}/cookHistory/{id}`: `recipeId`, `cookedAt` (device time, so offline events keep their
  date), `servings`.

## D10 — Joining and leaving households (Phase 2)
- A new invite writes `invites/{code}` (D4; `householdName` at most 60 characters) and shows the code on
  the household (`inviteCode`, `inviteExpiresAt`); it withdraws the previous code. Valid 48 h.
- Joining looks the code up on the server, never in the cache. The join itself is a Firestore
  transaction, so it fails at once without a connection instead of waiting in the offline queue; it
  writes only `memberUids` (arrayUnion) and `joinCode`, and the member's name in a second write.
- A member who was alone brings their pantry (quantities merged by increment) and unchecked shopping
  items; their old household, its subcollections and its invite are deleted. A member of a shared
  household just leaves it, and its data stays with the others. The joined household keeps its own
  staples and equipment.
- Leaving a shared household starts a new empty one with the default staples and equipment. The last
  member cannot leave; deleting the account removes the household.
- Device settings live in the DataStore file `device_settings`: the expiry reminder (off by default)
  and its time (17:00 by default). The reminder is a daily WorkManager job that reads the pantry from
  the Firestore cache, so it works offline.


## D11 — Own foods, barcodes and receipts (Phase 2)
- `households/{hid}/customIngredients/{id}`: `name`, `brand`, `barcode`, `category`, `nutrition` (per
  100 g, or 100 ml for drinks counted at 1 g/ml), `nutritionKnown`, `pantryUnit`, `gramsPerPiece`,
  `densityGPerMl`, `packageQuantity`, `allergens`, `dietFlags`, `catalogId`, `source`
  (`OPEN_FOOD_FACTS`/`MANUAL`), `updatedBy`, `updatedAt` (server), written with merge. A stored `kcal`
  is ignored: energy is always recomputed from protein, carbohydrates, fat and fibre (EU factors
  4/4/9/2). Text is in one language, as the user typed or Open Food Facts named it.
- Ids: `b<barcode digits>` for scanned products, so every member scanning a product shares one
  document; `m<32 hex>` for foods typed in without a barcode.
- A product linked to a catalog ingredient (`catalogId`, suggested from its name and Open Food Facts
  categories, confirmed by the user) goes into the pantry as that ingredient (`i_<slug>`), its amount
  converted into the ingredient's pantry unit; an amount that cannot be converted is left out. Recipes
  then count it. Unlinked products are `c_<id>` pantry items and only show in the pantry.
- A scanned code is looked up in the household's own foods first, then in the Open Food Facts API v2
  (`/api/v2/product/{code}.json` with a field list) with the User-Agent
  `Spajza/<version> (Android; https://github.com/kiss-m/spajza)`. Only the barcode is sent. Names,
  brand, package size, macros per 100 g, allergen and category tags are used; the draft is shown for
  checking before anything is saved. Data under ODbL, credited on the About screen.
- Barcodes are read by the Google code scanner from Play services (no camera permission). Receipts
  are photographed with the camera app or picked from the gallery and read on the phone by ML Kit
  text recognition; photos stay in the app cache and are deleted after a day. The parser only
  suggests rows; the user ticks what goes into the pantry, as catalog ingredients.
- A member who was alone when joining a household brings their own foods along with the pantry.
