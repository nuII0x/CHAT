package com.null0x.chat.security.identity

import android.content.Context

object RouteIdentityRegistry {
    @Volatile
    private var initialized = false

    @Volatile
    private var identityManager: CryptoIdentityManager? = null

    @Volatile
    private var sendTokenManager: SendTokenManager? = null

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            val identityStore = SharedPreferencesEncryptedKeyStore.from(appContext)
            identityManager = CryptoIdentityManager(appContext, identityStore)
            sendTokenManager = SendTokenManager(
                store = object : SendTokenStore {
                    private val prefs = appContext.getSharedPreferences("send_token", Context.MODE_PRIVATE)
                    override fun load(): String? = prefs.getString("send_token", null)
                    override fun save(token: String) {
                        prefs.edit().putString("send_token", token).apply()
                    }
                    override fun clear() {
                        prefs.edit().remove("send_token").apply()
                    }
                }
            )
            initialized = true
        }
    }

    fun identityManager(): CryptoIdentityManager {
        return identityManager ?: throw IllegalStateException("Registry de identidade não inicializado")
    }

    fun sendTokenManager(): SendTokenManager {
        return sendTokenManager ?: throw IllegalStateException("Registry de token não inicializado")
    }

    fun hasIdentity(): Boolean {
        return identityManager()?.getPublicKey()?.isNotBlank() == true
    }

    fun createIdentity(
        password: String,
        language: MnemonicLanguage = MnemonicLanguage.ENGLISH
    ): IdentityCreationResult {
        return identityManager().createIdentity(password, language)
    }

    fun restoreIdentity(
        mnemonic: String,
        password: String,
        language: MnemonicLanguage = MnemonicLanguage.ENGLISH
    ): IdentityCreationResult {
        return identityManager().restoreIdentity(mnemonic, password, language)
    }

    fun unlock(password: String): Boolean {
        return identityManager().unlock(password)
    }

    fun lock() {
        identityManager().lock()
    }
}
