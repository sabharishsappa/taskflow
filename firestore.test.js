const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_journal_123";
const BOB_UID = "bob_journal_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validEntryPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    heading: "Morning Trail Run at Marin Headlands",
    body: "Started early while the fog was lifting over the Golden Gate. Crisp air and quiet trails.",
    entryDateMillis: Date.now() - 3600 * 1000,
    category: "ACTIVITY",
    locationName: "Marin Headlands, CA",
    locationLat: 37.827,
    locationLng: -122.4994,
    tags: ["fitness", "outdoors", "morning"],
    attachments: [
      {
        id: "att-1",
        name: "trail_sunrise.jpg",
        mimeType: "image/jpeg",
        localPath: "/data/user/0/com.aistudio.foliojournal.qrwmzt/files/journal_attachments/att-1.jpg",
        kind: "IMAGE",
        sizeBytes: 245012,
      },
    ],
    bookmarked: false,
    wordCount: 22,
    createdAt: now,
    updatedAt: now,
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

test("Unauthenticated user: denied all reads and writes", async () => {
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

test("Authenticated owner: valid Kotlin create, list, get, partial update, bookmark toggle, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const entriesCol = aliceDb.collection("users").doc(ALICE_UID).collection("entries");
  const entryRef = entriesCol.doc("entry_1");

  // Create with optional coordinates included
  await assertSucceeds(entryRef.set(validEntryPayload(ALICE_UID)));

  // Create without optional coordinates (omitted via filterValues { it != null })
  const payloadNoCoords = validEntryPayload(ALICE_UID, {
    heading: "Quiet Evening Reading",
    locationName: "",
    tags: [],
    attachments: [],
  });
  delete payloadNoCoords.locationLat;
  delete payloadNoCoords.locationLng;
  await assertSucceeds(entriesCol.doc("entry_2").set(payloadNoCoords));

  // List query matching Kotlin repository: orderBy("entryDateMillis", DESCENDING)
  await assertSucceeds(entriesCol.orderBy("entryDateMillis", "desc").get());
  await assertSucceeds(entryRef.get());

  // Action 1: Bookmark toggle partial update
  await assertSucceeds(
    entryRef.update({
      bookmarked: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action 2: Full edit partial update (preserving createdAt & userId)
  await assertSucceeds(
    entryRef.update({
      heading: "Updated Trail Run Heading",
      body: "Added extra notes after stretching.",
      entryDateMillis: Date.now() - 7200 * 1000,
      category: "EVENT",
      locationName: "Sausalito, CA",
      locationLat: 37.8591,
      locationLng: -122.4853,
      tags: ["fitness", "weekend"],
      attachments: [],
      bookmarked: true,
      wordCount: 10,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete own entry
  await assertSucceeds(entryRef.delete());
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's entries", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("entries")
      .doc("alice_entry")
      .set(validEntryPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceEntries = bobDb.collection("users").doc(ALICE_UID).collection("entries");

  await assertFails(aliceEntries.get());
  await assertFails(aliceEntries.orderBy("entryDateMillis", "desc").get());
  await assertFails(aliceEntries.doc("alice_entry").get());
  await assertFails(
    aliceEntries.doc("bob_inject").set(validEntryPayload(BOB_UID))
  );
  await assertFails(
    aliceEntries.doc("alice_entry").update({
      heading: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceEntries.doc("alice_entry").delete());
});

test("Red Team validation: rejects ghost fields, @DocumentId 'id', future timestamps, invalid category, oversized strings, and immutable mutations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const entriesCol = aliceDb.collection("users").doc(ALICE_UID).collection("entries");

  // Shadow/ghost field injection
  await assertFails(
    entriesCol
      .doc("ghost_entry")
      .set(validEntryPayload(ALICE_UID, { isAdmin: true }))
  );

  // Writing @DocumentId 'id' inside document map must fail
  await assertFails(
    entriesCol
      .doc("id_field_entry")
      .set(validEntryPayload(ALICE_UID, { id: "id_field_entry" }))
  );

  // Future timestamp tampering must fail
  await assertFails(
    entriesCol.doc("future_ts").set(
      validEntryPayload(ALICE_UID, {
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
    )
  );

  // Invalid category enum must fail
  await assertFails(
    entriesCol
      .doc("bad_cat")
      .set(validEntryPayload(ALICE_UID, { category: "UNKNOWN_TYPE" }))
  );

  // Empty heading must fail
  await assertFails(
    entriesCol
      .doc("empty_heading")
      .set(validEntryPayload(ALICE_UID, { heading: "" }))
  );

  // Too many tags (> 10) must fail
  await assertFails(
    entriesCol.doc("too_many_tags").set(
      validEntryPayload(ALICE_UID, {
        tags: ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"],
      })
    )
  );

  // Spoofed userId inside own subcollection must fail
  await assertFails(
    entriesCol
      .doc("spoof_uid")
      .set(validEntryPayload(ALICE_UID, { userId: BOB_UID }))
  );

  // Immutable createdAt mutation on update must fail
  await assertSucceeds(entriesCol.doc("immut_entry").set(validEntryPayload(ALICE_UID)));
  await assertFails(
    entriesCol.doc("immut_entry").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Value poisoning on update (oversized heading > 200 chars) must fail
  await assertFails(
    entriesCol.doc("immut_entry").update({
      heading: "A".repeat(205),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
