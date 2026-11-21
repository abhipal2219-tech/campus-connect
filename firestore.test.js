const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "student_alice_123";
const BOB_UID = "student_bob_456";
const ADMIN_UID = "admin_abhi_001";
const PROMOTED_ADMIN_UID = "admin_promoted_002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTime(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
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

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: "abhipal2219@gmail.com",
    email_verified: true,
  });
}

function spoofedAdminContext() {
  return testEnv.authenticatedContext("spoof_user", {
    email: "abhipal2219@gmail.com",
    email_verified: false,
  });
}

// 1. Unauthenticated bounds
test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("directory").where("visibility", "==", "campus").get());
  await assertFails(unauthDb.collection("announcements").where("visibility", "==", "campus").get());
  await assertFails(unauthDb.collection("tickets").where("userId", "==", ALICE_UID).get());
});

// 2. User profiles & PII isolation
test("Users: owner create/update works; cross-user read, list, and ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTime(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Student",
      email: "alice@campus.edu",
      department: "Computer Science",
      notificationsEnabled: true,
      reminderLeadMinutes: 30,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Partial update succeeds while preserving createdAt
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      department: "Electrical Engineering",
      notificationsEnabled: false,
      reminderLeadMinutes: 15,
      updatedAt: pastTime(1000),
    })
  );

  // Ghost field / privilege escalation fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: pastTime(500),
    })
  );

  // Immutable createdAt mutation fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastTime(500),
      updatedAt: pastTime(500),
    })
  );

  // Cross-user PII read and collection list fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
  await assertFails(aliceDb.collection("users").get());
});

// 3. Schedule Items (user-scoped subcollection)
test("ScheduleItems: owner CRUD succeeds; cross-user, extra id key, and future timestamp fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTime(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("scheduleItems").doc("cls_1").set({
      userId: ALICE_UID,
      itemType: "CLASS",
      courseCode: "CS-301",
      title: "Operating Systems",
      dayOfWeek: 1,
      startTimeMinutes: 540,
      endTimeMinutes: 630,
      examEpochMillis: 0,
      location: "Turing Hall 204",
      instructor: "Dr. Patel",
      reminderEnabled: true,
      reminderLeadMinutes: 15,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("scheduleItems").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("scheduleItems").doc("cls_1").update({
      location: "Turing Hall 305",
      reminderEnabled: false,
      updatedAt: pastTime(1000),
    })
  );

  // Reject @DocumentId "id" key in payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("scheduleItems").doc("cls_2").set({
      id: "cls_2",
      userId: ALICE_UID,
      itemType: "EXAM",
      courseCode: "MATH-210",
      title: "Linear Algebra Midterm",
      dayOfWeek: 3,
      startTimeMinutes: 600,
      endTimeMinutes: 720,
      examEpochMillis: Date.now() + 86400000,
      location: "Main Auditorium",
      instructor: "Prof. Lin",
      reminderEnabled: true,
      reminderLeadMinutes: 60,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("scheduleItems").doc("cls_3").set({
      userId: ALICE_UID,
      itemType: "CLASS",
      courseCode: "CS-101",
      title: "Future Class",
      dayOfWeek: 2,
      startTimeMinutes: 540,
      endTimeMinutes: 600,
      examEpochMillis: 0,
      location: "Lab 1",
      instructor: "Prof. Kim",
      reminderEnabled: true,
      reminderLeadMinutes: 15,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: ts,
    })
  );

  // Cross-user access denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("scheduleItems").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("scheduleItems").doc("cls_1").delete()
  );
});

// 4. AI Chat Messages
test("ChatMessages: owner create/list/delete succeeds; update and cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTime(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").doc("msg_1").set({
      userId: ALICE_UID,
      sender: "USER",
      content: "When is my next exam?",
      createdAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").get()
  );

  // Chat messages are immutable
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").doc("msg_1").update({
      content: "Edited",
    })
  );

  // Cross-user denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("chatMessages").get()
  );

  // Owner can clear history
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").doc("msg_1").delete()
  );
});

// 5. Bootstrapped Admin, Email Spoofing, and Promoted Admins
test("Admins: bootstrapped verified admin can grant admin; spoofed unverified email and students denied", async () => {
  const adminDb = adminContext().firestore();
  const spoofDb = spoofedAdminContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastTime(5000);

  // Spoofed email (email_verified: false) must be rejected
  await assertFails(
    spoofDb.collection("admins").doc(PROMOTED_ADMIN_UID).set({
      adminUid: PROMOTED_ADMIN_UID,
      grantedBy: "spoof_user",
      label: "Spoof",
      createdAt: ts,
    })
  );

  // Student self-promotion must be rejected
  await assertFails(
    aliceDb.collection("admins").doc(ALICE_UID).set({
      adminUid: ALICE_UID,
      grantedBy: ALICE_UID,
      label: "Self",
      createdAt: ts,
    })
  );

  // Verified bootstrapped admin succeeds
  await assertSucceeds(
    adminDb.collection("admins").doc(PROMOTED_ADMIN_UID).set({
      adminUid: PROMOTED_ADMIN_UID,
      grantedBy: ADMIN_UID,
      label: "Dean of Students",
      createdAt: ts,
    })
  );

  // Promoted user can read their own admin record and broadcast campus announcements
  const promotedDb = testEnv.authenticatedContext(PROMOTED_ADMIN_UID).firestore();
  await assertSucceeds(promotedDb.collection("admins").doc(PROMOTED_ADMIN_UID).get());
  await assertSucceeds(
    promotedDb.collection("announcements").doc("promoted_alert").set({
      visibility: "campus",
      severity: "INFO",
      title: "Dean Office Hours",
      body: "Drop-in advising is open in Founders Hall 200 today.",
      department: "Dean of Students",
      authorId: PROMOTED_ADMIN_UID,
      authorName: "Dean",
      createdAt: ts,
    })
  );
});

