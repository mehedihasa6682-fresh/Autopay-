package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GatewayDao {

    // --- Users ---
    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun observeUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)

    @Query("UPDATE users SET totalVerifiedAmount = totalVerifiedAmount + :amount, verifiedOrdersCount = verifiedOrdersCount + 1 WHERE userId = :userId")
    suspend fun incrementUserStats(userId: String, amount: Double)

    // --- Orders ---
    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun observeOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE status = 'PENDING' ORDER BY createdAt DESC")
    suspend fun getPendingOrders(): List<OrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Query("UPDATE orders SET status = :status, trxId = :trxId, verifiedAt = :verifiedAt WHERE orderId = :orderId")
    suspend fun updateOrderVerified(orderId: String, status: String, trxId: String, verifiedAt: Long)

    @Query("UPDATE orders SET status = 'EXPIRED' WHERE sessionId IN (:expiredSessionIds) AND status = 'PENDING'")
    suspend fun markOrdersExpiredBySessions(expiredSessionIds: List<String>)

    // --- Payment Sessions ---
    @Query("SELECT * FROM payment_sessions ORDER BY createdAt DESC")
    fun observePaymentSessions(): Flow<List<PaymentSessionEntity>>

    @Query("SELECT * FROM payment_sessions WHERE status = 'ACTIVE' AND mfsProvider = :mfsProvider AND expiresAt > :now")
    suspend fun getActiveSessionsForProvider(mfsProvider: String, now: Long): List<PaymentSessionEntity>

    @Query("SELECT * FROM payment_sessions WHERE status = 'ACTIVE' AND expiresAt <= :now")
    suspend fun getExpiredActiveSessions(now: Long): List<PaymentSessionEntity>

    @Query("UPDATE payment_sessions SET status = 'EXPIRED' WHERE sessionId IN (:sessionIds)")
    suspend fun markSessionsExpired(sessionIds: List<String>)

    @Query("SELECT * FROM payment_sessions WHERE mfsProvider = :mfsProvider AND ABS(lockedAmount - :amount) < 0.005 ORDER BY createdAt DESC")
    suspend fun findSessionsByProviderAndAmount(mfsProvider: String, amount: Double): List<PaymentSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentSession(session: PaymentSessionEntity)

    @Query("UPDATE payment_sessions SET status = 'MATCHED', matchedTrxId = :trxId WHERE sessionId = :sessionId")
    suspend fun markSessionMatched(sessionId: String, trxId: String)

    // --- Unmatched Transactions ---
    @Query("SELECT * FROM unmatched_transactions ORDER BY receivedAt DESC")
    fun observeUnmatchedTransactions(): Flow<List<UnmatchedTransactionEntity>>

    @Query("SELECT * FROM unmatched_transactions WHERE status = 'UNCLAIMED' ORDER BY receivedAt DESC")
    suspend fun getUnclaimedTransactions(): List<UnmatchedTransactionEntity>

    @Query("SELECT * FROM unmatched_transactions WHERE trxId = :trxId LIMIT 1")
    suspend fun getUnmatchedByTrxId(trxId: String): UnmatchedTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnmatchedTransaction(tx: UnmatchedTransactionEntity): Long

    @Query("UPDATE unmatched_transactions SET status = 'CLAIMED', claimedByOrderId = :orderId WHERE trxId = :trxId")
    suspend fun markUnmatchedClaimed(trxId: String, orderId: String)

    // --- Processed TrxID Registry (Anti Double-Spend) ---
    @Query("SELECT * FROM processed_trx_ids WHERE trxId = :trxId LIMIT 1")
    suspend fun getProcessedTrx(trxId: String): ProcessedTrxEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProcessedTrx(processed: ProcessedTrxEntity)

    @Query("UPDATE processed_trx_ids SET outcome = :outcome, orderId = :orderId WHERE trxId = :trxId")
    suspend fun updateProcessedTrxOutcome(trxId: String, outcome: String, orderId: String)

    // --- Webhook Logs ---
    @Query("SELECT * FROM webhook_logs ORDER BY timestamp DESC LIMIT 100")
    fun observeWebhookLogs(): Flow<List<WebhookLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWebhookLog(log: WebhookLogEntity): Long

    @Query("DELETE FROM webhook_logs")
    suspend fun clearWebhookLogs()
}
