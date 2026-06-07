package com.null0x.chat.security.identity

import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

object MessageCrypto {
    private const val PREFIX = "RS-MSGv1"
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    private val secureRandom = SecureRandom()

    fun encryptForRecipient(message: String, recipientPublicKeyB64: String): String {
        val recipientPublicKey = X25519PublicKeyParameters(decode(recipientPublicKeyB64), 0)
        val ephemeralPrivateSeed = ByteArray(32).also { secureRandom.nextBytes(it) }
        val ephemeralPrivateKey = X25519PrivateKeyParameters(ephemeralPrivateSeed, 0)
        val ephemeralPublicKey = ephemeralPrivateKey.generatePublicKey().encoded

        val sharedSecret = ByteArray(32)
        ephemeralPrivateKey.generateSecret(recipientPublicKey, sharedSecret, 0)
        val nonce = ByteArray(NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val key = derivedAesKey(sharedSecret, nonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(message.toByteArray(Charsets.UTF_8))

        sharedSecret.fill(0)
        ephemeralPrivateSeed.fill(0)
        key.fill(0)

        return listOf(
            PREFIX,
            encode(ephemeralPublicKey),
            encode(nonce),
            encode(ciphertext)
        ).joinToString(":")
    }

    fun decryptMessage(ciphertext: String, privateKeyB64: String): String? {
        val parts = ciphertext.split(':')
        if (parts.size != 4 || parts[0] != PREFIX) return null
        val ephemeralPublic = decode(parts[1])
        val nonce = decode(parts[2])
        val payload = decode(parts[3])
        if (nonce.size != NONCE_BYTES) return null

        return runCatching {
            val privateKey = X25519PrivateKeyParameters(decode(privateKeyB64), 0)
            val sharedSecret = ByteArray(32)
            privateKey.generateSecret(X25519PublicKeyParameters(ephemeralPublic, 0), sharedSecret, 0)
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
            .digest(sharedSecret + nonce + "route-message-v1".toByteArray(Charsets.UTF_8))
    }

    private fun encode(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun decode(encoded: String): ByteArray {
        return Base64.getDecoder().decode(encoded)
    }
}
