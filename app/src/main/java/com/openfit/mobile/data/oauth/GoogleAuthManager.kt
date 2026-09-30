package com.openfit.mobile.data.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientSecretPost
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.NoClientAuthentication
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import org.json.JSONObject
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Drives the same OAuth 2.0 + PKCE flow OpenFit desktop uses against
 * Google's Health API v4, adapted to Android via AppAuth (Chrome Custom Tab
 * + a manifest-registered redirect scheme instead of a loopback HTTP
 * server). The client id/secret are whatever the user entered in Settings -
 * nothing is hardcoded. */
class GoogleAuthManager(
    private val context: Context,
    private val tokenStore: TokenStore,
) {
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(GoogleHealthOAuth.AUTHORIZATION_ENDPOINT),
        Uri.parse(GoogleHealthOAuth.TOKEN_ENDPOINT),
        null,
        Uri.parse(GoogleHealthOAuth.REVOCATION_ENDPOINT),
    )

    private val authService = AuthorizationService(context)

    /** Builds the intent that launches the system browser / Custom Tab for
     * the consent screen. Fire with an ActivityResult launcher and hand the
     * resulting Intent to [handleAuthorizationResponse]. */
    fun createAuthorizationIntent(config: OAuthConfig): Intent {
        require(config.isConfigured) { "OAuth client ID is not configured - set it in Settings first." }
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            config.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(config.redirectUri),
        )
            .setScopes(GoogleHealthOAuth.SCOPES)
            .setPrompt("consent")
            .setAdditionalParameters(
                mapOf(
                    "access_type" to "offline",
                    "include_granted_scopes" to "true",
                ),
            )
            .build()
        return authService.getAuthorizationRequestIntent(request)
    }

    /** Call from the activity-result callback with the Intent AppAuth's
     * redirect activity handed back. Exchanges the auth code for tokens and
     * persists them. */
    suspend fun handleAuthorizationResponse(intent: Intent, config: OAuthConfig) {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        if (exception != null) {
            throw OAuthFlowException(exception.errorDescription ?: exception.message ?: "Authorisation was denied or cancelled.")
        }
        checkNotNull(response) { "No authorisation response and no error - unexpected AppAuth state." }

        val tokenRequest = response.createTokenExchangeRequest()
        val tokenResponse = performTokenRequest(tokenRequest, clientAuthFor(config))
        persist(tokenResponse)
    }

    /** Returns a currently-valid access token, transparently refreshing if
     * the cached one is expired. Throws [OAuthFlowException] if there is no
     * stored session (caller should route to the connect flow). */
    suspend fun getValidAccessToken(config: OAuthConfig): String {
        val stored = tokenStore.load() ?: throw OAuthFlowException("Not connected to Google Health yet.")
        if (!stored.isExpired()) return stored.accessToken

        val refreshToken = stored.refreshToken
            ?: throw OAuthFlowException("Google Health session expired and no refresh token is available - reconnect the account.")

        val tokenRequest = TokenRequest.Builder(serviceConfig, config.clientId)
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(refreshToken)
            .build()
        val tokenResponse = performTokenRequest(tokenRequest, clientAuthFor(config))
        // Google's refresh response omits refresh_token; keep the existing one.
        persist(tokenResponse, fallbackRefreshToken = refreshToken)
        return checkNotNull(tokenResponse.accessToken)
    }

    fun currentAccountEmail(): String? = tokenStore.load()?.accountEmail

    fun isConnected(): Boolean = tokenStore.load() != null

    suspend fun signOut(config: OAuthConfig) {
        val stored = tokenStore.load()
        tokenStore.clear()
        val token = stored?.refreshToken ?: stored?.accessToken ?: return
        // Best-effort revoke; a network failure here shouldn't block sign-out.
        runCatching { revoke(token) }
    }

    private suspend fun performTokenRequest(
        request: TokenRequest,
        clientAuthentication: ClientAuthentication,
    ): TokenResponse = suspendCancellableCoroutine { continuation ->
        authService.performTokenRequest(request, clientAuthentication) { response, exception ->
            when {
                response != null -> continuation.resume(response)
                exception != null -> continuation.resumeWithException(
                    OAuthFlowException(exception.errorDescription ?: exception.message ?: "Token request failed.", exception),
                )
                else -> continuation.resumeWithException(OAuthFlowException("Token request returned neither a response nor an error."))
            }
        }
    }

    private fun persist(tokenResponse: TokenResponse, fallbackRefreshToken: String? = null) {
        val accessToken = checkNotNull(tokenResponse.accessToken) { "Google did not return an access token." }
        val expiresAt = tokenResponse.accessTokenExpirationTime ?: (System.currentTimeMillis() + 3600_000L)
        val email = extractEmail(tokenResponse.idToken) ?: tokenStore.load()?.accountEmail
        tokenStore.save(
            StoredTokens(
                accessToken = accessToken,
                refreshToken = tokenResponse.refreshToken ?: fallbackRefreshToken,
                expiresAtEpochMillis = expiresAt,
                idToken = tokenResponse.idToken,
                accountEmail = email,
            ),
        )
    }

    private fun extractEmail(idToken: String?): String? {
        if (idToken == null) return null
        return runCatching {
            val payload = idToken.split(".")[1]
            val decoded = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
            JSONObject(String(decoded, Charsets.UTF_8)).optString("email").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun clientAuthFor(config: OAuthConfig): ClientAuthentication =
        if (config.clientSecret.isBlank()) NoClientAuthentication.INSTANCE else ClientSecretPost(config.clientSecret)

    private suspend fun revoke(token: String) {
        // AppAuth doesn't wrap the revocation endpoint; it's a plain
        // form-encoded POST, same as OpenFit desktop's revokeToken().
        withContextIo {
            val connection = URL(GoogleHealthOAuth.REVOCATION_ENDPOINT).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write("token=$token".toByteArray()) }
            connection.responseCode
            connection.disconnect()
        }
    }

    fun dispose() = authService.dispose()
}

private suspend fun <T> withContextIo(block: () -> T): T =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }

class OAuthFlowException(message: String, cause: Throwable? = null) : Exception(message, cause)
