const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE = "alice_uid";
const BOB = "bob_uid";
const CHARLIE = "charlie_uid";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowDate() {
  return new Date();
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

test("Unauthenticated caller cannot read users, conversations, moments, or calls", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("users").where("isDiscoverable", "==", true).get());
  await assertFails(db.collection("conversations").get());
  await assertFails(db.collection("moments").where("visibility", "==", "public").get());
  await assertFails(db.collection("calls").get());
});

test("User profile: owner can create and update, cross-user update fails, private PII isolated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).set({
      userId: ALICE,
      username: "alice_neon",
      displayName: "Alice Nova",
      bio: "Futuristic chat lover",
      statusText: "Online",
      avatarColorHex: "#00D4FF",
      isOnline: true,
      isLive: true,
      isDiscoverable: true,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  // Shadow update with ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE).update({
      isAdmin: true,
      updatedAt: nowDate(),
    })
  );

  // Cross-user update must fail
  await assertFails(
    bobDb.collection("users").doc(ALICE).update({
      bio: "Hacked",
      updatedAt: nowDate(),
    })
  );

  // Discoverable query succeeds for Bob
  await assertSucceeds(
    bobDb.collection("users").where("isDiscoverable", "==", true).get()
  );

  // Private settings: Alice succeeds, Bob fails
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("private").doc("account").set({
      userId: ALICE,
      email: "alice@example.com",
      showOnlineStatus: true,
      readReceiptsEnabled: true,
      allowCallsFrom: "friends",
      notifyMessages: true,
      notifyCalls: true,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE).collection("private").doc("account").get()
  );
});

test("Conversations and messages: member access succeeds, non-member fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE).firestore();

  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_1").set({
      title: "Alice & Bob",
      isGroup: false,
      memberUids: [ALICE, BOB],
      memberNames: "Alice Nova, Bob Pulse",
      createdBy: ALICE,
      lastMessageText: "Hey!",
      lastMessageSenderId: ALICE,
      lastMessageType: "text",
      pinnedByUids: [],
      unreadByUids: [BOB],
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  await assertSucceeds(
    bobDb.collection("conversations").where("memberUids", "array-contains", BOB).get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc("conv_1").get()
  );
  await assertFails(
    charlieDb.collection("conversations").get()
  );

  // Message create by Alice
  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_1").collection("messages").doc("msg_1").set({
      conversationId: "conv_1",
      senderId: ALICE,
      senderName: "Alice Nova",
      text: "Hello P-CHAT!",
      messageType: "text",
      reactions: [],
      memberUids: [ALICE, BOB],
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  // Charlie cannot read message
  await assertFails(
    charlieDb.collection("conversations").doc("conv_1").collection("messages").doc("msg_1").get()
  );

  // Bob can react
  await assertSucceeds(
    bobDb.collection("conversations").doc("conv_1").collection("messages").doc("msg_1").update({
      reactions: ["🔥:" + BOB],
      updatedAt: nowDate(),
    })
  );
});

