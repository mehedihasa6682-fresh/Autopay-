package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.GatewayViewModel
import com.example.ui.screens.FirebaseBlueprintScreen
import com.example.ui.screens.GatewayHubScreen
import com.example.ui.screens.SessionsConcurrencyScreen
import com.example.ui.screens.UnmatchedQueueScreen
import com.example.ui.screens.WebhookSettingsScreen
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.MyApplicationTheme

enum class GatewayTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
) {
    GATEWAY_HUB("gateway_hub", R.string.nav_gateway, Icons.Default.Radar, "nav_tab_gateway"),
    SESSIONS("sessions", R.string.nav_sessions, Icons.Default.LockClock, "nav_tab_sessions"),
    UNMATCHED("unmatched", R.string.nav_unmatched, Icons.Default.PendingActions, "nav_tab_unmatched"),
    WEBHOOKS("webhooks", R.string.nav_webhooks, Icons.Default.Webhook, "nav_tab_webhooks"),
    FIREBASE_CODE("firebase_code", R.string.nav_blueprint, Icons.Default.Code, "nav_tab_blueprint")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            MyApplicationTheme(darkTheme = isDarkTheme) {
                PaySyncMfsApp(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaySyncMfsApp(
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    viewModel: GatewayViewModel = viewModel()
) {
    var currentTab by rememberSaveable { mutableStateOf(GatewayTab.GATEWAY_HUB) }

    // BackHandler on secondary tabs returns to Gateway Hub
    BackHandler(enabled = currentTab != GatewayTab.GATEWAY_HUB) {
        currentTab = GatewayTab.GATEWAY_HUB
    }

    // Refresh runtime permissions whenever user returns from Android Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val config by viewModel.configState.collectAsStateWithLifecycle()
    val sessions by viewModel.sessionsState.collectAsStateWithLifecycle()
    val orders by viewModel.ordersState.collectAsStateWithLifecycle()
    val unmatched by viewModel.unmatchedState.collectAsStateWithLifecycle()
    val users by viewModel.usersState.collectAsStateWithLifecycle()
    val webhookLogs by viewModel.webhookLogsState.collectAsStateWithLifecycle()
    val permissionState by viewModel.permissionState.collectAsStateWithLifecycle()
    val nowMillis by viewModel.nowMillis.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val liveRegexPreview by viewModel.liveRegexPreview.collectAsStateWithLifecycle()

    val activeSessions = sessions.filter { it.status == "ACTIVE" }
    val unclaimedCount = unmatched.count { it.status == "UNCLAIMED" }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(ElectricEmerald)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = stringResource(id = R.string.gateway_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.testTag("btn_toggle_theme")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Light/Dark Theme"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar {
                        GatewayTab.entries.forEach { tab ->
                            val badgeCount = when (tab) {
                                GatewayTab.SESSIONS -> activeSessions.size
                                GatewayTab.UNMATCHED -> unclaimedCount
                                else -> 0
                            }
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    if (badgeCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = stringResource(id = tab.labelRes)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = stringResource(id = tab.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(id = tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        GatewayTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(id = tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(id = tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Live Event Banner Notification
                    AnimatedVisibility(
                        visible = bannerMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        bannerMessage?.let { msg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable { viewModel.dismissBanner() }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .testTag("status_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Active Tab Content
                    when (currentTab) {
                        GatewayTab.GATEWAY_HUB -> GatewayHubScreen(
                            permissionState = permissionState,
                            activeSessions = activeSessions,
                            orders = orders,
                            unmatchedList = unmatched,
                            liveRegexPreview = liveRegexPreview,
                            onRefreshPermissions = viewModel::refreshPermissionStatuses,
                            onToggleForegroundService = viewModel::toggleForegroundService,
                            onScanDeviceInbox = viewModel::scanDeviceSmsInbox,
                            onUpdateRegexPreview = viewModel::updateLiveRegexPreview,
                            onProcessSms = viewModel::processSimulatedOrRealSms
                        )

                        GatewayTab.SESSIONS -> SessionsConcurrencyScreen(
                            sessions = sessions,
                            config = config,
                            nowMillis = nowMillis,
                            onInitiateSession = viewModel::initiateSingleSession,
                            onSpawn20ConcurrentSessions = viewModel::spawn20ConcurrentSessions,
                            onSimulateInstantMatch = viewModel::simulateInstantMatchForSession
                        )

                        GatewayTab.UNMATCHED -> UnmatchedQueueScreen(
                            unmatchedList = unmatched,
                            orders = orders,
                            users = users,
                            onClaimManualTrx = viewModel::claimManualTrx
                        )

                        GatewayTab.WEBHOOKS -> WebhookSettingsScreen(
                            config = config,
                            webhookLogs = webhookLogs,
                            onSaveConfig = viewModel::saveConfig,
                            onClearLogs = viewModel::clearWebhookLogs
                        )

                        GatewayTab.FIREBASE_CODE -> FirebaseBlueprintScreen(
                            onCodeCopied = { copiedMsg ->
                                viewModel.notifyBanner(copiedMsg)
                            }
                        )
                    }
                }
            }
        }
    }
}
