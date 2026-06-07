package com.null0x.chat.security.identity

import kotlin.random.Random

class PeerDiscoveryManager(
    private val localRouteProvider: () -> String,
    private val knownPeersProvider: () -> List<String>,
    private val activePeersProvider: () -> List<String> = { emptyList() }
) {
    fun discoverPeers(): List<String> {
        val localRoute = localRouteProvider().trim()
        return (activePeersProvider() + knownPeersProvider())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { it == localRoute }
    }

    fun chooseReplicaTargets(exclude: Set<String> = emptySet(), desiredCount: Int = 5): List<String> {
        if (desiredCount <= 0) return emptyList()
        val candidates = discoverPeers().filterNot { it in exclude }.shuffled(Random(System.nanoTime()))
        return candidates.take(desiredCount)
    }
}
