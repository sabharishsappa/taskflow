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

function pastTime(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

// --- UNAUTHENTICATED ACCESS TESTS ---

test("Unauthenticated user: cannot read or write lists or tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = pastTime();

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("lists").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("lists").doc("list_1").set({
      userId: ALICE_UID,
      name: "Personal",
      colorHex: "#235340",
      iconKey: "personal",
      sortOrder: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      listId: "list_1",
      title: "Buy milk",
      dueDateEpochDay: 20778,
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );
});

// --- LISTS CRUD & VALIDATION TESTS ---

test("Authenticated owner: valid Kotlin TaskList create, list, update, delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastTime(10000);

  // Empty subcollection list query succeeds (mirrors TaskRepository.observeLists)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("lists").orderBy("sortOrder").get()
  );

  // Create payload (NO @DocumentId `id` field)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("lists").doc("list_personal").set({
      userId: ALICE_UID,
      name: "Personal",
      colorHex: "#235340",
      iconKey: "personal",
      sortOrder: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update (mirrors TaskRepository.updateList)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("lists").doc("list_personal").update({
      name: "Daily Life",
      colorHex: "#C86D4A",
      iconKey: "home",
      updatedAt: pastTime(1000),
    })
  );

  // Delete list
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("lists").doc("list_personal").delete()
  );
});

