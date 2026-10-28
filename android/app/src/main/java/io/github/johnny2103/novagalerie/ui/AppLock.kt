package io.github.johnny2103.novagalerie.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.util.findActivity

private const val RELOCK_AFTER_MS = 60_000L
private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** Process-wide lock state: locked on cold start, re-locked after a minute in the background. */
object AppLockState {
    var unlocked by mutableStateOf(false)
    var stoppedAt by mutableLongStateOf(0L)
}

fun canUseAppLock(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

fun authenticate(context: Context, onSuccess: () -> Unit, onUnavailable: () -> Unit = {}) {
    val activity = context.findActivity() as? FragmentActivity ?: return
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Without any credential the lock cannot protect anything; do not lock the user out.
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    -> onUnavailable()
                    else -> Unit
                }
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.lock_title))
        .setSubtitle(context.getString(R.string.lock_subtitle))
        .setAllowedAuthenticators(AUTHENTICATORS)
        .build()
    prompt.authenticate(info)
}

/** Shows [content] only after biometric / device-credential authentication when the lock is enabled. */
@Composable
fun LockGate(enabled: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    if (!enabled || !canUseAppLock(context)) {
        content()
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> AppLockState.stoppedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val stopped = AppLockState.stoppedAt
                    if (stopped > 0 && System.currentTimeMillis() - stopped > RELOCK_AFTER_MS) AppLockState.unlocked = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (AppLockState.unlocked) {
        content()
    } else {
        val unlock = { AppLockState.unlocked = true }
        LaunchedEffect(Unit) { authenticate(context, onSuccess = unlock, onUnavailable = unlock) }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.lock_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { authenticate(context, onSuccess = unlock, onUnavailable = unlock) }) { Text(stringResource(R.string.lock_unlock)) }
            }
        }
    }
}
