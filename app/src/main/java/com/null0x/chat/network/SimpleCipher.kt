package com.null0x.chat.network

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SimpleCipher {
    private const val V2_PREFIX = "RS2:"
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val MAX_CIPHER_BYTES = 128 * 1024
    private const val MAX_CIPHER_TEXT_CHARS = 192 * 1024
    private val random = SecureRandom()
    private val key = SecretKeySpec(
        MessageDigest.getInstance("SHA-256")
            .digest("NullChat private transport message key v2".toByteArray(Charsets.UTF_8)),
        "AES"
    )

    fun encrypt(plainText: String): String {
        val input = plainText.toByteArray(Charsets.UTF_8)
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        val encrypted = cipher.doFinal(input)
        return V2_PREFIX + Base64.encodeToString(nonce + encrypted, Base64.NO_WRAP)
    }

    fun decrypt(cipherText: String): String? {
        val clean = cipherText.trim()
        if (clean.startsWith(V2_PREFIX)) {
            return decryptV2(clean.removePrefix(V2_PREFIX))
        }
        return null
    }

    private fun decryptV2(cipherText: String): String? {
        if (cipherText.length > MAX_CIPHER_TEXT_CHARS) return null
        return runCatching {
            val input = Base64.decode(cipherText, Base64.DEFAULT)
            if (input.size <= NONCE_BYTES || input.size > MAX_CIPHER_BYTES) return null
            val nonce = input.copyOfRange(0, NONCE_BYTES)
            val encrypted = input.copyOfRange(NONCE_BYTES, input.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

}
