package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.OrderEntity
import com.example.data.local.UnmatchedTransactionEntity
import com.example.data.local.UserEntity
import com.example.ui.theme.BkashPink
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.NagadOrange
import java.util.Locale

@Composable
fun UnmatchedQueueScreen(
    unmatchedList: List<UnmatchedTransactionEntity>,
    orders: List<OrderEntity>,
    users: List<UserEntity>,
    onClaimManualTrx: (String, String, String) -> Unit
) {
    val unclaimedTransactions = remember(unmatchedList) {
        unmatchedList.filter { it.status == "UNCLAIMED" }
    }

    var senderPhoneInput by remember(unclaimedTransactions) {
        mutableStateOf(unclaimedTransactions.firstOrNull()?.senderNumber ?: "")
    }
    var orderIdInput by remember { mutableStateOf("") }
    var trxIdInput by remember { mutableStateOf("") }
    var activeTableTab by remember { mutableStateOf("UNMATCHED") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("unmatched_queue_list"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // ১. শুধু সেন্ডার নাম্বার দিয়ে ম্যানুয়াল ভেরিফিকেশন বক্স
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Manual Verify",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "সেন্ডার নাম্বার দিয়ে পেমেন্ট ভেরিফাই",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "কোনো কাস্টমার আগে টাকা পাঠিয়ে থাকলে শুধু তার সেন্ডার নাম্বার দিয়ে ভেরিফাই করুন",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (unclaimedTransactions.isNotEmpty()) {
                        Text(
                            text = "অপেক্ষমান এসএমএস (ক্লিক করলে নাম্বার বসবে):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            unclaimedTransactions.take(5).forEach { tx ->
                                AssistChip(
                                    onClick = {
                                        senderPhoneInput = tx.senderNumber
                                        trxIdInput = tx.trxId
                                    },
                                    label = {
                                        Text("${tx.senderNumber} (৳${String.format(Locale.US, "%.0f", tx.amount)})")
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = senderPhoneInput,
                        onValueChange = { senderPhoneInput = it },
                        label = { Text("কাস্টমারের সেন্ডার নাম্বার (যেমন: 01611556677)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_claim_sender_phone")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = orderIdInput,
                            onValueChange = { orderIdInput = it },
                            label = { Text("অর্ডার আইডি (ঐচ্ছিক)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_claim_order_id")
                        )
                        OutlinedTextField(
                            value = trxIdInput,
                            onValueChange = { trxIdInput = it },
                            label = { Text("TrxID (ঐচ্ছিক)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_claim_trx_id")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onClaimManualTrx(orderIdInput, trxIdInput, senderPhoneInput)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_submit_manual_claim")
                    ) {
                        Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("সেন্ডার নাম্বার দেখে ভেরিফাই করুন")
                    }
                }
            }
        }

        // ২. তালিকা দেখার ৩টি ট্যাব
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTableTab == "UNMATCHED",
                    onClick = { activeTableTab = "UNMATCHED" },
                    label = { Text("আনম্যাচড (${unmatchedList.size})") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.PendingActions,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("tab_table_unmatched")
                )
                FilterChip(
                    selected = activeTableTab == "ORDERS",
                    onClick = { activeTableTab = "ORDERS" },
                    label = { Text("সব অর্ডার (${orders.size})") },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("tab_table_orders")
                )
                FilterChip(
                    selected = activeTableTab == "USERS",
                    onClick = { activeTableTab = "USERS" },
                    label = { Text("কাস্টমার (${users.size})") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("tab_table_users")
                )
            }
        }

        when (activeTableTab) {
            "UNMATCHED" -> {
                items(unmatchedList, key = { it.trxId }) { tx ->
                    UnmatchedTxCard(
                        tx = tx,
                        onSelectForClaim = {
                            senderPhoneInput = tx.senderNumber
                            trxIdInput = tx.trxId
                        }
                    )
                }
            }
            "ORDERS" -> {
                items(orders, key = { it.orderId }) { order ->
                    OrderRecordCard(
                        order = order,
                        onSelectOrder = {
                            orderIdInput = order.orderId
                            senderPhoneInput = order.customerPhone
                        }
                    )
                }
            }
            "USERS" -> {
                items(users, key = { it.userId }) { user ->
                    UserRecordCard(user = user)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun UnmatchedTxCard(
    tx: UnmatchedTransactionEntity,
    onSelectForClaim: () -> Unit
) {
    val providerColor = if (tx.mfsProvider == "bKash") BkashPink else NagadOrange
    val isClaimed = tx.status == "CLAIMED"

    Card(
        onClick = onSelectForClaim,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isClaimed) ElectricEmerald.copy(alpha = 0.4f) else NagadOrange.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
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
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = tx.mfsProvider,
                            style = MaterialTheme.typography.labelLarge,
                            color = providerColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "সেন্ডার: ${tx.senderNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "৳${String.format(Locale.US, "%.0f", tx.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isClaimed) ElectricEmerald else NagadOrange,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "TrxID: ${tx.trxId} • ${tx.reason}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isClaimed) {
                    "ভেরিফাইড (${tx.claimedByOrderId})"
                } else {
                    "অপেক্ষমান — ভেরিফাই করতে এই কার্ডে ট্যাপ করুন"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isClaimed) ElectricEmerald else NagadOrange,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OrderRecordCard(
    order: OrderEntity,
    onSelectOrder: () -> Unit
) {
    val isVerified = order.status == "VERIFIED"
    val statusColor = if (isVerified) ElectricEmerald else NagadOrange

    Card(
        onClick = onSelectOrder,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "সেন্ডার: ${order.customerPhone}",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${order.mfsProvider}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (order.mfsProvider == "bKash") BkashPink else NagadOrange
                    )
                }
                Text(
                    text = "${order.customerName} (${order.orderId})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (order.trxId != null) "TrxID: ${order.trxId}" else "এসএমএসের অপেক্ষায়",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "৳${String.format(Locale.US, "%.0f", order.payableAmount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isVerified) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ElectricEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isVerified) "ভেরিফাইড" else "অপেক্ষমান",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun UserRecordCard(user: UserEntity) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "সেন্ডার: ${user.phone}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "৳${String.format(Locale.US, "%,.0f", user.totalVerifiedAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricEmerald,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${user.verifiedOrdersCount} বার পেমেন্ট করেছে",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
