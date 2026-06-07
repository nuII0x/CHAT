package com.null0x.chat.security.identity

import java.security.SecureRandom
import java.util.Base64

interface SendTokenStore {
    fun load(): String?
    fun save(token: String)
    fun clear()
}

class InMemorySendTokenStore : SendTokenStore {
    private var token: String? = null

    override fun load(): String? = token

    override fun save(token: String) {
        this.token = token
    }

    override fun clear() {
        token = null
    }
}

class SendTokenManager(
    private val store: SendTokenStore,
    private val secureRandom: SecureRandom = SecureRandom()
) {
    fun currentToken(): String = store.load().orEmpty()

    fun ensureToken(): String {
        return currentToken().ifBlank { createToken() }
    }

    fun createToken(): String {
        val token = randomToken()
        store.save(token)
        return token
    }

    fun validateToken(token: String): Boolean {
        val current = store.load()?.trim().orEmpty()
        return current.isNotBlank() && current == token.trim()
    }

    fun rotateToken(): String {
        val token = randomToken()
        store.save(token)
        return token
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        return Base64.getEncoder().encodeToString(bytes)
    }
}
