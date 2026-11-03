import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { arrayRemove, arrayUnion, collection, deleteDoc, deleteField, doc, getDoc, getDocs, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { describe, it } from "node:test";
import { hoursFromNow, useRules } from "./support.mjs";

const rules = useRules(async (db) => {
  await setDoc(doc(db, "households/h1"), {
    name: "Byt",
    memberUids: ["alice", "bob"],
    memberNames: { alice: "Alice", bob: "Bob" },
    staples: ["salt"],
    equipment: ["STOVE", "OVEN"],
  });
  await setDoc(doc(db, "households/solo"), { name: "Sám", memberUids: ["carol"] });
});

describe("creating a household", () => {
  it("a user creates a household with only themselves as member", async () => {
    await assertSucceeds(setDoc(doc(rules.as("dave"), "households/h-dave"), { name: "Dave", memberUids: ["dave"], staples: [] }));
  });

  it("a household cannot be created with other members", async () => {
    await assertFails(setDoc(doc(rules.as("dave"), "households/h-x"), { name: "X", memberUids: ["dave", "alice"] }));
    await assertFails(setDoc(doc(rules.as("dave"), "households/h-y"), { name: "Y", memberUids: ["alice"] }));
  });

  it("signed out, no household can be created", async () => {
    await assertFails(setDoc(doc(rules.anonymous(), "households/h-z"), { name: "Z", memberUids: [] }));
  });
});

describe("reading a household", () => {
  it("members read it", async () => {
    await assertSucceeds(getDoc(doc(rules.as("bob"), "households/h1")));
  });

  it("others cannot read it", async () => {
    await assertFails(getDoc(doc(rules.as("carol"), "households/h1")));
  });

  it("a household that does not exist yet can be read, so a listener waits for it", async () => {
    await assertSucceeds(getDoc(doc(rules.as("carol"), "households/not-yet")));
  });

  it("signed out, not even a missing household can be read", async () => {
    await assertFails(getDoc(doc(rules.anonymous(), "households/not-yet")));
  });

  it("households cannot be listed", async () => {
    await assertFails(getDocs(collection(rules.as("alice"), "households")));
  });
});

describe("editing a household", () => {
  it("a member renames it and changes staples and equipment", async () => {
    await assertSucceeds(updateDoc(doc(rules.as("bob"), "households/h1"), {
      name: "Byt v Ružinove",
      staples: arrayUnion("pepper"),
      equipment: arrayRemove("OVEN"),
      updatedAt: serverTimestamp(),
    }));
  });

  it("a member adds their name and shows a new invite code", async () => {
    const db = rules.as("bob");
    await assertSucceeds(updateDoc(doc(db, "households/h1"), { "memberNames.bob": "Bobo", updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db, "households/h1"), { inviteCode: "KX7P2M", inviteExpiresAt: hoursFromNow(48) }));
  });

  it("a member cannot add someone to the member list", async () => {
    await assertFails(updateDoc(doc(rules.as("alice"), "households/h1"), { memberUids: arrayUnion("mallory") }));
  });

  it("a member cannot set the join code", async () => {
    await assertFails(updateDoc(doc(rules.as("alice"), "households/h1"), { joinCode: "KX7P2M" }));
  });

  it("others cannot edit it", async () => {
    await assertFails(updateDoc(doc(rules.as("carol"), "households/h1"), { name: "Mine" }));
  });
});

describe("leaving and deleting", () => {
  it("a member leaves and takes their name along", async () => {
    await assertSucceeds(updateDoc(doc(rules.as("bob"), "households/h1"), { memberUids: arrayRemove("bob"), "memberNames.bob": deleteField() }));
    await assertFails(getDoc(doc(rules.as("bob"), "households/h1")));
  });

  it("a member leaves a household without member names", async () => {
    await rules.admin((db) => setDoc(doc(db, "households/pair"), { name: "Pár", memberUids: ["erin", "frank"] }));
    await assertSucceeds(updateDoc(doc(rules.as("erin"), "households/pair"), { memberUids: arrayRemove("erin") }));
  });

  it("a member cannot remove someone else", async () => {
    await assertFails(updateDoc(doc(rules.as("bob"), "households/h1"), { memberUids: arrayRemove("alice") }));
  });

  it("leaving cannot remove another member's name", async () => {
    await assertFails(updateDoc(doc(rules.as("bob"), "households/h1"), { memberUids: arrayRemove("bob"), "memberNames.alice": deleteField() }));
  });

  it("leaving cannot change anything else at the same time", async () => {
    await assertFails(updateDoc(doc(rules.as("bob"), "households/h1"), { memberUids: arrayRemove("bob"), name: "Bye" }));
  });

  it("the last member deletes the household", async () => {
    await assertSucceeds(deleteDoc(doc(rules.as("carol"), "households/solo")));
  });

  it("a household with other members cannot be deleted", async () => {
    await assertFails(deleteDoc(doc(rules.as("alice"), "households/h1")));
  });

  it("others cannot delete a household", async () => {
    await assertFails(deleteDoc(doc(rules.as("alice"), "households/solo")));
  });
});
