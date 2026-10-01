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
    INCREMENTAL_TAKA(
        label = "Incremental Taka (+৳1.00, +৳2.00)",
        description = "Base ৳500 -> ৳501.00, ৳502.00 ... up to +৳40.00 per active 5-min window"
    ),
    FRACTIONAL_PAISA(
        label = "Fractional Paisa (+৳0.01 .. +৳0.99)",
        description = "Base ৳500 -> ৳500.01, ৳500.02 ... up to +৳0.99 per active 5-min window"
    )
}

data class GatewayConfig(
    val webhookUrl: String = "https://asia-southeast1-mfs-paysync.cloudfunctions.net/smsWebhookReceiver",
    val firebaseRealtimeDbUrl: String = "https://mfs-paysync-default-rtdb.asia-southeast1.firebasedatabase.app",
    val secretKey: String = "whsec_live_bd_mfs_89a7c4e21f09",
    val merchantBkashNumber: String = "01711987654",
    val merchantNagadNumber: String = "01819876543",
    val dynamicAmountMode: DynamicAmountMode = DynamicAmountMode.INCREMENTAL_TAKA,
    val sessionTtlMinutes: Int = 5,
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
        val modeStr = prefs[KEY_DYNAMIC_MODE] ?: DynamicAmountMode.INCREMENTAL_TAKA.name
        val mode = runCatching { DynamicAmountMode.valueOf(modeStr) }
            .getOrDefault(DynamicAmountMode.INCREMENTAL_TAKA)

        GatewayConfig(
            webhookUrl = prefs[KEY_WEBHOOK_URL]
                ?: "https://asia-southeast1-mfs-paysync.cloudfunctions.net/smsWebhookReceiver",
            firebaseRealtimeDbUrl = prefs[KEY_FIREBASE_DB_URL]
                ?: "https://mfs-paysync-default-rtdb.asia-southeast1.firebasedatabase.app",
            secretKey = prefs[KEY_SECRET_KEY] ?: "whsec_live_bd_mfs_89a7c4e21f09",
            merchantBkashNumber = prefs[KEY_BKASH_NUMBER] ?: "01711987654",
            merchantNagadNumber = prefs[KEY_NAGAD_NUMBER] ?: "01819876543",
            dynamicAmountMode = mode,
            sessionTtlMinutes = prefs[KEY_SESSION_TTL] ?: 5,
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
            prefs[KEY_SESSION_TTL] = sessionTtlMinutes.coerceIn(1, 30)
            prefs[KEY_SYNC_FIREBASE] = syncToFirebaseRest
        }
    }

    suspend fun setBackgroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BG_SERVICE] = enabled
        }
    }
}
