import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc } from "firebase/firestore";
import { describe, it } from "node:test";
import { useRules } from "./support.mjs";

const rules = useRules(async (db) => {
  await setDoc(doc(db, "users/alice"), { displayName: "Alice", householdId: "h1" });
  await setDoc(doc(db, "users/bob"), { displayName: "Bob", householdId: "h2" });
  await setDoc(doc(db, "users/bob/ratings/garlic-pasta"), { verdict: "LIKE", favorite: false });
  await setDoc(doc(db, "users/bob/cookHistory/e1"), { recipeId: "garlic-pasta", servings: 2 });
});

describe("user documents", () => {
  it("a user reads and changes their own document", async () => {
    const db = rules.as("alice");
    await assertSucceeds(getDoc(doc(db, "users/alice")));
    await assertSucceeds(setDoc(doc(db, "users/alice"), { displayName: "Alica" }, { merge: true }));
  });

  it("a new user creates their document", async () => {
    await assertSucceeds(setDoc(doc(rules.as("carol"), "users/carol"), { displayName: "Carol" }));
  });

  it("a user cannot read or change someone else's document", async () => {
    const db = rules.as("alice");
    await assertFails(getDoc(doc(db, "users/bob")));
    await assertFails(setDoc(doc(db, "users/bob"), { householdId: "h1" }, { merge: true }));
    await assertFails(deleteDoc(doc(db, "users/bob")));
  });

  it("signed out, nothing can be read", async () => {
    await assertFails(getDoc(doc(rules.anonymous(), "users/alice")));
  });

  it("user documents cannot be listed", async () => {
    await assertFails(getDocs(collection(rules.as("alice"), "users")));
  });

  it("a user deletes their own document when deleting the account", async () => {
    await assertSucceeds(deleteDoc(doc(rules.as("alice"), "users/alice")));
  });
});

describe("ratings and cooking history", () => {
  it("a user keeps their own ratings and cooking history", async () => {
    const db = rules.as("bob");
    await assertSucceeds(setDoc(doc(db, "users/bob/ratings/tomato-pasta"), { verdict: "DISLIKE", favorite: false }));
    await assertSucceeds(setDoc(doc(db, "users/bob/cookHistory/e2"), { recipeId: "tomato-pasta", servings: 4 }));
    await assertSucceeds(getDocs(collection(db, "users/bob/ratings")));
    await assertSucceeds(deleteDoc(doc(db, "users/bob/cookHistory/e1")));
  });

  it("a user cannot see or change someone else's ratings or history", async () => {
    const db = rules.as("alice");
    await assertFails(getDocs(collection(db, "users/bob/ratings")));
    await assertFails(getDoc(doc(db, "users/bob/cookHistory/e1")));
    await assertFails(setDoc(doc(db, "users/bob/ratings/garlic-pasta"), { verdict: "DISLIKE" }));
  });

  it("other subcollections of a user are denied", async () => {
    await assertFails(setDoc(doc(rules.as("alice"), "users/alice/secrets/x"), { value: 1 }));
  });
});
