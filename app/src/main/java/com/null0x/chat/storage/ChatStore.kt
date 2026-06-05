package com.null0x.chat.storage

import android.content.Context
import com.null0x.chat.model.DeliveryState
import com.null0x.chat.model.Message
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque

class ChatStore(context: Context) {

    private val chatsDir = File(context.filesDir, "chats").apply {
        mkdirs()
    }
    private val peersIndexFile = File(chatsDir, "peers.index")
    private val preferredLabelsFile = File(chatsDir, "preferred_labels.index")
    private var preferredLabelsCache: MutableMap<String, String>? = null

    @Synchronized
    fun load(peer: String): List<Message> {
        val file = chatFile(peer)
        if (!file.exists()) return emptyList()

        return file.useLines { lines ->
            lines.mapNotNull { line -> decodeMessageLine(line) }.toList()
        }
    }

    @Synchronized
    fun tail(peer: String, maxCount: Int): List<Message> {
        if (maxCount <= 0) return emptyList()
        val file = chatFile(peer)
        if (!file.exists()) return emptyList()

        val buffer = ArrayDeque<Message>(maxCount)
        file.useLines { lines ->
            lines.forEach { line ->
                val message = decodeMessageLine(line) ?: return@forEach
                if (buffer.size == maxCount) {
                    buffer.removeFirst()
                }
                buffer.addLast(message)
            }
        }
        return buffer.toList()
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
    fun rememberPeer(peer: String, preferredLabel: String? = null) {
        addPeerToIndex(peer)
        savePreferredLabel(peer, preferredLabel)
    }

    @Synchronized
    fun upsert(peer: String, message: Message) {
        val current = load(peer).toMutableList()
        val index = current.indexOfFirst { it.id == message.id && it.isMine == message.isMine }
        if (index >= 0) {
            current[index] = message
        } else {
            current.add(message)
        }
        writeAll(peer, current)
        addPeerToIndex(peer)
    }

    @Synchronized
    fun updateDeliveryStatus(peer: String, messageId: String, status: DeliveryState) {
        if (messageId.isBlank()) return
        val file = chatFile(peer)
        if (!file.exists()) return
        var changed = false
        val updatedLines = file.readLines().map { line ->
            val parts = line.split('|', limit = 5)
            if (parts.size >= 4 && parts[0] == "me" && parts[3] == messageId) {
                changed = true
                val delivery = status.name
                if (parts.size >= 5) {
                    "${parts[0]}|${parts[1]}|${parts[2]}|${parts[3]}|$delivery"
                } else {
                    "${parts[0]}|${parts[1]}|${parts.getOrNull(2).orEmpty()}|${parts[3]}|$delivery"
                }
            } else {
                line
            }
        }
        if (!changed) return
        file.writeText(updatedLines.joinToString(separator = "\n", postfix = "\n"))
    }

    @Synchronized
    fun hasMessage(peer: String, messageId: String, isMine: Boolean): Boolean {
        if (messageId.isBlank()) return false
        return load(peer).any { it.id == messageId && it.isMine == isMine }
    }

    @Synchronized
    fun pendingOutgoing(limit: Int = 250): List<Pair<String, Message>> {
        val out = mutableListOf<Pair<String, Message>>()
        for (peer in knownPeers()) {
            val remaining = limit - out.size
            if (remaining <= 0) break
            pendingOutgoingForPeer(peer, remaining).forEach { out.add(peer to it) }
            if (out.size >= limit) break
        }
        return out
    }

    @Synchronized
    fun clear(peer: String) {
        chatFile(peer).delete()
    }

    @Synchronized
    fun clearViewedMessages(peer: String) {
        val current = load(peer)
        if (current.isEmpty()) return
        val kept = current.filter { message ->
            message.isMine && message.delivery != DeliveryState.Sent && message.delivery != DeliveryState.Delivered
        }
        if (kept.isEmpty()) {
            clear(peer)
        } else {
            writeAll(peer, kept)
        }
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
            peersIndexFile.writeText(peers.joinToString(separator = "\n") { encodePeerIndexLine(it) })
        }
    }

    @Synchronized
    fun migratePeer(fromPeer: String, toPeer: String) {
        val from = fromPeer.trim()
        val to = toPeer.trim()
        if (from.isBlank() || to.isBlank() || from == to) return

        val merged = (load(to) + load(from))
            .distinctBy { "${it.isMine}:${it.id}" }
            .sortedBy { it.timestamp }
        if (merged.isNotEmpty()) {
            writeAll(to, merged)
        }
        chatFile(from).delete()

        val peers = knownPeers()
            .map { if (it == from) to else it }
            .distinct()
        if (peers.isEmpty()) {
            peersIndexFile.delete()
        } else {
            peersIndexFile.writeText(peers.joinToString(separator = "\n") { encodePeerIndexLine(it) })
        }
    }

