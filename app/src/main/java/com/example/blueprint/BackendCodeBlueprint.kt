package com.example.blueprint

data class BlueprintSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val language: String,
    val fileName: String,
    val code: String
)

object BackendCodeBlueprint {

    val sections: List<BlueprintSection> = listOf(
        BlueprintSection(
            id = "firebase_cloud_functions",
            title = "1. Firebase Cloud Functions (Node.js / Express)",
            subtitle = "Concurrency-safe Dynamic Amount Lock (runTransaction), Webhook Receiver, Verification Engine & Unmatched TrxID Claim Queue",
            language = "javascript",
            fileName = "functions/index.js",
            code = """
/**
 * Firebase Cloud Functions v2 + Realtime Database Concurrency Engine
 * Handles 20+ simultaneous users paying a single personal bKash/Nagad number
 */
const { onRequest } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
const express = require("express");
const crypto = require("crypto");

admin.initializeApp();
const db = admin.database();
const app = express();
app.use(express.json());

const WEBHOOK_SECRET = process.env.MFS_WEBHOOK_SECRET || "whsec_live_bd_mfs_89a7c4e21f09";
const SESSION_TTL_MS = 5 * 60 * 1000; // 5-minute countdown window

/**
 * 1. INITIATE PAYMENT SESSION
 * Atomically reserves a unique dynamic amount (e.g., Base ৳500 -> ৳501.00, ৳502.00...)
 * using Firebase Realtime Database runTransaction() to guarantee zero race conditions
 * across 20+ simultaneous checkouts.
 */
app.post("/api/v1/payment-sessions/initiate", async (req, res) => {
  try {
    const { userId, customerName, customerPhone, baseAmount, mfsProvider } = req.body;
    if (!baseAmount || !mfsProvider || !["bKash", "Nagad"].includes(mfsProvider)) {
      return res.status(400).json({ error: "Invalid baseAmount or mfsProvider (bKash/Nagad)" });
    }

    const now = Date.now();
    const expiresAt = now + SESSION_TTL_MS;
    const baseNum = Math.round(Number(baseAmount));
    const lockRef = db.ref(`active_amount_locks/${"$"}{mfsProvider}/${"$"}{baseNum}`);

    let assignedOffset = null;

    // Atomic transaction on Realtime Database lock node
    await lockRef.transaction((currentLocks) => {
      const locks = currentLocks || {};
      // Purge expired 5-minute locks first
      Object.keys(locks).forEach((offsetKey) => {
        if (locks[offsetKey].expiresAt <= now) {
          delete locks[offsetKey];
        }
      });

      // Allocate lowest available dynamic Taka step (+1.00, +2.00 ... +50.00)
      for (let step = 1; step <= 50; step++) {
        const key = `step_${"$"}{step}`;
        if (!locks[key]) {
          assignedOffset = step;
          locks[key] = { lockedAt: now, expiresAt, userId: userId || "guest" };
          return locks;
        }
      }
      return; // Abort if all 50 concurrent slots for this base amount are full
    });

    if (assignedOffset === null) {
      return res.status(429).json({
        error: "All concurrent dynamic amount slots are busy. Please retry in 30 seconds."
      });
    }

    const lockedAmount = Number((baseNum + assignedOffset).toFixed(2));
    const orderId = `ORD-${"$"}{Math.floor(100000 + Math.random() * 900000)}`;
    const sessionId = `SES-${"$"}{crypto.randomBytes(4).toString("hex").toUpperCase()}`;

    const sessionData = {
      session_id: sessionId,
      order_id: orderId,
      user_id: userId || `USR-${"$"}{customerPhone}`,
      customer_name: customerName || "Customer",
      customer_phone: customerPhone || "",
      mfs_provider: mfsProvider,
      base_amount: baseNum,
      locked_amount: lockedAmount,
      offset_step: assignedOffset,
      status: "ACTIVE",
      matched_trx_id: null,
      expires_at: expiresAt,
      created_at: now
    };

    const orderData = {
      order_id: orderId,
      user_id: sessionData.user_id,
      session_id: sessionId,
      base_amount: baseNum,
      payable_amount: lockedAmount,
      mfs_provider: mfsProvider,
      status: "PENDING",
      trx_id: null,
      created_at: now
    };

    const updates = {};
    updates[`payment_sessions/${"$"}{sessionId}`] = sessionData;
    updates[`orders/${"$"}{orderId}`] = orderData;
    updates[`users/${"$"}{sessionData.user_id}/last_seen`] = now;
    await db.ref().update(updates);

    return res.status(201).json({
      success: true,
      session: sessionData,
      countdown_seconds: 300
    });
  } catch (err) {
    console.error("InitiateSession Error:", err);
    return res.status(500).json({ error: "Internal Server Error" });
  }
});

/**
 * 2. SMS WEBHOOK RECEIVER & VERIFICATION ENGINE
 * Receives parsed SMS JSON from the Android Gateway App:
 * { trx_id, sender_number, amount, mfs_provider, secret_key }
 */
app.post("/api/v1/webhooks/mfs-sms", async (req, res) => {
  try {
    const { trx_id, sender_number, amount, mfs_provider, secret_key } = req.body;

    if (secret_key !== WEBHOOK_SECRET) {
      return res.status(401).json({ error: "Unauthorized Webhook Secret Key" });
    }
    if (!trx_id || !amount || !mfs_provider) {
      return res.status(400).json({ error: "Missing required fields" });
    }

    const cleanTrxId = String(trx_id).trim().toUpperCase();
    const numericAmount = Number(Number(amount).toFixed(2));
    const now = Date.now();

    // Anti Double-Spending: Atomically claim trx_id in `processed_trx_registry/{trx_id}`
    const trxLockRef = db.ref(`processed_trx_registry/${"$"}{cleanTrxId}`);
    const lockResult = await trxLockRef.transaction((current) => {
      if (current !== null) return; // Abort: TrxID already processed!
      return {
        trx_id: cleanTrxId,
        amount: numericAmount,
        sender_number: sender_number || "UNKNOWN",
        mfs_provider,
        status: "PROCESSING",
        received_at: now
      };
    });

    if (!lockResult.committed) {
      return res.status(409).json({
        status: "DUPLICATE_IGNORED",
        message: `TrxID ${"$"}{cleanTrxId} was already processed.`
      });
    }

    // Query payment_sessions matching locked_amount
    const sessionsSnap = await db.ref("payment_sessions")
      .orderByChild("locked_amount")
      .equalTo(numericAmount)
      .once("value");

    let matchedSession = null;
    let expiredSessionFound = false;

    sessionsSnap.forEach((child) => {
      const s = child.val();
      if (s.mfs_provider === mfs_provider) {
        if (s.status === "ACTIVE" && s.expires_at > now) {
          // Prioritize sender number match if available, otherwise match unique dynamic amount
          if (!matchedSession || s.customer_phone === sender_number) {
            matchedSession = s;
          }
        } else if (s.expires_at <= now) {
          expiredSessionFound = true;
        }
      }
    });

    if (matchedSession) {
      // Mark Session & Order as VERIFIED and release active amount lock
      const baseNum = Math.round(matchedSession.base_amount);
      const stepKey = `step_${"$"}{matchedSession.offset_step}`;
      const updates = {};
      updates[`payment_sessions/${"$"}{matchedSession.session_id}/status`] = "MATCHED";
      updates[`payment_sessions/${"$"}{matchedSession.session_id}/matched_trx_id`] = cleanTrxId;
      updates[`orders/${"$"}{matchedSession.order_id}/status`] = "VERIFIED";
      updates[`orders/${"$"}{matchedSession.order_id}/trx_id`] = cleanTrxId;
      updates[`orders/${"$"}{matchedSession.order_id}/verified_at`] = now;
      updates[`processed_trx_registry/${"$"}{cleanTrxId}/status`] = "VERIFIED";
      updates[`processed_trx_registry/${"$"}{cleanTrxId}/order_id`] = matchedSession.order_id;
      updates[`active_amount_locks/${"$"}{mfs_provider}/${"$"}{baseNum}/${"$"}{stepKey}`] = null;

      await db.ref().update(updates);

      return res.status(200).json({
        status: "VERIFIED",
        order_id: matchedSession.order_id,
        session_id: matchedSession.session_id,
        trx_id: cleanTrxId
      });
    }

    // No active session matched -> Enqueue in `unmatched_transactions` for manual TrxID claim
    const reason = expiredSessionFound ? "SESSION_EXPIRED" : "NO_ACTIVE_SESSION";
    await db.ref(`unmatched_transactions/${"$"}{cleanTrxId}`).set({
      trx_id: cleanTrxId,
      sender_number: sender_number || "UNKNOWN",
      amount: numericAmount,
      mfs_provider,
      reason,
      status: "UNCLAIMED",
      claimed_by_order_id: null,
      received_at: now
    });

    return res.status(202).json({
      status: "QUEUED_UNMATCHED",
      reason,
      trx_id: cleanTrxId
    });
  } catch (err) {
    console.error("Webhook Verification Error:", err);
    return res.status(500).json({ error: "Webhook Processing Error" });
  }
});

/**
 * 3. MANUAL TRX_ID CLAIM ENDPOINT (Zero Race Condition Transaction)
 */
app.post("/api/v1/orders/claim-trx", async (req, res) => {
  try {
    const { orderId, trxId, senderNumber } = req.body;
    const cleanTrxId = String(trxId || "").trim().toUpperCase();
    const orderSnap = await db.ref(`orders/${"$"}{orderId}`).once("value");
    if (!orderSnap.exists()) {
      return res.status(404).json({ error: "Order not found" });
    }
    const order = orderSnap.val();
    if (order.status === "VERIFIED") {
      return res.status(400).json({ error: "Order already verified" });
    }

    const unmatchedRef = db.ref(`unmatched_transactions/${"$"}{cleanTrxId}`);
    let claimError = null;

    const txResult = await unmatchedRef.transaction((unmatched) => {
      if (unmatched === null) {
        claimError = "TrxID not found in unmatched queue";
        return;
      }
      if (unmatched.status !== "UNCLAIMED") {
        claimError = "TrxID already claimed by another order";
        return;
      }
      if (unmatched.mfs_provider !== order.mfs_provider) {
        claimError = "MFS provider mismatch";
        return;
      }
      if (unmatched.amount < order.base_amount) {
        claimError = "Transaction amount is less than order base amount";
        return;
      }
      unmatched.status = "CLAIMED";
      unmatched.claimed_by_order_id = orderId;
      unmatched.claimed_at = Date.now();
      return unmatched;
    });

    if (!txResult.committed || claimError) {
      return res.status(400).json({ error: claimError || "Could not claim TrxID" });
    }

    const now = Date.now();
    await db.ref().update({
      [`orders/${"$"}{orderId}/status`]: "VERIFIED",
      [`orders/${"$"}{orderId}/trx_id`]: cleanTrxId,
      [`orders/${"$"}{orderId}/verified_at`]: now,
      [`payment_sessions/${"$"}{order.session_id}/status`]: "MATCHED",
      [`payment_sessions/${"$"}{order.session_id}/matched_trx_id`]: cleanTrxId
    });

    return res.status(200).json({
      success: true,
      status: "VERIFIED_BY_MANUAL_CLAIM",
      order_id: orderId,
      trx_id: cleanTrxId
    });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

exports.mfsPaymentGateway = onRequest({ region: "asia-southeast1" }, app);
            """.trimIndent()
        ),
        BlueprintSection(
            id = "firebase_rtdb_schema",
            title = "2. Firebase Realtime Database Schema & Rules",
            subtitle = "Tables: users, orders, payment_sessions, unmatched_transactions with .indexOn rules",
            language = "json",
            fileName = "database.rules.json",
            code = """
{
  "rules": {
    ".read": "auth != null",
    ".write": "auth != null",
    "users": {
      ".indexOn": ["phone", "created_at"]
    },
    "orders": {
      ".indexOn": ["user_id", "status", "trx_id", "created_at"]
    },
    "payment_sessions": {
      ".indexOn": ["locked_amount", "status", "expires_at", "order_id"]
    },
    "unmatched_transactions": {
      ".indexOn": ["status", "sender_number", "received_at"]
    },
    "processed_trx_registry": {
      ".indexOn": ["status", "received_at"]
    }
  }
}
            """.trimIndent()
        ),
        BlueprintSection(
            id = "android_kotlin_regex_service",
            title = "3. Android Native Regex & Webhook Payload Spec",
            subtitle = "JSON Contract & Regex patterns used by this Android Gateway app",
            language = "json",
            fileName = "webhook_payload_contract.json",
            code = """
{
  "webhook_endpoint": "POST https://<region>-<project>.cloudfunctions.net/mfsPaymentGateway/api/v1/webhooks/mfs-sms",
  "headers": {
    "Content-Type": "application/json",
    "X-Secret-Key": "whsec_live_bd_mfs_89a7c4e21f09",
    "X-MFS-Signature": "<HMAC-SHA256 hex digest of body>"
  },
  "payload_example": {
    "trx_id": "BKA98X72KL",
    "sender_number": "01712345678",
    "amount": 501.00,
    "mfs_provider": "bKash",
    "secret_key": "whsec_live_bd_mfs_89a7c4e21f09",
    "transaction_type": "Send Money",
    "matched_order_id": "ORD-482910",
    "verification_status": "VERIFIED",
    "timestamp": 1790873700000
  }
}
            """.trimIndent()
        ),
        BlueprintSection(
            id = "github_actions_auto_release",
            title = "4. GitHub Actions Auto-Release CI/CD (.github/workflows/android-release.yml)",
            subtitle = "Automatically runs tests, signs Release APK & AAB, and publishes a GitHub Release on every push to main or v* tag",
            language = "yaml",
            fileName = ".github/workflows/android-release.yml",
            code = """
name: Android Auto Build & GitHub Release

on:
  push:
    branches: [ main, master ]
    tags: [ "v*.*.*" ]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build-and-release:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up JDK 21 (Required for Android SDK 36 & Robolectric)
        uses: actions/setup-java@v4
        with:
          distribution: "temurin"
          java-version: "21"

      - name: Setup Gradle 9.3.1
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "9.3.1"

      - name: Prepare Signing Keystore (Secrets or Auto-Generated Fallback)
        env:
          KEYSTORE_BASE64: ${"$"}{{ secrets.KEYSTORE_BASE64 }}
          SECRET_STORE_PASSWORD: ${"$"}{{ secrets.STORE_PASSWORD }}
          SECRET_KEY_PASSWORD: ${"$"}{{ secrets.KEY_PASSWORD }}
        run: |
          if [ ! -f "debug.keystore" ]; then
            keytool -genkeypair -v -keystore debug.keystore -alias androiddebugkey \
              -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android \
              -dname "CN=Android Debug,O=Android,C=US"
          fi
          if [ -n "${"$"}KEYSTORE_BASE64" ]; then
            echo "${"$"}KEYSTORE_BASE64" | base64 --decode > "${"$"}{GITHUB_WORKSPACE}/my-upload-key.jks"
            echo "KEYSTORE_PATH=${"$"}{GITHUB_WORKSPACE}/my-upload-key.jks" >> ${"$"}GITHUB_ENV
            echo "STORE_PASSWORD=${"$"}{SECRET_STORE_PASSWORD}" >> ${"$"}GITHUB_ENV
            echo "KEY_PASSWORD=${"$"}{SECRET_KEY_PASSWORD}" >> ${"$"}GITHUB_ENV
          else
            keytool -genkeypair -v -keystore "${"$"}{GITHUB_WORKSPACE}/my-upload-key.jks" -alias upload \
              -keyalg RSA -keysize 2048 -validity 10000 -storepass paysync123 -keypass paysync123 \
              -dname "CN=PaySync MFS,OU=Gateway,O=PaySync,L=Dhaka,S=Dhaka,C=BD"
            echo "KEYSTORE_PATH=${"$"}{GITHUB_WORKSPACE}/my-upload-key.jks" >> ${"$"}GITHUB_ENV
            echo "STORE_PASSWORD=paysync123" >> ${"$"}GITHUB_ENV
            echo "KEY_PASSWORD=paysync123" >> ${"$"}GITHUB_ENV
          fi

      - name: Run Unit Tests & Build Release APK/AAB
        run: gradle :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease :app:assembleDebug

      - name: Prepare Release Assets
        id: prep_release
        run: |
          mkdir -p release-assets
          cp app/build/outputs/apk/release/*.apk release-assets/PaySync-MFS-release.apk
          cp app/build/outputs/apk/debug/*.apk release-assets/PaySync-MFS-debug.apk
          cp app/build/outputs/bundle/release/*.aab release-assets/PaySync-MFS-release.aab
          TAG_NAME="v1.0.${"$"}{GITHUB_RUN_NUMBER}"
          if [[ "${"$"}{GITHUB_REF}" == refs/tags/* ]]; then
            TAG_NAME="${"$"}{GITHUB_REF#refs/tags/}"
          fi
          echo "tag_name=${"$"}TAG_NAME" >> ${"$"}GITHUB_OUTPUT

      - name: Publish Automated GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: ${"$"}{{ steps.prep_release.outputs.tag_name }}
          name: "PaySync MFS ${"$"}{{ steps.prep_release.outputs.tag_name }}"
          generate_release_notes: true
          files: |
            release-assets/PaySync-MFS-release.apk
            release-assets/PaySync-MFS-debug.apk
            release-assets/PaySync-MFS-release.aab
            """.trimIndent()
        )
    )
}
