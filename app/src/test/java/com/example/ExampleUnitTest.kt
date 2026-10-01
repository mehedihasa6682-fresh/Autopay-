package com.example

import com.example.parser.MfsSmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun parse_bkashSendMoney_extractsTrxIdSenderAndAmount() {
        val raw = "You have received Tk 501.00 from 01712345678. Ref 1. Fee Tk 0.00. Balance Tk 12,450.00. TrxID BKA98X72KL at 01/10/2026 22:15"
        val parsed = MfsSmsParser.parse("bKash", raw)
        assertNotNull(parsed)
        assertEquals("bKash", parsed?.mfsProvider)
        assertEquals("BKA98X72KL", parsed?.trxId)
        assertEquals("01712345678", parsed?.senderNumber)
        assertEquals(501.00, parsed?.amount ?: 0.0, 0.001)
    }

    @Test
    fun parse_nagadMoneyReceived_extractsTxnIdSenderAndAmount() {
        val raw = "Money Received. Amount: Tk 1,002.00. Sender: 01911223344. Ref: Order. TxnID: 72M9K4P1. Balance: Tk 8,500.00. 01/10/2026 22:18"
        val parsed = MfsSmsParser.parse("Nagad", raw)
        assertNotNull(parsed)
        assertEquals("Nagad", parsed?.mfsProvider)
        assertEquals("72M9K4P1", parsed?.trxId)
        assertEquals("01911223344", parsed?.senderNumber)
        assertEquals(1002.00, parsed?.amount ?: 0.0, 0.001)
    }

    @Test
    fun parse_otpMessage_isSafelyIgnored() {
        val otpSms = "Your bKash verification code is 491820. Never share your PIN or OTP with anyone."
        val parsed = MfsSmsParser.parse("bKash", otpSms)
        assertNull(parsed)
    }
}
