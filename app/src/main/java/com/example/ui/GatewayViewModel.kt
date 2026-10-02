package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.OrderEntity
import com.example.data.local.PaymentSessionEntity
import com.example.data.local.UnmatchedTransactionEntity
import com.example.data.local.UserEntity
import com.example.data.local.WebhookLogEntity
import com.example.data.preferences.DynamicAmountMode
import com.example.data.preferences.GatewayConfig
import com.example.domain.ManualClaimResult
import com.example.domain.PaymentVerificationRepository
import com.example.domain.VerificationOutcome
import com.example.parser.MfsSmsParser
import com.example.parser.ParsedMfsSms
import com.example.service.MfsGatewayForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SystemPermissionState(
    val smsPermissionGranted: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val foregroundServiceRunning: Boolean = false
)

class GatewayViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PaymentVerificationRepository.getInstance(application)

    val configState: StateFlow<GatewayConfig> = repo.preferences.configFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GatewayConfig()
    )

    val sessionsState: StateFlow<List<PaymentSessionEntity>> = repo.sessionsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val ordersState: StateFlow<List<OrderEntity>> = repo.ordersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val unmatchedState: StateFlow<List<UnmatchedTransactionEntity>> = repo.unmatchedFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val usersState: StateFlow<List<UserEntity>> = repo.usersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val webhookLogsState: StateFlow<List<WebhookLogEntity>> = repo.webhookLogsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _permissionState = MutableStateFlow(SystemPermissionState())
    val permissionState: StateFlow<SystemPermissionState> = _permissionState.asStateFlow()

    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _liveRegexPreview = MutableStateFlow<ParsedMfsSms?>(null)
    val liveRegexPreview: StateFlow<ParsedMfsSms?> = _liveRegexPreview.asStateFlow()

    init {
        refreshPermissionStatuses()
        viewModelScope.launch {
            repo.ensureSeededDemoData()
            MfsGatewayForegroundService.startGatewayService(getApplication())
            refreshPermissionStatuses()
        }
        viewModelScope.launch {
            var tickCounter = 0
            while (isActive) {
                _nowMillis.value = System.currentTimeMillis()
                tickCounter++
                if (tickCounter % 5 == 0) {
                    repo.sweepExpiredSessions()
                    refreshPermissionStatuses()
                }
                delay(1_000L)
            }
        }
    }

    fun refreshPermissionStatuses() {
        val ctx = getApplication<Application>()
        _permissionState.value = SystemPermissionState(
            smsPermissionGranted = MfsGatewayForegroundService.hasSmsPermissions(ctx),
            notificationListenerEnabled = MfsGatewayForegroundService.isNotificationListenerEnabled(ctx),
            batteryOptimizationIgnored = MfsGatewayForegroundService.isBatteryOptimizationIgnored(ctx),
            foregroundServiceRunning = MfsGatewayForegroundService.isRunning.value
        )
    }

    fun toggleForegroundService(enable: Boolean) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            repo.preferences.setBackgroundServiceEnabled(enable)
            if (enable) {
                MfsGatewayForegroundService.startGatewayService(ctx)
                showBanner("২৪/৭ অটো এসএমএস ভেরিফিকেশন সার্ভিস চালু হয়েছে।")
            } else {
                MfsGatewayForegroundService.stopGatewayService(ctx)
                showBanner("অটো ভেরিফিকেশন সার্ভিস সাময়িকভাবে বন্ধ করা হয়েছে।")
            }
            refreshPermissionStatuses()
        }
    }

    fun updateLiveRegexPreview(sender: String, smsBody: String) {
        _liveRegexPreview.value = MfsSmsParser.parse(sender, smsBody)
    }

    fun processSimulatedOrRealSms(sender: String, smsBody: String) {
        viewModelScope.launch {
            when (val outcome = repo.processIncomingSmsOrNotification(sender, smsBody)) {
                is VerificationOutcome.Verified -> {
                    showBanner(
                        "ভেরিফাইড! সেন্ডার নাম্বার ${outcome.senderNumber} থেকে ৳${outcome.amount} (${outcome.mfsProvider}) মিলেছে! অর্ডার: ${outcome.orderId}"
                    )
                }
                is VerificationOutcome.QueuedUnmatched -> {
                    showBanner(
                        "এসএমএস রিসিভ হয়েছে (সেন্ডার: ${outcome.senderNumber}, ৳${outcome.amount}) — ওয়েবসাইটে পাঠানো হয়েছে এবং ম্যানুয়াল লিস্টে যোগ হয়েছে।"
                    )
                }
                is VerificationOutcome.DuplicateBlocked -> {
                    showBanner(
                        "সতর্কতা: এই TrxID (${outcome.trxId}) আগেই ব্যবহার করা হয়েছে! ডুপ্লিকেট ব্লক করা হয়েছে।"
                    )
                }
                is VerificationOutcome.IgnoredNonMfs -> {
                    showBanner(outcome.reason)
                }
            }
        }
    }

    fun initiateSingleSession(
        customerName: String,
        customerPhone: String,
        baseAmount: Double,
        mfsProvider: String
    ) {
        viewModelScope.launch {
            if (customerPhone.trim().length < 10) {
                showBanner("সঠিক ১১ ডিজিটের সেন্ডার মোবাইল নাম্বার দিন (যেমন: 01712345678)")
                return@launch
            }
            val session = repo.initiatePaymentSession(
                customerName = customerName,
                customerPhone = customerPhone,
                baseAmount = baseAmount,
                mfsProvider = mfsProvider
            )
            if (session.status == "MATCHED") {
                showBanner(
                    "সাথে সাথে ভেরিফাইড! ${session.customerPhone} নাম্বার থেকে আগেই ৳${session.lockedAmount} এসেছিল (TrxID: ${session.matchedTrxId})"
                )
            } else {
                showBanner(
                    "সেন্ডার নাম্বার (${session.customerPhone}) যুক্ত হয়েছে! এখন এই নাম্বার থেকে ৳${String.format("%.0f", session.lockedAmount)} আসলে অটো ভেরিফাই হবে।"
                )
            }
        }
    }

    fun spawn20ConcurrentSessions(baseAmount: Double, mfsProvider: String) {
        viewModelScope.launch {
            repo.spawnConcurrentSessionsBurst(
                count = 10,
                baseAmount = baseAmount,
                mfsProvider = mfsProvider
            )
            showBanner("১০টি টেস্ট সেন্ডার নাম্বার একসাথে যুক্ত করা হয়েছে!")
        }
    }

    fun simulateInstantMatchForSession(session: PaymentSessionEntity) {
        viewModelScope.launch {
            val randomTrxId = if (session.mfsProvider == "bKash") {
                "BK" + (1..8).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
            } else {
                "NG" + (1..6).map { "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".random() }.joinToString("")
            }
            val smsText = MfsSmsParser.buildSampleSms(
                provider = session.mfsProvider,
                amount = session.lockedAmount,
                senderNumber = session.customerPhone,
                trxId = randomTrxId,
                txType = "Send Money"
            )
            processSimulatedOrRealSms(session.mfsProvider, smsText)
        }
    }

    fun claimManualTrx(orderId: String, trxId: String, senderPhone: String) {
        viewModelScope.launch {
            when (val res = repo.verifyBySenderNumberOrTrx(senderPhone, orderId, trxId)) {
                is ManualClaimResult.Success -> {
                    showBanner(
                        "ভেরিফাইড! সেন্ডার নাম্বার ${res.senderNumber} (৳${res.amount}, TrxID: ${res.trxId}) সফলভাবে ভেরিফাই হয়েছে!"
                    )
                }
                is ManualClaimResult.Error -> {
                    showBanner(res.message)
                }
            }
        }
    }

    fun testWebsiteWebhook() {
        viewModelScope.launch {
            val res = repo.sendTestWebhookPing()
            if (res.isSuccess) {
                showBanner("ওয়েবসাইট টেস্ট সফল! (${res.responseSummary})")
            } else {
                showBanner("ওয়েবসাইট কানেকশন ত্রুটি: ${res.responseSummary}")
            }
        }
    }

    fun scanDeviceSmsInbox() {
        viewModelScope.launch {
            val count = repo.scanDeviceSmsInbox()
            showBanner("ফোনের SMS ইনবক্স চেক সম্পন্ন: $count টি নতুন পেমেন্ট পাওয়া গেছে।")
        }
    }

    fun saveConfig(
        webhookUrl: String,
        firebaseDbUrl: String,
        secretKey: String,
        bkashNumber: String,
        nagadNumber: String,
        dynamicMode: DynamicAmountMode,
        ttlMinutes: Int,
        syncToFirebaseRest: Boolean
    ) {
        viewModelScope.launch {
            repo.preferences.updateConfig(
                webhookUrl = webhookUrl,
                firebaseRealtimeDbUrl = firebaseDbUrl,
                secretKey = secretKey,
                merchantBkashNumber = bkashNumber,
                merchantNagadNumber = nagadNumber,
                dynamicAmountMode = dynamicMode,
                sessionTtlMinutes = ttlMinutes,
                syncToFirebaseRest = syncToFirebaseRest
            )
            showBanner("ওয়েবসাইট Webhook URL এবং সেটিংস সেভ করা হয়েছে!")
        }
    }

    fun clearWebhookLogs() {
        viewModelScope.launch {
            repo.clearWebhookLogs()
            showBanner("লগ ক্লিয়ার করা হয়েছে।")
        }
    }

    fun dismissBanner() {
        _bannerMessage.value = null
    }

    fun notifyBanner(msg: String) {
        _bannerMessage.value = msg
    }

    private fun showBanner(msg: String) {
        _bannerMessage.value = msg
    }
}
