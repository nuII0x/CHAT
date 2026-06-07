package com.null0x.chat.security.identity

import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

data class IdentityCreationResult(
    val mnemonic: String,
    val signingPublicKeyB64: String,
    val exchangePublicKeyB64: String,
    val createdAt: Long
)

class CryptoIdentityManager(
    private val mnemonicManager: MnemonicManager,
    private val keyStore: EncryptedKeyStore,
    private val secureRandom: SecureRandom = SecureRandom()
) {

    data class KeyDerivationParams(
        val n: Int = 16_384,
        val r: Int = 8,
        val p: Int = 1,
        val dkLen: Int = 32
    )

    private var unlockedSeed: ByteArray? = null

    fun createIdentity(password: String): IdentityCreationResult {
        val mnemonic = mnemonicManager.generate12Words()
        return restoreIdentity(mnemonic, password)
    }

    fun restoreIdentity(mnemonic: String, password: String): IdentityCreationResult {
        require(mnemonicManager.validateMnemonic(mnemonic)) {
            "Mnemonic inválida"
        }
        val seed = mnemonicManager.seedFromMnemonic(mnemonic)
        val signingPublic = signingPublicKeyFor(seed)
        val exchangePublic = exchangePublicKeyFor(seed)
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val params = KeyDerivationParams()
        val encryptedSeed = encryptSeed(seed, password, salt, params)
        keyStore.save(
            IdentityRecord(
                encryptedPrivateSeedB64 = encryptedSeed,
                saltB64 = encode(salt),
                kdfN = params.n,
                kdfR = params.r,
                kdfP = params.p,
                signingPublicKeyB64 = signingPublic,
                exchangePublicKeyB64 = exchangePublic,
                createdAt = System.currentTimeMillis()
            )
        )
        unlockSeed(seed)
        return IdentityCreationResult(
            mnemonic = mnemonic,
            signingPublicKeyB64 = signingPublic,
            exchangePublicKeyB64 = exchangePublic,
            createdAt = System.currentTimeMillis()
        )
    }

    fun unlock(password: String): Boolean {
        val record = keyStore.load() ?: return false
        val decryptedSeed = runCatching {
            decryptSeed(record.encryptedPrivateSeedB64, password, decode(record.saltB64), record)
        }.getOrNull() ?: return false
        val signingPublic = signingPublicKeyFor(decryptedSeed)
        val exchangePublic = exchangePublicKeyFor(decryptedSeed)
        if (signingPublic != record.signingPublicKeyB64 || exchangePublic != record.exchangePublicKeyB64) {
            decryptedSeed.fill(0)
            return false
        }
        unlockSeed(decryptedSeed)
        return true
    }

    fun lock() {
        unlockedSeed?.fill(0)
        unlockedSeed = null
    }

    fun isUnlocked(): Boolean = unlockedSeed != null

    fun getPublicKey(): String = keyStore.load()?.signingPublicKeyB64.orEmpty()

    fun getPublicKeyHash(): String {
        val publicKey = getPublicKey().trim()
        if (publicKey.isBlank()) return ""
        return sha256(Base64.getDecoder().decode(publicKey))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    fun getExchangePublicKey(): String = keyStore.load()?.exchangePublicKeyB64.orEmpty()

    fun getExchangePrivateKeyB64(): String {
        val seed = unlockedSeed ?: throw IllegalStateException("Identidade bloqueada")
        return encode(exchangeSeedFrom(seed))
    }

    fun signRequest(method: String, path: String, body: String, timestamp: Long, nonce: String): String {
        val seed = unlockedSeed ?: throw IllegalStateException("Identidade bloqueada")
        val privateKey = Ed25519PrivateKeyParameters(signingSeedFrom(seed), 0)
        val canonical = canonicalRequest(method, path, body, timestamp, nonce)
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        val payload = canonical.toByteArray(Charsets.UTF_8)
        signer.update(payload, 0, payload.size)
        return encode(signer.generateSignature())
    }

    fun signText(text: String): String {
        val seed = unlockedSeed ?: throw IllegalStateException("Identidade bloqueada")
        val privateKey = Ed25519PrivateKeyParameters(signingSeedFrom(seed), 0)
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        val payload = text.toByteArray(Charsets.UTF_8)
        signer.update(payload, 0, payload.size)
        return encode(signer.generateSignature())
    }

    fun verifyText(publicKeyB64: String, text: String, signatureB64: String): Boolean {
        return runCatching {
            val publicKey = Ed25519PublicKeyParameters(decode(publicKeyB64), 0)
            val signer = Ed25519Signer()
            signer.init(false, publicKey)
            val payload = text.toByteArray(Charsets.UTF_8)
            signer.update(payload, 0, payload.size)
            signer.verifySignature(decode(signatureB64))
        }.getOrDefault(false)
    }

    fun buildSignedRequestHeaders(
        method: String,
        path: String,
        body: String,
        timestamp: Long = System.currentTimeMillis(),
        nonce: String = randomNonce()
    ): Map<String, String> {
        return mapOf(
            "X-Public-Key" to getPublicKey(),
            "X-Timestamp" to timestamp.toString(),
            "X-Nonce" to nonce,
            "X-Signature" to signRequest(method, path, body, timestamp, nonce)
        )
    }

    fun privateSeedForTesting(): ByteArray? = unlockedSeed?.clone()

    private fun unlockSeed(seed: ByteArray) {
        unlockedSeed?.fill(0)
        unlockedSeed = seed.clone()
    }

    private fun signingSeedFrom(seed: ByteArray): ByteArray {
        return sha256(seed + "ed25519".toByteArray(Charsets.UTF_8))
    }

    private fun exchangeSeedFrom(seed: ByteArray): ByteArray {
        return sha256(seed + "x25519".toByteArray(Charsets.UTF_8))
    }

    private fun signingPublicKeyFor(seed: ByteArray): String {
        val privateKey = Ed25519PrivateKeyParameters(signingSeedFrom(seed), 0)
        return encode(privateKey.generatePublicKey().encoded)
    }

    private fun exchangePublicKeyFor(seed: ByteArray): String {
        val privateKey = X25519PrivateKeyParameters(exchangeSeedFrom(seed), 0)
        return encode(privateKey.generatePublicKey().encoded)
    }

    private fun encryptSeed(
        seed: ByteArray,
        password: String,
        salt: ByteArray,
        params: KeyDerivationParams
    ): String {
        val derived = SCrypt.generate(
            password.toByteArray(Charsets.UTF_8),
            salt,
            params.n,
            params.r,
            params.p,
            params.dkLen
        )
        val key = SecretKeySpec(derived, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(seed)
        derived.fill(0)
        return encode(iv + encrypted)
    }

    private fun decryptSeed(
        encryptedSeedB64: String,
        password: String,
        salt: ByteArray,
        record: IdentityRecord
    ): ByteArray {
        val payload = decode(encryptedSeedB64)
        if (payload.size < 13) {
            throw IllegalStateException("Seed criptografada inválida")
        }
        val derived = SCrypt.generate(
            password.toByteArray(Charsets.UTF_8),
            salt,
            record.kdfN,
            record.kdfR,
            record.kdfP,
            32
        )
        val key = SecretKeySpec(derived, "AES")
        val iv = payload.copyOfRange(0, 12)
        val body = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return runCatching { cipher.doFinal(body) }
            .also { derived.fill(0) }
            .getOrElse { throw IllegalArgumentException("Senha errada, tente novamente") }
    }

    private fun canonicalRequest(method: String, path: String, body: String, timestamp: Long, nonce: String): String {
        val normalizedMethod = method.trim().uppercase()
        val normalizedPath = path.trim()
        val normalizedNonce = nonce.trim()
        return buildString {
            append(normalizedMethod)
            append('\n')
            append(normalizedPath)
            append('\n')
            append(bodySha256Hex(body))
            append('\n')
            append(timestamp)
            append('\n')
            append(normalizedNonce)
        }
    }

    private fun bodySha256Hex(body: String): String {
        return sha256(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun sha256(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(input)
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(16).also { secureRandom.nextBytes(it) }
        return encode(bytes)
    }

    private fun encode(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun decode(encoded: String): ByteArray {
        return Base64.getDecoder().decode(encoded)
    }
}
