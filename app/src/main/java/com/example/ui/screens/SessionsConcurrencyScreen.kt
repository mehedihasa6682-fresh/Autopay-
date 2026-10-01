package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.PaymentSessionEntity
import com.example.data.preferences.GatewayConfig
import com.example.ui.theme.BkashPink
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.NagadOrange
import java.util.Locale

@Composable
fun SessionsConcurrencyScreen(
    sessions: List<PaymentSessionEntity>,
    config: GatewayConfig,
    nowMillis: Long,
    onInitiateSession: (String, String, Double, String) -> Unit,
    onSpawn20ConcurrentSessions: (Double, String) -> Unit,
    onSimulateInstantMatch: (PaymentSessionEntity) -> Unit
) {
    var customerName by remember { mutableStateOf("Rahim Uddin") }
    var customerPhone by remember { mutableStateOf("01715889900") }
    var baseAmountText by remember { mutableStateOf("500") }
    var selectedProvider by remember { mutableStateOf("bKash") }
    var statusFilter by remember { mutableStateOf("ALL") }

    val filteredSessions = remember(sessions, statusFilter) {
        when (statusFilter) {
            "ACTIVE" -> sessions.filter { it.status == "ACTIVE" }
            "MATCHED" -> sessions.filter { it.status == "MATCHED" }
            "EXPIRED" -> sessions.filter { it.status == "EXPIRED" }
            else -> sessions
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("sessions_concurrency_list"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Dynamic Amount Session Generator & 20-User Concurrency Stress-Tester
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LockClock,
                            contentDescription = "Dynamic Amount Lock",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Initiate Payment Session (Dynamic Amount)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Mode: ${config.dynamicAmountMode.label} • ${config.sessionTtlMinutes}m Countdown Lock",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedProvider == "bKash",
                            onClick = { selectedProvider = "bKash" },
                            label = { Text("bKash (${config.merchantBkashNumber})") },
                            modifier = Modifier.testTag("select_provider_bkash")
                        )
                        FilterChip(
                            selected = selectedProvider == "Nagad",
                            onClick = { selectedProvider = "Nagad" },
                            label = { Text("Nagad (${config.merchantNagadNumber})") },
                            modifier = Modifier.testTag("select_provider_nagad")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            label = { Text("Customer Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_session_customer_name")
                        )
                        OutlinedTextField(
                            value = customerPhone,
                            onValueChange = { customerPhone = it },
                            label = { Text("Sender Mobile") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_session_customer_phone")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = baseAmountText,
                        onValueChange = { baseAmountText = it },
                        label = { Text("Base Order Amount (৳ BDT)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_session_base_amount")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val base = baseAmountText.toDoubleOrNull() ?: 500.0
                                onInitiateSession(customerName, customerPhone, base, selectedProvider)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_initiate_session")
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lock Session")
                        }

                        OutlinedButton(
                            onClick = {
                                val base = baseAmountText.toDoubleOrNull() ?: 500.0
                                onSpawn20ConcurrentSessions(base, selectedProvider)
                            },
                            modifier = Modifier
                                .weight(1.1f)
                                .height(48.dp)
                                .testTag("btn_spawn_20_sessions")
                        ) {
                            Icon(
                                Icons.Default.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("20x Concurrent Burst")
                        }
                    }
                }
            }
        }

        // 2. Filter Row for Payment Sessions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val activeCount = sessions.count { it.status == "ACTIVE" }
                val matchedCount = sessions.count { it.status == "MATCHED" }
                val expiredCount = sessions.count { it.status == "EXPIRED" }

                FilterChip(
                    selected = statusFilter == "ALL",
                    onClick = { statusFilter = "ALL" },
                    label = { Text("All (${sessions.size})") },
                    modifier = Modifier.testTag("filter_sessions_all")
                )
                FilterChip(
                    selected = statusFilter == "ACTIVE",
                    onClick = { statusFilter = "ACTIVE" },
                    label = { Text("Active ($activeCount)") },
                    modifier = Modifier.testTag("filter_sessions_active")
                )
                FilterChip(
                    selected = statusFilter == "MATCHED",
                    onClick = { statusFilter = "MATCHED" },
                    label = { Text("Verified ($matchedCount)") },
                    modifier = Modifier.testTag("filter_sessions_matched")
                )
                FilterChip(
                    selected = statusFilter == "EXPIRED",
                    onClick = { statusFilter = "EXPIRED" },
                    label = { Text("Expired ($expiredCount)") },
                    modifier = Modifier.testTag("filter_sessions_expired")
                )
            }
        }

        // 3. Payment Session Cards with Live 5-Minute Countdown Timer
        items(filteredSessions, key = { it.sessionId }) { session ->
            PaymentSessionCard(
                session = session,
                nowMillis = nowMillis,
                onSimulateInstantMatch = { onSimulateInstantMatch(session) }
            )
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun PaymentSessionCard(
    session: PaymentSessionEntity,
    nowMillis: Long,
    onSimulateInstantMatch: () -> Unit
) {
    val providerColor = if (session.mfsProvider == "bKash") BkashPink else NagadOrange
    val remainingMillis = (session.expiresAt - nowMillis).coerceAtLeast(0L)
    val totalWindowMillis = (session.expiresAt - session.createdAt).coerceAtLeast(1L)
    val progress = (remainingMillis.toFloat() / totalWindowMillis.toFloat()).coerceIn(0f, 1f)
    val mins = (remainingMillis / 1000) / 60
    val secs = (remainingMillis / 1000) % 60

    val statusColor = when (session.status) {
        "MATCHED" -> ElectricEmerald
        "ACTIVE" -> providerColor
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = statusColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(providerColor.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = session.mfsProvider,
                            style = MaterialTheme.typography.labelLarge,
                            color = providerColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = session.orderId,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (session.status == "MATCHED") {
                            "VERIFIED (${session.matchedTrxId})"
                        } else if (session.status == "ACTIVE") {
                            String.format(Locale.US, "LOCKED • %02d:%02d", mins, secs)
                        } else {
                            "EXPIRED"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = session.customerName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Sender: ${session.customerPhone}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Base: ৳${String.format(Locale.US, "%.2f", session.baseAmount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "৳${String.format(Locale.US, "%.2f", session.lockedAmount)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = statusColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (session.status == "ACTIVE") {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = providerColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Unique amount reserved for 5m window",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onSimulateInstantMatch,
                        colors = ButtonDefaults.buttonColors(containerColor = providerColor),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_instant_match_${session.sessionId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simulate SMS", color = Color.White)
                    }
                }
            } else if (session.status == "MATCHED") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ElectricEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Auto-matched via SMS Regex & synced to Webhook",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricEmerald
                    )
                }
            }
        }
    }
}
