package com.openfit.mobile.data.ai

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared OkHttp client for the AI provider implementations - generous
 * timeouts since LLM completions can legitimately take 30-60s, especially
 * for a self-hosted CUSTOM endpoint. */
object AiHttpClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
