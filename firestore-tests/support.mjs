// Shared setup for the rules tests: the real firestore.rules, loaded into the emulator that
// `npm test` starts (firebase emulators:exec sets FIRESTORE_EMULATOR_HOST).
import { initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { setLogLevel, Timestamp } from "firebase/firestore";
import { readFileSync } from "node:fs";
import { after, before, beforeEach } from "node:test";

const rules = readFileSync(new URL("../firestore.rules", import.meta.url), "utf8");

// Denied writes are what many tests expect; the SDK would log each of them as a warning.
setLogLevel("error");

/**
 * Starts a test environment for one file. Before each test the database is emptied and [seed]
 * writes the starting data with the rules switched off.
 */
export function useRules(seed = async () => {}) {
  const handle = {
    env: null,
    /** Firestore as the signed-in user [uid]. */
    as(uid) {
      return handle.env.authenticatedContext(uid).firestore();
    },
    /** Firestore without signing in. */
    anonymous() {
      return handle.env.unauthenticatedContext().firestore();
    },
    /** Writes or reads with the rules switched off. */
    async admin(block) {
      await handle.env.withSecurityRulesDisabled(async (context) => block(context.firestore()));
    },
  };
  before(async () => {
    handle.env = await initializeTestEnvironment({ projectId: "demo-spajza", firestore: { rules } });
  });
  beforeEach(async () => {
    await handle.env.clearFirestore();
    await handle.admin(seed);
  });
  after(async () => {
    await handle.env?.cleanup();
  });
  return handle;
}

export function hoursFromNow(hours) {
  return Timestamp.fromMillis(Date.now() + hours * 3_600_000);
}
