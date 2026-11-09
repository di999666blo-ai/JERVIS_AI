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

class BobbyConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_private_config", Context.MODE_PRIVATE)
    private val alias = "bobby_api_key_aes"

    fun endpoint(): String = prefs.getString("endpoint", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
    fun model(): String = prefs.getString("model", "gpt-6.1-sol") ?: "gpt-6.1-sol"
    fun apiKey(): String = prefs.getString("api_key_encrypted", null)?.let { decrypt(it) }.orEmpty()

    fun save(endpoint: String, model: String, apiKey: String) {
        require(endpoint.startsWith("https://")) { "API endpoint must use HTTPS." }
        val editor = prefs.edit().putString("endpoint", endpoint.trim()).putString("model", model.trim())
        if (apiKey.isNotBlank()) editor.putString("api_key_encrypted", encrypt(apiKey.trim()))
        editor.apply()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size).put(cipher.iv).put(encrypted).array(), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(bytes)
        val iv = ByteArray(buffer.int).also { buffer.get(it) }
        val encrypted = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrDefault("")
}
