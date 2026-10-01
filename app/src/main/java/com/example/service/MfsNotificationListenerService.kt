package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.domain.PaymentVerificationRepository
import com.example.parser.MfsSmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MfsNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "MfsNotificationListener"
        private val MFS_PACKAGES = setOf(
            "com.bKash.customerapp",
            "com.konasl.nagad",
            "com.google.android.apps.messaging",
            "com.android.mms"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        // Ignore our own foreground service notifications
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return

        val combinedSenderHint = "$packageName $title"
        val provider = MfsSmsParser.detectMfsProvider(combinedSenderHint, text)

        if (provider != null || MFS_PACKAGES.contains(packageName)) {
            serviceScope.launch {
                try {
                    Log.d(TAG, "Evaluating notification from $packageName ($title)")
                    val repo = PaymentVerificationRepository.getInstance(applicationContext)
                    repo.processIncomingSmsOrNotification(
                        senderAddress = provider ?: title,
                        rawMessageBody = text,
                        timestamp = sbn.postTime
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing MFS notification: ${e.message}", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
