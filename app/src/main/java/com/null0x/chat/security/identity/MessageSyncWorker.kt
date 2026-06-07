package com.null0x.chat.security.identity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MessageSyncWorker(
    private val scope: CoroutineScope,
    private val store: DistributedMessageStore,
    private val peerDiscoveryManager: PeerDiscoveryManager,
    private val sendMessage: suspend (peer: String, text: String) -> Result<Unit>,
    private val onLocalMessage: (peer: String, envelope: MessageEnvelope, text: String) -> Unit,
    private val localRecipientHash: () -> String
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                runCycle()
                delay(15_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun runCycle() {
        store.purgeExpired()
        replicatePending()
        pullPending()
        deliverLocalPending()
    }

    suspend fun replicatePending(replicaFactor: Int = 5) {
        val records = store.pendingReplication(replicaFactor = replicaFactor)
        if (records.isEmpty()) return
        val discoveredPeers = peerDiscoveryManager.chooseReplicaTargets(desiredCount = replicaFactor)
        records.forEach { record ->
            val envelopeText = P2PMessageRouter.encodeEnvelope(record.envelope)
            val exclude = record.replicaPeers
            discoveredPeers.filterNot { it in exclude }.take(replicaFactor).forEach { peer ->
                if (sendMessage(peer, envelopeText).isSuccess) {
                    store.markReplica(record.envelope.messageId, peer)
                }
            }
        }
    }

    suspend fun pullPending(limit: Int = 50) {
        val recipientHash = localRecipientHash().trim()
        if (recipientHash.isBlank()) return
        val peers = peerDiscoveryManager.chooseReplicaTargets(desiredCount = 5)
        if (peers.isEmpty()) return
        val request = P2PMessageRouter.encodePullRequest(
            PullRequest(
                recipientPublicKeyHash = recipientHash,
                sinceTimestamp = 0L,
                limit = limit
            )
        )
        peers.forEach { peer ->
            sendMessage(peer, request)
        }
    }

    private fun deliverLocalPending(limit: Int = 50) {
        val localHash = localRecipientHash().trim()
        if (localHash.isBlank()) return
        store.pendingLocalDeliveries(limit).forEach { record ->
            val plain = store.decryptForLocal(record) ?: return@forEach
            onLocalMessage(record.envelope.senderRoute ?: record.envelope.senderPublicKey.orEmpty(), record.envelope, plain)
        }
    }
}
