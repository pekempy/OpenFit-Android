package com.openfit.mobile.data.ai

import com.openfit.mobile.data.settings.AiPersonalisationSettings

/** Resolves the user's own name and any custom persona instructions set in
 * Settings, with neutral fallbacks when left blank. Nothing about a specific
 * agent's identity/backstory is hard-coded anywhere in the app - whatever
 * personality a self-hosted assistant should keep is entirely up to what the
 * user types here, and applies equally to every provider (Claude/GPT/Gemini/
 * Custom), not just self-hosted ones. */
object PersonaPrompt {
    fun userName(personalisation: AiPersonalisationSettings): String =
        personalisation.userDisplayName.trim().ifBlank { "the user" }

    fun personaPreamble(personalisation: AiPersonalisationSettings): String? {
        val instructions = personalisation.personaInstructions.trim()
        if (instructions.isBlank()) return null
        return "Maintain your existing personality and character exactly as configured by the user: $instructions. Do not overwrite or lose who you are."
    }
}
