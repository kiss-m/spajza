import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { arrayUnion, collection, doc, getDocs, runTransaction, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { describe, it } from "node:test";
import { hoursFromNow, useRules } from "./support.mjs";

const rules = useRules(async (db) => {
  await setDoc(doc(db, "households/h1"), { name: "Byt", memberUids: ["alice"], memberNames: { alice: "Alice" } });
  await setDoc(doc(db, "households/h1/pantry/i_milk"), { ingredientId: "milk", quantity: 1000 });
  await setDoc(doc(db, "households/h2"), { name: "Iný", memberUids: ["dave"] });
  await setDoc(doc(db, "invites/KX7P2M"), { householdId: "h1", householdName: "Byt", expiresAt: hoursFromNow(24), createdBy: "alice" });
  await setDoc(doc(db, "invites/EXP4RD"), { householdId: "h1", householdName: "Byt", expiresAt: hoursFromNow(-1), createdBy: "alice" });
  await setDoc(doc(db, "invites/DV9Q3N"), { householdId: "h2", householdName: "Iný", expiresAt: hoursFromNow(24), createdBy: "dave" });
});

const join = (uid, fields) => updateDoc(doc(rules.as(uid), "households/h1"), fields);

describe("joining with an invite", () => {
  it("a user joins in a transaction, as the app does, then adds their name and sees the pantry", async () => {
    const db = rules.as("bob");
    const household = doc(db, "households/h1");
    await assertSucceeds(runTransaction(db, async (transaction) => {
      transaction.update(household, { memberUids: arrayUnion("bob"), joinCode: "KX7P2M" });
    }));
    await assertSucceeds(updateDoc(household, { "memberNames.bob": "Bob", updatedAt: serverTimestamp() }));
    await assertSucceeds(getDocs(collection(db, "households/h1/pantry")));
  });

  it("an expired invite is rejected", async () => {
    await assertFails(join("bob", { memberUids: arrayUnion("bob"), joinCode: "EXP4RD" }));
  });

  it("an invite to another household is rejected", async () => {
    await assertFails(join("bob", { memberUids: arrayUnion("bob"), joinCode: "DV9Q3N" }));
  });

  it("an unknown code or no code is rejected", async () => {
    await assertFails(join("bob", { memberUids: arrayUnion("bob"), joinCode: "NNNNNN" }));
    await assertFails(join("bob", { memberUids: arrayUnion("bob") }));
  });

  it("nobody else can be added while joining", async () => {
    await assertFails(join("bob", { memberUids: arrayUnion("bob", "mallory"), joinCode: "KX7P2M" }));
    await assertFails(join("bob", { memberUids: arrayUnion("mallory"), joinCode: "KX7P2M" }));
  });

  it("nothing else can change while joining", async () => {
    await assertFails(join("bob", { memberUids: arrayUnion("bob"), joinCode: "KX7P2M", name: "Môj byt" }));
    await assertFails(join("bob", { memberUids: arrayUnion("bob"), joinCode: "KX7P2M", "memberNames.bob": "Bob" }));
  });

  it("a member cannot join again", async () => {
    await assertFails(join("alice", { memberUids: arrayUnion("alice"), joinCode: "KX7P2M" }));
  });

  it("signed out, nobody can join", async () => {
    await assertFails(updateDoc(doc(rules.anonymous(), "households/h1"), { joinCode: "KX7P2M" }));
  });
});