// 6. Campus Directory & Announcements (scoped campus visibility queries)
test("Directory & Announcements: admin write succeeds; student scoped read succeeds; student write & unfiltered query fail", async () => {
  const adminDb = adminContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastTime(5000);

  // Admin creates directory office & announcement
  await assertSucceeds(
    adminDb.collection("directory").doc("it_helpdesk").set({
      visibility: "campus",
      name: "IT Helpdesk",
      category: "IT_SUPPORT",
      building: "Technology Center",
      room: "Suite 110",
      hours: "Mon-Fri 08:00 - 20:00",
      phone: "555-0142",
      email: "it@campus.edu",
      description: "Wi-Fi, campus accounts, and lab hardware support.",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    adminDb.collection("announcements").doc("alert_1").set({
      visibility: "campus",
      severity: "URGENT",
      title: "Library HVAC Maintenance",
      body: "North Wing study rooms close at 18:00 today for scheduled maintenance.",
      department: "Campus Operations",
      authorId: ADMIN_UID,
      authorName: "Campus Admin",
      createdAt: ts,
    })
  );

  // Student scoped queries matching Kotlin repositories succeed
  await assertSucceeds(
    aliceDb.collection("directory").where("visibility", "==", "campus").get()
  );
  await assertSucceeds(
    aliceDb.collection("announcements").where("visibility", "==", "campus").get()
  );

  // Unfiltered queries must be rejected
  await assertFails(aliceDb.collection("directory").get());
  await assertFails(aliceDb.collection("announcements").get());

  // Student cannot write directory or broadcast announcements
  await assertFails(
    aliceDb.collection("announcements").doc("alert_fake").set({
      visibility: "campus",
      severity: "URGENT",
      title: "Fake Alert",
      body: "Unauthorized broadcast attempt.",
      department: "None",
      authorId: ALICE_UID,
      authorName: "Alice",
      createdAt: ts,
    })
  );
  await assertFails(aliceDb.collection("directory").doc("it_helpdesk").delete());
});

// 7. Helpdesk Tickets (Student isolation, Terminal State Lock, Admin triage)
test("Tickets: student create/read/close works; terminal CLOSED locks student; admin triages all", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = adminContext().firestore();
  const ts = pastTime(10000);

  // Student creates OPEN ticket
  await assertSucceeds(
    aliceDb.collection("tickets").doc("tkt_1").set({
      userId: ALICE_UID,
      authorName: "Alice Student",
      category: "IT_SUPPORT",
      priority: "HIGH",
      subject: "Eduroam Wi-Fi down in Hall B",
      description: "Cannot authenticate to campus Wi-Fi since 09:00.",
      location: "Hall B Room 210",
      status: "OPEN",
      adminResponse: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Student cannot forge initial status or adminResponse
  await assertFails(
    aliceDb.collection("tickets").doc("tkt_bad").set({
      userId: ALICE_UID,
      authorName: "Alice Student",
      category: "IT_SUPPORT",
      priority: "LOW",
      subject: "Forged status",
      description: "Trying to pre-resolve ticket.",
      location: "Lab 1",
      status: "RESOLVED",
      adminResponse: "Approved",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Student scoped query succeeds; unfiltered query fails; Bob cannot read Alice's ticket
  await assertSucceeds(aliceDb.collection("tickets").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("tickets").get());
  await assertFails(bobDb.collection("tickets").doc("tkt_1").get());

  // Admin can list all tickets and post resolution
  await assertSucceeds(adminDb.collection("tickets").get());
  await assertSucceeds(
    adminDb.collection("tickets").doc("tkt_1").update({
      status: "IN_PROGRESS",
      adminResponse: "Network team is resetting the Hall B access point.",
      updatedAt: pastTime(3000),
    })
  );

  // Student closes own ticket
  await assertSucceeds(
    aliceDb.collection("tickets").doc("tkt_1").update({
      status: "CLOSED",
      updatedAt: pastTime(2000),
    })
  );

  // Terminal state lock: student cannot reopen or edit CLOSED ticket
  await assertFails(
    aliceDb.collection("tickets").doc("tkt_1").update({
      status: "OPEN",
      updatedAt: pastTime(1000),
    })
  );

  // Admin override works on CLOSED ticket
  await assertSucceeds(
    adminDb.collection("tickets").doc("tkt_1").update({
      status: "RESOLVED",
      adminResponse: "Confirmed restored and archived.",
      updatedAt: pastTime(500),
    })
  );
});
