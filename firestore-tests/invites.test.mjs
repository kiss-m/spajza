import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc, updateDoc } from "firebase/firestore";
import { describe, it } from "node:test";
import { hoursFromNow, useRules } from "./support.mjs";

const rules = useRules(async (db) => {
  await setDoc(doc(db, "households/h1"), { name: "Byt", memberUids: ["alice"] });
  await setDoc(doc(db, "households/h2"), { name: "Iný", memberUids: ["dave"] });
  await setDoc(doc(db, "invites/KX7P2M"), { householdId: "h1", householdName: "Byt", expiresAt: hoursFromNow(24), createdBy: "alice" });
});

const invite = (overrides = {}) => ({ householdId: "h1", householdName: "Byt", expiresAt: hoursFromNow(48), createdBy: "alice", ...overrides });

describe("reading invites", () => {
  it("anyone signed in reads an invite by its code", async () => {
    await assertSucceeds(getDoc(doc(rules.as("bob"), "invites/KX7P2M")));
  });

  it("signed out, invites cannot be read", async () => {
    await assertFails(getDoc(doc(rules.anonymous(), "invites/KX7P2M")));
  });

  it("invites cannot be listed", async () => {
    await assertFails(getDocs(collection(rules.as("alice"), "invites")));
  });
});

describe("creating invites", () => {
  it("a member creates an invite for their household, valid for 48 hours", async () => {
    await assertSucceeds(setDoc(doc(rules.as("alice"), "invites/QW3RTY"), invite()));
  });

  it("codes with look-alike characters or of the wrong length are rejected", async () => {
    const db = rules.as("alice");
    for (const code of ["ABC0EF", "ABCIEF", "ABCLEF", "ABCDE", "ABCDEFG", "abcdef"]) {
      await assertFails(setDoc(doc(db, `invites/${code}`), invite()));
    }
  });

  it("an invite valid for longer than 49 hours is rejected", async () => {
    await assertFails(setDoc(doc(rules.as("alice"), "invites/WQNGER"), invite({ expiresAt: hoursFromNow(72) })));
  });

  it("an invite that has already expired is rejected", async () => {
    await assertFails(setDoc(doc(rules.as("alice"), "invites/PASTXX"), invite({ expiresAt: hoursFromNow(-1) })));
  });

  it("an invite to a household the user is not in is rejected", async () => {
    await assertFails(setDoc(doc(rules.as("bob"), "invites/BXBXBX"), invite({ createdBy: "bob" })));
  });

  it("an invite in someone else's name is rejected", async () => {
    await assertFails(setDoc(doc(rules.as("alice"), "invites/NAMEXX"), invite({ createdBy: "dave" })));
  });

  it("extra fields or a household name over 60 characters are rejected", async () => {
    const db = rules.as("alice");
    await assertFails(setDoc(doc(db, "invites/EXTRAX"), invite({ admin: true })));
    await assertFails(setDoc(doc(db, "invites/W2NGXX"), invite({ householdName: "x".repeat(61) })));
    await assertSucceeds(setDoc(doc(db, "invites/W3NGXX"), invite({ householdName: "x".repeat(60) })));
  });
});

describe("changing invites", () => {
  it("an existing invite cannot be overwritten or changed", async () => {
    const db = rules.as("alice");
    await assertFails(setDoc(doc(db, "invites/KX7P2M"), invite()));
    await assertFails(updateDoc(doc(db, "invites/KX7P2M"), { expiresAt: hoursFromNow(40) }));
  });

  it("a member withdraws an invite", async () => {
    await assertSucceeds(deleteDoc(doc(rules.as("alice"), "invites/KX7P2M")));
  });

  it("others cannot withdraw it", async () => {
    await assertFails(deleteDoc(doc(rules.as("dave"), "invites/KX7P2M")));
  });
});
