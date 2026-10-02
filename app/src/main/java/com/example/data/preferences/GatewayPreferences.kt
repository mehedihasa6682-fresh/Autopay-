package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mfs_gateway_prefs")

enum class DynamicAmountMode(val label: String, val description: String) {
    SENDER_NUMBER_ONLY(
        label = "শুধু সেন্ডার নাম্বার দিয়ে ভেরিফাই (Exact Amount)",
        description = "কাস্টমার সাইটে শুধু নিজের নাম্বার দেবে, অ্যাপ সেন্ডার নাম্বার ও টাকার পরিমাণ মিলিয়ে ভেরিফাই করবে"
    ),
    INCREMENTAL_TAKA(
        label = "ডাইনামিক টাকা (+৳1, +৳2)",
        description = "একই সময়ে অনেকে পাঠালে ৳501, ৳502 হিসেবে আলাদা করবে"
    ),
    FRACTIONAL_PAISA(
        label = "ডাইনামিক পয়সা (+৳0.01, +৳0.02)",
        description = "একই সময়ে অনেকে পাঠালে ৳500.01, ৳500.02 হিসেবে আলাদা করবে"
    )
}

data class GatewayConfig(
    val webhookUrl: String = "https://yourwebsite.com/api/mfs-webhook.php",
    val firebaseRealtimeDbUrl: String = "https://mfs-paysync-default-rtdb.asia-southeast1.firebasedatabase.app",
    val secretKey: String = "whsec_live_bd_mfs_89a7c4e21f09",
    val merchantBkashNumber: String = "01711987654",
    val merchantNagadNumber: String = "01819876543",
    val dynamicAmountMode: DynamicAmountMode = DynamicAmountMode.SENDER_NUMBER_ONLY,
    val sessionTtlMinutes: Int = 15,
    val isBackgroundServiceEnabled: Boolean = true,
    val syncToFirebaseRest: Boolean = false
)

class GatewayPreferences(private val context: Context) {

    companion object {
        private val KEY_WEBHOOK_URL = stringPreferencesKey("webhook_url")
        private val KEY_FIREBASE_DB_URL = stringPreferencesKey("firebase_db_url")
        private val KEY_SECRET_KEY = stringPreferencesKey("secret_key")
        private val KEY_BKASH_NUMBER = stringPreferencesKey("merchant_bkash_number")
        private val KEY_NAGAD_NUMBER = stringPreferencesKey("merchant_nagad_number")
        private val KEY_DYNAMIC_MODE = stringPreferencesKey("dynamic_amount_mode")
        private val KEY_SESSION_TTL = intPreferencesKey("session_ttl_minutes")
        private val KEY_BG_SERVICE = booleanPreferencesKey("bg_service_enabled")
        private val KEY_SYNC_FIREBASE = booleanPreferencesKey("sync_to_firebase_rest")
    }

    val configFlow: Flow<GatewayConfig> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[KEY_DYNAMIC_MODE] ?: DynamicAmountMode.SENDER_NUMBER_ONLY.name
        val mode = runCatching { DynamicAmountMode.valueOf(modeStr) }
            .getOrDefault(DynamicAmountMode.SENDER_NUMBER_ONLY)

        GatewayConfig(
            webhookUrl = prefs[KEY_WEBHOOK_URL]
                ?: "https://yourwebsite.com/api/mfs-webhook.php",
            firebaseRealtimeDbUrl = prefs[KEY_FIREBASE_DB_URL]
                ?: "https://mfs-paysync-default-rtdb.asia-southeast1.firebasedatabase.app",
            secretKey = prefs[KEY_SECRET_KEY] ?: "whsec_live_bd_mfs_89a7c4e21f09",
            merchantBkashNumber = prefs[KEY_BKASH_NUMBER] ?: "01711987654",
            merchantNagadNumber = prefs[KEY_NAGAD_NUMBER] ?: "01819876543",
            dynamicAmountMode = mode,
            sessionTtlMinutes = prefs[KEY_SESSION_TTL] ?: 15,
            isBackgroundServiceEnabled = prefs[KEY_BG_SERVICE] ?: true,
            syncToFirebaseRest = prefs[KEY_SYNC_FIREBASE] ?: false
        )
    }

    suspend fun updateConfig(
        webhookUrl: String,
        firebaseRealtimeDbUrl: String,
        secretKey: String,
        merchantBkashNumber: String,
        merchantNagadNumber: String,
        dynamicAmountMode: DynamicAmountMode,
        sessionTtlMinutes: Int,
        syncToFirebaseRest: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WEBHOOK_URL] = webhookUrl.trim()
            prefs[KEY_FIREBASE_DB_URL] = firebaseRealtimeDbUrl.trim().trimEnd('/')
            prefs[KEY_SECRET_KEY] = secretKey.trim()
            prefs[KEY_BKASH_NUMBER] = merchantBkashNumber.trim()
            prefs[KEY_NAGAD_NUMBER] = merchantNagadNumber.trim()
            prefs[KEY_DYNAMIC_MODE] = dynamicAmountMode.name
            prefs[KEY_SESSION_TTL] = sessionTtlMinutes.coerceIn(1, 60)
            prefs[KEY_SYNC_FIREBASE] = syncToFirebaseRest
        }
    }

    suspend fun setBackgroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BG_SERVICE] = enabled
        }
    }
}
