package com.openfit.mobile.data.backup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Drive authorisation via the Play Services Authorization API.
 *
 * Requests access to the per-app Drive folder (drive.appdata scope) only.
 * The folder is private to OpenFit — not visible in Drive UI and not counted
 * in the user's storage. The consent screen says as much.
 *
 * The developer must register an Android OAuth client in Google Cloud Console
 * with this app's package name and signing certificate SHA-1. The user never
 * enters credentials; they only tap "Sign in with Google" and pick an account.
 *
 * The token is short-lived and kept in memory only.
 */
class DriveAuth(private val context: Context) {

    companion object {
        private const val TAG = "OpenFitDriveAuth"
        private const val APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val DEVELOPER_ERROR = 10
        private const val UNREGISTERED = "UNREGISTERED_ON_API_CONSOLE"
    }

    data class Account(val name: String?, val email: String?)

    var token by mutableStateOf<String?>(null)
        private set
    var account by mutableStateOf<Account?>(null)
        private set

    val hasToken: Boolean get() = token != null
    val signedInAs: Account? get() = account

    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(
            Scope(APPDATA_SCOPE),
            Scope("email"),
            Scope("profile"),
        ))
        .build()

    sealed interface Step {
        data class Token(val value: String) : Step
        data class NeedsConsent(val intentSender: android.content.IntentSender) : Step
        data class Failed(val message: String) : Step
    }

    suspend fun begin(): Step = withContext(Dispatchers.IO) {
        token?.let { return@withContext Step.Token(it) }
        try {
            val result = authorize()
            val sender = result.pendingIntent?.intentSender
            when {
                result.accessToken != null -> {
                    store(result.accessToken!!)
                    Step.Token(result.accessToken!!)
                }
                sender != null -> Step.NeedsConsent(sender)
                else -> Step.Failed("Google didn't return a token or a way to ask for one")
            }
        } catch (e: Exception) {
            Step.Failed(explain(e))
        }
    }

    fun onConsentResult(data: Intent?): Result<String> = try {
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        val t = result.accessToken
        if (t != null) {
            store(t)
            Result.success(t)
        } else {
            Result.failure(IllegalStateException("Sign-in was cancelled"))
        }
    } catch (e: Exception) {
        Log.w(TAG, "authorisation failed", e)
        Result.failure(IllegalStateException(explain(e), e))
    }

    fun signOut() {
        token = null
        account = null
    }

    private fun explain(e: Exception): String {
        val misconfigured =
            (e as? ApiException)?.statusCode == DEVELOPER_ERROR ||
                e.message?.contains(UNREGISTERED) == true
        if (misconfigured) return unregisteredMessage()
        if (e is ApiException) {
            return when (e.statusCode) {
                12500, 12501 -> "Sign-in was cancelled"
                7 -> "No network"
                16 -> "Google Play Services isn't available on this device"
                else -> "Google sign-in failed (status ${e.statusCode}: ${e.message})"
            }
        }
        return e.message ?: "Google sign-in failed"
    }

    private fun unregisteredMessage(): String {
        val sha1 = signingSha1() ?: "unavailable"
        return "Google doesn't recognise this build. Register an Android OAuth client at " +
            "console.cloud.google.com with:\n" +
            "Package: ${context.packageName}\n" +
            "SHA-1: $sha1"
    }

    private fun signingSha1(): String? = runCatching {
        val pm = context.packageManager
        val name = context.packageName
        val certs: Array<Signature> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
                val signing = info.signingInfo ?: return null
                if (signing.hasMultipleSigners()) signing.apkContentsSigners
                else signing.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures
            } ?: return null
        val cert = certs.firstOrNull() ?: return null
        MessageDigest.getInstance("SHA-1").digest(cert.toByteArray())
            .joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    private fun store(t: String) {
        token = t
        runCatching { account = fetchAccount(t) }
    }

    private suspend fun authorize(): AuthorizationResult =
        suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(context)
                .authorize(request())
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private fun fetchAccount(token: String): Account? {
        val c = (URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection()
                as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 8_000; readTimeout = 8_000
        }
        return try {
            if (c.responseCode !in 200..299) return null
            val body = c.inputStream.bufferedReader().use { it.readText() }
            Account(
                name = Regex("\"name\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1),
                email = Regex("\"email\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1),
            )
        } catch (_: Exception) { null } finally { c.disconnect() }
    }
}
