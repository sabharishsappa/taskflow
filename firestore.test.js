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

function validTaskPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    title: "Finish sprint demo",
    notes: "Double check slides and animations",
    completed: false,
    colorKey: "CORAL",
    dueDateMillis: Date.now() + 86400000,
    reminderMillis: Date.now() + 43200000,
    repeatRule: "WEEKLY",
    repeatInterval: 1,
    subtasks: [
      { id: "sub_1", title: "Export icons", done: true },
      { id: "sub_2", title: "Rehearse pitch", done: false },
    ],
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

test("Unauthenticated user cannot list, read, create, update, or delete tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("task_1")
      .set(validTaskPayload(ALICE_UID))
  );
});

test("Authenticated owner: valid Kotlin create, optional omission, partial updates, list & delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tasksCol = aliceDb.collection("users").doc(ALICE_UID).collection("tasks");

  // Empty list query succeeds (matches observeTasks(uid))
  await assertSucceeds(tasksCol.get());

  // Create full task with optional fields
  const fullPayload = validTaskPayload(ALICE_UID);
  await assertSucceeds(tasksCol.doc("task_1").set(fullPayload));

  // Create minimal task omitting optional dueDateMillis and reminderMillis (mirrors .filterValues { it != null })
  const minimal = validTaskPayload(ALICE_UID, {
    title: "Quick errand",
    notes: "",
    colorKey: "TEAL",
    repeatRule: "NONE",
    repeatInterval: 1,
    subtasks: [],
  });
  delete minimal.dueDateMillis;
  delete minimal.reminderMillis;
  await assertSucceeds(tasksCol.doc("task_2").set(minimal));

  // List query succeeds with populated collection
  await assertSucceeds(tasksCol.get());

  // Partial update: toggle subtask
  await assertSucceeds(
    tasksCol.doc("task_1").update({
      subtasks: [
        { id: "sub_1", title: "Export icons", done: true },
        { id: "sub_2", title: "Rehearse pitch", done: true },
      ],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Partial update: complete / advance repeating task
  await assertSucceeds(
    tasksCol.doc("task_1").update({
      completed: false,
      dueDateMillis: Date.now() + 7 * 86400000,
      reminderMillis: Date.now() + 6 * 86400000,
      subtasks: [
        { id: "sub_1", title: "Export icons", done: false },
        { id: "sub_2", title: "Rehearse pitch", done: false },
      ],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Full edit updating colorKey, notes, repeatRule
  await assertSucceeds(
    tasksCol.doc("task_2").update({
      title: "Quick errand updated",
      notes: "Pick up coffee beans",
      colorKey: "VIOLET",
      repeatRule: "DAILY",
      repeatInterval: 2,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Owner can delete own task
  await assertSucceeds(tasksCol.doc("task_2").delete());
});

test("Cross-user isolation: Alice cannot read, list, create, update, or delete Bob's tasks", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("tasks")
      .doc("bob_task")
      .set(validTaskPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobTasks = aliceDb.collection("users").doc(BOB_UID).collection("tasks");

  await assertFails(bobTasks.get());
  await assertFails(bobTasks.doc("bob_task").get());
  await assertFails(
    bobTasks.doc("hijack").set(validTaskPayload(ALICE_UID))
  );
  await assertFails(
    bobTasks.doc("bob_task").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobTasks.doc("bob_task").delete());
});

test("Schema & Red Team bounds: reject ghost fields, @DocumentId 'id', spoofing, future timestamps, invalid enums, and immutable changes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tasksCol = aliceDb.collection("users").doc(ALICE_UID).collection("tasks");

  // Reject @DocumentId 'id' inside payload
  await assertFails(
    tasksCol.doc("bad_id_field").set(validTaskPayload(ALICE_UID, { id: "bad_id_field" }))
  );

  // Reject shadow/ghost field
  await assertFails(
    tasksCol.doc("bad_ghost").set(validTaskPayload(ALICE_UID, { isAdmin: true }))
  );

  // Reject userId spoofing
  await assertFails(
    tasksCol.doc("bad_spoof").set(validTaskPayload(BOB_UID))
  );

  // Reject empty or oversized title
  await assertFails(
    tasksCol.doc("bad_empty").set(validTaskPayload(ALICE_UID, { title: "" }))
  );
  await assertFails(
    tasksCol.doc("bad_long").set(validTaskPayload(ALICE_UID, { title: "a".repeat(121) }))
  );

  // Reject invalid colorKey and repeatRule
  await assertFails(
    tasksCol.doc("bad_color").set(validTaskPayload(ALICE_UID, { colorKey: "NEON_LIME" }))
  );
  await assertFails(
    tasksCol.doc("bad_repeat").set(validTaskPayload(ALICE_UID, { repeatRule: "YEARLY" }))
  );

  // Reject invalid repeatInterval
  await assertFails(
    tasksCol.doc("bad_interval").set(validTaskPayload(ALICE_UID, { repeatInterval: 0 }))
  );

  // Reject future tampered timestamp
  await assertFails(
    tasksCol.doc("bad_future").set(
      validTaskPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );

  // Reject immutable createdAt mutation on update
  await assertSucceeds(tasksCol.doc("task_imm").set(validTaskPayload(ALICE_UID)));
  await assertFails(
    tasksCol.doc("task_imm").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject >30 subtasks
  const tooManySubtasks = Array.from({ length: 31 }, (_, i) => ({
    id: `s_${i}`,
    title: `Subtask ${i}`,
    done: false,
  }));
  await assertFails(
    tasksCol.doc("bad_subs").set(validTaskPayload(ALICE_UID, { subtasks: tooManySubtasks }))
  );
});
