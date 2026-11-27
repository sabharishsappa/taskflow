const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validEntryPayload(uid, overrides = {}) {
  const past = new Date(Date.now() - 5000);
  return {
    userId: uid,
    title: "Morning Walk by the River",
    content: "The air felt crisp and quiet after last night's rain.",
    mood: "CALM",
    favorite: false,
    tags: ["morning", "nature"],
    wordCount: 10,
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write journal entries", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("entries").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("entries")
      .doc("entry_1")
      .set(validEntryPayload(ALICE_UID))
  );
});

test("Authenticated owner can create, list, orderBy createdAt, update, toggle favorite, and delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const entriesRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("entries");

  // Empty subcollection list query (mirrors Kotlin observeEntries)
  await assertSucceeds(entriesRef.orderBy("createdAt", "desc").get());

  // Create valid entry mirroring Kotlin JournalRepository.createEntry()
  await assertSucceeds(
    entriesRef.doc("entry_1").set(validEntryPayload(ALICE_UID))
  );

  // Get single entry
  await assertSucceeds(entriesRef.doc("entry_1").get());

  // List populated entries ordered by createdAt desc
  await assertSucceeds(entriesRef.orderBy("createdAt", "desc").get());

  // Partial update: toggle favorite (mirrors JournalRepository.toggleFavorite)
  await assertSucceeds(
    entriesRef.doc("entry_1").update({
      favorite: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Partial update: full edit (mirrors JournalRepository.updateEntry)
  await assertSucceeds(
    entriesRef.doc("entry_1").update({
      title: "Updated Morning Walk",
      content: "Noticed the mist rising over the trees.",
      mood: "GRATEFUL",
      favorite: true,
      tags: ["gratitude"],
      wordCount: 7,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete own entry
  await assertSucceeds(entriesRef.doc("entry_1").delete());
});

test("Cross-user isolation blocks Bob from reading, writing, updating, or deleting Alice's entries", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("entries")
      .doc("entry_alice")
      .set(validEntryPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceEntries = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("entries");

  await assertFails(aliceEntries.get());
  await assertFails(aliceEntries.doc("entry_alice").get());
  await assertFails(
    aliceEntries.doc("entry_bob_injected").set(validEntryPayload(BOB_UID))
  );
  await assertFails(
    aliceEntries.doc("entry_alice").update({
      favorite: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceEntries.doc("entry_alice").delete());
});

test("Schema validation rejects ghost fields, @DocumentId 'id', invalid mood, future timestamp, oversized title, and immutable createdAt edits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const entriesRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("entries");

  // Reject ghost field (Shadow Update / Create test)
  await assertFails(
    entriesRef
      .doc("entry_ghost")
      .set(validEntryPayload(ALICE_UID, { isAdmin: true }))
  );

  // Reject @DocumentId 'id' inside document body
  await assertFails(
    entriesRef
      .doc("entry_with_id")
      .set(validEntryPayload(ALICE_UID, { id: "entry_with_id" }))
  );

  // Reject invalid mood enum
  await assertFails(
    entriesRef
      .doc("entry_bad_mood")
      .set(validEntryPayload(ALICE_UID, { mood: "calm" }))
  );

  // Reject future timestamp
  await assertFails(
    entriesRef
      .doc("entry_future")
      .set(
        validEntryPayload(ALICE_UID, {
          createdAt: new Date(Date.now() + 60000),
          updatedAt: new Date(Date.now() + 60000),
        })
      )
  );

  // Reject empty or oversized title
  await assertFails(
    entriesRef.doc("entry_empty_title").set(validEntryPayload(ALICE_UID, { title: "" }))
  );
  await assertFails(
    entriesRef
      .doc("entry_long_title")
      .set(validEntryPayload(ALICE_UID, { title: "a".repeat(161) }))
  );

  // Reject too many tags (> 8)
  await assertFails(
    entriesRef.doc("entry_many_tags").set(
      validEntryPayload(ALICE_UID, {
        tags: ["1", "2", "3", "4", "5", "6", "7", "8", "9"],
      })
    )
  );

  // Reject spoofed userId
  await assertFails(
    entriesRef
      .doc("entry_spoof")
      .set(validEntryPayload(ALICE_UID, { userId: BOB_UID }))
  );

  // Create valid doc, then test update immutability & value poisoning
  await assertSucceeds(
    entriesRef.doc("entry_valid").set(validEntryPayload(ALICE_UID))
  );

  // Reject mutating immutable createdAt
  await assertFails(
    entriesRef.doc("entry_valid").update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject value poisoning on allowlisted key
  await assertSucceeds(
    entriesRef.doc("entry_valid").update({
      favorite: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    entriesRef.doc("entry_valid").update({
      favorite: "not_a_boolean",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
