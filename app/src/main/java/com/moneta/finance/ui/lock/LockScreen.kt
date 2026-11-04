package com.moneta.finance.ui.lock

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.moneta.finance.security.AppLockManager
import com.moneta.finance.ui.theme.Moneta
import kotlinx.coroutines.launch

@Composable
fun LockScreen(lock: AppLockManager, activity: FragmentActivity) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Enter your PIN") }
    var busy by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val canUseBiometric = remember {
        lock.isBiometricEnabled &&
            BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    BackHandler { activity.moveTaskToBack(true) }

    fun showBiometric() {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lock.unlock()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Moneta")
            .setSubtitle("Confirm it's you")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(Unit) { if (canUseBiometric) showBiometric() }

    fun submit() {
        if (busy || pin.length < 4) return
        busy = true
        scope.launch {
            when (val r = lock.verify(pin)) {
                AppLockManager.VerifyResult.Success -> lock.unlock()
                is AppLockManager.VerifyResult.Wrong -> {
                    message = "Wrong PIN · ${r.attemptsLeft} attempts left"
                    pin = ""
                    repeat(3) { shake.animateTo(14f); shake.animateTo(-14f) }
                    shake.animateTo(0f)
                }
                is AppLockManager.VerifyResult.LockedOut -> {
                    message = "Too many attempts. Try again in ${r.seconds} s"
                    pin = ""
                }
            }
            busy = false
        }
    }

    val c = Moneta.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.heroStart, c.heroEnd)))
            .clickable(enabled = false) {}
            .systemBarsPadding(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(c.onHero.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = c.gold, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Moneta is locked", style = MaterialTheme.typography.headlineSmall, color = c.onHero)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = c.onHero.copy(alpha = 0.75f))
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier.offset { IntOffset(shake.value.toInt(), 0) }.semantics { contentDescription = "${pin.length} digits entered" },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(
                        Modifier.size(14.dp).clip(CircleShape)
                            .background(if (i < pin.length) c.onHero else c.onHero.copy(alpha = 0.25f)),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(if (k.length == 1) c.onHero.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable(enabled = !busy && (k != "bio" || canUseBiometric)) {
                                    when (k) {
                                        "del" -> pin = pin.dropLast(1)
                                        "bio" -> showBiometric()
                                        else -> if (pin.length < 8) {
                                            pin += k
                                            if (pin.length >= 4) submitIfComplete(pin, lock) { submit() }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "del" -> Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete digit", tint = c.onHero)
                                "bio" -> if (canUseBiometric) Icon(Icons.Filled.Fingerprint, contentDescription = "Unlock with biometrics", tint = c.onHero, modifier = Modifier.size(32.dp))
                                else -> Text(k, style = MaterialTheme.typography.headlineMedium, color = c.onHero)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Submits automatically once the entered PIN has the configured length. */
private inline fun submitIfComplete(pin: String, lock: AppLockManager, submit: () -> Unit) {
    if (pin.length == lock.pinLength) submit()
}
