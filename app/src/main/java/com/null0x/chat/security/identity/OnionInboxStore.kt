package com.null0x.chat.security.identity

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class OnionInboxMessage(
    val id: String,
    val recipientPublicKey: String,
    val senderPublicKey: String,
    val nonce: String,
    val timestamp: Long,
    val ciphertext: String,
    val read: Boolean,
    val receivedAt: Long
)

class OnionInboxStore(context: Context) {
    private val inboxFile = File(context.filesDir, "onion_inbox/messages.json")

    init {
        inboxFile.parentFile?.mkdirs()
    }

    @Synchronized
    fun append(
        recipientPublicKey: String,
        senderPublicKey: String,
        nonce: String,
        timestamp: Long,
        ciphertext: String
    ): OnionInboxMessage {
        val message = OnionInboxMessage(
            id = messageId(recipientPublicKey, nonce, ciphertext),
            recipientPublicKey = recipientPublicKey,
            senderPublicKey = senderPublicKey,
            nonce = nonce,
            timestamp = timestamp,
            ciphertext = ciphertext,
            read = false,
            receivedAt = System.currentTimeMillis()
        )
        val messages = listMessages().filterNot { it.id == message.id } + message
        writeAll(messages.sortedByDescending { it.receivedAt }.take(500))
        return message
    }

    @Synchronized
    fun listMessages(): List<OnionInboxMessage> {
        if (!inboxFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(inboxFile.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        OnionInboxMessage(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            recipientPublicKey = item.optString("recipientPublicKey"),
                            senderPublicKey = item.optString("senderPublicKey"),
                            nonce = item.optString("nonce"),
                            timestamp = item.optLong("timestamp"),
                            ciphertext = item.optString("ciphertext"),
                            read = item.optBoolean("read", false),
                            receivedAt = item.optLong("receivedAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun markRead(id: String): Boolean {
        var changed = false
        val updated = listMessages().map { message ->
            if (message.id == id) {
                changed = true
                message.copy(read = true)
            } else {
                message
            }
        }
        if (changed) writeAll(updated)
        return changed
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val current = listMessages()
        val updated = current.filterNot { it.id == id }
        if (updated.size == current.size) return false
        writeAll(updated)
        return true
    }

    private fun writeAll(messages: List<OnionInboxMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("recipientPublicKey", message.recipientPublicKey)
                    .put("senderPublicKey", message.senderPublicKey)
                    .put("nonce", message.nonce)
                    .put("timestamp", message.timestamp)
                    .put("ciphertext", message.ciphertext)
                    .put("read", message.read)
                    .put("receivedAt", message.receivedAt)
            )
        }
        inboxFile.writeText(array.toString())
    }

    private fun messageId(recipientPublicKey: String, nonce: String, ciphertext: String): String {
        val payload = "$recipientPublicKey|$nonce|$ciphertext"
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
