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
import com.example.network.WebhookDispatchResult
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
import kotlin.math.abs
import kotlin.math.roundToInt

sealed class VerificationOutcome {
    data class Verified(
        val orderId: String,
        val sessionId: String,
        val trxId: String,
        val senderNumber: String,
        val amount: Double,
        val mfsProvider: String,
        val customerName: String
    ) : VerificationOutcome()

    data class QueuedUnmatched(
        val trxId: String,
        val senderNumber: String,
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
        val senderNumber: String,
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

    private val sessionAllocationMutex = Mutex()
    private val verificationMutex = Mutex()

    val usersFlow: Flow<List<UserEntity>> = dao.observeUsers()
    val ordersFlow: Flow<List<OrderEntity>> = dao.observeOrders()
    val sessionsFlow: Flow<List<PaymentSessionEntity>> = dao.observePaymentSessions()
    val unmatchedFlow: Flow<List<UnmatchedTransactionEntity>> = dao.observeUnmatchedTransactions()
    val webhookLogsFlow: Flow<List<WebhookLogEntity>> = dao.observeWebhookLogs()

    /**
     * Checks if two Bangladeshi mobile numbers match (supports +880 prefix and masked digits like 01712***678).
     */
    fun isSenderNumberMatch(expectedPhone: String, smsSenderPhone: String): Boolean {
        val normExpected = normalizeBdPhone(expectedPhone)
        val normSms = normalizeBdPhone(smsSenderPhone)
        if (normExpected.isEmpty() || normSms.isEmpty() || normSms == "UNKNOWN") return false

        if (normExpected == normSms) return true

        // Support masked SMS numbers from bKash/Nagad (e.g. "01712***678" or "017*****678")
        if (normSms.contains("*")) {
            val prefix = normSms.substringBefore("*")
            val suffix = normSms.substringAfterLast("*")
            if (prefix.length >= 3 && suffix.length >= 2) {
                return normExpected.startsWith(prefix) && normExpected.endsWith(suffix)
            }
        }
        return false
    }

    /**
     * Registers a customer's Sender Number session.
     * - By default (SENDER_NUMBER_ONLY mode), keeps exact baseAmount (e.g. ৳500 -> ৳500) and verifies by Sender Number!
     * - If the customer ALREADY sent the SMS from that Sender Number before submitting on the site,
     *   auto-matches it immediately from the Unclaimed Queue!
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

        val lockedAmount = allocateUniqueAmount(
            baseAmount = baseAmount,
            mode = config.dynamicAmountMode,
            takenCents = lockedAmountsSet
        )

        val normalizedPhone = normalizeBdPhone(customerPhone)
        val existingUser = dao.getUserByPhone(normalizedPhone)
        val userId = existingUser?.userId ?: "USR-${UUID.randomUUID().toString().take(6).uppercase(Locale.US)}"
        val resolvedName = customerName.ifBlank { "কাস্টমার (${normalizedPhone.takeLast(4)})" }

        if (existingUser == null) {
            dao.upsertUser(
                UserEntity(
                    userId = userId,
                    name = resolvedName,
                    phone = normalizedPhone,
                    email = "${normalizedPhone}@customer.bd",
                    createdAt = now
                )
            )
        }

        val orderId = "ORD-${(100000..999999).random()}"
        val sessionId = "SES-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}"
        val expiresAt = now + ttlMillis

        // Check if an UNCLAIMED SMS from this Sender Number already arrived earlier!
        val unclaimedMatch = dao.getUnclaimedTransactions().firstOrNull { tx ->
            tx.mfsProvider.equals(mfsProvider, ignoreCase = true) &&
                isSenderNumberMatch(normalizedPhone, tx.senderNumber) &&
                tx.amount + 0.001 >= baseAmount
        }

        val initialSessionStatus = if (unclaimedMatch != null) "MATCHED" else "ACTIVE"
        val initialOrderStatus = if (unclaimedMatch != null) "VERIFIED" else "PENDING"
        val matchedTrx = unclaimedMatch?.trxId

        val session = PaymentSessionEntity(
            sessionId = sessionId,
            orderId = orderId,
            userId = userId,
            customerName = resolvedName,
            customerPhone = normalizedPhone,
            mfsProvider = mfsProvider,
            baseAmount = baseAmount,
            lockedAmount = if (unclaimedMatch != null) unclaimedMatch.amount else lockedAmount,
            status = initialSessionStatus,
            matchedTrxId = matchedTrx,
            expiresAt = expiresAt,
            createdAt = now
        )

        val order = OrderEntity(
            orderId = orderId,
            userId = userId,
            customerName = resolvedName,
            customerPhone = normalizedPhone,
            baseAmount = baseAmount,
            payableAmount = session.lockedAmount,
            mfsProvider = mfsProvider,
            status = initialOrderStatus,
            trxId = matchedTrx,
            sessionId = sessionId,
            verifiedAt = if (unclaimedMatch != null) now else null,
            createdAt = now
        )

        dao.insertPaymentSession(session)
        dao.insertOrder(order)

        if (unclaimedMatch != null) {
            dao.markUnmatchedClaimed(unclaimedMatch.trxId, orderId)
            dao.incrementUserStats(userId, unclaimedMatch.amount)
            dao.updateProcessedTrxOutcome(unclaimedMatch.trxId, "AUTO_VERIFIED_BY_SENDER", orderId)
        }

        return session
    }

    private fun allocateUniqueAmount(
        baseAmount: Double,
        mode: DynamicAmountMode,
        takenCents: Set<Int>
    ): Double {
        if (mode == DynamicAmountMode.SENDER_NUMBER_ONLY) {
            return baseAmount
        }
        val baseCents = (baseAmount * 100.0).roundToInt()
        for (step in 1..250) {
            val deltaCents = when (mode) {
                DynamicAmountMode.INCREMENTAL_TAKA -> step * 100
                DynamicAmountMode.FRACTIONAL_PAISA -> step
                DynamicAmountMode.SENDER_NUMBER_ONLY -> 0
            }
            val candidateCents = baseCents + deltaCents
            if (!takenCents.contains(candidateCents)) {
                return candidateCents / 100.0
            }
        }
        return baseAmount
    }

    suspend fun spawnConcurrentSessionsBurst(
        count: Int = 10,
        baseAmount: Double = 500.0,
        mfsProvider: String = "bKash"
    ): List<PaymentSessionEntity> = coroutineScope {
        val bangladeshiNames = listOf(
            "তানভীর আহমেদ", "নুসরাত জাহান", "মেহেদী হাসান", "ফারহানা আক্তার", "রাকিবুল ইসলাম",
            "সাদিয়া আফরিন", "মাহমুদুল হাসান", "তাসনিম জারা", "আশরাফুল আলম", "সাব্বির হোসেন"
        )
        (0 until count).map { idx ->
            async(Dispatchers.IO) {
                val name = bangladeshiNames[idx % bangladeshiNames.size]
                val phone = String.format(Locale.US, "01711%06d", 300100 + idx)
                initiatePaymentSession(
                    customerName = name,
                    customerPhone = phone,
                    baseAmount = baseAmount,
                    mfsProvider = mfsProvider
                )
            }
        }.awaitAll()
    }

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
     * Primarily matches by SENDER MOBILE NUMBER (customerPhone == sms.senderNumber) + Amount!
     * Also sends the parsed SMS JSON to the Website Webhook URL so any external site can verify by sender_number.
     */
    suspend fun processIncomingSmsOrNotification(
        senderAddress: String?,
        rawMessageBody: String,
        timestamp: Long = System.currentTimeMillis()
    ): VerificationOutcome = verificationMutex.withLock {
        val parsed: ParsedMfsSms = MfsSmsParser.parse(senderAddress, rawMessageBody, timestamp)
            ?: return@withLock VerificationOutcome.IgnoredNonMfs("এটি কোনো bKash বা Nagad পেমেন্ট এসএমএস নয়")

        // 1. Check Duplicate TrxID (Anti Double-Spending)
        val existingProcessed = dao.getProcessedTrx(parsed.trxId)
        if (existingProcessed != null) {
            return@withLock VerificationOutcome.DuplicateBlocked(
                trxId = parsed.trxId,
                originalOutcome = existingProcessed.outcome
            )
        }

        val now = System.currentTimeMillis()
        expireStaleSessionsInternal(now)

        val normalizedSender = normalizeBdPhone(parsed.senderNumber)
        val activeSessionsForProvider = dao.getActiveSessionsForProvider(parsed.mfsProvider, now)

        // Priority 1: Match by SENDER NUMBER + Amount >= Base Amount
        val senderMatchedSession = activeSessionsForProvider.firstOrNull { session ->
            isSenderNumberMatch(session.customerPhone, normalizedSender) &&
                parsed.amount + 0.001 >= session.baseAmount
        }

        // Priority 2: Fallback match by exact lockedAmount if dynamic mode is used
        val matchedSession = senderMatchedSession ?: activeSessionsForProvider.firstOrNull { session ->
            abs(session.lockedAmount - parsed.amount) < 0.005
        }

        val config = preferences.configFlow.first()

        if (matchedSession != null) {
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
                senderNumber = normalizedSender,
                amount = parsed.amount,
                mfsProvider = parsed.mfsProvider,
                customerName = matchedSession.customerName
            )
        } else {
            val reason = "এই সেন্ডার নাম্বার ($normalizedSender) দিয়ে কোনো অপেক্ষমান অর্ডার পাওয়া যায়নি"

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

            // Even if no local session existed on the phone, still send the Webhook to the Website!
            // Because the external website may hold the pending order by sender_number in its own DB!
            dispatchAndLogWebhook(
                config = config,
                parsed = parsed,
                matchedOrderId = null,
                verificationStatus = "WEBHOOK_SENT_BY_SENDER_NUMBER"
            )

            return@withLock VerificationOutcome.QueuedUnmatched(
                trxId = parsed.trxId,
                senderNumber = normalizedSender,
                amount = parsed.amount,
                mfsProvider = parsed.mfsProvider,
                reason = reason
            )
        }
    }

