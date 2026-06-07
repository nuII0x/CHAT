package com.null0x.chat.security.identity

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.Base64

data class SignedRequest(
    val method: String,
    val path: String,
    val body: String,
    val publicKeyB64: String,
    val timestamp: Long,
    val nonce: String,
    val signatureB64: String
)

class PrivateAuthMiddleware(
    private val authorizedPublicKeyB64: String,
    private val allowedClockSkewMs: Long = 5 * 60 * 1000L
) {
    private val seenNonces = ConcurrentHashMap<String, Long>()

    fun validateSignedRequest(request: SignedRequest, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!request.publicKeyB64.equals(authorizedPublicKeyB64, ignoreCase = false)) return false
        if (kotlin.math.abs(nowMs - request.timestamp) > allowedClockSkewMs) return false

        purgeExpiredNonces(nowMs)
        val nonceKey = "${request.publicKeyB64}:${request.nonce}"
        if (!verifySignature(request)) return false
        return seenNonces.putIfAbsent(nonceKey, request.timestamp) == null
    }

    private fun verifySignature(request: SignedRequest): Boolean {
        return runCatching {
            val publicKey = Ed25519PublicKeyParameters(decode(request.publicKeyB64), 0)
            val signer = Ed25519Signer()
            signer.init(false, publicKey)
            val canonical = canonicalRequest(request.method, request.path, request.body, request.timestamp, request.nonce)
            val payload = canonical.toByteArray(Charsets.UTF_8)
            signer.update(payload, 0, payload.size)
            signer.verifySignature(decode(request.signatureB64))
        }.getOrDefault(false)
    }

    private fun canonicalRequest(method: String, path: String, body: String, timestamp: Long, nonce: String): String {
        return buildString {
            append(method.trim().uppercase())
            append('\n')
            append(path.trim())
            append('\n')
            append(bodySha256Hex(body))
            append('\n')
            append(timestamp)
            append('\n')
            append(nonce.trim())
        }
    }

    private fun bodySha256Hex(body: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(body.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun purgeExpiredNonces(nowMs: Long) {
        val iterator = seenNonces.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMs - entry.value > allowedClockSkewMs) {
                iterator.remove()
            }
        }
    }

    private fun decode(encoded: String): ByteArray {
        return Base64.getDecoder().decode(encoded)
    }
}
