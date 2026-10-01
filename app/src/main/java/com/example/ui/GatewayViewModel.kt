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
        // 1-second ticker for real-time 5-minute session countdown timers & expiry sweeping
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
                showBanner("24/7 Foreground MFS Listener Service started.")
            } else {
                MfsGatewayForegroundService.stopGatewayService(ctx)
                showBanner("Foreground MFS Listener Service paused.")
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
                        "VERIFIED! ${outcome.mfsProvider} TrxID ${outcome.trxId} (৳${outcome.amount}) matched Order ${outcome.orderId} (${outcome.customerName})"
                    )
                }
                is VerificationOutcome.QueuedUnmatched -> {
                    showBanner(
                        "QUEUED IN UNMATCHED: ${outcome.mfsProvider} TrxID ${outcome.trxId} (৳${outcome.amount}) — ${outcome.reason}"
                    )
                }
                is VerificationOutcome.DuplicateBlocked -> {
                    showBanner(
                        "DOUBLE-SPEND BLOCKED: TrxID ${outcome.trxId} was already processed (${outcome.originalOutcome})!"
                    )
                }
                is VerificationOutcome.IgnoredNonMfs -> {
                    showBanner("Ignored: ${outcome.reason}")
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
            val session = repo.initiatePaymentSession(
                customerName = customerName,
                customerPhone = customerPhone,
                baseAmount = baseAmount,
                mfsProvider = mfsProvider
            )
            showBanner(
                "Session Locked: Send ৳${String.format("%.2f", session.lockedAmount)} via ${session.mfsProvider} within 5:00 mins (Order ${session.orderId})"
            )
        }
    }

    fun spawn20ConcurrentSessions(baseAmount: Double, mfsProvider: String) {
        viewModelScope.launch {
            val created = repo.spawnConcurrentSessionsBurst(
                count = 20,
                baseAmount = baseAmount,
                mfsProvider = mfsProvider
            )
            val minAmt = created.minOfOrNull { it.lockedAmount } ?: baseAmount
            val maxAmt = created.maxOfOrNull { it.lockedAmount } ?: baseAmount
            showBanner(
                "20 Concurrent Sessions Locked! Unique amounts ৳${String.format("%.2f", minAmt)} .. ৳${String.format("%.2f", maxAmt)} assigned with 0 collisions."
            )
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
            when (val res = repo.claimUnmatchedTransaction(orderId, trxId, senderPhone)) {
                is ManualClaimResult.Success -> {
                    showBanner(
                        "MANUAL CLAIM VERIFIED! Order ${res.orderId} linked to ${res.mfsProvider} TrxID ${res.trxId} (৳${res.amount})"
                    )
                }
                is ManualClaimResult.Error -> {
                    showBanner("Claim Rejected: ${res.message}")
                }
            }
        }
    }

    fun scanDeviceSmsInbox() {
        viewModelScope.launch {
            val count = repo.scanDeviceSmsInbox()
            showBanner("Scanned Device SMS Inbox: Processed $count new bKash/Nagad transaction(s).")
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
            showBanner("Gateway & Firebase Webhook configuration saved.")
        }
    }

    fun clearWebhookLogs() {
        viewModelScope.launch {
            repo.clearWebhookLogs()
            showBanner("Webhook logs cleared.")
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
