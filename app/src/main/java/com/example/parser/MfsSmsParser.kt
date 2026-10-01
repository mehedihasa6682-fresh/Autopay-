package com.example.parser

import java.util.Locale

data class ParsedMfsSms(
    val trxId: String,
    val senderNumber: String,
    val amount: Double,
    val mfsProvider: String, // "bKash" or "Nagad"
    val transactionType: String, // "Send Money", "Cash In", "Payment"
    val rawMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)

object MfsSmsParser {

    // 1. TrxID / TxnID extraction regex (bKash uses TrxID 10-char alphanumeric; Nagad uses TxnID 8-12 char alphanumeric)
    private val TRX_ID_REGEX = Regex(
        pattern = """(?:TrxID|TxnID|TranID|Transaction\s*ID|Trx\s*ID|Txn\s*ID|ট্রানজেকশন\s*আইডি)\s*[:\-]?\s*([A-Z0-9]{6,16})""",
        option = RegexOption.IGNORE_CASE
    )

    // 2. Primary Amount extraction regex (targets received amount, avoiding Fee/Balance)
    private val PRIMARY_AMOUNT_REGEX = Regex(
        pattern = """(?:You\s+have\s+received|Cash\s*In|Cash-In|Payment\s+of|Money\s+Received.*?Amount\s*[:\-]?|Amount\s*[:\-]?|of)\s*(?:Tk\.?|BDT|৳)\s*([0-9,]+(?:\.\d{1,2})?)""",
        option = RegexOption.IGNORE_CASE
    )

    // Fallback currency amount regex (first Tk/BDT/৳ amount in message)
    private val FALLBACK_AMOUNT_REGEX = Regex(
        pattern = """(?:Tk\.?|BDT|৳)\s*([0-9,]+(?:\.\d{1,2})?)""",
        option = RegexOption.IGNORE_CASE
    )

    // 3. Sender Bangladeshi Mobile Number extraction regex
    private val SENDER_CONTEXT_REGEX = Regex(
        pattern = """(?:from|Sender\s*[:\-]?|A/C\s*[:\-]?|হতে|প্রেরক\s*[:\-]?)\s*(?:\+?88)?(01[3-9]\d{8}|01[3-9]\d{2}\*+\d{2,4})""",
        option = RegexOption.IGNORE_CASE
    )

    private val BD_MOBILE_FALLBACK_REGEX = Regex(
        pattern = """(?:\+?88)?(01[3-9]\d{8}|01[3-9]\d{2}\*+\d{2,4})"""
    )

    /**
     * Determines if the SMS/Notification originates from bKash or Nagad
     */
    fun detectMfsProvider(senderAddress: String?, messageBody: String): String? {
        val normalizedSender = senderAddress?.trim()?.lowercase(Locale.US) ?: ""
        val normalizedBody = messageBody.lowercase(Locale.US)

        return when {
            normalizedSender.contains("bkash") ||
                normalizedSender == "16247" ||
                normalizedSender.contains("com.bkash") -> "bKash"

            normalizedSender.contains("nagad") ||
                normalizedSender == "16167" ||
                normalizedSender.contains("com.konasl.nagad") -> "Nagad"

            normalizedBody.contains("trxid") &&
                (normalizedBody.contains("bkash") || normalizedBody.contains("you have received tk") || normalizedBody.contains("cash in tk")) -> "bKash"

            normalizedBody.contains("txnid") || normalizedBody.contains("nagad") || normalizedBody.contains("money received. amount:") -> "Nagad"

            normalizedBody.contains("trxid") -> "bKash"
            else -> null
        }
    }

    /**
     * Parses raw SMS or Notification text from bKash / Nagad into structured payment verification data.
     * Returns null if the message is not a valid incoming payment confirmation (e.g., promotional SMS or OTP).
     */
    fun parse(senderAddress: String?, messageBody: String, timestamp: Long = System.currentTimeMillis()): ParsedMfsSms? {
        val cleanBody = messageBody.trim()
        if (cleanBody.isEmpty()) return null

        // Safety & Accuracy guard: Ignore OTP / verification code / outgoing payment messages
        val lowerBody = cleanBody.lowercase(Locale.US)
        if (lowerBody.contains("otp") ||
            lowerBody.contains("verification code") ||
            lowerBody.contains("pin") ||
            lowerBody.contains("never share")
        ) {
            return null
        }

        val provider = detectMfsProvider(senderAddress, cleanBody) ?: return null

        // Extract TrxID
        val trxMatch = TRX_ID_REGEX.find(cleanBody) ?: return null
        val trxId = trxMatch.groupValues[1].uppercase(Locale.US).trim()

        // Extract Amount
        val amountStr = PRIMARY_AMOUNT_REGEX.find(cleanBody)?.groupValues?.get(1)
            ?: FALLBACK_AMOUNT_REGEX.find(cleanBody)?.groupValues?.get(1)
            ?: return null

        val amount = amountStr.replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        // Extract Sender Mobile Number
        val senderNumber = SENDER_CONTEXT_REGEX.find(cleanBody)?.groupValues?.get(1)
            ?: BD_MOBILE_FALLBACK_REGEX.find(cleanBody)?.groupValues?.get(1)
            ?: "UNKNOWN"

        // Determine transaction type
        val txType = when {
            lowerBody.contains("cash in") || lowerBody.contains("cash-in") -> "Cash In"
            lowerBody.contains("payment") -> "Payment"
            else -> "Send Money"
        }

        return ParsedMfsSms(
            trxId = trxId,
            senderNumber = senderNumber,
            amount = amount,
            mfsProvider = provider,
            transactionType = txType,
            rawMessage = cleanBody,
            timestamp = timestamp
        )
    }

    /**
     * Generates a realistic bKash or Nagad incoming SMS string for testing/simulation.
     */
    fun buildSampleSms(
        provider: String,
        amount: Double,
        senderNumber: String,
        trxId: String,
        txType: String = "Send Money"
    ): String {
        val formattedAmount = String.format(Locale.US, "%.2f", amount)
        val formattedBalance = String.format(Locale.US, "%,.2f", 18450.00 + amount)
        return if (provider.equals("Nagad", ignoreCase = true)) {
            if (txType.equals("Cash In", ignoreCase = true)) {
                "Cash-In from $senderNumber of Tk $formattedAmount is successful. Fee: Tk 0.00. Balance: Tk $formattedBalance. TxnID: $trxId. 01/10/2026 22:18"
            } else {
                "Money Received. Amount: Tk $formattedAmount. Sender: $senderNumber. Ref: OrderPay. TxnID: $trxId. Balance: Tk $formattedBalance. 01/10/2026 22:18"
            }
        } else {
            if (txType.equals("Cash In", ignoreCase = true)) {
                "Cash In Tk $formattedAmount from $senderNumber successful. Fee Tk 0.00. Balance Tk $formattedBalance. TrxID $trxId at 01/10/2026 22:18"
            } else {
                "You have received Tk $formattedAmount from $senderNumber. Ref 1. Fee Tk 0.00. Balance Tk $formattedBalance. TrxID $trxId at 01/10/2026 22:18"
            }
        }
    }
}
