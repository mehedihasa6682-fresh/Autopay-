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
            id = "php_website_webhook",
            title = "১. যেকোনো ওয়েবসাইটের কোড (PHP / WordPress / cPanel)",
            subtitle = "কাস্টমার সাইটে শুধু সেন্ডার নাম্বার দেবে এবং অ্যাপ থেকে Webhook এসে সেন্ডার নাম্বার দেখে অটো ভেরিফাই করবে",
            language = "php",
            fileName = "mfs-webhook.php",
            code = """
<?php
/**
 * ফাইল: mfs-webhook.php
 * আপনার ওয়েবসাইটের সার্ভারে এই ফাইলটি রাখুন এবং এর লিংকটি অ্যাপের "ওয়েবসাইট API" ট্যাবে বসান।
 * যেমন: https://yourwebsite.com/api/mfs-webhook.php
 */
header("Content-Type: application/json");

${"$"}SECRET_KEY = "whsec_live_bd_mfs_89a7c4e21f09"; // অ্যাপের Secret Key-এর সাথে মিল রাখুন
${"$"}DB_FILE = __DIR__ . "/payments_db.json";

if (!file_exists(${"$"}DB_FILE)) {
    file_put_contents(${"$"}DB_FILE, json_encode(["orders" => [], "sms_inbox" => []]));
}
${"$"}db = json_decode(file_get_contents(${"$"}DB_FILE), true);

// ১. অ্যান্ড্রয়েড অ্যাপ থেকে যখন অটো SMS Webhook আসবে:
${"$"}rawInput = file_get_contents("php://input");
${"$"}data = json_decode(${"$"}rawInput, true);

if (${"$"}_SERVER["REQUEST_METHOD"] === "POST" && isset(${"$"}data["sender_number"])) {
    if ((${"$"}data["secret_key"] ?? "") !== ${"$"}SECRET_KEY) {
        http_response_code(401);
        echo json_encode(["status" => "ERROR", "message" => "Invalid Secret Key"]);
        exit;
    }

    ${"$"}sender = preg_replace('/^\+?88/', '', trim(${"$"}data["sender_number"]));
    ${"$"}amount = floatval(${"$"}data["amount"]);
    ${"$"}trxId  = strtoupper(trim(${"$"}data["trx_id"]));
    ${"$"}provider = ${"$"}data["mfs_provider"];

    // চেক করুন এই সেন্ডার নাম্বার দিয়ে কোনো পেন্ডিং অর্ডার আছে কিনা:
    ${"$"}matchedOrderId = null;
    foreach (${"$"}db["orders"] as &${"$"}order) {
        if (${"$"}order["status"] === "PENDING" &&
            ${"$"}order["sender_number"] === ${"$"}sender &&
            ${"$"}amount >= floatval(${"$"}order["amount"])) {
            ${"$"}order["status"] = "VERIFIED";
            ${"$"}order["trx_id"] = ${"$"}trxId;
            ${"$"}order["verified_at"] = date("Y-m-d H:i:s");
            ${"$"}matchedOrderId = ${"$"}order["order_id"];
            break;
        }
    }

    // যদি কাস্টমার আগে টাকা পাঠায় এবং পরে সাইটে নাম্বার দেয়, তার জন্য সেভ করে রাখুন:
    ${"$"}db["sms_inbox"][${"$"}trxId] = [
        "trx_id" => ${"$"}trxId,
        "sender_number" => ${"$"}sender,
        "amount" => ${"$"}amount,
        "mfs_provider" => ${"$"}provider,
        "claimed_by" => ${"$"}matchedOrderId,
        "time" => time()
    ];

    file_put_contents(${"$"}DB_FILE, json_encode(${"$"}db, JSON_PRETTY_PRINT));

    echo json_encode([
        "status" => "SUCCESS",
        "verified_order_id" => ${"$"}matchedOrderId,
        "sender_number" => ${"$"}sender,
        "trx_id" => ${"$"}trxId
    ]);
    exit;
}

// ২. ওয়েবসাইটে কাস্টমার যখন শুধু সেন্ডার নাম্বার দিয়ে সাবমিট করবে (GET/POST check):
if (isset(${"$"}_POST["customer_sender_number"])) {
    ${"$"}sender = preg_replace('/^\+?88/', '', trim(${"$"}_POST["customer_sender_number"]));
    ${"$"}amount = floatval(${"$"}_POST["amount"] ?? 500);
    ${"$"}orderId = "ORD-" . rand(100000, 999999);
    ${"$"}status = "PENDING";
    ${"$"}matchedTrx = null;

    // চেক করুন ওই সেন্ডার নাম্বার থেকে টাকা ইতিমধ্যে চলে এসেছে কিনা:
    foreach (${"$"}db["sms_inbox"] as ${"$"}trx => &${"$"}sms) {
        if (${"$"}sms["claimed_by"] === null &&
            ${"$"}sms["sender_number"] === ${"$"}sender &&
            ${"$"}sms["amount"] >= ${"$"}amount) {
            ${"$"}status = "VERIFIED";
            ${"$"}matchedTrx = ${"$"}trx;
            ${"$"}sms["claimed_by"] = ${"$"}orderId;
            break;
        }
    }

    ${"$"}db["orders"][] = [
        "order_id" => ${"$"}orderId,
        "sender_number" => ${"$"}sender,
        "amount" => ${"$"}amount,
        "status" => ${"$"}status,
        "trx_id" => ${"$"}matchedTrx
    ];
    file_put_contents(${"$"}DB_FILE, json_encode(${"$"}db, JSON_PRETTY_PRINT));

    echo json_encode([
        "order_id" => ${"$"}orderId,
        "sender_number" => ${"$"}sender,
        "status" => ${"$"}status,
        "trx_id" => ${"$"}matchedTrx
    ]);
    exit;
}
?>
            """.trimIndent()
        ),
        BlueprintSection(
            id = "firebase_cloud_functions",
            title = "২. Firebase Cloud Functions ও Realtime DB কোড",
            subtitle = "ফায়ারবেজ ব্যাকএন্ডের মাধ্যমে সেন্ডার নাম্বার দেখে অটো ভেরিফিকেশন",
            language = "javascript",
            fileName = "functions/index.js",
            code = """
const { onRequest } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const express = require("express");

admin.initializeApp();
const db = admin.database();
const app = express();
app.use(express.json());

const WEBHOOK_SECRET = process.env.MFS_WEBHOOK_SECRET || "whsec_live_bd_mfs_89a7c4e21f09";

/**
 * ১. ওয়েবসাইট থেকে কাস্টমার শুধু নিজের সেন্ডার নাম্বার (senderPhone) দিয়ে সেশন শুরু করবে
 */
app.post("/api/v1/payment-sessions/initiate", async (req, res) => {
  const { customerName, customerPhone, baseAmount, mfsProvider } = req.body;
  const cleanPhone = String(customerPhone || "").replace(/^\+?88/, "").trim();
  const amount = Number(baseAmount || 500);
  const now = Date.now();
  const orderId = `ORD-${"$"}{Math.floor(100000 + Math.random() * 900000)}`;

  const orderData = {
    order_id: orderId,
    customer_name: customerName || "Customer",
    sender_number: cleanPhone,
    amount: amount,
    mfs_provider: mfsProvider || "bKash",
    status: "PENDING",
    trx_id: null,
    created_at: now
  };

  await db.ref(`orders/${"$"}{orderId}`).set(orderData);
  return res.status(201).json({ success: true, order: orderData });
});

/**
 * ২. অ্যান্ড্রয়েড অ্যাপ থেকে SMS আসলে সেন্ডার নাম্বার (sender_number) মিলিয়ে অটো ভেরিফাই করবে
 */
app.post("/api/v1/webhooks/mfs-sms", async (req, res) => {
  const { trx_id, sender_number, amount, mfs_provider, secret_key } = req.body;
  if (secret_key !== WEBHOOK_SECRET) {
    return res.status(401).json({ error: "Unauthorized Secret Key" });
  }

  const cleanPhone = String(sender_number || "").replace(/^\+?88/, "").trim();
  const numericAmount = Number(amount);

  // পেন্ডিং অর্ডারে সেন্ডার নাম্বার খুঁজুন
  const snap = await db.ref("orders")
    .orderByChild("sender_number")
    .equalTo(cleanPhone)
    .once("value");

  let matchedOrder = null;
  snap.forEach((child) => {
    const ord = child.val();
    if (ord.status === "PENDING" && numericAmount >= ord.amount) {
      matchedOrder = ord;
    }
  });

  if (matchedOrder) {
    await db.ref(`orders/${"$"}{matchedOrder.order_id}`).update({
      status: "VERIFIED",
      trx_id: trx_id,
      verified_at: Date.now()
    });
    return res.status(200).json({
      status: "VERIFIED",
      order_id: matchedOrder.order_id,
      sender_number: cleanPhone,
      trx_id: trx_id
    });
  }

  // আগে টাকা পাঠিয়ে থাকলে unmatched_transactions-এ রাখুন
  await db.ref(`unmatched_transactions/${"$"}{trx_id}`).set({
    trx_id,
    sender_number: cleanPhone,
    amount: numericAmount,
    mfs_provider,
    status: "UNCLAIMED",
    received_at: Date.now()
  });

  return res.status(202).json({ status: "SAVED_BY_SENDER_NUMBER", sender_number: cleanPhone });
});

exports.mfsPaymentGateway = onRequest({ region: "asia-southeast1" }, app);
            """.trimIndent()
        ),
        BlueprintSection(
            id = "github_actions_auto_release",
            title = "৩. GitHub Actions অটো রিলিজ (.github/workflows/android-release.yml)",
            subtitle = "JDK 21 ও Gradle 9.3.1 সহ অটোমেটিক APK/AAB বিল্ড এবং GitHub Release পাবলিশ",
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

      - name: Run Unit Tests & Build Release APK/AAB
        run: gradle :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease :app:assembleDebug
            """.trimIndent()
        )
    )
}