    @Synchronized
    fun knownPeers(): List<String> {
        if (!peersIndexFile.exists()) return emptyList()
        return peersIndexFile.readLines()
            .map { line -> decodePeerIndexLine(line.trim()) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    @Synchronized
    fun preferredLabel(peer: String): String? {
        val clean = peer.trim()
        if (clean.isBlank()) return null
        return ensurePreferredLabelsCache()[clean]
    }

    private fun chatFile(peer: String): File {
        return File(chatsDir, "${safeName(peer)}.chat")
    }

    private fun safeName(peer: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(peer.trim().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun encodeText(text: String): String {
        return LocalStoreCipher.encrypt(text)
    }

    private fun decodeText(text: String): String? {
        return LocalStoreCipher.decrypt(text)
    }

    private fun decodeMessageLine(line: String): Message? {
        val parts = line.split('|', limit = 5)
        if (parts.size < 2) return null
        val direction = parts[0]
        val encodedText = parts[1]
        val timestamp = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        val id = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
            ?: java.util.UUID.randomUUID().toString()
        val delivery = parts.getOrNull(4)
            ?.let { raw -> DeliveryState.entries.firstOrNull { it.name == raw } }
            ?: if (direction == "me") DeliveryState.Sent else DeliveryState.Delivered
        val text = decodeText(encodedText) ?: return null
        return Message(
            id = id,
            text = text,
            isMine = direction == "me",
            timestamp = if (timestamp > 0L) timestamp else System.currentTimeMillis(),
            delivery = delivery
        )
    }

    private fun pendingOutgoingForPeer(peer: String, limit: Int): List<Message> {
        if (limit <= 0) return emptyList()
        val file = chatFile(peer)
        if (!file.exists()) return emptyList()

        val pending = mutableListOf<Message>()
        file.useLines { lines ->
            val iterator = lines.iterator()
            while (iterator.hasNext() && pending.size < limit) {
                val message = decodeMessageLine(iterator.next()) ?: continue
                if (message.isMine && message.delivery != DeliveryState.Delivered) {
                    pending.add(message)
                }
            }
        }
        return pending
    }

    private fun encodePeerIndexLine(peer: String): String {
        return LocalStoreCipher.encrypt(peer)
    }

    private fun decodePeerIndexLine(line: String): String {
        if (line.isBlank()) return ""
        return LocalStoreCipher.decrypt(line).orEmpty()
    }

    private fun ensurePreferredLabelsCache(): MutableMap<String, String> {
        preferredLabelsCache?.let { return it }
        val loaded = if (!preferredLabelsFile.exists()) {
            mutableMapOf()
        } else {
            preferredLabelsFile.readLines()
            .mapNotNull { line ->
                val parts = line.split('|', limit = 2)
                if (parts.size < 2) return@mapNotNull null
                val peer = decodeText(parts[0])?.trim().orEmpty()
                val label = decodeText(parts[1])?.trim().orEmpty()
                if (peer.isBlank() || label.isBlank()) null else peer to label
            }
            .toMap()
            .toMutableMap()
        }
        preferredLabelsCache = loaded
        return loaded
    }

    private fun savePreferredLabel(peer: String, label: String?) {
        val cleanPeer = peer.trim()
        val cleanLabel = label?.trim().orEmpty()
        if (cleanPeer.isBlank() || cleanLabel.isBlank()) return

        val labels = ensurePreferredLabelsCache()
        labels[cleanPeer] = cleanLabel
        if (labels.isEmpty()) {
            preferredLabelsFile.delete()
            return
        }

        preferredLabelsFile.writeText(
            labels.entries.joinToString(separator = "\n") { (storedPeer, storedLabel) ->
                "${encodeText(storedPeer)}|${encodeText(storedLabel)}"
            } + "\n"
        )
    }

    @Synchronized
    private fun writeAll(peer: String, messages: List<Message>) {
        val file = chatFile(peer)
        val lines = messages.joinToString(separator = "\n") { message ->
            val direction = if (message.isMine) "me" else "peer"
            "$direction|${encodeText(message.text)}|${message.timestamp}|${message.id}|${message.delivery.name}"
        }
        file.writeText(if (lines.isBlank()) "" else "$lines\n")
    }

    @Synchronized
    private fun addPeerToIndex(peer: String) {
        val clean = peer.trim()
        if (clean.isBlank()) return
        if (!LocalStoreCipher.canDecrypt()) {
            peersIndexFile.appendText("${encodePeerIndexLine(clean)}\n")
            return
        }
        val peers = knownPeers().toMutableList()
        if (!peers.contains(clean)) {
            peers.add(0, clean)
            peersIndexFile.writeText(peers.joinToString(separator = "\n") { encodePeerIndexLine(it) })
        }
    }
}
