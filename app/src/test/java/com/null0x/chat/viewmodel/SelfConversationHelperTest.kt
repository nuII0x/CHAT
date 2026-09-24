package com.null0x.chat.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfConversationHelperTest {
    private val currentRoute = "current.onion"
    private val previousRoute = "previous.onion"
    private val helper = SelfConversationHelper(
        canonicalRoute = { it.trim().lowercase() },
        activeRouteProvider = { currentRoute },
        routeSources = { listOf(currentRoute, previousRoute) },
        displayNameProvider = { "" }
    )

    @Test
    fun `self aliases resolve to the active onion route`() {
        assertEquals(currentRoute, helper.canonicalConversationRoute(currentRoute))
        assertEquals(currentRoute, helper.canonicalConversationRoute(previousRoute))
        assertEquals(listOf(previousRoute), helper.legacyRoutes())
    }

    @Test
    fun `other onion routes remain unchanged`() {
        assertFalse(helper.isSelfConversation("contact.onion"))
        assertEquals("contact.onion", helper.canonicalConversationRoute("contact.onion"))
        assertTrue(helper.isSelfConversation(previousRoute))
    }
}
