package com.openfit.mobile.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** The four AI backends selectable in Settings. CUSTOM covers Odysseus and
 * any other self-hosted OpenAI-compatible endpoint. */
@Serializable
enum class AiProviderKind {
    CLAUDE, OPENAI, GEMINI, CUSTOM
}

/** Which JSON request/response shape a CUSTOM endpoint speaks. SIMPLE_MESSAGE
 * is the lightweight {message in / text out} convention used by Odysseus
 * and similar lightweight self-hosted chat APIs. OPENAI_CHAT is the
 * standard /v1/chat/completions shape used by Ollama, LM Studio, vLLM,
 * text-generation-webui, and most other self-hosted OpenAI-compatible
 * servers. */
@Serializable
enum class CustomWireFormat { SIMPLE_MESSAGE, OPENAI_CHAT }

@Serializable
data class AiProviderConfig(
    val kind: AiProviderKind,
    val apiKey: String = "",
    val model: String = "",
    /** CUSTOM only: e.g. "http://192.168.1.50:7000" for a home-network self-hosted instance. */
    val baseUrl: String = "",
    /** CUSTOM only: request path appended to baseUrl, default "/api/chat". */
    val chatPath: String = "/api/chat",
    /** CUSTOM only: an existing session/conversation id some self-hosted
     * assistants require alongside the message. */
    val sessionId: String = "",
    /** CUSTOM only: which request/response shape to speak. */
    val wireFormat: CustomWireFormat = CustomWireFormat.SIMPLE_MESSAGE,
) {
    val isConfigured: Boolean
        get() = when (kind) {
            AiProviderKind.CUSTOM -> baseUrl.isNotBlank()
            AiProviderKind.CLAUDE, AiProviderKind.OPENAI, AiProviderKind.GEMINI -> apiKey.isNotBlank()
        }
}

/** A saved, user-named CUSTOM/self-hosted endpoint (e.g. "Odysseus - home
 * server", "Odysseus - VPN"). Every field is freely editable - nothing about
 * any specific personal agent is hard-coded; these are just labelled
 * connection profiles a user fills in themselves. Any number may be saved
 * and switched between from Settings. */
@Serializable
data class CustomEndpointProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val chatPath: String = "/api/chat",
    val sessionId: String = "",
    val wireFormat: CustomWireFormat = CustomWireFormat.SIMPLE_MESSAGE,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    val displayName: String get() = name.ifBlank { baseUrl.ifBlank { "New endpoint" } }

    fun toProviderConfig(): AiProviderConfig = AiProviderConfig(
        kind = AiProviderKind.CUSTOM,
        apiKey = apiKey,
        model = model,
        baseUrl = baseUrl,
        chatPath = chatPath,
        sessionId = sessionId,
        wireFormat = wireFormat,
    )
}

data class AiResult(
    val text: String,
    val providerKind: AiProviderKind,
    val model: String,
)

class AiProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val timestampEpochMillis: Long = System.currentTimeMillis(),
)
