package com.null0x.chat.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Locale

class NameRegistry(context: Context) {
    data class Record(
        val name: String,
        val normalizedName: String,
        val username: String,
        val displayName: String,
        val ownerPublicKey: String,
        val sequence: Long,
        val updatedAt: Long,
        val signature: String
    ) {
        val claimId: String = sha256Hex("$normalizedName|$ownerPublicKey")
        val recordId: String = sha256Hex("${canonicalPayload()}|$signature")

        fun canonicalPayload(): String {
            return listOf(
                "v1",
                normalizedName,
                username,
                displayName,
                ownerPublicKey,
                sequence.toString(),
                updatedAt.toString()
            ).joinToString("|")
        }
    }

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("name_identity", Context.MODE_PRIVATE)
    private val file = File(appContext.filesDir, "name-registry.jsonl")
    private val recordsById = linkedMapOf<String, Record>()
    private val keyPair = loadOrCreateKeyPair()

    init {
        load()
    }

    @Synchronized
    fun register(name: String, username: String, displayName: String): Result<Record> {
        val normalized = normalizeName(name)
        if (normalized == null) {
            return Result.failure(IllegalArgumentException("Nome unico invalido"))
        }
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException("Rota local ainda nao esta pronta"))
        }

        val existing = recordsById.values
            .filter { it.normalizedName == normalized && it.ownerPublicKey == publicKeyText() }
            .maxByOrNull { it.sequence }
        val unsigned = Record(
            name = normalized,
            normalizedName = normalized,
            username = username,
            displayName = displayName.trim().ifBlank { "DoveChat" },
            ownerPublicKey = publicKeyText(),
            sequence = (existing?.sequence ?: 0L) + 1L,
            updatedAt = System.currentTimeMillis(),
            signature = ""
        )
        val signature = sign(unsigned.canonicalPayload().toByteArray(Charsets.UTF_8), keyPair.private)
        val record = unsigned.copy(signature = signature)
        if (!isValid(record)) {
            return Result.failure(IllegalStateException("Falha ao validar assinatura local"))
        }
        recordsById[record.recordId] = record
        save()
        return Result.success(record)
    }

    @Synchronized
    fun resolve(name: String): Record? {
        val normalized = normalizeName(name) ?: return null
        return winnerFor(normalized)
    }

    @Synchronized
    fun allRecords(): List<Record> = recordsById.values.toList()

    @Synchronized
    fun exportPayload(): String {
        val array = JSONArray()
        allRecords().forEach { array.put(it.toJson()) }
        return array.toString()
    }

    @Synchronized
    fun importPayload(payload: String): Boolean {
        val imported = runCatching {
            val array = JSONArray(payload)
            var changed = false
            for (i in 0 until array.length()) {
                val record = array.getJSONObject(i).toRecord() ?: continue
                if (!isValid(record)) continue
                if (!recordsById.containsKey(record.recordId)) {
                    recordsById[record.recordId] = record
                    changed = true
                }
            }
            changed
        }.getOrDefault(false)
        if (imported) save()
        return imported
    }

    private fun winnerFor(normalizedName: String): Record? {
        return recordsById.values
            .filter { it.normalizedName == normalizedName && isValid(it) }
            .groupBy { it.ownerPublicKey }
            .mapNotNull { (_, records) -> records.maxWithOrNull(compareBy<Record> { it.sequence }.thenBy { it.updatedAt }) }
            .minWithOrNull(compareBy<Record> { it.claimId }.thenBy { it.recordId })
    }

    private fun isValid(record: Record): Boolean {
        if (normalizeName(record.name) != record.normalizedName) return false
        if (!isValidRoute(record.username)) return false
        if (record.ownerPublicKey.isBlank() || record.signature.isBlank()) return false
        return runCatching {
            val publicKey = decodePublicKey(record.ownerPublicKey)
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(record.copy(signature = "").canonicalPayload().toByteArray(Charsets.UTF_8))
            verifier.verify(Base64.decode(record.signature, Base64.DEFAULT))
        }.getOrDefault(false)
    }

    private fun load() {
        if (!file.exists()) return
        file.readLines()
            .mapNotNull { line -> runCatching { JSONObject(line).toRecord() }.getOrNull() }
            .filter { isValid(it) }
            .forEach { recordsById[it.recordId] = it }
    }

    private fun save() {
        file.writeText(recordsById.values.joinToString(separator = "\n") { it.toJson().toString() })
    }

    private fun loadOrCreateKeyPair(): KeyPair {
        val privateText = prefs.getString("private_key", null)
        val publicText = prefs.getString("public_key", null)
        if (!privateText.isNullOrBlank() && !publicText.isNullOrBlank()) {
            val privateKey = decodePrivateKey(privateText)
            val publicKey = decodePublicKey(publicText)
            return KeyPair(publicKey, privateKey)
        }

        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        prefs.edit()
            .putString("private_key", Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP))
            .putString("public_key", Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP))
            .apply()
        return pair
    }

    private fun publicKeyText(): String = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

    private fun sign(data: ByteArray, privateKey: PrivateKey): String {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(data)
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
    }

    private fun decodePrivateKey(text: String): PrivateKey {
        val spec = PKCS8EncodedKeySpec(Base64.decode(text, Base64.DEFAULT))
        return KeyFactory.getInstance("EC").generatePrivate(spec)
    }

    private fun decodePublicKey(text: String): PublicKey {
        val spec = X509EncodedKeySpec(Base64.decode(text, Base64.DEFAULT))
        return KeyFactory.getInstance("EC").generatePublic(spec)
    }

    private fun Record.toJson(): JSONObject {
        return JSONObject()
            .put("name", name)
            .put("normalizedName", normalizedName)
            .put("username", username)
            .put("displayName", displayName)
            .put("ownerPublicKey", ownerPublicKey)
            .put("sequence", sequence)
            .put("updatedAt", updatedAt)
            .put("signature", signature)
    }

    private fun JSONObject.toRecord(): Record? {
        return runCatching {
            Record(
                name = getString("name"),
                normalizedName = getString("normalizedName"),
                username = getString("username"),
                displayName = optString("displayName", "DoveChat"),
                ownerPublicKey = getString("ownerPublicKey"),
                sequence = getLong("sequence"),
                updatedAt = getLong("updatedAt"),
                signature = getString("signature")
            )
        }.getOrNull()
    }

    companion object {
        fun normalizeName(name: String): String? {
            val normalized = name.trim().lowercase(Locale.US)
            if (normalized.length !in 3..32) return null
            if (normalized.any { it !in 'a'..'z' && it !in '0'..'9' && it != '_' && it != '-' }) return null
            return normalized
        }

        fun isValidRoute(route: String): Boolean {
            val clean = route.trim()
            if (UsernameCodec.decode(clean) != null) return true
            if (!clean.startsWith("onion:", ignoreCase = true)) return false
            val value = clean.substringAfter(':')
            val separator = value.lastIndexOf(':')
            if (separator <= 0 || separator == value.lastIndex) return false
            val host = value.substring(0, separator).lowercase(Locale.US)
            val port = value.substring(separator + 1).toIntOrNull() ?: return false
            return host.endsWith(".onion") && port in 1..65535
        }

        private fun sha256Hex(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}
