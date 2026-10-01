package com.example.domain

import android.content.Context
import android.provider.Telephony
import com.example.data.local.AppDatabase
import com.example.data.local.OrderEntity
import com.example.data.local.PaymentSessionEntity
import com.example.data.local.ProcessedTrxEntity
import com.example.data.local.UnmatchedTransactionEntity
import com.example.data.local.UserEntity
import com.example.data.local.WebhookLogEntity
import com.example.data.preferences.DynamicAmountMode
import com.example.data.preferences.GatewayPreferences
import com.example.network.MfsWebhookPayload
import com.example.network.WebhookNetworkClient
import com.example.parser.MfsSmsParser
import com.example.parser.ParsedMfsSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

sealed class VerificationOutcome {
    data class Verified(
        val orderId: String,
        val sessionId: String,
        val trxId: String,
        val amount: Double,
        val mfsProvider: String,
        val customerName: String
    ) : VerificationOutcome()

    data class QueuedUnmatched(
        val trxId: String,
        val amount: Double,
        val mfsProvider: String,
        val reason: String
    ) : VerificationOutcome()

    data class DuplicateBlocked(
        val trxId: String,
        val originalOutcome: String
    ) : VerificationOutcome()

    data class IgnoredNonMfs(val reason: String) : VerificationOutcome()
}

sealed class ManualClaimResult {
    data class Success(
        val orderId: String,
        val trxId: String,
        val amount: Double,
        val mfsProvider: String
    ) : ManualClaimResult()

    data class Error(val message: String) : ManualClaimResult()
}

