package com.openfit.mobile.data.ai

import com.openfit.mobile.model.AiProviderConfig
import com.openfit.mobile.model.AiProviderException
import com.openfit.mobile.model.AiProviderKind
import com.openfit.mobile.model.AiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiProvider : AiProvider {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun complete(
        config: AiProviderConfig,
        systemPrompt: String,
        userPrompt: String,
    ): AiResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            throw AiProviderException("OpenAI API key is missing or blank")
        }

        val effectiveModel = config.model.ifBlank { DEFAULT_MODEL }

        val requestPayload = buildJsonObject {
            put("model", effectiveModel)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(CHAT_COMPLETIONS_URL)
            .header("Authorization", "Bearer ${config.apiKey.trim()}")
            .header("content-type", "application/json")
            .post(requestPayload.toRequestBody(MEDIA_TYPE_JSON))
            .build()

        val responseBodyString = try {
            AiHttpClient.client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorDetail = extractErrorMessage(body)
                    val message = if (errorDetail.isNotBlank()) {
                        "OpenAI API error (HTTP ${response.code}): $errorDetail"
                    } else {
                        "OpenAI API error (HTTP ${response.code})"
                    }
                    throw AiProviderException(message)
                }
                body
            }
        } catch (e: AiProviderException) {
            throw e
        } catch (e: Exception) {
            throw AiProviderException("OpenAI API request failed: ${e.message}", e)
        }

        try {
            val rootObj = json.parseToJsonElement(responseBodyString).jsonObject
            val choices = rootObj["choices"]?.jsonArray
            val firstChoice = choices?.firstOrNull()?.jsonObject
            val messageObj = firstChoice?.get("message")?.jsonObject
            val extractedText = messageObj?.get("content")?.jsonPrimitive?.contentOrNull
                ?: throw AiProviderException("OpenAI response did not contain message content")

            val actualModel = rootObj["model"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: effectiveModel

            AiResult(
                text = extractedText.trim(),
                providerKind = AiProviderKind.OPENAI,
                model = actualModel,
            )
        } catch (e: AiProviderException) {
            throw e
        } catch (e: Exception) {
            throw AiProviderException("Failed to parse OpenAI API response: ${e.message}", e)
        }
    }

    private fun extractErrorMessage(bodyString: String): String {
        if (bodyString.isBlank()) return ""
        return try {
            val root = json.parseToJsonElement(bodyString).jsonObject
            val errorElement = root["error"]
            when {
                errorElement is JsonObject -> {
                    errorElement["message"]?.jsonPrimitive?.contentOrNull
                        ?: errorElement["type"]?.jsonPrimitive?.contentOrNull
                        ?: bodyString
                }
                errorElement is JsonPrimitive -> {
                    errorElement.contentOrNull ?: bodyString
                }
                root["message"] != null -> {
                    root["message"]?.jsonPrimitive?.contentOrNull ?: bodyString
                }
                else -> bodyString
            }
        } catch (_: Exception) {
            bodyString
        }
    }

    private companion object {
        const val CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions"
        const val DEFAULT_MODEL = "gpt-5.1"
        val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
    }
}
