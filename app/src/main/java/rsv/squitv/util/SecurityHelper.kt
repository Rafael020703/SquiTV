package rsv.squitv.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecurityHelper {
    private const val KEY_ALIAS = "iptv_credential_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val VERSION_PREFIX = "v1"

    private val keyStore: KeyStore? by lazy {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (_: Throwable) {
            null
        }
    }

    private var jvmTestKey: SecretKey? = null

    init {
        generateKeyIfNeeded()
    }

    private fun generateKeyIfNeeded() {
        val ks = keyStore
        if (ks != null) {
            try {
                if (!ks.containsAlias(KEY_ALIAS)) {
                    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                    val spec = KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                    keyGenerator.init(spec)
                    keyGenerator.generateKey()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to generate security key in AndroidKeyStore")
            }
        }
    }

    private val isAndroidOS: Boolean by lazy {
        try {
            System.getProperty("java.vendor")?.contains("Android", ignoreCase = true) == true ||
                    android.os.Build.VERSION.SDK_INT > 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun getSecretKey(): SecretKey? {
        if (isAndroidOS) {
            val ks = keyStore ?: return null
            return try {
                ks.getKey(KEY_ALIAS, null) as? SecretKey
            } catch (e: Exception) {
                Timber.e(e, "Failed to retrieve key from AndroidKeyStore")
                null
            }
        }
        if (jvmTestKey == null) {
            try {
                val kg = KeyGenerator.getInstance("AES")
                kg.init(256)
                jvmTestKey = kg.generateKey()
            } catch (_: Throwable) {}
        }
        return jvmTestKey
    }

    private const val BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Throwable) {
            val sb = StringBuilder()
            var i = 0
            while (i < bytes.size) {
                val b0 = bytes[i++].toInt() and 0xFF
                val b1 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1
                val b2 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1

                sb.append(BASE64_CHARS[b0 shr 2])
                if (b1 != -1) {
                    sb.append(BASE64_CHARS[((b0 and 0x03) shl 4) or (b1 shr 4)])
                    if (b2 != -1) {
                        sb.append(BASE64_CHARS[((b1 and 0x0F) shl 2) or (b2 shr 6)])
                        sb.append(BASE64_CHARS[b2 and 0x3F])
                    } else {
                        sb.append(BASE64_CHARS[(b1 and 0x0F) shl 2])
                        sb.append('=')
                    }
                } else {
                    sb.append(BASE64_CHARS[(b0 and 0x03) shl 4])
                    sb.append("==")
                }
            }
            sb.toString()
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            Base64.decode(str, Base64.NO_WRAP)
        } catch (_: Throwable) {
            val clean = str.trimEnd('=')
            val bytes = mutableListOf<Byte>()
            var i = 0
            while (i < clean.length) {
                val c0 = BASE64_CHARS.indexOf(clean[i++])
                val c1 = if (i < clean.length) BASE64_CHARS.indexOf(clean[i++]) else 0
                val c2 = if (i < clean.length) BASE64_CHARS.indexOf(clean[i++]) else -1
                val c3 = if (i < clean.length) BASE64_CHARS.indexOf(clean[i++]) else -1

                bytes.add(((c0 shl 2) or (c1 shr 4)).toByte())
                if (c2 != -1) {
                    bytes.add((((c1 and 0x0F) shl 4) or (c2 shr 2)).toByte())
                    if (c3 != -1) {
                        bytes.add((((c2 and 0x03) shl 6) or c3).toByte())
                    }
                }
            }
            bytes.toByteArray()
        }
    }

    fun encrypt(plaintext: String?): String? {
        if (plaintext == null) return null
        var key = getSecretKey()
        if (key == null && keyStore != null) {
            generateKeyIfNeeded()
            key = getSecretKey()
        }
        val validKey = key ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, validKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            
            val ivBase64 = encodeBase64(iv)
            val encryptedBase64 = encodeBase64(encryptedBytes)
            
            "$VERSION_PREFIX:$ivBase64:$encryptedBase64"
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            null
        }
    }

    fun decrypt(encryptedValue: String?): String? {
        if (encryptedValue == null) return null
        if (!encryptedValue.startsWith("$VERSION_PREFIX:")) return encryptedValue

        val key = getSecretKey() ?: return null

        return try {
            val parts = encryptedValue.split(":")
            if (parts.size != 3) return null
            
            val iv = decodeBase64(parts[1])
            val ciphertext = decodeBase64(parts[2])
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed for encrypted payload")
            null
        }
    }

    fun isEncrypted(value: String?): Boolean {
        return value?.startsWith("$VERSION_PREFIX:") == true
    }
}