class PaymentVerificationRepository private constructor(
    private val context: Context
) {
    private val db = AppDatabase.getInstance(context)
    private val dao = db.gatewayDao()
    val preferences = GatewayPreferences(context)

    // Mutexes guarantee zero race conditions across 20+ simultaneous sessions & incoming SMS threads
    private val sessionAllocationMutex = Mutex()
    private val verificationMutex = Mutex()

    val usersFlow: Flow<List<UserEntity>> = dao.observeUsers()
    val ordersFlow: Flow<List<OrderEntity>> = dao.observeOrders()
    val sessionsFlow: Flow<List<PaymentSessionEntity>> = dao.observePaymentSessions()
    val unmatchedFlow: Flow<List<UnmatchedTransactionEntity>> = dao.observeUnmatchedTransactions()
    val webhookLogsFlow: Flow<List<WebhookLogEntity>> = dao.observeWebhookLogs()

    /**
     * Initiates a concurrency-safe payment session with a unique dynamic locked amount
     * (e.g., Base ৳500 -> ৳501.00, ৳502.00 or ৳500.01, ৳500.02) and a 5-minute countdown window.
     */
    suspend fun initiatePaymentSession(
        customerName: String,
        customerPhone: String,
        baseAmount: Double,
        mfsProvider: String
    ): PaymentSessionEntity = sessionAllocationMutex.withLock {
        val now = System.currentTimeMillis()
        expireStaleSessionsInternal(now)

        val config = preferences.configFlow.first()
        val ttlMillis = config.sessionTtlMinutes * 60_000L
        val activeSessions = dao.getActiveSessionsForProvider(mfsProvider, now)
        val lockedAmountsSet = activeSessions.map { (it.lockedAmount * 100.0).roundToInt() }.toSet()

        // Find the lowest available unique dynamic amount slot for this baseAmount
        val lockedAmount = allocateUniqueAmount(
            baseAmount = baseAmount,
            mode = config.dynamicAmountMode,
            takenCents = lockedAmountsSet
        )

        // Ensure user exists in `users` table
        val normalizedPhone = normalizeBdPhone(customerPhone)
        val existingUser = dao.getUserByPhone(normalizedPhone)
        val userId = existingUser?.userId ?: "USR-${UUID.randomUUID().toString().take(6).uppercase(Locale.US)}"
        if (existingUser == null) {
            dao.upsertUser(
                UserEntity(
                    userId = userId,
                    name = customerName.ifBlank { "Customer ${normalizedPhone.takeLast(4)}" },
                    phone = normalizedPhone,
                    email = "${normalizedPhone}@customer.paysync.bd",
                    createdAt = now
                )
            )
        }

        val orderId = "ORD-${(100000..999999).random()}"
        val sessionId = "SES-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}"
        val expiresAt = now + ttlMillis

        val session = PaymentSessionEntity(
            sessionId = sessionId,
            orderId = orderId,
            userId = userId,
            customerName = customerName.ifBlank { "Customer ${normalizedPhone.takeLast(4)}" },
            customerPhone = normalizedPhone,
            mfsProvider = mfsProvider,
            baseAmount = baseAmount,
            lockedAmount = lockedAmount,
            status = "ACTIVE",
            expiresAt = expiresAt,
            createdAt = now
        )

        val order = OrderEntity(
            orderId = orderId,
            userId = userId,
            customerName = session.customerName,
            customerPhone = normalizedPhone,
            baseAmount = baseAmount,
            payableAmount = lockedAmount,
            mfsProvider = mfsProvider,
            status = "PENDING",
            sessionId = sessionId,
            createdAt = now
        )

        dao.insertPaymentSession(session)
        dao.insertOrder(order)
        return session
    }

    private fun allocateUniqueAmount(
        baseAmount: Double,
        mode: DynamicAmountMode,
        takenCents: Set<Int>
    ): Double {
        val baseCents = (baseAmount * 100.0).roundToInt()
        for (step in 1..250) {
            val deltaCents = when (mode) {
                DynamicAmountMode.INCREMENTAL_TAKA -> step * 100 // +৳1.00, +৳2.00, +৳3.00...
                DynamicAmountMode.FRACTIONAL_PAISA -> step // +৳0.01, +৳0.02, +৳0.03...
            }
            val candidateCents = baseCents + deltaCents
            if (!takenCents.contains(candidateCents)) {
                return candidateCents / 100.0
            }
        }
        return (baseCents + (251..999).random()) / 100.0
    }

    /**
     * Stress-tests concurrency by spawning N simultaneous users requesting the same base amount
     * at the exact same moment.
     */
    suspend fun spawnConcurrentSessionsBurst(
        count: Int = 20,
        baseAmount: Double = 500.0,
        mfsProvider: String = "bKash"
    ): List<PaymentSessionEntity> = coroutineScope {
        val bangladeshiNames = listOf(
            "Tanvir Ahmed", "Nusrat Jahan", "Mehedi Hasan", "Farhana Akter", "Rakibul Islam",
            "Sadia Afrin", "Mahmudul Hasan", "Tasnim Zara", "Ashraful Alam", "Mim Chowdhury",
            "Sabbir Hossain", "Sumaiya Rahman", "Nayeem Siddiqui", "Jannatul Ferdous", "Arifur Rahman",
            "Tahmina Sultana", "Shakil Mahmud", "Rifat Hossain", "Rubaiya Yasmin", "Imran Kabir",
            "Fahim Shahriar", "Nazia Hassan", "Zahidul Islam", "Lamia Karim", "Kamrul Hasan"
        )
        (0 until count).map { idx ->
            async(Dispatchers.IO) {
                val name = bangladeshiNames[idx % bangladeshiNames.size]
                val phone = String.format(Locale.US, "01711%06d", 200100 + idx)
                initiatePaymentSession(
                    customerName = name,
                    customerPhone = phone,
                    baseAmount = baseAmount,
                    mfsProvider = mfsProvider
                )
            }
        }.awaitAll()
    }

    /**
     * Sweeps and marks any expired 5-minute sessions as EXPIRED.
     */
    suspend fun sweepExpiredSessions() = sessionAllocationMutex.withLock {
        expireStaleSessionsInternal(System.currentTimeMillis())
    }

    private suspend fun expireStaleSessionsInternal(now: Long) {
        val expired = dao.getExpiredActiveSessions(now)
        if (expired.isNotEmpty()) {
            val ids = expired.map { it.sessionId }
            dao.markSessionsExpired(ids)
            dao.markOrdersExpiredBySessions(ids)
        }
    }

    /**
     * Core Verification Engine:
     * 1. Parses incoming SMS / Notification using Regex
     * 2. Enforces anti-double-spend (duplicate TrxID check)
     * 3. Matches against active Payment Sessions (Amount + Provider + Sender priority)
     * 4. Handles expired sessions & unknown payments via the Unmatched Queue
     * 5. Dispatches JSON payload to Webhook URL via Retrofit
     */
    suspend fun processIncomingSmsOrNotification(
        senderAddress: String?,
        rawMessageBody: String,
        timestamp: Long = System.currentTimeMillis()
    ): VerificationOutcome = verificationMutex.withLock {
        val parsed: ParsedMfsSms = MfsSmsParser.parse(senderAddress, rawMessageBody, timestamp)
            ?: return@withLock VerificationOutcome.IgnoredNonMfs("Message is not a valid bKash/Nagad incoming payment SMS")

        // 1. Check Duplicate TrxID (Zero Double-Spending Guarantee)
        val existingProcessed = dao.getProcessedTrx(parsed.trxId)
        if (existingProcessed != null) {
            return@withLock VerificationOutcome.DuplicateBlocked(
                trxId = parsed.trxId,
                originalOutcome = existingProcessed.outcome
            )
        }

        val now = System.currentTimeMillis()
        val candidateSessions = dao.findSessionsByProviderAndAmount(parsed.mfsProvider, parsed.amount)

        // Separate into currently ACTIVE (non-expired) vs EXPIRED sessions
        val activeMatches = candidateSessions.filter { it.status == "ACTIVE" && it.expiresAt > now }
        val expiredMatches = candidateSessions.filter {
            it.status == "EXPIRED" || (it.status == "ACTIVE" && it.expiresAt <= now)
        }

        // Sweep any newly expired sessions
        expireStaleSessionsInternal(now)

        // Prioritize active session that also matches sender phone, otherwise match by unique dynamic lockedAmount
        val normalizedSender = normalizeBdPhone(parsed.senderNumber)
        val matchedSession = activeMatches.firstOrNull {
            normalizeBdPhone(it.customerPhone) == normalizedSender
        } ?: activeMatches.firstOrNull()

        val config = preferences.configFlow.first()

        if (matchedSession != null) {
            // AUTO-VERIFIED!
            dao.markSessionMatched(matchedSession.sessionId, parsed.trxId)
            dao.updateOrderVerified(
                orderId = matchedSession.orderId,
                status = "VERIFIED",
                trxId = parsed.trxId,
                verifiedAt = now
            )
            dao.incrementUserStats(matchedSession.userId, parsed.amount)
            dao.insertProcessedTrx(
                ProcessedTrxEntity(
                    trxId = parsed.trxId,
                    mfsProvider = parsed.mfsProvider,
                    amount = parsed.amount,
                    senderNumber = normalizedSender,
                    outcome = "AUTO_VERIFIED",
                    orderId = matchedSession.orderId,
                    processedAt = now
                )
            )

            // Dispatch Webhook via Retrofit
            dispatchAndLogWebhook(
                config = config,
                parsed = parsed,
                matchedOrderId = matchedSession.orderId,
                verificationStatus = "VERIFIED"
            )

            return@withLock VerificationOutcome.Verified(
                orderId = matchedSession.orderId,
                sessionId = matchedSession.sessionId,
                trxId = parsed.trxId,
                amount = parsed.amount,
                mfsProvider = parsed.mfsProvider,
                customerName = matchedSession.customerName
            )
        } else {
            // Route to Unmatched Transactions Queue (for Manual TrxID Claim)
            val reason = if (expiredMatches.isNotEmpty()) {
                "SESSION_EXPIRED (Arrived after 5m window)"
            } else {
                "NO_ACTIVE_SESSION (Dynamic amount not locked)"
            }

            dao.insertUnmatchedTransaction(
                UnmatchedTransactionEntity(
                    trxId = parsed.trxId,
                    senderNumber = normalizedSender,
                    amount = parsed.amount,
                    mfsProvider = parsed.mfsProvider,
                    rawSms = parsed.rawMessage,
                    reason = reason,
                    status = "UNCLAIMED",
                    receivedAt = now
                )
            )
            dao.insertProcessedTrx(
                ProcessedTrxEntity(
                    trxId = parsed.trxId,
                    mfsProvider = parsed.mfsProvider,
                    amount = parsed.amount,
                    senderNumber = normalizedSender,
                    outcome = "QUEUED_UNMATCHED",
                    orderId = null,
                    processedAt = now
                )
            )

            dispatchAndLogWebhook(
                config = config,
                parsed = parsed,
                matchedOrderId = null,
                verificationStatus = "UNMATCHED_QUEUED"
            )

            return@withLock VerificationOutcome.QueuedUnmatched(
                trxId = parsed.trxId,
                amount = parsed.amount,
                mfsProvider = parsed.mfsProvider,
                reason = reason
            )
        }
    }

    /**
     * Manual TrxID Claim Queue Verification:
     * Allows a user whose session expired or who sent the wrong fractional amount
     * to claim an UNCLAIMED transaction by submitting Order ID + TrxID.
     */
    suspend fun claimUnmatchedTransaction(
        orderIdInput: String,
        trxIdInput: String,
        senderPhoneInput: String? = null
    ): ManualClaimResult = verificationMutex.withLock {
        val cleanOrderId = orderIdInput.trim().uppercase(Locale.US)
        val cleanTrxId = trxIdInput.trim().uppercase(Locale.US)

        if (cleanOrderId.isEmpty() || cleanTrxId.isEmpty()) {
            return@withLock ManualClaimResult.Error("Both Order ID and TrxID are required.")
        }

        val order = dao.getOrderById(cleanOrderId)
            ?: return@withLock ManualClaimResult.Error("Order '$cleanOrderId' was not found.")

        if (order.status == "VERIFIED") {
            return@withLock ManualClaimResult.Error("Order '$cleanOrderId' is already verified (TrxID: ${order.trxId}).")
        }

        val unmatched = dao.getUnmatchedByTrxId(cleanTrxId)
            ?: return@withLock ManualClaimResult.Error("TrxID '$cleanTrxId' was not found in the Unmatched Queue.")

        if (unmatched.status != "UNCLAIMED") {
            return@withLock ManualClaimResult.Error("TrxID '$cleanTrxId' has already been claimed by Order ${unmatched.claimedByOrderId}.")
        }

        if (!unmatched.mfsProvider.equals(order.mfsProvider, ignoreCase = true)) {
            return@withLock ManualClaimResult.Error("Provider mismatch: Order expects ${order.mfsProvider}, but TrxID is from ${unmatched.mfsProvider}.")
        }

        if (unmatched.amount + 0.001 < order.baseAmount) {
            return@withLock ManualClaimResult.Error(
                "Insufficient amount: TrxID has ৳${String.format(Locale.US, "%.2f", unmatched.amount)}, but order base amount is ৳${String.format(Locale.US, "%.2f", order.baseAmount)}."
            )
        }

        if (!senderPhoneInput.isNullOrBlank()) {
            val normInput = normalizeBdPhone(senderPhoneInput)
            val normTx = normalizeBdPhone(unmatched.senderNumber)
            if (normTx != "UNKNOWN" && !normTx.contains("*")) {
                if (normInput != normTx) {
                    return@withLock ManualClaimResult.Error("Sender number ($normInput) does not match SMS sender ($normTx).")
                }
            }
        }

        val now = System.currentTimeMillis()
        dao.markUnmatchedClaimed(cleanTrxId, order.orderId)
        dao.updateOrderVerified(
            orderId = order.orderId,
            status = "VERIFIED",
            trxId = cleanTrxId,
            verifiedAt = now
        )
        dao.markSessionMatched(order.sessionId, cleanTrxId)
        dao.incrementUserStats(order.userId, unmatched.amount)
        dao.updateProcessedTrxOutcome(cleanTrxId, "MANUAL_CLAIMED", order.orderId)

        return@withLock ManualClaimResult.Success(
            orderId = order.orderId,
            trxId = cleanTrxId,
            amount = unmatched.amount,
            mfsProvider = unmatched.mfsProvider
        )
    }

    private suspend fun dispatchAndLogWebhook(
        config: com.example.data.preferences.GatewayConfig,
        parsed: ParsedMfsSms,
        matchedOrderId: String?,
        verificationStatus: String
    ) {
        val payload = MfsWebhookPayload(
            trxId = parsed.trxId,
            senderNumber = parsed.senderNumber,
            amount = parsed.amount,
            mfsProvider = parsed.mfsProvider,
            secretKey = config.secretKey,
            transactionType = parsed.transactionType,
            matchedOrderId = matchedOrderId,
            verificationStatus = verificationStatus,
            timestamp = parsed.timestamp
        )

        val result = WebhookNetworkClient.dispatchWebhook(
            webhookUrl = config.webhookUrl,
            firebaseDbUrl = config.firebaseRealtimeDbUrl,
            syncToFirebaseRest = config.syncToFirebaseRest,
            payload = payload
        )

        dao.insertWebhookLog(
            WebhookLogEntity(
                trxId = parsed.trxId,
                senderNumber = parsed.senderNumber,
                amount = parsed.amount,
                mfsProvider = parsed.mfsProvider,
                targetUrl = config.webhookUrl,
                payloadJson = result.payloadJson,
                httpStatus = result.httpStatus,
                responseSummary = result.responseSummary,
                isSuccess = result.isSuccess,
                latencyMs = result.latencyMs,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Reads real SMS inbox messages from the Android device (when READ_SMS is granted)
     * and processes any bKash or Nagad payment messages.
     */
    suspend fun scanDeviceSmsInbox(): Int = withContext(Dispatchers.IO) {
        var processedCount = 0
        runCatching {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 40"
            )
            cursor?.use { c ->
                val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    val address = if (addrIdx >= 0) c.getString(addrIdx) else null
                    val body = if (bodyIdx >= 0) c.getString(bodyIdx) else continue
                    val date = if (dateIdx >= 0) c.getLong(dateIdx) else System.currentTimeMillis()

                    if (MfsSmsParser.detectMfsProvider(address, body) != null) {
                        val outcome = processIncomingSmsOrNotification(address, body, date)
                        if (outcome is VerificationOutcome.Verified || outcome is VerificationOutcome.QueuedUnmatched) {
                            processedCount++
                        }
                    }
                }
            }
        }
        processedCount
    }

    /**
     * Seeds initial realistic demo data on first launch so the merchant dashboard,
     * active sessions, verified orders, and unmatched queue are immediately demonstrable.
     */
    suspend fun ensureSeededDemoData() {
        val currentSessions = dao.observePaymentSessions().first()
        if (currentSessions.isNotEmpty()) return

        // Create 3 active payment sessions (Base ৳500 -> ৳501, ৳502 on bKash and ৳1001 on Nagad)
        val s1 = initiatePaymentSession(
            customerName = "Tanvir Ahmed",
            customerPhone = "01712345678",
            baseAmount = 500.0,
            mfsProvider = "bKash"
        )
        val s2 = initiatePaymentSession(
            customerName = "Nusrat Jahan",
            customerPhone = "01819876543",
            baseAmount = 500.0,
            mfsProvider = "bKash"
        )
        initiatePaymentSession(
            customerName = "Mehedi Hasan",
            customerPhone = "01911223344",
            baseAmount = 1000.0,
            mfsProvider = "Nagad"
        )

        // Automatically verify s1 with a realistic bKash SMS so Verified Orders & Webhook Logs are populated
        val sampleSms1 = MfsSmsParser.buildSampleSms(
            provider = "bKash",
            amount = s1.lockedAmount,
            senderNumber = s1.customerPhone,
            trxId = "BKA98X72KL",
            txType = "Send Money"
        )
        processIncomingSmsOrNotification("bKash", sampleSms1)

        // Inject 1 Unmatched Nagad SMS (e.g. customer sent ৳500.00 flat instead of dynamic amount, or session expired)
        val unmatchedSms = MfsSmsParser.buildSampleSms(
            provider = "bKash",
            amount = 500.00,
            senderNumber = s2.customerPhone,
            trxId = "BKM77Q41ZP",
            txType = "Send Money"
        )
        processIncomingSmsOrNotification("bKash", unmatchedSms)
    }

    suspend fun clearWebhookLogs() {
        dao.clearWebhookLogs()
    }

    private fun normalizeBdPhone(phone: String): String {
        val trimmed = phone.trim().replace(" ", "").replace("-", "")
        return when {
            trimmed.startsWith("+880") -> trimmed.removePrefix("+88")
            trimmed.startsWith("880") && trimmed.length == 13 -> trimmed.removePrefix("88")
            else -> trimmed
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PaymentVerificationRepository? = null

        fun getInstance(context: Context): PaymentVerificationRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = PaymentVerificationRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
