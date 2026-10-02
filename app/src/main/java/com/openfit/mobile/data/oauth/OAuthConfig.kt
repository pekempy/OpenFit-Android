package com.openfit.mobile.data.oauth

import kotlinx.serialization.Serializable

/** OAuth configuration for the Google Health API v4 cloud path.
 *
 * Two supported client types — configure in Google Cloud Console:
 *
 * Android client (no secret):
 *   - Application type: Android
 *   - Package name: com.openfit.mobile
 *   - SHA-1 fingerprint: your debug/release signing certificate
 *   - Must also add com.openfit.mobile:/oauth/callback as an Authorised
 *     Redirect URI (Credentials → edit the client)
 *
 * Web application client (with secret):
 *   - Application type: Web application
 *   - Add Authorised Redirect URI: com.openfit.mobile:/oauth/callback
 *   - Enter both Client ID and Client Secret in the app settings
 *
 * The Web application path is identical to OpenFit desktop's OAuth config.
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
