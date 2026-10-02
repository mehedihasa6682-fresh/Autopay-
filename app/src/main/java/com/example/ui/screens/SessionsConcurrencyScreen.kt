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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    var customerPhone by remember { mutableStateOf("01715889900") }
    var baseAmountText by remember { mutableStateOf("500") }
    var customerName by remember { mutableStateOf("") }
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

        // ১. শুধু সেন্ডার নাম্বার যোগ করার সহজ বক্স
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Sender Number",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "কাস্টমারের সেন্ডার নাম্বার যোগ করুন",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "সাইটে কাস্টমার যে নাম্বার দিয়েছে সেটি দিন — ওই নাম্বার থেকে টাকা আসলেই অটো ভেরিফাই হবে",
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

                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("কাস্টমারের সেন্ডার নাম্বার (যেমন: 01715889900)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_session_customer_phone")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = baseAmountText,
                            onValueChange = { baseAmountText = it },
                            label = { Text("টাকার পরিমাণ (৳)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_session_base_amount")
                        )
                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            label = { Text("নাম (ঐচ্ছিক)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_session_customer_name")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val base = baseAmountText.toDoubleOrNull() ?: 500.0
                            onInitiateSession(customerName, customerPhone, base, selectedProvider)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_initiate_session")
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("সেন্ডার নাম্বার ভেরিফিকেশনে যুক্ত করুন")
                    }
                }
            }
        }

        // ২. ফিল্টার বাটন
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val activeCount = sessions.count { it.status == "ACTIVE" }
                val matchedCount = sessions.count { it.status == "MATCHED" }

                FilterChip(
                    selected = statusFilter == "ALL",
                    onClick = { statusFilter = "ALL" },
                    label = { Text("সব (${sessions.size})") },
                    modifier = Modifier.testTag("filter_sessions_all")
                )
                FilterChip(
                    selected = statusFilter == "ACTIVE",
                    onClick = { statusFilter = "ACTIVE" },
                    label = { Text("অপেক্ষমান ($activeCount)") },
                    modifier = Modifier.testTag("filter_sessions_active")
                )
                FilterChip(
                    selected = statusFilter == "MATCHED",
                    onClick = { statusFilter = "MATCHED" },
                    label = { Text("ভেরিফাইড ($matchedCount)") },
                    modifier = Modifier.testTag("filter_sessions_matched")
                )
            }
        }

        // ৩. সেন্ডার নাম্বার কার্ড লিস্ট
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
                            "ভেরিফাইড (TrxID: ${session.matchedTrxId})"
                        } else if (session.status == "ACTIVE") {
                            String.format(Locale.US, "অপেক্ষমান • %02d:%02d", mins, secs)
                        } else {
                            "সময় শেষ"
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
                        text = "সেন্ডার: ${session.customerPhone}",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = session.customerName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "টাকার পরিমাণ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "৳${String.format(Locale.US, "%.0f", session.lockedAmount)}",
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
                            text = "এই নাম্বার থেকে এসএমএস আসলেই ভেরিফাই হবে",
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
                        Text("টেস্ট SMS", color = Color.White)
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
                        text = "সেন্ডার নাম্বার মিলিয়ে অটো ভেরিফাই সম্পন্ন ও ওয়েবসাইটে পাঠানো হয়েছে",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricEmerald
                    )
                }
            }
        }
    }
}
