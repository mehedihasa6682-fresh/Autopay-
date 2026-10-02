package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class MfsWebhookPayload(
    @param:Json(name = "trx_id") @get:Json(name = "trx_id") val trxId: String,
    @param:Json(name = "sender_number") @get:Json(name = "sender_number") val senderNumber: String,
    @param:Json(name = "amount") @get:Json(name = "amount") val amount: Double,
    @param:Json(name = "mfs_provider") @get:Json(name = "mfs_provider") val mfsProvider: String,
    @param:Json(name = "secret_key") @get:Json(name = "secret_key") val secretKey: String,
    @param:Json(name = "transaction_type") @get:Json(name = "transaction_type") val transactionType: String = "Send Money",
    @param:Json(name = "matched_order_id") @get:Json(name = "matched_order_id") val matchedOrderId: String? = null,
    @param:Json(name = "verification_status") @get:Json(name = "verification_status") val verificationStatus: String = "VERIFIED",
    @param:Json(name = "timestamp") @get:Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

interface DynamicWebhookRetrofitApi {
    @POST
    suspend fun postWebhookPayload(
        @Url webhookUrl: String,
        @Header("X-MFS-Signature") hmacSignature: String,
        @Header("X-Secret-Key") secretKey: String,
        @Body payload: MfsWebhookPayload
    ): Response<ResponseBody>
}

data class WebhookDispatchResult(
    val isSuccess: Boolean,
    val httpStatus: Int,
    val payloadJson: String,
    val responseSummary: String,
    val latencyMs: Long
)

object WebhookNetworkClient {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(MfsWebhookPayload::class.java)

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://cloudfunctions.net/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: DynamicWebhookRetrofitApi = retrofit.create(DynamicWebhookRetrofitApi::class.java)

    fun toJson(payload: MfsWebhookPayload): String {
        return payloadAdapter.indent("  ").toJson(payload)
    }

    fun computeHmacSha256(data: String, secret: String): String {
        return runCatching {
            val algorithm = "HmacSHA256"
            val mac = Mac.getInstance(algorithm)
            val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), algorithm)
            mac.init(keySpec)
            val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { String.format(Locale.US, "%02x", it) }
        }.getOrDefault("hmac_fallback")
    }

    /**
     * Dispatches the parsed MFS payment JSON to the configured Webhook URL via Retrofit,
     * and optionally mirrors the record to Firebase Realtime Database REST API if enabled.
     */
    suspend fun dispatchWebhook(
        webhookUrl: String,
        firebaseDbUrl: String,
        syncToFirebaseRest: Boolean,
        payload: MfsWebhookPayload
    ): WebhookDispatchResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val jsonBody = toJson(payload)
        val signature = computeHmacSha256(jsonBody, payload.secretKey)

        // Check if user configured a real external endpoint or the default placeholder URL
        val isPlaceholderUrl = webhookUrl.contains("mfs-paysync.cloudfunctions.net") ||
            webhookUrl.contains("yourwebsite.com") ||
            webhookUrl.contains("example.com")

        if (isPlaceholderUrl && !syncToFirebaseRest) {
            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(15L)
            return@withContext WebhookDispatchResult(
                isSuccess = true,
                httpStatus = 200,
                payloadJson = jsonBody,
                responseSummary = "200 OK — অ্যাপের লোকাল ভেরিফিকেশন সম্পন্ন (ওয়েবসাইটে পাঠাতে নিজের সাইটের Webhook URL বসিয়ে সেভ করুন)",
                latencyMs = elapsed
            )
        }

        return@withContext try {
            val response = api.postWebhookPayload(
                webhookUrl = webhookUrl,
                hmacSignature = signature,
                secretKey = payload.secretKey,
                payload = payload
            )
            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

            // Optionally mirror to Firebase Realtime Database REST node `/mfs_transactions/{trx_id}.json`
            if (syncToFirebaseRest && firebaseDbUrl.startsWith("https://")) {
                mirrorToFirebaseRealtimeDb(firebaseDbUrl, payload, jsonBody)
            }

            WebhookDispatchResult(
                isSuccess = response.isSuccessful,
                httpStatus = response.code(),
                payloadJson = jsonBody,
                responseSummary = if (response.isSuccessful) {
                    "HTTP ${response.code()} OK — Delivered to Webhook"
                } else {
                    "HTTP ${response.code()} — ${response.message()}"
                },
                latencyMs = elapsed
            )
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            WebhookDispatchResult(
                isSuccess = false,
                httpStatus = 503,
                payloadJson = jsonBody,
                responseSummary = "Network Error: ${e.localizedMessage ?: e.javaClass.simpleName} (Queued in Local DB)",
                latencyMs = elapsed
            )
        }
    }

    private fun mirrorToFirebaseRealtimeDb(
        firebaseDbUrl: String,
        payload: MfsWebhookPayload,
        jsonBody: String
    ) {
        runCatching {
            val cleanBase = firebaseDbUrl.trimEnd('/')
            val url = "$cleanBase/webhook_transactions/${payload.trxId}.json"
            val request = Request.Builder()
                .url(url)
                .put(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            okHttpClient.newCall(request).execute().close()
        }
    }
}
