package com.openfit.mobile.data.ai

import com.openfit.mobile.model.AiProviderKind

object AiProviderFactory {
    private val claude by lazy { ClaudeProvider() }
    private val openAi by lazy { OpenAiProvider() }
    private val gemini by lazy { GeminiProvider() }
    private val custom by lazy { CustomProvider() }

    fun forKind(kind: AiProviderKind): AiProvider = when (kind) {
        AiProviderKind.CLAUDE -> claude
        AiProviderKind.OPENAI -> openAi
        AiProviderKind.GEMINI -> gemini
        AiProviderKind.CUSTOM -> custom
    }
}
