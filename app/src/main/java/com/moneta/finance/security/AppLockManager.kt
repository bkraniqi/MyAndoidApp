package com.moneta.finance.security

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Optional app lock. The PIN is never stored: only a salted PBKDF2 hash is kept in private
 * app storage (excluded from any cloud backup).
 */
class AppLockManager(context: Context) {
    private val prefs = context.getSharedPreferences("moneta_security", Context.MODE_PRIVATE)

    private val _locked = MutableStateFlow(isPinSet)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null
    private var failedAttempts = 0
    private var lockoutUntil = 0L

    val isPinSet: Boolean get() = prefs.contains(KEY_HASH)
    val isBiometricEnabled: Boolean get() = isPinSet && prefs.getBoolean(KEY_BIOMETRIC, false)
    val pinLength: Int get() = prefs.getInt(KEY_LENGTH, 0)

    private val _state = MutableStateFlow(LockSettings(isPinSet, isBiometricEnabled))
    val settings: StateFlow<LockSettings> = _state.asStateFlow()

    data class LockSettings(val pinEnabled: Boolean, val biometricEnabled: Boolean)

    sealed interface VerifyResult {
        data object Success : VerifyResult
        data class Wrong(val attemptsLeft: Int) : VerifyResult
        data class LockedOut(val seconds: Long) : VerifyResult
    }

    suspend fun setPin(pin: String) {
        require(pin.length in 4..8 && pin.all { it.isDigit() }) { "PIN must have 4 to 8 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_LENGTH, pin.length)
            .apply()
        publish()
    }

    fun disable() {
        prefs.edit().remove(KEY_HASH).remove(KEY_SALT).remove(KEY_BIOMETRIC).remove(KEY_LENGTH).apply()
        _locked.value = false
        publish()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled && isPinSet).apply()
        publish()
    }

    suspend fun verify(pin: String): VerifyResult {
        val now = SystemClock.elapsedRealtime()
        if (now < lockoutUntil) return VerifyResult.LockedOut((lockoutUntil - now + 999) / 1000)
        val salt = prefs.getString(KEY_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return VerifyResult.Success
        val expected = prefs.getString(KEY_HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return VerifyResult.Success
        val ok = MessageDigest.isEqual(hash(pin, salt), expected)
        return if (ok) {
            failedAttempts = 0
            VerifyResult.Success
        } else {
            failedAttempts++
            if (failedAttempts >= MAX_ATTEMPTS) {
                failedAttempts = 0
                lockoutUntil = SystemClock.elapsedRealtime() + LOCKOUT_MS
                VerifyResult.LockedOut(LOCKOUT_MS / 1000)
            } else {
                VerifyResult.Wrong(MAX_ATTEMPTS - failedAttempts)
            }
        }
    }

    fun unlock() {
        _locked.value = false
        backgroundedAt = null
    }

    fun onAppBackgrounded() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onAppForegrounded() {
        val since = backgroundedAt ?: return
        if (isPinSet && SystemClock.elapsedRealtime() - since >= RELOCK_AFTER_MS) _locked.value = true
        backgroundedAt = null
    }

    private fun publish() {
        _state.value = LockSettings(isPinSet, isBiometricEnabled)
    }

    private suspend fun hash(pin: String, salt: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_BIOMETRIC = "biometric"
        private const val KEY_LENGTH = "pin_length"
        private const val ITERATIONS = 120_000
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 30_000L
        private const val RELOCK_AFTER_MS = 30_000L
    }
}
