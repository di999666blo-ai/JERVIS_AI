package ai.diablo.bobby

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted credential vault. Values use an Android Keystore AES-GCM key. */
class BobbySecretVault(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_secret_vault", Context.MODE_PRIVATE)
    private val alias = "bobby_secret_vault_aes_v1"
    fun put(name: String, value: String) {
        require(name.matches(Regex("[a-z0-9_]{1,64}")))
        require(value.isNotBlank())
        prefs.edit().putString(name, encrypt(value)).apply()
    }
    fun get(name: String): String = prefs.getString(name, null)?.let { decrypt(it) }.orEmpty()
    fun remove(name: String) { prefs.edit().remove(name).apply() }
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return gen.generateKey()
    }
    private fun encrypt(value: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
        val data = c.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(ByteBuffer.allocate(4 + c.iv.size + data.size).putInt(c.iv.size).put(c.iv).put(data).array(), Base64.NO_WRAP)
    }
    private fun decrypt(encoded: String): String = runCatching {
        val b = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP))
        val iv = ByteArray(b.int).also { b.get(it) }; val data = ByteArray(b.remaining()).also { b.get(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(c.doFinal(data), Charsets.UTF_8)
    }.getOrDefault("")
}
