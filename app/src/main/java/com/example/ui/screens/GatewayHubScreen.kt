package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.OrderEntity
import com.example.data.local.PaymentSessionEntity
import com.example.data.local.UnmatchedTransactionEntity
import com.example.parser.MfsSmsParser
import com.example.parser.ParsedMfsSms
import com.example.ui.SystemPermissionState
import com.example.ui.theme.BkashPink
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.NagadOrange
import java.util.Locale

@Composable
fun GatewayHubScreen(
    permissionState: SystemPermissionState,
    activeSessions: List<PaymentSessionEntity>,
    orders: List<OrderEntity>,
    unmatchedList: List<UnmatchedTransactionEntity>,
    liveRegexPreview: ParsedMfsSms?,
    onRefreshPermissions: () -> Unit,
    onToggleForegroundService: (Boolean) -> Unit,
    onScanDeviceInbox: () -> Unit,
    onUpdateRegexPreview: (String, String) -> Unit,
    onProcessSms: (String, String) -> Unit
) {
    val context = LocalContext.current

    var senderInput by remember { mutableStateOf("bKash") }
    var smsInput by remember {
        mutableStateOf(
            "You have received Tk 502.00 from 01819876543. Ref 1. Fee Tk 0.00. Balance Tk 14,250.00. TrxID BKA72M91KL at 01/10/2026 22:20"
        )
    }

    LaunchedEffect(senderInput, smsInput) {
        onUpdateRegexPreview(senderInput, smsInput)
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermissions()
    }

    val verifiedCount = remember(orders) { orders.count { it.status == "VERIFIED" } }
    val verifiedVolume = remember(orders) {
        orders.filter { it.status == "VERIFIED" }.sumOf { it.payableAmount }
    }
    val unclaimedCount = remember(unmatchedList) {
        unmatchedList.count { it.status == "UNCLAIMED" }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("gateway_hub_list"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Hero Command Center Banner
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_gateway_hero),
                        contentDescription = stringResource(id = R.string.hero_banner_desc),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MidnightNavy.copy(alpha = 0.55f),
                                        MidnightNavy.copy(alpha = 0.94f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(ElectricEmerald.copy(alpha = 0.18f))
                                    .border(1.dp, ElectricEmerald.copy(alpha = 0.5f), RoundedCornerShape(50))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(ElectricEmerald)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (permissionState.foregroundServiceRunning) {
                                        "24/7 GATEWAY DAEMON ONLINE"
                                    } else {
                                        "GATEWAY STANDBY"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Switch(
                                checked = permissionState.foregroundServiceRunning,
                                onCheckedChange = onToggleForegroundService,
                                modifier = Modifier.testTag("toggle_foreground_service")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "bKash & Nagad Automation Engine",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Real-time SMS/Notification Regex Parser • Dynamic Amount Locks • Firebase Sync",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.82f)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiPill(
                                label = "Active Locks",
                                value = "${activeSessions.size}",
                                accent = ElectricEmerald,
                                modifier = Modifier.weight(1f)
                            )
                            KpiPill(
                                label = "Verified (${verifiedCount})",
                                value = "৳${String.format(Locale.US, "%,.0f", verifiedVolume)}",
                                accent = BkashPink,
                                modifier = Modifier.weight(1f)
                            )
                            KpiPill(
                                label = "Unmatched",
                                value = "$unclaimedCount",
                                accent = NagadOrange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 2. Runtime Permissions & 24/7 Battery Bypass Controls (Prompt 1 Requirements)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Permissions",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "24/7 Background & Runtime Permissions",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "SMS Receiver, Notification Listener & Battery Doze Bypass",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionControlRow(
                        icon = Icons.Default.Sms,
                        title = "SMS Read & Broadcast Receiver",
                        subtitle = if (permissionState.smsPermissionGranted) {
                            "RECEIVE_SMS & READ_SMS granted for bKash/Nagad"
                        } else {
                            "Grant runtime SMS permission to intercept bKash/Nagad TrxID"
                        },
                        isGranted = permissionState.smsPermissionGranted,
                        actionLabel = if (permissionState.smsPermissionGranted) "Scan Inbox" else "Grant SMS",
                        testTag = "btn_sms_permission",
                        onClick = {
                            if (permissionState.smsPermissionGranted) {
                                onScanDeviceInbox()
                            } else {
                                val perms = buildList {
                                    add(Manifest.permission.RECEIVE_SMS)
                                    add(Manifest.permission.READ_SMS)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }.toTypedArray()
                                smsPermissionLauncher.launch(perms)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PermissionControlRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "MFS Notification Listener Service",
                        subtitle = if (permissionState.notificationListenerEnabled) {
                            "Listening for bKash & Nagad app push notifications"
                        } else {
                            "Enable Notification Listener for instant push verification"
                        },
                        isGranted = permissionState.notificationListenerEnabled,
                        actionLabel = if (permissionState.notificationListenerEnabled) "Configured" else "Enable",
                        testTag = "btn_notification_listener",
                        onClick = {
                            runCatching {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PermissionControlRow(
                        icon = Icons.Default.BatteryChargingFull,
                        title = "Battery Optimization Bypass (24/7 Uptime)",
                        subtitle = if (permissionState.batteryOptimizationIgnored) {
                            "Unrestricted background execution active (Doze bypassed)"
                        } else {
                            "Whitelist app from Android OS battery optimization"
                        },
                        isGranted = permissionState.batteryOptimizationIgnored,
                        actionLabel = if (permissionState.batteryOptimizationIgnored) "Active" else "Bypass Doze",
                        testTag = "btn_battery_bypass",
                        onClick = {
                            runCatching {
                                val intent = Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }.onFailure {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        // 3. Interactive SMS Regex Parser & Webhook Simulator Lab
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Regex Lab",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Live SMS Regex Parser & Webhook Injector",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Test bKash/Nagad Regex extraction & trigger the Verification Engine",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset chips for rapid testing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                val target = activeSessions.firstOrNull()
                                if (target != null) {
                                    senderInput = target.mfsProvider
                                    val trx = if (target.mfsProvider == "bKash") {
                                        "BK" + (10000000..99999999).random()
                                    } else {
                                        "NG" + (100000..999999).random()
                                    }
                                    smsInput = MfsSmsParser.buildSampleSms(
                                        provider = target.mfsProvider,
                                        amount = target.lockedAmount,
                                        senderNumber = target.customerPhone,
                                        trxId = trx
                                    )
                                } else {
                                    senderInput = "bKash"
                                    smsInput = MfsSmsParser.buildSampleSms(
                                        provider = "bKash",
                                        amount = 501.00,
                                        senderNumber = "01712345678",
                                        trxId = "BKA55N82QP"
                                    )
                                }
                            },
                            label = { Text("Match Active Session") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("chip_preset_match_active")
                        )

                        AssistChip(
                            onClick = {
                                senderInput = "bKash"
                                val trx = "BKC" + (1000000..9999999).random()
                                smsInput = "Cash In Tk 502.00 from 01819876543 successful. Fee Tk 0.00. Balance Tk 18,420.00. TrxID $trx at 01/10/2026 22:25"
                            },
                            label = { Text("bKash Cash In") },
                            modifier = Modifier.testTag("chip_preset_bkash")
                        )

                        AssistChip(
                            onClick = {
                                senderInput = "Nagad"
                                val trx = "78N" + (10000..99999).random()
                                smsInput = "Money Received. Amount: Tk 1001.00. Sender: 01911223344. Ref: Order. TxnID: $trx. Balance: Tk 9,501.00. 01/10/2026 22:30"
                            },
                            label = { Text("Nagad Received") },
                            modifier = Modifier.testTag("chip_preset_nagad")
                        )

                        AssistChip(
                            onClick = {
                                senderInput = "bKash"
                                val trx = "BKU" + (1000000..9999999).random()
                                smsInput = "You have received Tk 499.00 from 01611002233. Ref WrongAmt. Fee Tk 0.00. Balance Tk 15,000.00. TrxID $trx at 01/10/2026 22:31"
                            },
                            label = { Text("Unmatched Amount") },
                            modifier = Modifier.testTag("chip_preset_unmatched")
                        )

                        AssistChip(
                            onClick = {
                                senderInput = "bKash"
                                smsInput = "You have received Tk 501.00 from 01712345678. Ref 1. Fee Tk 0.00. Balance Tk 12,450.00. TrxID BKA98X72KL at 01/10/2026 22:15"
                            },
                            label = { Text("Duplicate TrxID Abuse") },
                            modifier = Modifier.testTag("chip_preset_duplicate")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = senderInput,
                            onValueChange = { senderInput = it },
                            label = { Text("Sender ID (bKash/Nagad)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_sms_sender")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = smsInput,
                        onValueChange = { smsInput = it },
                        label = { Text("Incoming MFS SMS / Notification Payload") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_sms_body")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Regex Extraction Preview Box
                    AnimatedVisibility(visible = true) {
                        RegexExtractionCard(parsed = liveRegexPreview)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onProcessSms(senderInput, smsInput) },
                            enabled = liveRegexPreview != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_parse_and_verify"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verify & Send Webhook")
                        }

                        if (permissionState.smsPermissionGranted) {
                            OutlinedButton(
                                onClick = onScanDeviceInbox,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("btn_scan_inbox")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inbox,
                                    contentDescription = "Scan Inbox",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Inbox")
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun KpiPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun PermissionControlRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) ElectricEmerald.copy(alpha = 0.16f)
                        else NagadOrange.copy(alpha = 0.16f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isGranted) ElectricEmerald else NagadOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .height(48.dp)
                .testTag(testTag)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) ElectricEmerald else NagadOrange,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = actionLabel)
        }
    }
}

@Composable
private fun RegexExtractionCard(parsed: ParsedMfsSms?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        if (parsed == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Regex Did Not Match: Ensure message contains a valid bKash/Nagad TrxID & Amount (OTP messages are automatically ignored).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = ElectricEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE REGEX PARSED JSON PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricEmerald
                        )
                    }
                    Text(
                        text = "${parsed.mfsProvider} • ${parsed.transactionType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (parsed.mfsProvider == "bKash") BkashPink else NagadOrange,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ParsedFieldChip(
                        label = "trx_id",
                        value = parsed.trxId,
                        modifier = Modifier.weight(1f)
                    )
                    ParsedFieldChip(
                        label = "amount",
                        value = "৳${String.format(Locale.US, "%.2f", parsed.amount)}",
                        modifier = Modifier.weight(1f)
                    )
                    ParsedFieldChip(
                        label = "sender_number",
                        value = parsed.senderNumber,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ParsedFieldChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
