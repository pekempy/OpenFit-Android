package com.openfit.mobile.data.health

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Serial rate limiter matching OpenFit desktop's waitForApiSlot(): at most
 * one Google Health API request every 225ms, process-wide. Google Health v4
 * has a tight per-minute quota; without this a 20-request sync burst trips
 * 429s almost immediately. */
object HealthApiRateLimiter {
    private val mutex = Mutex()
    private var nextSlotAtMillis = 0L

    suspend fun <T> withSlot(block: suspend () -> T): T {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val slot = maxOf(now, nextSlotAtMillis)
            nextSlotAtMillis = slot + 225L
            val wait = slot - now
            if (wait > 0) kotlinx.coroutines.delay(wait)
        }
        return block()
    }
}

object HealthApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun create(): GoogleHealthApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(RetryOn429Interceptor())
            .addInterceptor(logging)
            .build()

        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(GoogleHealthApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(GoogleHealthApi::class.java)
    }
}

/** Mirrors the reference implementation's 429 handling: respect
 * Retry-After when present, otherwise exponential backoff, up to 2 retries. */
private class RetryOn429Interceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        var response = chain.proceed(chain.request())
        var attempt = 0
        while (response.code == 429 && attempt < 2) {
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
            val delayMillis = if (retryAfterSeconds != null && retryAfterSeconds > 0) {
                minOf(30_000L, retryAfterSeconds * 1000)
            } else {
                minOf(30_000L, 1100L * (1 shl attempt))
            }
            response.close()
            Thread.sleep(delayMillis)
            attempt += 1
            response = chain.proceed(chain.request())
        }
        return response
    }
}