    /**
     * Verifies a payment simply by entering the Customer's Sender Number (and optional Order ID / TrxID)!
     */
    suspend fun verifyBySenderNumberOrTrx(
        senderPhoneInput: String,
        orderIdInput: String = "",
        trxIdInput: String = ""
    ): ManualClaimResult = verificationMutex.withLock {
        val cleanPhone = normalizeBdPhone(senderPhoneInput)
        val cleanOrderId = orderIdInput.trim().uppercase(Locale.US)
        val cleanTrxId = trxIdInput.trim().uppercase(Locale.US)

        if (cleanPhone.isEmpty() && cleanTrxId.isEmpty()) {
            return@withLock ManualClaimResult.Error("অনুগ্রহ করে কাস্টমারের সেন্ডার নাম্বার দিন (যেমন: 017XXXXXXXX)")
        }

        // Find unclaimed SMS matching either the Sender Number OR the TrxID
        val unclaimedList = dao.getUnclaimedTransactions()
        val matchedTx = unclaimedList.firstOrNull { tx ->
            (cleanPhone.isNotEmpty() && isSenderNumberMatch(cleanPhone, tx.senderNumber)) ||
                (cleanTrxId.isNotEmpty() && tx.trxId.equals(cleanTrxId, ignoreCase = true))
        } ?: return@withLock ManualClaimResult.Error(
            "এই সেন্ডার নাম্বার ($cleanPhone) থেকে কোনো আনম্যাচড পেমেন্ট এসএমএস পাওয়া যায়নি।"
        )

        // Find pending order matching either Order ID or Sender Number, or create a verified order record automatically
        val pendingOrders = dao.getPendingOrders()
        val targetOrder = when {
            cleanOrderId.isNotEmpty() -> dao.getOrderById(cleanOrderId)
            cleanPhone.isNotEmpty() -> pendingOrders.firstOrNull { isSenderNumberMatch(cleanPhone, it.customerPhone) }
            else -> pendingOrders.firstOrNull()
        }

        val now = System.currentTimeMillis()

        if (targetOrder != null) {
            if (targetOrder.status == "VERIFIED") {
                return@withLock ManualClaimResult.Error("অর্ডার ${targetOrder.orderId} আগেই ভেরিফাই করা হয়েছে।")
            }
            if (matchedTx.amount + 0.001 < targetOrder.baseAmount) {
                return@withLock ManualClaimResult.Error(
                    "টাকার পরিমাণ কম: এসএমএস-এ এসেছে ৳${matchedTx.amount}, কিন্তু অর্ডারের পরিমাণ ৳${targetOrder.baseAmount}"
                )
            }

            dao.markUnmatchedClaimed(matchedTx.trxId, targetOrder.orderId)
            dao.updateOrderVerified(
                orderId = targetOrder.orderId,
                status = "VERIFIED",
                trxId = matchedTx.trxId,
                verifiedAt = now
            )
            dao.markSessionMatched(targetOrder.sessionId, matchedTx.trxId)
            dao.incrementUserStats(targetOrder.userId, matchedTx.amount)
            dao.updateProcessedTrxOutcome(matchedTx.trxId, "SENDER_NUMBER_VERIFIED", targetOrder.orderId)

            return@withLock ManualClaimResult.Success(
                orderId = targetOrder.orderId,
                trxId = matchedTx.trxId,
                senderNumber = matchedTx.senderNumber,
                amount = matchedTx.amount,
                mfsProvider = matchedTx.mfsProvider
            )
        } else {
            // Create a verified order record directly for this Sender Number
            val newOrderId = "ORD-${(100000..999999).random()}"
            val newSessionId = "SES-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}"
            val userId = "USR-${matchedTx.senderNumber.takeLast(6)}"

            dao.upsertUser(
                UserEntity(
                    userId = userId,
                    name = "কাস্টমার (${matchedTx.senderNumber})",
                    phone = matchedTx.senderNumber,
                    email = "${matchedTx.senderNumber}@customer.bd",
                    totalVerifiedAmount = matchedTx.amount,
                    verifiedOrdersCount = 1,
                    createdAt = now
                )
            )
            dao.insertOrder(
                OrderEntity(
                    orderId = newOrderId,
                    userId = userId,
                    customerName = "কাস্টমার (${matchedTx.senderNumber})",
                    customerPhone = matchedTx.senderNumber,
                    baseAmount = matchedTx.amount,
                    payableAmount = matchedTx.amount,
                    mfsProvider = matchedTx.mfsProvider,
                    status = "VERIFIED",
                    trxId = matchedTx.trxId,
                    sessionId = newSessionId,
                    verifiedAt = now,
                    createdAt = now
                )
            )
            dao.markUnmatchedClaimed(matchedTx.trxId, newOrderId)
            dao.updateProcessedTrxOutcome(matchedTx.trxId, "SENDER_NUMBER_VERIFIED", newOrderId)

            return@withLock ManualClaimResult.Success(
                orderId = newOrderId,
                trxId = matchedTx.trxId,
                senderNumber = matchedTx.senderNumber,
                amount = matchedTx.amount,
                mfsProvider = matchedTx.mfsProvider
            )
        }
    }

