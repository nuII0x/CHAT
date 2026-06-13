package com.null0x.chat.storage

import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.key.protection.SecretKeyRingProtector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Date

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

    fun installEncryptionKey(publicRing: PGPPublicKeyRing) {
        publicKeyRing = publicRing
    }

    fun clearDecryptionKeys() {
        secretKeyRing = null
        protector = null
    }

    fun clearAllKeys() {
        publicKeyRing = null
        clearDecryptionKeys()
    }

    fun encrypt(text: String): String {
        val target = publicKeyRing ?: throw IllegalStateException("App bloqueado")
        val encryptionKey = target.publicKeys.asSequence()
            .firstOrNull { it.isEncryptionKey }
            ?: throw IllegalStateException("Chave local sem subchave de criptografia")
        val encrypted = encryptWithPublicKey(text.toByteArray(Charsets.UTF_8), encryptionKey)
        return PREFIX + android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
    }

    private fun encryptWithPublicKey(data: ByteArray, encryptionKey: PGPPublicKey): ByteArray {
        val random = SecureRandom()
        val output = ByteArrayOutputStream()
        val encryptor = BcPGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
            .setWithIntegrityPacket(true)
            .setSecureRandom(random)
        val encryptedDataGenerator = PGPEncryptedDataGenerator(encryptor)
        encryptedDataGenerator.addMethod(
            BcPublicKeyKeyEncryptionMethodGenerator(encryptionKey).setSecureRandom(random)
        )
        encryptedDataGenerator.open(output, ByteArray(1 shl 16)).use { encryptedOut ->
            val literalDataGenerator = PGPLiteralDataGenerator()
            try {
                literalDataGenerator.open(
                    encryptedOut,
                    PGPLiteralData.BINARY,
                    PGPLiteralData.CONSOLE,
                    data.size.toLong(),
                    Date()
                ).use { literalOut ->
                    literalOut.write(data)
                }
            } finally {
                literalDataGenerator.close()
            }
        }
        return output.toByteArray()
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

    fun canDecrypt(): Boolean {
        return secretKeyRing != null && protector != null
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
