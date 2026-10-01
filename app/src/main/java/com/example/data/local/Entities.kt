package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["phone"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val userId: String,
    val name: String,
    val phone: String,
    val email: String,
    val totalVerifiedAmount: Double = 0.0,
    val verifiedOrdersCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["trxId"]),
        Index(value = ["status"]),
        Index(value = ["userId"])
    ]
)
data class OrderEntity(
    @PrimaryKey val orderId: String,
    val userId: String,
    val customerName: String,
    val customerPhone: String,
    val baseAmount: Double,
    val payableAmount: Double,
    val mfsProvider: String, // "bKash" or "Nagad"
    val status: String, // "PENDING", "VERIFIED", "EXPIRED", "MANUAL_REVIEW"
    val trxId: String? = null,
    val sessionId: String,
    val verifiedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payment_sessions",
    indices = [
        Index(value = ["status", "mfsProvider", "lockedAmount"]),
        Index(value = ["orderId"], unique = true)
    ]
)
data class PaymentSessionEntity(
    @PrimaryKey val sessionId: String,
    val orderId: String,
    val userId: String,
    val customerName: String,
    val customerPhone: String,
    val mfsProvider: String, // "bKash" or "Nagad"
    val baseAmount: Double,
    val lockedAmount: Double, // Unique dynamic amount e.g. 501.00, 502.00 or 500.01
    val status: String, // "ACTIVE", "MATCHED", "EXPIRED"
    val matchedTrxId: String? = null,
    val expiresAt: Long, // 5-minute countdown timestamp
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "unmatched_transactions",
    indices = [
        Index(value = ["trxId"], unique = true),
        Index(value = ["status"])
    ]
)
data class UnmatchedTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trxId: String,
    val senderNumber: String,
    val amount: Double,
    val mfsProvider: String, // "bKash" or "Nagad"
    val rawSms: String,
    val reason: String, // "NO_ACTIVE_SESSION", "SESSION_EXPIRED", "AMOUNT_MISMATCH", "DUPLICATE_ATTEMPT"
    val status: String, // "UNCLAIMED", "CLAIMED", "BLOCKED_DUPLICATE"
    val claimedByOrderId: String? = null,
    val receivedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "processed_trx_ids",
    indices = [Index(value = ["trxId"], unique = true)]
)
data class ProcessedTrxEntity(
    @PrimaryKey val trxId: String,
    val mfsProvider: String,
    val amount: Double,
    val senderNumber: String,
    val outcome: String, // "AUTO_VERIFIED", "QUEUED_UNMATCHED", "MANUAL_CLAIMED"
    val orderId: String? = null,
    val processedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "webhook_logs")
data class WebhookLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trxId: String,
    val senderNumber: String,
    val amount: Double,
    val mfsProvider: String,
    val targetUrl: String,
    val payloadJson: String,
    val httpStatus: Int,
    val responseSummary: String,
    val isSuccess: Boolean,
    val latencyMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
