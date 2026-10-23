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

