package com.openfit.mobile.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientSecretPost
import net.openid.appauth.NoClientAuthentication
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"

/** Drive scope that writes to the app's private appDataFolder — invisible
 * to the user in Drive UI, not counted against their visible quota. */
private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
private const val EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email"

/** Handles OAuth 2.0 + PKCE for Google Drive App Data access, using the
 * same AppAuth library as the Health API flow.  The redirect URI reuses
 * the app's registered custom scheme so no extra manifest entry is needed. */
class DriveAuthManager(
    private val context: Context,
    private val tokenStore: DriveTokenStore,
) {
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(AUTH_ENDPOINT),
        Uri.parse(TOKEN_ENDPOINT),
        null,
        Uri.parse(REVOKE_ENDPOINT),
    )

    private val authService = AuthorizationService(context)

    /** Builds the intent that launches the consent screen. Pass [clientId]
     * (and optional [clientSecret] for web-type clients) from settings. */
    fun createAuthIntent(clientId: String, clientSecret: String): Intent {
        val req = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse("com.openfit.mobile:/oauth2redirect/drive"),
        )
            .setScopes(DRIVE_SCOPE, EMAIL_SCOPE)
            .build()
        return authService.getAuthorizationRequestIntent(req)
    }

    suspend fun handleAuthorizationResponse(
        intent: Intent,
        clientId: String,
        clientSecret: String,
    ) {
        val resp = AuthorizationResponse.fromIntent(intent)
            ?: throw DriveAuthException("No authorization response in intent")

        val clientAuth = if (clientSecret.isBlank()) NoClientAuthentication.INSTANCE
        else ClientSecretPost(clientSecret)

        val tokenResp = performTokenRequest(resp.createTokenExchangeRequest(), clientAuth)

        val expiresIn = tokenResp.accessTokenExpirationTime ?: 0L
        tokenStore.save(
            DriveTokenStore.DriveTokens(
                accessToken = tokenResp.accessToken ?: error("No access token"),
                refreshToken = tokenResp.refreshToken,
                accountEmail = extractEmail(tokenResp.idToken),
                expiresAtEpochSeconds = expiresIn / 1000,
            )
        )
    }

    /** Returns a valid access token, refreshing if needed. */
    suspend fun getValidToken(clientId: String, clientSecret: String): String {
        val tokens = tokenStore.load() ?: throw DriveAuthException("Not connected to Drive")
        if (!tokenStore.isExpired(tokens)) return tokens.accessToken

        val refreshToken = tokens.refreshToken ?: throw DriveAuthException("No refresh token")
        val clientAuth = if (clientSecret.isBlank()) NoClientAuthentication.INSTANCE
        else ClientSecretPost(clientSecret)

        val req = TokenRequest.Builder(serviceConfig, clientId)
            .setGrantType(net.openid.appauth.GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(refreshToken)
            .build()
        val tokenResp = performTokenRequest(req, clientAuth)

        val expiresIn = tokenResp.accessTokenExpirationTime ?: 0L
        val refreshed = DriveTokenStore.DriveTokens(
            accessToken = tokenResp.accessToken ?: throw DriveAuthException("No access token after refresh"),
            refreshToken = tokenResp.refreshToken ?: refreshToken,
            accountEmail = tokens.accountEmail,
            expiresAtEpochSeconds = expiresIn / 1000,
        )
        tokenStore.save(refreshed)
        return refreshed.accessToken
    }

    suspend fun signOut() {
        val tokens = tokenStore.load()
        tokens?.accessToken?.let { token ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val url = URL("$REVOKE_ENDPOINT?token=$token")
                    (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connect()
                        disconnect()
                    }
                }
            }
        }
        tokenStore.clear()
    }

    fun isConnected(): Boolean = kotlinx.coroutines.runBlocking { tokenStore.load() != null }

    fun dispose() = authService.dispose()

    private suspend fun performTokenRequest(
        request: TokenRequest,
        clientAuth: net.openid.appauth.ClientAuthentication,
    ): TokenResponse = suspendCancellableCoroutine { cont ->
        authService.performTokenRequest(request, clientAuth) { resp, ex ->
            when {
                resp != null -> cont.resume(resp)
                else -> cont.resumeWithException(DriveAuthException(ex?.message ?: "Token exchange failed", ex))
            }
        }
    }

    private fun extractEmail(idToken: String?): String? = runCatching {
        val payload = idToken?.split(".")?.getOrNull(1) ?: return@runCatching null
        val decoded = android.util.Base64.decode(
            payload.replace('-', '+').replace('_', '/'),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING,
        )
        org.json.JSONObject(String(decoded)).optString("email").takeIf { it.isNotBlank() }
    }.getOrNull()
}

class DriveAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
