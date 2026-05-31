package com.null0x.chat.storage

import android.content.Context
import android.util.Base64
import com.null0x.chat.model.DeliveryState
import com.null0x.chat.model.Message
import java.io.File
import java.security.MessageDigest

class ChatStore(context: Context) {

    private val chatsDir = File(context.filesDir, "chats").apply {
        mkdirs()
    }
    private val peersIndexFile = File(chatsDir, "peers.index")

    @Synchronized
    fun load(peer: String): List<Message> {
        val file = chatFile(peer)
        if (!file.exists()) return emptyList()

        return file.readLines()
            .mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 2) return@mapNotNull null
                val direction = parts[0]
                val encodedText = parts[1]
                val timestamp = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                val id = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
                    ?: java.util.UUID.randomUUID().toString()
                val delivery = parts.getOrNull(4)
                    ?.let { raw -> DeliveryState.entries.firstOrNull { it.name == raw } }
                    ?: if (direction == "me") DeliveryState.Sent else DeliveryState.Delivered
                val text = decodeText(encodedText) ?: return@mapNotNull null
                Message(
                    id = id,
                    text = text,
                    isMine = direction == "me",
                    timestamp = if (timestamp > 0L) timestamp else System.currentTimeMillis(),
                    delivery = delivery
                )
            }
    }

    @Synchronized
    fun append(peer: String, message: Message) {
        val direction = if (message.isMine) "me" else "peer"
        chatFile(peer).appendText(
            "$direction|${encodeText(message.text)}|${message.timestamp}|${message.id}|${message.delivery.name}\n"
        )
        addPeerToIndex(peer)
    }

    @Synchronized
    fun updateDeliveryStatus(peer: String, messageId: String, status: DeliveryState) {
        if (messageId.isBlank()) return
        val current = load(peer)
        if (current.isEmpty()) return
        var changed = false
        val updated = current.map { item ->
            if (item.id == messageId && item.isMine) {
                changed = true
                item.copy(delivery = status)
            } else {
                item
            }
        }
        if (!changed) return
        val file = chatFile(peer)
        file.writeText("")
        updated.forEach { append(peer, it) }
    }

    @Synchronized
    fun clear(peer: String) {
        chatFile(peer).delete()
    }

    @Synchronized
    fun remove(peer: String) {
        val clean = peer.trim()
        if (clean.isBlank()) return

        chatFile(clean).delete()
        val peers = knownPeers().filterNot { it == clean }
        if (peers.isEmpty()) {
            peersIndexFile.delete()
        } else {
            peersIndexFile.writeText(peers.joinToString(separator = "\n"))
        }
    }

    @Synchronized
    fun knownPeers(): List<String> {
        if (!peersIndexFile.exists()) return emptyList()
        return peersIndexFile.readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun chatFile(peer: String): File {
        return File(chatsDir, "${safeName(peer)}.chat")
    }

    private fun safeName(peer: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(peer.trim().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun encodeText(text: String): String {
        return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun decodeText(text: String): String? {
        return runCatching {
            String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }

    @Synchronized
    private fun addPeerToIndex(peer: String) {
        val clean = peer.trim()
        if (clean.isBlank()) return
        val peers = knownPeers().toMutableList()
        if (!peers.contains(clean)) {
            peers.add(0, clean)
            peersIndexFile.writeText(peers.joinToString(separator = "\n"))
        }
    }
}