    /**
     * Sends a live test ping to the configured Website Webhook URL to verify connectivity.
     */
    suspend fun sendTestWebhookPing(): WebhookDispatchResult {
        val config = preferences.configFlow.first()
        val testParsed = ParsedMfsSms(
            trxId = "TEST${(100000..999999).random()}",
            senderNumber = "01712345678",
            amount = 500.00,
            mfsProvider = "bKash",
            transactionType = "Send Money",
            rawMessage = "Test Webhook Ping from PaySync MFS App",
            timestamp = System.currentTimeMillis()
        )
        return dispatchAndLogWebhook(
            config = config,
            parsed = testParsed,
            matchedOrderId = "ORD-TEST01",
            verificationStatus = "TEST_WEBHOOK_PING"
        )
    }

    private suspend fun dispatchAndLogWebhook(
        config: com.example.data.preferences.GatewayConfig,
        parsed: ParsedMfsSms,
        matchedOrderId: String?,
        verificationStatus: String
    ): WebhookDispatchResult {
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
        return result
    }

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

    suspend fun ensureSeededDemoData() {
        val currentSessions = dao.observePaymentSessions().first()
        if (currentSessions.isNotEmpty()) return

        val s1 = initiatePaymentSession(
            customerName = "তানভীর আহমেদ",
            customerPhone = "01712345678",
            baseAmount = 500.0,
            mfsProvider = "bKash"
        )
        initiatePaymentSession(
            customerName = "নুসরাত জাহান",
            customerPhone = "01819876543",
            baseAmount = 500.0,
            mfsProvider = "bKash"
        )
        initiatePaymentSession(
            customerName = "মেহেদী হাসান",
            customerPhone = "01911223344",
            baseAmount = 1000.0,
            mfsProvider = "Nagad"
        )

        val sampleSms1 = MfsSmsParser.buildSampleSms(
            provider = "bKash",
            amount = s1.lockedAmount,
            senderNumber = s1.customerPhone,
            trxId = "BKA98X72KL",
            txType = "Send Money"
        )
        processIncomingSmsOrNotification("bKash", sampleSms1)

        val unmatchedSms = MfsSmsParser.buildSampleSms(
            provider = "bKash",
            amount = 500.00,
            senderNumber = "01611556677",
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
