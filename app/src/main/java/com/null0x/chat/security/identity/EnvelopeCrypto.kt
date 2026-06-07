package com.null0x.chat.security.identity

import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EnvelopeCiphertext(
    val ciphertextB64: String,
    val nonceB64: String
)

object EnvelopeCrypto {
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val DOMAIN = "p2p-envelope-v1"

    private val secureRandom = SecureRandom()

    fun encrypt(
        plaintext: String,
        senderExchangePrivateKeyB64: String,
        recipientExchangePublicKeyB64: String
    ): EnvelopeCiphertext {
        val senderPrivate = X25519PrivateKeyParameters(decode(senderExchangePrivateKeyB64), 0)
        val recipientPublic = X25519PublicKeyParameters(decode(recipientExchangePublicKeyB64), 0)
        val sharedSecret = ByteArray(32)
        senderPrivate.generateSecret(recipientPublic, sharedSecret, 0)

        val nonce = ByteArray(NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val key = derivedAesKey(sharedSecret, nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        sharedSecret.fill(0)
        key.fill(0)

        return EnvelopeCiphertext(
            ciphertextB64 = encode(ciphertext),
            nonceB64 = encode(nonce)
        )
    }

    fun decrypt(
        ciphertextB64: String,
        nonceB64: String,
        senderExchangePublicKeyB64: String,
        recipientExchangePrivateKeyB64: String
    ): String? {
        val nonce = runCatching { decode(nonceB64) }.getOrNull() ?: return null
        if (nonce.size != NONCE_BYTES) return null

        return runCatching {
            val senderPublic = X25519PublicKeyParameters(decode(senderExchangePublicKeyB64), 0)
            val recipientPrivate = X25519PrivateKeyParameters(decode(recipientExchangePrivateKeyB64), 0)
            val payload = decode(ciphertextB64)
            val sharedSecret = ByteArray(32)
            recipientPrivate.generateSecret(senderPublic, sharedSecret, 0)
            val key = derivedAesKey(sharedSecret, nonce)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            val plain = String(cipher.doFinal(payload), Charsets.UTF_8)
            sharedSecret.fill(0)
            key.fill(0)
            plain
        }.getOrNull()
    }

    private fun derivedAesKey(sharedSecret: ByteArray, nonce: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256")
            .digest(sharedSecret + nonce + DOMAIN.toByteArray(Charsets.UTF_8))
    }

    private fun encode(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun decode(encoded: String): ByteArray {
        return Base64.getDecoder().decode(encoded)
    }
}
