package com.null0x.chat.viewmodel

internal class SelfConversationHelper(
    private val canonicalRoute: (String) -> String,
    private val activeRouteProvider: () -> String,
    private val routeSources: () -> List<String>,
    private val displayNameProvider: () -> String
) {
    fun isSelfConversation(route: String): Boolean {
        val clean = canonicalRoute(route)
        if (clean.isBlank()) return false
        return ownerRouteCandidates().any { it == clean }
    }

    fun displayName(): String {
        return displayNameProvider().ifBlank { "Você" }
    }

    fun priority(route: String): Int {
        return if (isSelfConversation(route)) 4 else 0
    }

    fun canonicalSelfRoute(): String {
        val activeRoute = canonicalRoute(activeRouteProvider())
        return activeRoute.ifBlank { ownerRouteCandidates().firstOrNull().orEmpty() }
    }

    fun canonicalConversationRoute(route: String): String {
        val clean = canonicalRoute(route)
        if (!isSelfConversation(clean)) return clean
        return canonicalSelfRoute().ifBlank { clean }
    }

    fun legacyRoutes(): List<String> {
        val current = canonicalSelfRoute()
        if (current.isBlank()) return emptyList()
        return ownerRouteCandidates().filterNot { it == current }
    }

    private fun ownerRouteCandidates(): List<String> {
        return routeSources()
            .map { canonicalRoute(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }
}
