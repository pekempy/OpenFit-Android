package com.openfit.mobile.data.oauth

import kotlinx.serialization.Serializable

/** Same OAuth flow OpenFit's desktop app uses (Google Health API v4,
 * read-only scopes), but the client is user-configurable in Settings
 * rather than baked into the app.
 *
 * Recommended setup mirrors OpenFit's own docs: create a Google Cloud
 * project, enable the Google Health API, and add an OAuth client. For
 * Android specifically, use an "Android" application-type client (package
 * name com.openfit.mobile + your debug/release signing SHA-1) - it has no
 * client secret, matching how AppAuth's PKCE flow authenticates. A
 * Web-application client with a secret also works if that's what you
 * already created for OpenFit desktop; clientSecret is optional here for
 * exactly that reason.
 */
@Serializable
data class OAuthConfig(
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectScheme: String = "com.openfit.mobile",
    val redirectPath: String = "/oauth/callback",
) {
    val redirectUri: String get() = "$redirectScheme:$redirectPath"
    val isConfigured: Boolean get() = clientId.isNotBlank()
}

object GoogleHealthOAuth {
    const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val REVOCATION_ENDPOINT = "https://oauth2.googleapis.com/revoke"

    // Read-only scopes - identical set to OpenFit desktop's Google Health
    // integration (electron/google-health-service.cjs).
    val SCOPES = listOf(
        "openid",
        "profile",
        "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
        "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly",
        "https://www.googleapis.com/auth/googlehealth.ecg.readonly",
        "https://www.googleapis.com/auth/googlehealth.irn.readonly",
        "https://www.googleapis.com/auth/googlehealth.location.readonly",
        "https://www.googleapis.com/auth/googlehealth.nutrition.readonly",
        "https://www.googleapis.com/auth/googlehealth.profile.readonly",
        "https://www.googleapis.com/auth/googlehealth.settings.readonly",
        "https://www.googleapis.com/auth/googlehealth.sleep.readonly",
    )
}
