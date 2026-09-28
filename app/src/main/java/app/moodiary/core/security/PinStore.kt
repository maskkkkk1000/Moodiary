package app.moodiary.core.security

import android.content.Context
import android.util.Base64
import app.moodiary.R
import app.moodiary.core.localization.localizedString
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local UI lock; does not claim to encrypt Room or replace device encryption. Never backed up. */
@Singleton class PinStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val storage = context.getSharedPreferences("private_lock", Context.MODE_PRIVATE)
    val enabled: Boolean get() = storage.contains("hash")
    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 310_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    suspend fun set(pin: String) = withContext(Dispatchers.IO) {
        require(pin.length in 6..12 && pin.all(Char::isDigit)) { context.localizedString(R.string.privacy_digits) }
        val salt = ByteArray(32).also(SecureRandom()::nextBytes)
        check(storage.edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(derive(pin, salt), Base64.NO_WRAP))
            .putInt("failures", 0).putLong("retryAt", 0).commit()) { context.localizedString(R.string.privacy_save_failed) }
    }
    suspend fun verify(pin: String): Boolean = withContext(Dispatchers.IO) {
        check(System.currentTimeMillis() >= storage.getLong("retryAt", 0)) { context.localizedString(R.string.privacy_retry_later) }
        if (!enabled) return@withContext false
        val salt = Base64.decode(storage.getString("salt", ""), Base64.NO_WRAP)
        val expected = Base64.decode(storage.getString("hash", ""), Base64.NO_WRAP)
        val matches = MessageDigest.isEqual(derive(pin, salt), expected)
        val failures = if (matches) 0 else storage.getInt("failures", 0) + 1
        val delay = if (failures < 5) 0L else (30_000L * (1L shl (failures - 5).coerceAtMost(6)))
        check(storage.edit().putInt("failures", failures).putLong("retryAt", System.currentTimeMillis() + delay).commit())
        matches
    }
    suspend fun remove(pin: String) {
        check(verify(pin)) { context.localizedString(R.string.privacy_incorrect_pin) }
        withContext(Dispatchers.IO) { check(storage.edit().clear().commit()) }
    }
}
