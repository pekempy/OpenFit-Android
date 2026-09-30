package com.openfit.mobile.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openfit.mobile.AppContainer
import com.openfit.mobile.data.healthconnect.HealthConnectManager
import com.openfit.mobile.data.oauth.OAuthConfig
import com.openfit.mobile.data.settings.HealthDataSourceKind
import kotlinx.coroutines.launch

/** First-run / signed-out flow. Health Connect (on-device, no cloud setup)
 * is the recommended path - one system permission screen and done. The
 * Google Health API v4 cloud path (OAuth client id, same as OpenFit
 * desktop) is still available as an "Advanced" fallback for anyone who
 * wants direct cloud access instead. */
@Composable
fun OnboardingScreen(
    container: AppContainer,
    launchAuthIntent: (android.content.Intent, (android.content.Intent?) -> Unit) -> Unit,
    launchHealthConnectPermission: ((Set<String>) -> Unit) -> Unit,
    onConnected: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showAdvanced by remember { mutableStateOf(false) }
    var isConnectingHealthConnect by remember { mutableStateOf(false) }
    var healthConnectError by remember { mutableStateOf<String?>(null) }
    val hcAvailable = remember { HealthConnectManager.isAvailable(container.appContext) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Connect your health data", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "OpenFit reads your steps, sleep, heart rate, and other vitals from Health Connect - the same on-device data your Fitbit, Pixel Watch, or other tracker already syncs to. No account setup, just a permission grant.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        if (!hcAvailable) {
            Text(
                "Health Connect isn't available on this device. Install it from the Play Store, or use the advanced Google Health API option below.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
        }

        healthConnectError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                healthConnectError = null
                isConnectingHealthConnect = true
                launchHealthConnectPermission { granted ->
                    isConnectingHealthConnect = false
                    scope.launch {
                        val hasPerms = HealthConnectManager.hasPermissions(container.appContext) || granted.isNotEmpty()
                        if (hasPerms) {
                            container.settingsRepository.setDataSourceKind(HealthDataSourceKind.HEALTH_CONNECT)
                            onConnected()
                        } else {
                            healthConnectError = "No permissions were granted. Please grant permissions in Health Connect to view your health data."
                        }
                    }
                }
            },
            enabled = hcAvailable && !isConnectingHealthConnect,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isConnectingHealthConnect) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text("Connect Health Connect")
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showAdvanced) "Hide advanced option" else "Advanced: use Google Health API instead")
        }

        if (showAdvanced) {
            Spacer(Modifier.height(8.dp))
            GoogleHealthApiForm(
                container = container,
                launchAuthIntent = launchAuthIntent,
                onConnected = {
                    scope.launch {
                        container.settingsRepository.setDataSourceKind(HealthDataSourceKind.GOOGLE_HEALTH_API)
                        onConnected()
                    }
                },
            )
        }
    }
}

@Composable
private fun GoogleHealthApiForm(
    container: AppContainer,
    launchAuthIntent: (android.content.Intent, (android.content.Intent?) -> Unit) -> Unit,
    onConnected: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = null)
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        val s = settings ?: return@LaunchedEffect
        if (!initialized) {
            clientId = s.oauthConfig.clientId
            clientSecret = s.oauthConfig.clientSecret
            initialized = true
        }
    }

    Column {
        Text(
            "Create an OAuth client in your own Google Cloud project (Application type: Android, package com.openfit.mobile) and paste the Client ID below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = clientId, onValueChange = { clientId = it },
            label = { Text("OAuth Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = clientSecret, onValueChange = { clientSecret = it },
            label = { Text("Client Secret (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                errorMessage = null
                val config = OAuthConfig(clientId = clientId.trim(), clientSecret = clientSecret.trim())
                if (!config.isConfigured) {
                    errorMessage = "Enter a Client ID first."
                    return@OutlinedButton
                }
                scope.launch {
                    container.settingsRepository.updateOAuthConfig(config)
                    isConnecting = true
                    val intent = runCatching { container.authManager.createAuthorizationIntent(config) }
                        .getOrElse {
                            errorMessage = it.message
                            isConnecting = false
                            return@launch
                        }
                    launchAuthIntent(intent) { resultIntent ->
                        scope.launch {
                            try {
                                if (resultIntent == null) {
                                    errorMessage = "Connection was cancelled."
                                } else {
                                    container.authManager.handleAuthorizationResponse(resultIntent, config)
                                    onConnected()
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Failed to connect to Google Health."
                            } finally {
                                isConnecting = false
                            }
                        }
                    }
                }
            },
            enabled = !isConnecting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isConnecting) "Connecting…" else "Connect Google Health API")
        }
    }
}
