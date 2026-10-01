package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.domain.PaymentVerificationRepository
import com.example.parser.MfsSmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group multi-part SMS segments by originating sender address
        val groupedBySender = mutableMapOf<String, StringBuilder>()
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: "UNKNOWN"
            val bodyPart = sms.displayMessageBody ?: continue
            groupedBySender.getOrPut(sender) { StringBuilder() }.append(bodyPart)
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val repo = PaymentVerificationRepository.getInstance(context.applicationContext)
                for ((sender, bodyBuilder) in groupedBySender) {
                    val fullBody = bodyBuilder.toString()
                    val detectedProvider = MfsSmsParser.detectMfsProvider(sender, fullBody)
                    if (detectedProvider != null) {
                        Log.i(TAG, "Intercepted incoming $detectedProvider SMS from $sender")
                        repo.processIncomingSmsOrNotification(
                            senderAddress = sender,
                            rawMessageBody = fullBody,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming MFS SMS: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
