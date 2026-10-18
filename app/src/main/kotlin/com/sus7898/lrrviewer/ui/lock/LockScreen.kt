package com.sus7898.lrrviewer.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sus7898.lrrviewer.ui.common.findActivity
import com.sus7898.lrrviewer.ui.common.rememberResumeTick

/** Weak biometrics (fingerprint/face) or the device PIN/pattern/password. Works on API 28+ with BiometricPrompt 1.1. */
private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** True when the device has a fingerprint/face or a screen lock that BiometricPrompt can use. */
fun canUseAppLock(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/** Shows the system authentication sheet. [onError] receives a user-facing message (not called on cancel by the user). */
fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_CANCELED) onError(errString.toString())
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle("지문·얼굴 또는 화면 잠금으로 확인")
        .setAllowedAuthenticators(AUTHENTICATORS)
        .build()
    prompt.authenticate(info)
}

/** Opaque cover shown over the whole app while locked; asks for authentication on every resume. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? FragmentActivity }
    val resumeTick = rememberResumeTick()
    var error by remember { mutableStateOf<String?>(null) }

    fun ask() {
        val a = activity ?: run { onUnlocked(); return } // cannot prompt without a FragmentActivity: fail open rather than trap the user
        if (!canUseAppLock(a)) { onUnlocked(); return } // screen lock removed since the setting was turned on
        authenticate(a, "LRR Viewer 잠금 해제", onSuccess = onUnlocked, onError = { error = it })
    }
    LaunchedEffect(resumeTick) { ask() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("잠겨 있습니다", style = MaterialTheme.typography.titleLarge)
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = { error = null; ask() }) { Text("잠금 해제") }
        }
    }
}
