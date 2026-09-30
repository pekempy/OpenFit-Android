package com.openfit.mobile.data.ai

import com.openfit.mobile.model.AiProviderConfig
import com.openfit.mobile.model.AiProviderException
import com.openfit.mobile.model.AiProviderKind
import com.openfit.mobile.model.AiResult
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
import java.net.URLEncoder

class GeminiProvider : AiProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun complete(
        config: AiProviderConfig,
        systemPrompt: String,
        userPrompt: String,
    ): AiResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            throw AiProviderException("Gemini API key is missing or blank")
        }

        val model = config.model.ifBlank { "gemini-2.5-flash" }
        val encodedKey = URLEncoder.encode(config.apiKey, "UTF-8")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$encodedKey"

        val requestJson = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject {
                        put("text", systemPrompt)
                    })
                })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", userPrompt)
                        })
                    })
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        val response = try {
            AiHttpClient.client.newCall(request).execute()
        } catch (e: Exception) {
            throw AiProviderException("Network error calling Gemini API: ${e.message}", e)
        }

        response.use { resp ->
            val responseBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val errorMessage = try {
                    val errorElement = json.parseToJsonElement(responseBody)
                    ((errorElement as? JsonObject)?.get("error") as? JsonObject)
                        ?.get("message")
                        ?.let { (it as? JsonPrimitive)?.contentOrNull }
                } catch (_: Exception) {
                    null
                }

                val detail = when {
                    !errorMessage.isNullOrBlank() -> ": $errorMessage"
                    responseBody.isNotBlank() -> ": $responseBody"
                    else -> ""
                }
                throw AiProviderException("Gemini API request failed with HTTP ${resp.code}$detail")
            }

            val rootElement = try {
                json.parseToJsonElement(responseBody)
            } catch (e: Exception) {
                throw AiProviderException("Failed to parse Gemini API response JSON: ${e.message}", e)
            }

            val rootObj = rootElement as? JsonObject
                ?: throw AiProviderException("Gemini API response was not a JSON object")

            val candidates = (rootObj["candidates"] as? JsonArray)
                ?: throw AiProviderException("Gemini response missing 'candidates' array: $responseBody")

            if (candidates.isEmpty()) {
                throw AiProviderException("Gemini response contained no candidates: $responseBody")
            }

            val firstCandidate = candidates[0] as? JsonObject
                ?: throw AiProviderException("Gemini candidate was not a JSON object: $responseBody")

            val content = firstCandidate["content"] as? JsonObject
                ?: throw AiProviderException("Gemini candidate missing 'content': $responseBody")

            val parts = content["parts"] as? JsonArray
                ?: throw AiProviderException("Gemini candidate content missing 'parts': $responseBody")

            val extractedText = parts.mapNotNull { part ->
                ((part as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull
            }.joinToString(separator = "")

            AiResult(
                text = extractedText.trim(),
                providerKind = AiProviderKind.GEMINI,
                model = model,
            )
        }
    }
}