test("Moments and comments: public visibility and author isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();

  await assertSucceeds(
    aliceDb.collection("moments").doc("mom_1").set({
      authorId: ALICE,
      authorName: "Alice Nova",
      authorUsername: "alice_neon",
      caption: "Midnight neon vibes",
      mediaType: "photo",
      filterTag: "neon_crown",
      isStory: true,
      visibility: "public",
      likedByUids: [],
      commentCount: 0,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  await assertSucceeds(
    bobDb.collection("moments").where("visibility", "==", "public").get()
  );

  // Bob can like & increment commentCount, cannot edit caption
  await assertSucceeds(
    bobDb.collection("moments").doc("mom_1").update({
      likedByUids: [BOB],
      updatedAt: nowDate(),
    })
  );
  await assertFails(
    bobDb.collection("moments").doc("mom_1").update({
      caption: "Altered caption",
      updatedAt: nowDate(),
    })
  );

  // Bob can comment
  await assertSucceeds(
    bobDb.collection("moments").doc("mom_1").collection("comments").doc("c_1").set({
      momentId: "mom_1",
      momentAuthorId: ALICE,
      authorId: BOB,
      authorName: "Bob Pulse",
      text: "Looks awesome!",
      visibility: "public",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
});

test("Calls: terminal status locking prevents reopening ended call", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      conversationId: "conv_1",
      callerId: ALICE,
      callerName: "Alice Nova",
      title: "Alice & Bob Video",
      participantUids: [ALICE, BOB],
      callType: "video",
      status: "ringing",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "ended",
      updatedAt: nowDate(),
    })
  );

  // Once ended, cannot update again
  await assertFails(
    aliceDb.collection("calls").doc("call_1").update({
      status: "active",
      updatedAt: nowDate(),
    })
  );
});

test("Wallet: owner isolation, immutable ledger, and single-claim peer transfers", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE).firestore();

  // Alice creates her wallet summary
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("wallet").doc("summary").set({
      userId: ALICE,
      balanceCents: 2500,
      coins: 250,
      currency: "USD",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  // Bob cannot read or overwrite Alice's wallet
  await assertFails(
    bobDb.collection("users").doc(ALICE).collection("wallet").doc("summary").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE).collection("wallet").doc("summary").update({
      balanceCents: 999999,
      updatedAt: nowDate(),
    })
  );

  // Negative balance rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE).collection("wallet").doc("summary").update({
      balanceCents: -100,
      updatedAt: nowDate(),
    })
  );

  // Alice logs immutable transaction
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("walletTransactions").doc("tx_1").set({
      userId: ALICE,
      txType: "deposit",
      amountCents: 2500,
      coinDelta: 0,
      title: "Welcome Bonus & Top Up",
      status: "completed",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE).collection("walletTransactions").doc("tx_1").update({
      amountCents: 9999,
      updatedAt: nowDate(),
    })
  );

  // Peer transfer: Alice sends $5.00 + 25 coins to Bob
  await assertSucceeds(
    aliceDb.collection("walletTransfers").doc("tr_1").set({
      senderId: ALICE,
      senderName: "Alice Nova",
      recipientId: BOB,
      recipientName: "Bob Pulse",
      participantUids: [ALICE, BOB],
      amountCents: 500,
      coins: 25,
      note: "Coffee gift",
      status: "sent",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  // Charlie cannot read transfer
  await assertFails(charlieDb.collection("walletTransfers").doc("tr_1").get());
  // Alice cannot claim her own transfer
  await assertFails(
    aliceDb.collection("walletTransfers").doc("tr_1").update({
      status: "claimed",
      updatedAt: nowDate(),
    })
  );

  // Bob claims transfer once
  await assertSucceeds(
    bobDb.collection("walletTransfers").doc("tr_1").update({
      status: "claimed",
      updatedAt: nowDate(),
    })
  );
  // Cannot claim twice
  await assertFails(
    bobDb.collection("walletTransfers").doc("tr_1").update({
      status: "claimed",
      updatedAt: nowDate(),
    })
  );
});

test("Theme & Filter API: black/white theme updates, owner-only filterPresets, and serverFilters catalog rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  // Create private settings with black theme
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("private").doc("account").set({
      userId: ALICE,
      showOnlineStatus: true,
      readReceiptsEnabled: true,
      allowCallsFrom: "friends",
      notifyMessages: true,
      notifyCalls: true,
      themeMode: "black",
      activeCameraFilterId: "neon_crown",
      discoverOnlineOnly: false,
      discoverVerifiedOnly: false,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  // Valid switch to white theme + active filter update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("private").doc("account").update({
      themeMode: "white",
      discoverLanguage: "English",
      discoverOnlineOnly: true,
      updatedAt: nowDate(),
    })
  );

  // Invalid theme name rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE).collection("private").doc("account").update({
      themeMode: "neon_invalid",
      updatedAt: nowDate(),
    })
  );

  // Filter presets subcollection: owner CRUD succeeds, cross-user fails
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("filterPresets").doc("preset_1").set({
      userId: ALICE,
      name: "White + Verified Uganda",
      themeMode: "white",
      languageFilter: "Luganda",
      countryFilter: "Uganda",
      onlineOnly: true,
      verifiedOnly: true,
      cameraFilterId: "beauty_glow",
      sortBy: "online_first",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("filterPresets").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE).collection("filterPresets").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE).collection("filterPresets").doc("preset_1").delete()
  );

  // Server filter catalog: authenticated bootstrap create, filtered query & usage telemetry update succeed;
  // unauthenticated query, unfiltered list, and tampering fail
  await assertSucceeds(
    aliceDb.collection("serverFilters").doc("beauty_glow").set({
      filterId: "beauty_glow",
      name: "Beauty Glow",
      category: "ar_lens",
      badge: "💎",
      description: "Soft rose-pearl illumination and bokeh catchlights",
      usageCount: 1,
      sortOrder: 1,
      isEnabled: true,
      createdBy: ALICE,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  await assertFails(unauthDb.collection("serverFilters").where("isEnabled", "==", true).get());
  await assertFails(bobDb.collection("serverFilters").get());
  await assertSucceeds(bobDb.collection("serverFilters").where("isEnabled", "==", true).get());

  // Any signed-in user can increment usageCount safely
  await assertSucceeds(
    bobDb.collection("serverFilters").doc("beauty_glow").update({
      usageCount: 2,
      updatedAt: nowDate(),
    })
  );

  // Bob cannot hijack filter description or overwrite name
  await assertFails(
    bobDb.collection("serverFilters").doc("beauty_glow").update({
      description: "Hijacked",
      updatedAt: nowDate(),
    })
  );
});

test("International Wallet FX: verified settlement, fee integrity, local currency credit, and dual receipts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();

  // Valid international transfer (USD -> UGX) with verified settlement & matching totalDebitedCents
  await assertSucceeds(
    aliceDb.collection("walletTransfers").doc("intl_1").set({
      senderId: ALICE,
      senderName: "Alice Nova",
      recipientId: BOB,
      recipientName: "Bob Kampala",
      participantUids: [ALICE, BOB],
      amountCents: 5000,
      coins: 0,
      note: "Family support Uganda",
      senderCurrency: "USD",
      recipientCountry: "Uganda",
      recipientCurrency: "UGX",
      exchangeRate: 3720.0,
      recipientLocalAmountMinor: 186000,
      feeCents: 90,
      totalDebitedCents: 5090,
      paymentProvider: "M-Pesa / MTN MoMo",
      settlementRef: "STL-20261122-9F3A1C",
      receiptId: "RCT-20261122-4B7E2D",
      settlementVerified: true,
      status: "sent",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  // Tampered totalDebitedCents (does not equal amountCents + feeCents) must fail
  await assertFails(
    aliceDb.collection("walletTransfers").doc("intl_tampered_fee").set({
      senderId: ALICE,
      senderName: "Alice Nova",
      recipientId: BOB,
      recipientName: "Bob Kampala",
      participantUids: [ALICE, BOB],
      amountCents: 5000,
      coins: 0,
      senderCurrency: "USD",
      recipientCountry: "Uganda",
      recipientCurrency: "UGX",
      exchangeRate: 3720.0,
      recipientLocalAmountMinor: 186000,
      feeCents: 90,
      totalDebitedCents: 5000,
      paymentProvider: "M-Pesa / MTN MoMo",
      settlementRef: "STL-20261122-9F3A1C",
      receiptId: "RCT-20261122-4B7E2D",
      settlementVerified: true,
      status: "sent",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  // Claimed verified settlement without settlementRef must fail
  await assertFails(
    aliceDb.collection("walletTransfers").doc("intl_unverified").set({
      senderId: ALICE,
      senderName: "Alice Nova",
      recipientId: BOB,
      recipientName: "Bob Kampala",
      participantUids: [ALICE, BOB],
      amountCents: 5000,
      coins: 0,
      senderCurrency: "USD",
      recipientCountry: "Uganda",
      recipientCurrency: "UGX",
      exchangeRate: 3720.0,
      recipientLocalAmountMinor: 186000,
      feeCents: 90,
      totalDebitedCents: 5090,
      paymentProvider: "M-Pesa / MTN MoMo",
      settlementRef: "",
      receiptId: "RCT-20261122-4B7E2D",
      settlementVerified: true,
      status: "sent",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  // Sender generates itemized outgoing receipt
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("walletTransactions").doc("rct_sender_1").set({
      userId: ALICE,
      txType: "transfer_out",
      amountCents: -5090,
      coinDelta: 0,
      title: "Sent UGX to Bob Kampala",
      counterpartyId: BOB,
      counterpartyName: "Bob Kampala",
      note: "Family support Uganda",
      baseAmountCents: 5000,
      feeCents: 90,
      currency: "USD",
      localCurrency: "UGX",
      localAmountMinor: 186000,
      exchangeRate: 3720.0,
      recipientCountry: "Uganda",
      paymentProvider: "M-Pesa / MTN MoMo",
      settlementRef: "STL-20261122-9F3A1C",
      receiptId: "RCT-20261122-4B7E2D",
      settlementVerified: true,
      status: "completed",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  // Bob claims transfer & receives UGX into localBalanceMinor + matching receipt
  await assertSucceeds(
    bobDb.collection("walletTransfers").doc("intl_1").update({
      status: "claimed",
      updatedAt: nowDate(),
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB).collection("wallet").doc("summary").set({
      userId: BOB,
      balanceCents: 5000,
      coins: 0,
      currency: "USD",
      localCurrency: "UGX",
      localBalanceMinor: 186000,
      localCountry: "Uganda",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB).collection("walletTransactions").doc("rct_rec_1").set({
      userId: BOB,
      txType: "transfer_in",
      amountCents: 5000,
      coinDelta: 0,
      title: "Received UGX from Alice Nova",
      counterpartyId: ALICE,
      counterpartyName: "Alice Nova",
      note: "Family support Uganda",
      baseAmountCents: 5000,
      feeCents: 90,
      currency: "USD",
      localCurrency: "UGX",
      localAmountMinor: 186000,
      exchangeRate: 3720.0,
      recipientCountry: "Uganda",
      paymentProvider: "M-Pesa / MTN MoMo",
      settlementRef: "STL-20261122-9F3A1C",
      receiptId: "RCT-20261122-4B7E2D",
      settlementVerified: true,
      status: "completed",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
});

test("Auth collections: usernames unique index, userSettings, userSessions, loginHistory, securityLogs, deviceRegistrations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();

  // 1. usernames collection: listing denied, direct create/get allowed, hijack denied
  await assertFails(unauthDb.collection("usernames").doc("alice_pchat").get());
  await assertFails(aliceDb.collection("usernames").get());

  await assertSucceeds(
    aliceDb.collection("usernames").doc("alice_pchat").set({
      username: "alice_pchat",
      userId: ALICE,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertSucceeds(bobDb.collection("usernames").doc("alice_pchat").get());
  // Bob cannot overwrite or steal Alice's username claim
  await assertFails(
    bobDb.collection("usernames").doc("alice_pchat").set({
      username: "alice_pchat",
      userId: BOB,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  // Spoofing uid in create fails
  await assertFails(
    bobDb.collection("usernames").doc("bob_spoof").set({
      username: "bob_spoof",
      userId: ALICE,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  // Extended UserProfile & PrivateAccountSettings fields work under owner rules
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).set({
      userId: ALICE,
      username: "alice_pchat",
      displayName: "Alice Nova",
      fullName: "Alice Nova",
      preferredLanguage: "Luganda",
      authProvider: "google",
      profileCompleted: true,
      isOnline: true,
      isLive: true,
      isDiscoverable: true,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("private").doc("account").set({
      userId: ALICE,
      fullName: "Alice Nova",
      email: "alice@example.com",
      phoneNumber: "+256700123456",
      country: "Uganda",
      countryFlag: "🇺🇬",
      preferredLanguage: "Luganda",
      dateOfBirth: "2000-05-14",
      authProvider: "google",
      emailVerified: true,
      phoneVerified: false,
      profileCompleted: true,
      showOnlineStatus: true,
      readReceiptsEnabled: true,
      allowCallsFrom: "friends",
      notifyMessages: true,
      notifyCalls: true,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );

  // 2. userSettings: owner create/update ok, invalid provider or cross-user fails
  await assertSucceeds(
    aliceDb.collection("userSettings").doc(ALICE).set({
      userId: ALICE,
      preferredLanguage: "English",
      country: "Uganda",
      countryFlag: "🇺🇬",
      themeMode: "black",
      activeCameraFilterId: "none",
      lastAuthMethod: "google",
      emailVerified: true,
      phoneVerified: false,
      profileCompleted: true,
      accountStatus: "active",
      notifyMessages: true,
      notifyCalls: true,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertFails(bobDb.collection("userSettings").doc(ALICE).get());
  await assertFails(
    aliceDb.collection("userSettings").doc(ALICE).update({
      lastAuthMethod: "anonymous_hack",
      updatedAt: nowDate(),
    })
  );

  // 3. userSessions: owner create + revoke status update ok, cross-user read/revoke fails
  await assertSucceeds(
    aliceDb.collection("userSessions").doc("sess_alice_1").set({
      sessionId: "sess_alice_1",
      userId: ALICE,
      deviceId: "dev_pixel_9",
      provider: "google",
      status: "active",
      platform: "android",
      appVersion: "1.0",
      lastActiveAt: nowDate(),
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("userSessions").doc("sess_alice_1").update({
      status: "revoked",
      updatedAt: nowDate(),
    })
  );
  await assertFails(bobDb.collection("userSessions").doc("sess_alice_1").get());
  await assertFails(
    bobDb.collection("userSessions").doc("sess_alice_1").update({
      status: "revoked",
      updatedAt: nowDate(),
    })
  );

  // 4. loginHistory: owner create ok, update immutable (fails), cross-user get fails
  await assertSucceeds(
    aliceDb.collection("loginHistory").doc("hist_alice_1").set({
      entryId: "hist_alice_1",
      userId: ALICE,
      provider: "google",
      eventType: "login_success",
      outcome: "success",
      statusCode: "OK",
      message: "Signed in cleanly via Google Credential Manager",
      deviceId: "dev_pixel_9",
      verificationStatus: "email_verified",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertFails(
    aliceDb.collection("loginHistory").doc("hist_alice_1").update({
      outcome: "failure",
      updatedAt: nowDate(),
    })
  );
  await assertFails(bobDb.collection("loginHistory").doc("hist_alice_1").get());

  // 5. securityLogs: requires redacted == true, immutable, owner-only
  await assertFails(
    aliceDb.collection("securityLogs").doc("sec_unredacted").set({
      logId: "sec_unredacted",
      userId: ALICE,
      category: "auth_attempt",
      severity: "info",
      provider: "phone",
      summary: "Attempt started",
      redacted: false,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("securityLogs").doc("sec_alice_1").set({
      logId: "sec_alice_1",
      userId: ALICE,
      category: "auth_attempt",
      severity: "info",
      provider: "phone",
      summary: "OTP requested for +256***456",
      redacted: true,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertFails(
    aliceDb.collection("securityLogs").doc("sec_alice_1").update({
      summary: "Tampered",
      updatedAt: nowDate(),
    })
  );
  await assertFails(bobDb.collection("securityLogs").doc("sec_alice_1").get());

  // 6. deviceRegistrations: owner create & update ok, cross-user denied
  const devDocId = `${ALICE}_dev_pixel_9`;
  await assertSucceeds(
    aliceDb.collection("deviceRegistrations").doc(devDocId).set({
      deviceDocId: devDocId,
      userId: ALICE,
      deviceId: "dev_pixel_9",
      deviceModel: "Google Pixel 9",
      osVersion: "Android 15",
      appVersion: "1.0",
      platform: "android",
      isTrusted: true,
      lastSeenAt: nowDate(),
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertFails(bobDb.collection("deviceRegistrations").doc(devDocId).get());
  await assertFails(
    bobDb.collection("deviceRegistrations").doc(devDocId).update({
      isTrusted: false,
      updatedAt: nowDate(),
    })
  );
});

test("Audit fix: missing-doc get() succeeds before creation while existing cross-user get() fails, and profile edits sync settings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE).firestore();
  const bobDb = testEnv.authenticatedContext(BOB).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE).firestore();

  // Missing documents must allow check-before-create .get() without PERMISSION_DENIED
  await assertSucceeds(aliceDb.collection("friendships").doc(`${ALICE}_${BOB}`).get());
  await assertSucceeds(aliceDb.collection("conversations").doc(`dm_${ALICE}_${BOB}`).get());
  await assertSucceeds(aliceDb.collection("userSessions").doc("sess_missing_1").get());
  await assertSucceeds(aliceDb.collection("deviceRegistrations").doc(`${ALICE}_dev_missing`).get());
  await assertSucceeds(aliceDb.collection("serverFilters").doc("nonexistent_filter").get());

  // Once friendship and DM exist, third-party Charlie cannot get() them
  await assertSucceeds(
    aliceDb.collection("friendships").doc(`${ALICE}_${BOB}`).set({
      userIds: [ALICE, BOB],
      requesterId: ALICE,
      recipientId: BOB,
      requesterName: "Alice",
      recipientName: "Bob",
      status: "pending",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertFails(charlieDb.collection("friendships").doc(`${ALICE}_${BOB}`).get());

  await assertSucceeds(
    aliceDb.collection("conversations").doc(`dm_${ALICE}_${BOB}`).set({
      title: "Bob",
      isGroup: false,
      memberUids: [ALICE, BOB],
      memberNames: "Alice, Bob",
      createdBy: ALICE,
      lastMessageText: "Hi",
      lastMessageSenderId: ALICE,
      lastMessageType: "text",
      pinnedByUids: [],
      unreadByUids: [],
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertFails(charlieDb.collection("conversations").doc(`dm_${ALICE}_${BOB}`).get());

  // Owner profile edits sync country, countryFlag, preferredLanguage, and fullName to private & userSettings
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("private").doc("account").set({
      userId: ALICE,
      fullName: "Alice Nova",
      country: "Global",
      countryFlag: "🌍",
      preferredLanguage: "English",
      showOnlineStatus: true,
      readReceiptsEnabled: true,
      allowCallsFrom: "friends",
      notifyMessages: true,
      notifyCalls: true,
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("userSettings").doc(ALICE).set({
      userId: ALICE,
      preferredLanguage: "English",
      country: "Global",
      countryFlag: "🌍",
      themeMode: "black",
      lastAuthMethod: "google",
      profileCompleted: true,
      accountStatus: "active",
      createdAt: nowDate(),
      updatedAt: nowDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE).collection("private").doc("account").update({
      fullName: "Alice Kampala",
      country: "Uganda",
      countryFlag: "🇺🇬",
      preferredLanguage: "Luganda",
      updatedAt: nowDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("userSettings").doc(ALICE).update({
      country: "Uganda",
      countryFlag: "🇺🇬",
      preferredLanguage: "Luganda",
      updatedAt: nowDate(),
    })
  );
  await assertFails(
    bobDb.collection("userSettings").doc(ALICE).update({
      country: "Kenya",
      updatedAt: nowDate(),
    })
  );
});


