package com.null0x.chat.security

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.null0x.chat.storage.LocalStoreCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.key.protection.SecretKeyRingProtector
import org.pgpainless.util.Passphrase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AppSecurityManager {

    sealed interface GateState {
        data object Uninitialized : GateState
        data object SetupRequired : GateState
        data object Locked : GateState
        data object Unlocked : GateState
    }

    private const val PREFS_NAME = "app_security"
    private const val SALT_KEY = "install_salt"
    private const val PGP_SECRET_RING_KEY = "pgp_secret_key_ring"
    private const val PGP_VERIFIER_KEY = "pgp_verifier"
    private const val AUTO_UNLOCK_KEY = "auto_unlock_blob"
    private const val MANUAL_LOCK_KEY = "manual_lock_enabled"
    private const val AUTO_UNLOCK_ALIAS = "rotasegura_auto_unlock"
    private const val CHECK_TEXT = "ROTASEGURA_LOCK_OK"
    private const val WRONG_PASSWORD_MESSAGE = "Senha errada, tente novamente"
    private const val CREATE_PASSWORD_ERROR_MESSAGE = "Não foi possível criar a senha, tente novamente"

    private val random = SecureRandom()
    private val _state = MutableStateFlow<GateState>(GateState.Uninitialized)
    val state: StateFlow<GateState> = _state.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        val contextRef = context.applicationContext
        appContext = contextRef
        if (_state.value != GateState.Uninitialized) {
            return
        }
        val prefs = contextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasPgpSetup = !prefs.getString(PGP_SECRET_RING_KEY, null).isNullOrBlank()
        LocalStoreCipher.clearAllKeys()
        if (!hasPgpSetup) {
            _state.value = GateState.SetupRequired
            return
        }
        installPublicEncryptionKey(prefs)
        if (prefs.getBoolean(MANUAL_LOCK_KEY, false)) {
            _state.value = GateState.Locked
            return
        }
        _state.value = if (restoreAutoUnlock(prefs)) {
            GateState.Unlocked
        } else {
            GateState.Locked
        }
    }

    fun isUnlocked(): Boolean = _state.value is GateState.Unlocked

    fun unlock(context: Context, password: String): Result<Unit> {
        return runCatching {
            val contextRef = context.applicationContext
            val prefs = contextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val salt = loadSalt(prefs) ?: throw IllegalStateException("Proteção ainda não configurada")

            val secretArmor = prefs.getString(PGP_SECRET_RING_KEY, null)
                ?: throw IllegalStateException("Proteção ainda não configurada")
            val secretKeyRing = readSecretKeyRing(secretArmor)
            val passphrase = Passphrase.fromPassword(derivePgpPassphrase(contextRef, password, salt))
            val protector = SecretKeyRingProtector.unlockEachKeyWith(passphrase, secretKeyRing)
            val publicKeyRing = PGPainless.extractCertificate(secretKeyRing)
            val verifier = prefs.getString(PGP_VERIFIER_KEY, null)
                ?: throw IllegalStateException("Proteção inválida")
            val plain = decryptPgpText(secretKeyRing, protector, verifier)
            if (plain != CHECK_TEXT) {
                throw IllegalArgumentException("Senha incorreta")
            }

            installKeys(secretKeyRing, publicKeyRing, protector)
            persistAutoUnlockToken(prefs, derivePgpPassphrase(contextRef, password, salt))
            prefs.edit().putBoolean(MANUAL_LOCK_KEY, false).apply()
            _state.value = GateState.Unlocked
            Unit
        }.sanitizePasswordFailure()
    }

    fun verifyPassword(context: Context, password: String): Result<Unit> {
        return runCatching {
            val contextRef = context.applicationContext
            val prefs = contextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val salt = loadSalt(prefs) ?: throw IllegalStateException("Proteção ainda não configurada")
            val secretArmor = prefs.getString(PGP_SECRET_RING_KEY, null)
                ?: throw IllegalStateException("Proteção ainda não configurada")
            val verifier = prefs.getString(PGP_VERIFIER_KEY, null)
                ?: throw IllegalStateException("Proteção inválida")
            val secretKeyRing = readSecretKeyRing(secretArmor)
            val passphrase = Passphrase.fromPassword(derivePgpPassphrase(contextRef, password, salt))
            val protector = SecretKeyRingProtector.unlockEachKeyWith(passphrase, secretKeyRing)
            val plain = decryptPgpText(secretKeyRing, protector, verifier)
            if (plain != CHECK_TEXT) {
                throw IllegalArgumentException("Senha incorreta")
            }
            Unit
        }.sanitizePasswordFailure()
    }

    fun createPassword(context: Context, password: String): Result<Unit> {
        return runCatching {
            val contextRef = context.applicationContext
            val prefs = contextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val salt = loadSalt(prefs) ?: generateSalt().also {
                prefs.edit().putString(SALT_KEY, Base64.encodeToString(it, Base64.NO_WRAP)).apply()
            }
            val secretKeyRing = generateSecretRing(contextRef, password, salt)
            val publicKeyRing = PGPainless.extractCertificate(secretKeyRing)
            val protector = SecretKeyRingProtector.unlockEachKeyWith(
                Passphrase.fromPassword(derivePgpPassphrase(contextRef, password, salt)),
                secretKeyRing
            )
            val verifier = encryptPgpText(publicKeyRing, CHECK_TEXT)
            prefs.edit()
                .putString(PGP_SECRET_RING_KEY, PGPainless.asciiArmor(secretKeyRing))
                .putString(PGP_VERIFIER_KEY, verifier)
                .apply()
            installKeys(secretKeyRing, publicKeyRing, protector)
            persistAutoUnlockToken(prefs, derivePgpPassphrase(contextRef, password, salt))
            prefs.edit().putBoolean(MANUAL_LOCK_KEY, false).apply()
            _state.value = GateState.Unlocked
            Unit
        }.sanitizeCreatePasswordFailure()
    }

    fun lock() {
        LocalStoreCipher.clearDecryptionKeys()
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.let { prefs -> installPublicEncryptionKey(prefs) }
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(MANUAL_LOCK_KEY, true)
            ?.apply()
        _state.value = if (hasConfiguration()) GateState.Locked else GateState.SetupRequired
    }

    fun currentState(): GateState = _state.value

    private fun hasConfiguration(): Boolean {
        val contextRef = appContext ?: return false
        val prefs = contextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(PGP_SECRET_RING_KEY, null).isNullOrBlank()
    }

    private fun loadSalt(prefs: android.content.SharedPreferences): ByteArray? {
        val raw = prefs.getString(SALT_KEY, null) ?: return null
        return runCatching { Base64.decode(raw, Base64.DEFAULT) }.getOrNull()
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }

    private fun derivePgpPassphrase(context: Context, password: String, salt: ByteArray): String {
        val combinedSalt = salt + deviceEntropy(context)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), combinedSalt, 160_000, 256)
        val encoded = factory.generateSecret(spec).encoded
        return Base64.encodeToString(encoded, Base64.NO_WRAP)
    }

    private fun deviceEntropy(context: Context): ByteArray {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val payload = buildString {
            append(androidId)
            append('|')
            append(context.packageName)
            append('|')
            append(android.os.Build.FINGERPRINT)
            append('|')
            append(android.os.Build.MODEL)
            append('|')
            append(android.os.Build.MANUFACTURER)
        }
        return payload.toByteArray(Charsets.UTF_8)
    }

    private fun generateSecretRing(context: Context, password: String, salt: ByteArray): PGPSecretKeyRing {
        val passphrase = derivePgpPassphrase(context, password, salt)
        val userId = buildUserId(context)
        val templates = PGPainless.generateKeyRing()
        return templates.modernKeyRing(userId, passphrase)
    }

    private fun buildUserId(context: Context): String {
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(deviceEntropy(context))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return "RotaSegura <$fingerprint>"
    }

    private fun installPublicEncryptionKey(prefs: android.content.SharedPreferences) {
        val secretArmor = prefs.getString(PGP_SECRET_RING_KEY, null) ?: return
        val publicKeyRing = runCatching {
            PGPainless.extractCertificate(readSecretKeyRing(secretArmor))
        }.getOrNull() ?: return
        LocalStoreCipher.installEncryptionKey(publicKeyRing)
    }

    private fun encryptPgpText(publicKeyRing: PGPPublicKeyRing, plain: String): String {
        val output = ByteArrayOutputStream()
        val encryptionOptions = EncryptionOptions.encryptDataAtRest().addRecipient(publicKeyRing)
        val producerOptions = ProducerOptions.encrypt(encryptionOptions).setAsciiArmor(false)
        PGPainless.encryptAndOrSign()
            .onOutputStream(output)
            .withOptions(producerOptions)
            .use { stream ->
                stream.write(plain.toByteArray(Charsets.UTF_8))
            }
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun decryptPgpText(
        secretKeyRing: PGPSecretKeyRing,
        protector: SecretKeyRingProtector,
        payload: String
    ): String {
        val raw = Base64.decode(payload, Base64.DEFAULT)
        val consumerOptions = ConsumerOptions().addDecryptionKey(secretKeyRing, protector)
        val input = ByteArrayInputStream(raw)
        return PGPainless.decryptAndOrVerify()
            .onInputStream(input)
            .withOptions(consumerOptions)
            .use { stream ->
                String(stream.readBytes(), Charsets.UTF_8)
            }
    }

    private fun installKeys(
        secretKeyRing: PGPSecretKeyRing,
        publicKeyRing: PGPPublicKeyRing,
        protector: SecretKeyRingProtector
    ) {
        LocalStoreCipher.installKeys(secretKeyRing, publicKeyRing, protector)
    }

    private fun persistAutoUnlockToken(prefs: android.content.SharedPreferences, token: String) {
        val encrypted = encryptAutoUnlockToken(token)
        prefs.edit().putString(AUTO_UNLOCK_KEY, encrypted).apply()
    }

    private fun restoreAutoUnlock(prefs: android.content.SharedPreferences): Boolean {
        val token = prefs.getString(AUTO_UNLOCK_KEY, null) ?: return false
        return runCatching {
            val secretArmor = prefs.getString(PGP_SECRET_RING_KEY, null)
                ?: throw IllegalStateException("Proteção ainda não configurada")
            val secretKeyRing = readSecretKeyRing(secretArmor)
            loadSalt(prefs) ?: throw IllegalStateException("Proteção ainda não configurada")
            val passphrase = Passphrase.fromPassword(decryptAutoUnlockToken(token))
            val protector = SecretKeyRingProtector.unlockEachKeyWith(passphrase, secretKeyRing)
            val publicKeyRing = PGPainless.extractCertificate(secretKeyRing)
            val verifier = prefs.getString(PGP_VERIFIER_KEY, null)
                ?: throw IllegalStateException("Proteção inválida")
            val plain = decryptPgpText(secretKeyRing, protector, verifier)
            if (plain != CHECK_TEXT) {
                throw IllegalStateException("Proteção inválida")
            }
            installKeys(secretKeyRing, publicKeyRing, protector)
            prefs.edit().putBoolean(MANUAL_LOCK_KEY, false).apply()
        }.isSuccess
    }

    private fun encryptAutoUnlockToken(token: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, autoUnlockSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    private fun decryptAutoUnlockToken(encoded: String): String {
        val raw = Base64.decode(encoded, Base64.DEFAULT)
        if (raw.size < 13) {
            throw IllegalStateException("Token de desbloqueio inválido")
        }
        val iv = raw.copyOfRange(0, 12)
        val payload = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, autoUnlockSecretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(payload), Charsets.UTF_8)
    }

    private fun autoUnlockSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
        (keyStore.getKey(AUTO_UNLOCK_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            AUTO_UNLOCK_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun readSecretKeyRing(armor: String): PGPSecretKeyRing {
        val collection = PGPainless.readKeyRing().secretKeyRingCollection(armor)
        val iterator = collection.iterator()
        if (!iterator.hasNext()) {
            throw IllegalStateException("Chave privada OpenPGP inválida")
        }
        return iterator.next()
    }

    private fun Result<Unit>.sanitizePasswordFailure(): Result<Unit> {
        return fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(IllegalArgumentException(WRONG_PASSWORD_MESSAGE)) }
        )
    }

    private fun Result<Unit>.sanitizeCreatePasswordFailure(): Result<Unit> {
        return fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(IllegalStateException(CREATE_PASSWORD_ERROR_MESSAGE)) }
        )
    }
}
