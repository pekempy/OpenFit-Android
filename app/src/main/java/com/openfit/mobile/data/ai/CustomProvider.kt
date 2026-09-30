package com.openfit.mobile.data.ai

import com.openfit.mobile.model.AiProviderConfig
import com.openfit.mobile.model.AiProviderException
import com.openfit.mobile.model.AiProviderKind
import com.openfit.mobile.model.AiResult
import com.openfit.mobile.model.CustomWireFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class CustomProvider : AiProvider {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun complete(
        config: AiProviderConfig,
        systemPrompt: String,
        userPrompt: String,
    ): AiResult = withContext(Dispatchers.IO) {
        if (config.baseUrl.isBlank()) {
            throw AiProviderException("Custom AI endpoint base URL is not configured.")
        }

        val baseUrl = config.baseUrl.trim().trimEnd('/')
        val defaultPath = if (config.wireFormat == CustomWireFormat.OPENAI_CHAT) "/v1/chat/completions" else "/api/chat"
        val rawPath = if (config.chatPath.isNotBlank()) config.chatPath.trim() else defaultPath
        val chatPath = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        val url = "$baseUrl$chatPath"

        val requestPayload = when (config.wireFormat) {
            CustomWireFormat.SIMPLE_MESSAGE -> buildJsonObject {
                put("message", "$systemPrompt\n\n$userPrompt")
                if (config.model.isNotBlank()) {
                    put("model", config.model)
                }
                if (config.sessionId.isNotBlank()) {
                    put("session", config.sessionId)
                }
            }
            CustomWireFormat.OPENAI_CHAT -> buildJsonObject {
                if (config.model.isNotBlank()) {
                    put("model", config.model)
                }
                put("messages", buildJsonArray {
                    add(buildJsonObject { put("role", "system"); put("content", systemPrompt) })
                    add(buildJsonObject { put("role", "user"); put("content", userPrompt) })
                })
                put("stream", false)
            }
        }.toString()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("content-type", "application/json")
            .post(requestPayload.toRequestBody(MEDIA_TYPE_JSON))

        if (config.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey.trim()}")
        }

        val request = requestBuilder.build()

        val responseBodyString = try {
            AiHttpClient.client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AiProviderException(
                        "Custom AI request failed with HTTP ${response.code}: ${body.take(300)}"
                    )
                }
                body
            }
        } catch (e: AiProviderException) {
            throw e
        } catch (e: Exception) {
            throw AiProviderException("Custom AI request failed: ${e.message}", e)
        }

        val extractedText = try {
            val rootElement = json.parseToJsonElement(responseBodyString)
            val rootObj = rootElement as? JsonObject
                ?: throw AiProviderException(
                    "Expected JSON object in response: ${responseBodyString.take(300)}"
                )

            extractOpenAiChoiceText(rootObj)
                ?: CANDIDATE_KEYS.firstNotNullOfOrNull { key ->
                    (rootObj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                }
                ?: throw AiProviderException(
                    "No text answer found in response (checked OpenAI choices[0].message.content and keys $CANDIDATE_KEYS): ${responseBodyString.take(300)}"
                )
        } catch (e: AiProviderException) {
            throw e
        } catch (e: Exception) {
            throw AiProviderException(
                "Failed to parse custom AI response: ${e.message}. Response preview: ${responseBodyString.take(300)}",
                e,
            )
        }

        AiResult(
            text = extractedText.trim(),
            providerKind = AiProviderKind.CUSTOM,
            model = config.model,
        )
    }

    /** Tries the standard OpenAI chat-completions response shape
     * (choices[0].message.content) regardless of which wire format was
     * requested, since some servers reply in this shape even when the
     * request used the simple format. Returns null rather than throwing so
     * callers can fall back to the generic candidate-key scan. */
    private fun extractOpenAiChoiceText(root: JsonObject): String? {
        val choices = root["choices"] as? JsonArray ?: return null
        val firstChoice = choices.firstOrNull() as? JsonObject ?: return null
        val message = firstChoice["message"] as? JsonObject
        val content = message?.get("content") as? JsonPrimitive
        return content?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private companion object {
        val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
        val CANDIDATE_KEYS = listOf("response", "message", "text", "reply", "content", "answer")
    }
}
