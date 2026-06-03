package com.null0x.chat.storage

import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.key.protection.SecretKeyRingProtector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object LocalStoreCipher {
    private const val PREFIX = "PGP:"

    @Volatile
    private var publicKeyRing: PGPPublicKeyRing? = null

    @Volatile
    private var secretKeyRing: PGPSecretKeyRing? = null

    @Volatile
    private var protector: SecretKeyRingProtector? = null

    fun installKeys(
        secretRing: PGPSecretKeyRing,
        publicRing: PGPPublicKeyRing,
        ringProtector: SecretKeyRingProtector
    ) {
        secretKeyRing = secretRing
        publicKeyRing = publicRing
        protector = ringProtector
    }

    fun clearKeys() {
        publicKeyRing = null
        secretKeyRing = null
        protector = null
    }

    fun encrypt(text: String): String {
        val target = publicKeyRing ?: throw IllegalStateException("App bloqueado")
        val output = ByteArrayOutputStream()
        val encryptionOptions = EncryptionOptions.encryptDataAtRest().addRecipient(target)
        val producerOptions = ProducerOptions.encrypt(encryptionOptions).setAsciiArmor(false)
        PGPainless.encryptAndOrSign()
            .onOutputStream(output)
            .withOptions(producerOptions)
            .use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            }
        return PREFIX + android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
    }

    fun decrypt(text: String): String? {
        if (text.startsWith(PREFIX)) {
            return decryptPgp(text.removePrefix(PREFIX))
        }
        return null
    }

    fun isEncrypted(text: String): Boolean {
        return text.startsWith(PREFIX)
    }

    private fun decryptPgp(payload: String): String? {
        val secret = secretKeyRing ?: return null
        val ringProtector = protector ?: return null
        return runCatching {
            val raw = android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
            val consumerOptions = ConsumerOptions().addDecryptionKey(secret, ringProtector)
            PGPainless.decryptAndOrVerify()
                .onInputStream(ByteArrayInputStream(raw))
                .withOptions(consumerOptions)
                .use { stream ->
                    String(stream.readBytes(), Charsets.UTF_8)
                }
        }.getOrNull()
    }

}
