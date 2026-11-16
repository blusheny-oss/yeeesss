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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validActPayload(uid, overrides = {}) {
  return {
    userId: uid,
    title: "Квартира на Тверской",
    address: "г. Москва, ул. Тверская, д. 12",
    apartmentNumber: "45",
    depositAmountRub: 30000,
    monthlyRentRub: 55000,
    creatorRole: "tenant",
    landlordName: "Иванов И.И.",
    landlordPhone: "+79001112233",
    tenantName: "Петрова А.С.",
    tenantPhone: "+79004445566",
    status: "draft",
    latitude: 55.7652,
    longitude: 37.6046,
    locationLabel: "55.76520, 37.60460",
    coldWaterMeter: "124.5",
    hotWaterMeter: "89.2",
    electricityMeter: "4312",
    gasMeter: "0",
    photosCount: 0,
    defectsCount: 0,
    creatorSignedAtMillis: 0,
    creatorSmsCodeHash: "",
    counterpartyConfirmed: false,
    counterpartyName: "",
    counterpartyPhone: "",
    counterpartySignedAtMillis: 0,
    counterpartySmsCodeHash: "",
    shareToken: "TOK123",
    selectedPlan: "single_act",
    pdfGenerated: false,
    createdAt: new Date(),
    updatedAt: new Date(),
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

test("Unauthenticated user cannot read or write acts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("acts").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("acts").doc("act_1").set(validActPayload(ALICE_UID))
  );
});

test("Authenticated owner can create and list own act", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("acts").doc("act_1");
  await assertSucceeds(ref.set(validActPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("acts").get());
});

test("Cross-user access to acts is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("acts").doc("bob_act").set(
      validActPayload(BOB_UID)
    );
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("acts").doc("bob_act").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("acts").get());
});

test("Ghost field injection is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("acts").doc("act_ghost").set(
      validActPayload(ALICE_UID, { isAdmin: true })
    )
  );
});

test("Cannot create act already in signed terminal state", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("acts").doc("act_signed").set(
      validActPayload(ALICE_UID, { status: "signed" })
    )
  );
});

test("Creator sign -> counterparty sign -> terminal state lock blocks further edits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const actRef = aliceDb.collection("users").doc(ALICE_UID).collection("acts").doc("act_flow");
  await assertSucceeds(actRef.set(validActPayload(ALICE_UID)));

  // Creator signs
  await assertSucceeds(
    actRef.update({
      status: "pending_counterparty",
      creatorSignedAtMillis: 1700000000000,
      creatorSmsCodeHash: "sha256_creator_1234",
      updatedAt: new Date(),
    })
  );

  // Counterparty confirms -> terminal signed
  await assertSucceeds(
    actRef.update({
      status: "signed",
      counterpartyConfirmed: true,
      counterpartyName: "Иванов И.И.",
      counterpartyPhone: "+79001112233",
      counterpartySignedAtMillis: 1700000060000,
      counterpartySmsCodeHash: "sha256_cp_5678",
      updatedAt: new Date(),
    })
  );

  // Subsequent update and delete must fail
  await assertFails(actRef.update({ depositAmountRub: 99999, updatedAt: new Date() }));
  await assertFails(actRef.delete());
});

test("Checklist item respects parent gate and blocks edits after act is signed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const actRef = aliceDb.collection("users").doc(ALICE_UID).collection("acts").doc("act_items");
  await assertSucceeds(actRef.set(validActPayload(ALICE_UID)));

  const itemRef = actRef.collection("items").doc("item_1");
  await assertSucceeds(
    itemRef.set({
      userId: ALICE_UID,
      actId: "act_items",
      zone: "Пол и плинтусы",
      title: "Ламинат в гостиной",
      condition: "defect",
      comment: "Царапина у окна 4 см",
      photoLocalPath: "/data/user/0/photo.jpg",
      photoCapturedAtIso: "2026-11-15T22:05:00",
      photoLat: 55.76,
      photoLng: 37.60,
      photoSha256: "abcdef1234567890",
      sortOrder: 0,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Sign act
  await assertSucceeds(
    actRef.update({
      status: "pending_counterparty",
      creatorSignedAtMillis: 1700000000000,
      creatorSmsCodeHash: "sha256_creator_1234",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    actRef.update({
      status: "signed",
      counterpartyConfirmed: true,
      counterpartyName: "Иванов И.И.",
      counterpartyPhone: "+79001112233",
      counterpartySignedAtMillis: 1700000060000,
      counterpartySmsCodeHash: "sha256_cp_5678",
      updatedAt: new Date(),
    })
  );

  // Editing or adding items after signing must fail
  await assertFails(itemRef.update({ condition: "ok", updatedAt: new Date() }));
  await assertFails(
    actRef.collection("items").doc("item_2").set({
      userId: ALICE_UID,
      actId: "act_items",
      zone: "Стены",
      title: "Обои",
      condition: "ok",
      sortOrder: 1,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});
