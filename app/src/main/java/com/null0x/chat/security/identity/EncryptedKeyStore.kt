package com.null0x.chat.security.identity

import android.content.Context
import android.content.SharedPreferences

data class IdentityRecord(
    val encryptedPrivateSeedB64: String,
    val saltB64: String,
    val kdfN: Int,
    val kdfR: Int,
    val kdfP: Int,
    val signingPublicKeyB64: String,
    val exchangePublicKeyB64: String,
    val createdAt: Long
)

interface EncryptedKeyStore {
    fun save(record: IdentityRecord)
    fun load(): IdentityRecord?
    fun clear()
}

class SharedPreferencesEncryptedKeyStore(
    private val prefs: SharedPreferences
) : EncryptedKeyStore {
    companion object {
        private const val KEY_ENCRYPTED_PRIVATE_SEED = "encrypted_private_seed"
        private const val KEY_SALT = "salt"
        private const val KEY_KDF_N = "kdf_n"
        private const val KEY_KDF_R = "kdf_r"
        private const val KEY_KDF_P = "kdf_p"
        private const val KEY_SIGNING_PUBLIC = "signing_public_key"
        private const val KEY_EXCHANGE_PUBLIC = "exchange_public_key"
        private const val KEY_CREATED_AT = "created_at"

        fun from(context: Context, name: String = "route_identity"): SharedPreferencesEncryptedKeyStore {
            return SharedPreferencesEncryptedKeyStore(
                context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            )
        }
    }

    override fun save(record: IdentityRecord) {
        prefs.edit()
            .putString(KEY_ENCRYPTED_PRIVATE_SEED, record.encryptedPrivateSeedB64)
            .putString(KEY_SALT, record.saltB64)
            .putInt(KEY_KDF_N, record.kdfN)
            .putInt(KEY_KDF_R, record.kdfR)
            .putInt(KEY_KDF_P, record.kdfP)
            .putString(KEY_SIGNING_PUBLIC, record.signingPublicKeyB64)
            .putString(KEY_EXCHANGE_PUBLIC, record.exchangePublicKeyB64)
            .putLong(KEY_CREATED_AT, record.createdAt)
            .apply()
    }

    override fun load(): IdentityRecord? {
        val encryptedSeed = prefs.getString(KEY_ENCRYPTED_PRIVATE_SEED, null)?.trim().orEmpty()
        val salt = prefs.getString(KEY_SALT, null)?.trim().orEmpty()
        val signingPublic = prefs.getString(KEY_SIGNING_PUBLIC, null)?.trim().orEmpty()
        val exchangePublic = prefs.getString(KEY_EXCHANGE_PUBLIC, null)?.trim().orEmpty()
        if (encryptedSeed.isBlank() || salt.isBlank() || signingPublic.isBlank() || exchangePublic.isBlank()) {
            return null
        }
        return IdentityRecord(
            encryptedPrivateSeedB64 = encryptedSeed,
            saltB64 = salt,
            kdfN = prefs.getInt(KEY_KDF_N, 16_384),
            kdfR = prefs.getInt(KEY_KDF_R, 8),
            kdfP = prefs.getInt(KEY_KDF_P, 1),
            signingPublicKeyB64 = signingPublic,
            exchangePublicKeyB64 = exchangePublic,
            createdAt = prefs.getLong(KEY_CREATED_AT, 0L)
        )
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

class InMemoryEncryptedKeyStore : EncryptedKeyStore {
    private var record: IdentityRecord? = null

    override fun save(record: IdentityRecord) {
        this.record = record
    }

    override fun load(): IdentityRecord? = record

    override fun clear() {
        record = null
    }
}
