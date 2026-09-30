package com.openfit.mobile.data.ai

import com.openfit.mobile.model.AiProviderConfig
import com.openfit.mobile.model.AiResult

/** One AI backend. Implementations are stateless, single-shot completions -
 * no chat history is kept server-side; each call gets the full context it
 * needs (system prompt + user prompt) since the only callers are the
 * morning/evening summary workers, not an interactive chat UI. */
interface AiProvider {
    suspend fun complete(config: AiProviderConfig, systemPrompt: String, userPrompt: String): AiResult
}
