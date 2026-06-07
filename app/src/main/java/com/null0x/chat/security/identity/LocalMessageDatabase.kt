package com.null0x.chat.security.identity

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class StoredEnvelopeRecord(
    val envelope: MessageEnvelope,
    val storedAt: Long = envelope.timestamp,
    val replicaPeers: Set<String> = emptySet(),
    val deliveredAt: Long? = null,
    val ackedAt: Long? = null
)

interface LocalMessageDatabase {
    fun upsert(record: StoredEnvelopeRecord): StoredEnvelopeRecord
    fun get(messageId: String): StoredEnvelopeRecord?
    fun listAll(): List<StoredEnvelopeRecord>
    fun listForRecipient(recipientPublicKeyHash: String, limit: Int = 50): List<StoredEnvelopeRecord>
    fun listPendingReplication(replicaFactor: Int, nowMs: Long = System.currentTimeMillis(), limit: Int = 50): List<StoredEnvelopeRecord>
    fun markReplica(messageId: String, peer: String)
    fun markDelivered(messageId: String, deliveredAt: Long = System.currentTimeMillis())
    fun markAcked(messageId: String, ackedAt: Long = System.currentTimeMillis())
    fun remove(messageId: String)
    fun purgeExpired(nowMs: Long = System.currentTimeMillis()): Int
    fun clear()
}

