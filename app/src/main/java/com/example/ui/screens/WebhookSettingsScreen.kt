package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.WebhookLogEntity
import com.example.data.preferences.DynamicAmountMode
import com.example.data.preferences.GatewayConfig
import com.example.ui.theme.BkashPink
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.NagadOrange
import java.util.Locale

@Composable
fun WebhookSettingsScreen(
    config: GatewayConfig,
    webhookLogs: List<WebhookLogEntity>,
    onSaveConfig: (
        webhookUrl: String,
        firebaseDbUrl: String,
        secretKey: String,
        bkashNumber: String,
        nagadNumber: String,
        dynamicMode: DynamicAmountMode,
        ttlMinutes: Int,
        syncToFirebaseRest: Boolean
    ) -> Unit,
    onClearLogs: () -> Unit
) {
    var webhookUrl by remember(config.webhookUrl) { mutableStateOf(config.webhookUrl) }
    var firebaseDbUrl by remember(config.firebaseRealtimeDbUrl) { mutableStateOf(config.firebaseRealtimeDbUrl) }
    var secretKey by remember(config.secretKey) { mutableStateOf(config.secretKey) }
    var bkashNumber by remember(config.merchantBkashNumber) { mutableStateOf(config.merchantBkashNumber) }
    var nagadNumber by remember(config.merchantNagadNumber) { mutableStateOf(config.merchantNagadNumber) }
    var dynamicMode by remember(config.dynamicAmountMode) { mutableStateOf(config.dynamicAmountMode) }
    var syncToFirebase by remember(config.syncToFirebaseRest) { mutableStateOf(config.syncToFirebaseRest) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("webhook_settings_list"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Retrofit Webhook & Firebase Realtime DB Configuration Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Webhook Config",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Retrofit Webhook & Firebase Endpoint",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Dispatches { trx_id, sender_number, amount, mfs_provider, secret_key }",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = webhookUrl,
                        onValueChange = { webhookUrl = it },
                        label = { Text("Cloud Function / Webhook API URL") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_webhook_url")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = firebaseDbUrl,
                        onValueChange = { firebaseDbUrl = it },
                        label = { Text("Firebase Realtime Database URL") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_firebase_db_url")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = secretKey,
                        onValueChange = { secretKey = it },
                        label = { Text("Webhook Secret Key (HMAC-SHA256 & JSON)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_secret_key")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = bkashNumber,
                            onValueChange = { bkashNumber = it },
                            label = { Text("Personal bKash No.") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_bkash_number")
                        )
                        OutlinedTextField(
                            value = nagadNumber,
                            onValueChange = { nagadNumber = it },
                            label = { Text("Personal Nagad No.") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_nagad_number")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Dynamic Amount Concurrency Strategy:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = dynamicMode == DynamicAmountMode.INCREMENTAL_TAKA,
                            onClick = { dynamicMode = DynamicAmountMode.INCREMENTAL_TAKA },
                            label = { Text("৳501.00, ৳502.00 (+৳1)") },
                            modifier = Modifier.testTag("chip_mode_taka")
                        )
                        FilterChip(
                            selected = dynamicMode == DynamicAmountMode.FRACTIONAL_PAISA,
                            onClick = { dynamicMode = DynamicAmountMode.FRACTIONAL_PAISA },
                            label = { Text("৳500.01, ৳500.02 (+৳0.01)") },
                            modifier = Modifier.testTag("chip_mode_paisa")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Direct Firebase Realtime DB REST Mirroring",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Also PUT verified JSON directly to /webhook_transactions/{trx_id}.json",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = syncToFirebase,
                            onCheckedChange = { syncToFirebase = it },
                            modifier = Modifier.testTag("switch_firebase_rest_sync")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onSaveConfig(
                                webhookUrl,
                                firebaseDbUrl,
                                secretKey,
                                bkashNumber,
                                nagadNumber,
                                dynamicMode,
                                config.sessionTtlMinutes,
                                syncToFirebase
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_save_gateway_config")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Gateway & Webhook Configuration")
                    }
                }
            }
        }

        // 2. Live Retrofit Webhook Delivery Logs Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Webhook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Retrofit Webhook Dispatch Logs (${webhookLogs.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (webhookLogs.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearLogs,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_clear_webhook_logs")
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear Logs",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        }

        items(webhookLogs, key = { it.id }) { log ->
            WebhookLogCard(log = log)
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun WebhookLogCard(log: WebhookLogEntity) {
    val providerColor = if (log.mfsProvider == "bKash") BkashPink else NagadOrange
    val statusColor = if (log.isSuccess) ElectricEmerald else MaterialTheme.colorScheme.error

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "HTTP ${log.httpStatus} (${log.latencyMs}ms)",
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${log.mfsProvider} • ${log.trxId}",
                        style = MaterialTheme.typography.labelLarge,
                        color = providerColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "৳${String.format(Locale.US, "%.2f", log.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = log.responseSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp)
            ) {
                Text(
                    text = log.payloadJson,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
