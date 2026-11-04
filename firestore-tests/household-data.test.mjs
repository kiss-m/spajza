import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { arrayRemove, collection, deleteDoc, doc, getDoc, getDocs, increment, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { describe, it } from "node:test";
import { useRules } from "./support.mjs";

// The household subcollections the app uses (docs/DECISIONS.md D9–D12).
const SHARED = {
  pantry: { id: "i_milk", data: { ingredientId: "milk", quantity: 1000 } },
  shopping: { id: "i_eggs", data: { ingredientId: "eggs", amount: 10, checked: false } },
  recipes: { id: "r1", data: { title: "Babičkine halušky", servings: 4 } },
  customIngredients: { id: "c1", data: { name: "Bryndza od suseda" } },
};

// A week of the removed meal plan (D13, D15), which the app now only deletes.
const OLD_PLAN = "households/h1/mealPlans/2026-W45";

const rules = useRules(async (db) => {
  await setDoc(doc(db, "households/h1"), { name: "Byt", memberUids: ["alice", "bob"] });
  await setDoc(doc(db, "households/h2"), { name: "Iný", memberUids: ["dave"] });
  for (const [name, { id, data }] of Object.entries(SHARED)) {
    await setDoc(doc(db, `households/h1/${name}/${id}`), data);
  }
  await setDoc(doc(db, OLD_PLAN), { entries: {} });
});

describe("shared household data", () => {
  for (const [name, { id, data }] of Object.entries(SHARED)) {
    it(`members read and write ${name}`, async () => {
      const db = rules.as("bob");
      await assertSucceeds(getDocs(collection(db, `households/h1/${name}`)));
      await assertSucceeds(setDoc(doc(db, `households/h1/${name}/${id}-2`), data));
      await assertSucceeds(deleteDoc(doc(db, `households/h1/${name}/${id}`)));
    });

    it(`others cannot read or write ${name}`, async () => {
      const db = rules.as("dave");
      await assertFails(getDocs(collection(db, `households/h1/${name}`)));
      await assertFails(getDoc(doc(db, `households/h1/${name}/${id}`)));
      await assertFails(setDoc(doc(db, `households/h1/${name}/${id}-3`), data));
    });
  }

  it("pantry quantities change with increments", async () => {
    await assertSucceeds(updateDoc(doc(rules.as("alice"), "households/h1/pantry/i_milk"), { quantity: increment(-250), updatedAt: serverTimestamp() }));
  });

  it("signed out, nothing can be read", async () => {
    await assertFails(getDocs(collection(rules.anonymous(), "households/h1/pantry")));
  });

  it("unknown subcollections are denied", async () => {
    await assertFails(setDoc(doc(rules.as("alice"), "households/h1/secrets/x"), { value: 1 }));
    await assertFails(getDocs(collection(rules.as("alice"), "households/h1/secrets")));
  });

  it("a member who left loses access", async () => {
    await assertSucceeds(updateDoc(doc(rules.as("bob"), "households/h1"), { memberUids: arrayRemove("bob") }));
    await assertFails(getDocs(collection(rules.as("bob"), "households/h1/pantry")));
    await assertFails(setDoc(doc(rules.as("bob"), "households/h1/shopping/i_bread"), { ingredientId: "bread" }));
  });
});

describe("old meal plans", () => {
  const entry = { date: "2026-11-10", meal: "DINNER", recipeId: "garlic-pasta", servings: 2, addedBy: "alice" };

  it("members read and delete what the removed plan left", async () => {
    const db = rules.as("bob");
    await assertSucceeds(getDocs(collection(db, "households/h1/mealPlans")));
    await assertSucceeds(deleteDoc(doc(db, OLD_PLAN)));
  });

  it("nobody plans meals any more", async () => {
    const db = rules.as("alice");
    await assertFails(setDoc(doc(db, "households/h1/mealPlans/2026-W46"), { entries: { a1b2c3d4e5f60708: { ...entry, addedAt: serverTimestamp() } } }));
    await assertFails(updateDoc(doc(db, OLD_PLAN), { "entries.a1b2c3d4e5f60708": entry }));
  });

  it("another household can neither read nor delete them", async () => {
    const db = rules.as("dave");
    await assertFails(getDocs(collection(db, "households/h1/mealPlans")));
    await assertFails(deleteDoc(doc(db, OLD_PLAN)));
  });
});