class FileLocalMessageDatabase(
    rootDir: File
) : LocalMessageDatabase {

    private val storageFile = File(rootDir, "p2p_messages/messages.json")

    init {
        storageFile.parentFile?.mkdirs()
    }

    @Synchronized
    override fun upsert(record: StoredEnvelopeRecord): StoredEnvelopeRecord {
        val current = listAll().toMutableList()
        val index = current.indexOfFirst { it.envelope.messageId == record.envelope.messageId }
        val merged = if (index >= 0) {
            val existing = current[index]
            existing.copy(
                envelope = record.envelope.copy(
                    deliveryStatus = when {
                        existing.ackedAt != null || record.ackedAt != null -> EnvelopeDeliveryStatus.Acked
                        existing.deliveredAt != null || record.deliveredAt != null -> EnvelopeDeliveryStatus.Delivered
                        existing.envelope.deliveryStatus.ordinal >= record.envelope.deliveryStatus.ordinal -> existing.envelope.deliveryStatus
                        else -> record.envelope.deliveryStatus
                    }
                ),
                storedAt = minOf(existing.storedAt, record.storedAt),
                replicaPeers = existing.replicaPeers + record.replicaPeers,
                deliveredAt = existing.deliveredAt ?: record.deliveredAt,
                ackedAt = existing.ackedAt ?: record.ackedAt
            ).also { current[index] = it }
        } else {
            current.add(record)
            record
        }
        writeAll(current)
        return merged
    }

    @Synchronized
    override fun get(messageId: String): StoredEnvelopeRecord? {
        return listAll().firstOrNull { it.envelope.messageId == messageId }
    }

    @Synchronized
    override fun listAll(): List<StoredEnvelopeRecord> {
        if (!storageFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(storageFile.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val envelopeJson = item.optJSONObject("envelope") ?: continue
                    val envelope = MessageEnvelope.fromJson(envelopeJson) ?: continue
                    add(
                            StoredEnvelopeRecord(
                                envelope = envelope,
                                storedAt = item.optLong("storedAt", envelope.timestamp),
                                replicaPeers = item.optJSONArray("replicaPeers")?.toStringSet().orEmpty(),
                                deliveredAt = item.optLongOrNull("deliveredAt"),
                                ackedAt = item.optLongOrNull("ackedAt")
                            )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    override fun listForRecipient(recipientPublicKeyHash: String, limit: Int): List<StoredEnvelopeRecord> {
        return listAll()
            .asSequence()
            .filter { it.envelope.recipientPublicKeyHash == recipientPublicKeyHash }
            .filterNot { it.isExpired() }
            .filter { it.ackedAt == null }
            .sortedBy { it.envelope.timestamp }
            .take(limit)
            .toList()
    }

    @Synchronized
    override fun listPendingReplication(replicaFactor: Int, nowMs: Long, limit: Int): List<StoredEnvelopeRecord> {
        return listAll()
            .asSequence()
            .filterNot { it.isExpired(nowMs) }
            .filter { it.ackedAt == null }
            .filter { it.replicaPeers.size < replicaFactor }
            .sortedBy { it.envelope.timestamp }
            .take(limit)
            .toList()
    }

    @Synchronized
    override fun markReplica(messageId: String, peer: String) {
        val current = listAll().toMutableList()
        val index = current.indexOfFirst { it.envelope.messageId == messageId }
        if (index < 0) return
        val existing = current[index]
        val updated = existing.copy(replicaPeers = existing.replicaPeers + peer.trim())
        current[index] = updated
        writeAll(current)
    }

    @Synchronized
    override fun markDelivered(messageId: String, deliveredAt: Long) {
        val current = listAll().toMutableList()
        val index = current.indexOfFirst { it.envelope.messageId == messageId }
        if (index < 0) return
        val existing = current[index]
        current[index] = existing.copy(
            envelope = existing.envelope.copy(deliveryStatus = EnvelopeDeliveryStatus.Delivered),
            deliveredAt = deliveredAt
        )
        writeAll(current)
    }

    @Synchronized
    override fun markAcked(messageId: String, ackedAt: Long) {
        val current = listAll().toMutableList()
        val index = current.indexOfFirst { it.envelope.messageId == messageId }
        if (index < 0) return
        val existing = current[index]
        current[index] = existing.copy(
            envelope = existing.envelope.copy(deliveryStatus = EnvelopeDeliveryStatus.Acked),
            ackedAt = ackedAt
        )
        writeAll(current)
    }

    @Synchronized
    override fun remove(messageId: String) {
        val current = listAll()
        val updated = current.filterNot { it.envelope.messageId == messageId }
        if (updated.size != current.size) {
            writeAll(updated)
        }
    }

    @Synchronized
    override fun purgeExpired(nowMs: Long): Int {
        val current = listAll()
        val updated = current.filterNot { it.isExpired(nowMs) || it.ackedAt != null }
        val removed = current.size - updated.size
        if (removed > 0) {
            writeAll(updated)
        }
        return removed
    }

    @Synchronized
    override fun clear() {
        storageFile.delete()
    }

    private fun writeAll(records: List<StoredEnvelopeRecord>) {
        val array = JSONArray()
        records.sortedBy { it.envelope.timestamp }.forEach { record ->
            array.put(record.toJson())
        }
        storageFile.writeText(array.toString())
    }

    private fun StoredEnvelopeRecord.isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        return envelope.isExpired(nowMs)
    }

    private fun StoredEnvelopeRecord.toJson(): JSONObject {
        return JSONObject()
            .put("envelope", envelope.toJson())
            .put("storedAt", storedAt)
            .put("replicaPeers", JSONArray(replicaPeers.toList()))
            .put("deliveredAt", deliveredAt ?: JSONObject.NULL)
            .put("ackedAt", ackedAt ?: JSONObject.NULL)
    }

    private fun JSONArray.toStringSet(): Set<String> {
        return buildSet {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
    }

    private fun JSONObject.optLongOrNull(name: String): Long? {
        return if (has(name) && !isNull(name)) optLong(name, 0L) else null
    }
}

class InMemoryLocalMessageDatabase : LocalMessageDatabase {
    private val records = mutableMapOf<String, StoredEnvelopeRecord>()

    override fun upsert(record: StoredEnvelopeRecord): StoredEnvelopeRecord {
        val existing = records[record.envelope.messageId]
        val merged = if (existing == null) {
            record
        } else {
            existing.copy(
                envelope = record.envelope.copy(
                    deliveryStatus = when {
                        existing.ackedAt != null || record.ackedAt != null -> EnvelopeDeliveryStatus.Acked
                        existing.deliveredAt != null || record.deliveredAt != null -> EnvelopeDeliveryStatus.Delivered
                        else -> if (record.envelope.deliveryStatus.ordinal >= existing.envelope.deliveryStatus.ordinal) {
                            record.envelope.deliveryStatus
                        } else {
                            existing.envelope.deliveryStatus
                        }
                    }
                ),
                storedAt = minOf(existing.storedAt, record.storedAt),
                replicaPeers = existing.replicaPeers + record.replicaPeers,
                deliveredAt = existing.deliveredAt ?: record.deliveredAt,
                ackedAt = existing.ackedAt ?: record.ackedAt
            )
        }
        records[merged.envelope.messageId] = merged
        return merged
    }

    override fun get(messageId: String): StoredEnvelopeRecord? = records[messageId]

    override fun listAll(): List<StoredEnvelopeRecord> = records.values.sortedBy { it.envelope.timestamp }

    override fun listForRecipient(recipientPublicKeyHash: String, limit: Int): List<StoredEnvelopeRecord> {
        return listAll()
            .filter { it.envelope.recipientPublicKeyHash == recipientPublicKeyHash }
            .filterNot { it.isExpired() }
            .filter { it.ackedAt == null }
            .take(limit)
    }

    override fun listPendingReplication(replicaFactor: Int, nowMs: Long, limit: Int): List<StoredEnvelopeRecord> {
        return listAll()
            .filterNot { it.isExpired(nowMs) }
            .filter { it.ackedAt == null }
            .filter { it.replicaPeers.size < replicaFactor }
            .take(limit)
    }

    override fun markReplica(messageId: String, peer: String) {
        val existing = records[messageId] ?: return
        records[messageId] = existing.copy(replicaPeers = existing.replicaPeers + peer.trim())
    }

    override fun markDelivered(messageId: String, deliveredAt: Long) {
        val existing = records[messageId] ?: return
        records[messageId] = existing.copy(
            envelope = existing.envelope.copy(deliveryStatus = EnvelopeDeliveryStatus.Delivered),
            deliveredAt = deliveredAt
        )
    }

    override fun markAcked(messageId: String, ackedAt: Long) {
        val existing = records[messageId] ?: return
        records[messageId] = existing.copy(
            envelope = existing.envelope.copy(deliveryStatus = EnvelopeDeliveryStatus.Acked),
            ackedAt = ackedAt
        )
    }

    override fun remove(messageId: String) {
        records.remove(messageId)
    }

    override fun purgeExpired(nowMs: Long): Int {
        val expired = records.values.count { it.isExpired(nowMs) || it.ackedAt != null }
        records.entries.removeIf { it.value.isExpired(nowMs) || it.value.ackedAt != null }
        return expired
    }

    override fun clear() {
        records.clear()
    }

    private fun StoredEnvelopeRecord.isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        return envelope.isExpired(nowMs)
    }
}