test("TaskList security bounds: reject ghost fields, id field, invalid color/icon, immutable createdAt, future timestamp", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastTime(10000);
  const listsRef = aliceDb.collection("users").doc(ALICE_UID).collection("lists");

  // Reject `@DocumentId` `id` field in payload
  await assertFails(
    listsRef.doc("list_bad_id").set({
      id: "list_bad_id",
      userId: ALICE_UID,
      name: "Work",
      colorHex: "#2B5B84",
      iconKey: "work",
      sortOrder: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject ghost field
  await assertFails(
    listsRef.doc("list_ghost").set({
      userId: ALICE_UID,
      name: "Work",
      colorHex: "#2B5B84",
      iconKey: "work",
      sortOrder: 1,
      isAdmin: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject invalid hex color
  await assertFails(
    listsRef.doc("list_bad_color").set({
      userId: ALICE_UID,
      name: "Work",
      colorHex: "green",
      iconKey: "work",
      sortOrder: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject unknown icon key
  await assertFails(
    listsRef.doc("list_bad_icon").set({
      userId: ALICE_UID,
      name: "Work",
      colorHex: "#2B5B84",
      iconKey: "unknown_icon",
      sortOrder: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    listsRef.doc("list_future").set({
      userId: ALICE_UID,
      name: "Work",
      colorHex: "#2B5B84",
      iconKey: "work",
      sortOrder: 1,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Create valid list then test immutable createdAt & userId
  await assertSucceeds(
    listsRef.doc("list_ok").set({
      userId: ALICE_UID,
      name: "Work",
      colorHex: "#2B5B84",
      iconKey: "work",
      sortOrder: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    listsRef.doc("list_ok").update({
      createdAt: pastTime(500),
      updatedAt: pastTime(500),
    })
  );

  await assertFails(
    listsRef.doc("list_ok").update({
      userId: BOB_UID,
      updatedAt: pastTime(500),
    })
  );
});

// --- TASKS CRUD, PARENT EXISTENCE & ACTION-BASED UPDATE TESTS ---

test("Authenticated owner: valid Kotlin TaskItem create, toggle completion, edit, clear reminder, delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastTime(10000);
  const listsRef = aliceDb.collection("users").doc(ALICE_UID).collection("lists");
  const tasksRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks");

  // Empty tasks list query succeeds (mirrors TaskRepository.observeTasks)
  await assertSucceeds(tasksRef.orderBy("dueDateEpochDay").get());

  // Task creation MUST fail if referenced listId does not exist (Global Consistency Invariant)
  await assertFails(
    tasksRef.doc("task_orphan").set({
      userId: ALICE_UID,
      listId: "missing_list",
      title: "Orphaned task",
      dueDateEpochDay: 20778,
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create two lists
  await assertSucceeds(
    listsRef.doc("list_work").set({
      userId: ALICE_UID,
      name: "Work",
      colorHex: "#2B5B84",
      iconKey: "work",
      sortOrder: 0,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    listsRef.doc("list_home").set({
      userId: ALICE_UID,
      name: "Home",
      colorHex: "#C86D4A",
      iconKey: "home",
      sortOrder: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create task with optional notes & reminder (mirrors TaskRepository.addTask)
  await assertSucceeds(
    tasksRef.doc("task_1").set({
      userId: ALICE_UID,
      listId: "list_work",
      title: "Prepare Q4 roadmap",
      notes: "Include design milestones",
      dueDateEpochDay: 20780,
      reminderEpochMillis: Date.now() + 3600000,
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Action 1: Toggle completion (mirrors TaskRepository.toggleTaskCompleted)
  await assertSucceeds(
    tasksRef.doc("task_1").update({
      completed: true,
      completedAtEpochMillis: Date.now(),
      updatedAt: pastTime(1000),
    })
  );

  // Action 2: Edit task details, move to another existing list, clear reminder via null
  await assertSucceeds(
    tasksRef.doc("task_1").update({
      title: "Finalize Q4 roadmap",
      notes: "Updated slides",
      listId: "list_home",
      dueDateEpochDay: 20785,
      reminderEpochMillis: null,
      completed: false,
      completedAtEpochMillis: null,
      updatedAt: pastTime(500),
    })
  );

  // Moving task to a non-existent list must fail
  await assertFails(
    tasksRef.doc("task_1").update({
      listId: "nonexistent_list",
      updatedAt: pastTime(200),
    })
  );

  // Delete task
  await assertSucceeds(tasksRef.doc("task_1").delete());
});

test("TaskItem security bounds: reject oversized title, ghost fields, id field, immutable createdAt", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastTime(10000);
  const listsRef = aliceDb.collection("users").doc(ALICE_UID).collection("lists");
  const tasksRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks");

  await assertSucceeds(
    listsRef.doc("list_1").set({
      userId: ALICE_UID,
      name: "Personal",
      colorHex: "#235340",
      iconKey: "personal",
      sortOrder: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject oversized title (> 200 chars)
  await assertFails(
    tasksRef.doc("task_long").set({
      userId: ALICE_UID,
      listId: "list_1",
      title: "a".repeat(201),
      dueDateEpochDay: 20780,
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject `@DocumentId` `id` field
  await assertFails(
    tasksRef.doc("task_with_id").set({
      id: "task_with_id",
      userId: ALICE_UID,
      listId: "list_1",
      title: "Valid title",
      dueDateEpochDay: 20780,
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject ghost field on create
  await assertFails(
    tasksRef.doc("task_ghost").set({
      userId: ALICE_UID,
      listId: "list_1",
      title: "Valid title",
      dueDateEpochDay: 20780,
      completed: false,
      priority: "urgent",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create valid task then reject immutable createdAt update and wrong type value poisoning
  await assertSucceeds(
    tasksRef.doc("task_valid").set({
      userId: ALICE_UID,
      listId: "list_1",
      title: "Call dentist",
      dueDateEpochDay: 20780,
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    tasksRef.doc("task_valid").update({
      createdAt: pastTime(100),
      updatedAt: pastTime(100),
    })
  );

  await assertFails(
    tasksRef.doc("task_valid").update({
      completed: "yes",
      updatedAt: pastTime(100),
    })
  );
});

// --- CROSS-USER ISOLATION TESTS ---

test("Cross-user isolation: Alice cannot read, list, create, update, or delete Bob's lists or tasks", async () => {
  const created = pastTime(10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("users").doc(BOB_UID).collection("lists").doc("bob_list").set({
      userId: BOB_UID,
      name: "Bob Private",
      colorHex: "#7A4988",
      iconKey: "personal",
      sortOrder: 0,
      createdAt: created,
      updatedAt: created,
    });
    await adminDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").set({
      userId: BOB_UID,
      listId: "bob_list",
      title: "Bob Secret Task",
      dueDateEpochDay: 20780,
      completed: false,
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Lists isolation
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("lists").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("lists").doc("bob_list").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("lists").doc("bob_list").update({
      name: "Hijacked",
      updatedAt: pastTime(1000),
    })
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("lists").doc("bob_list").delete());

  // Tasks isolation
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").update({
      completed: true,
      updatedAt: pastTime(1000),
    })
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task").delete());
});
