package com.null0x.chat.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.null0x.chat.model.DeliveryState
import com.null0x.chat.model.Message
import com.null0x.chat.network.ChatNodeManager
import com.null0x.chat.storage.ChatStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val ROUTE_TOKENS_PREFS = "route_tokens"
    }

    data class ConversationPreview(
        val username: String,
        val displayName: String,
        val emoji: String,
        val lastTimestamp: Long,
        val unreadCount: Int,
        val previewLine: String
    )

    data class SearchSummary(
        val conversations: List<ConversationPreview>,
        val conversationCount: Int,
        val resultCount: Int
    )

    data class RouteLookup(
        val name: String,
        val username: String,
        val displayName: String,
        val emoji: String,
        val isLocalOwner: Boolean,
        val source: String
    )

    data class PublicProfile(
        val displayName: String,
        val route: String,
        val source: String,
        val localName: String,
        val emoji: String,
        val bio: String
    )

    data class PrivacyNotice(
        val id: String,
        val route: String,
        val timestamp: Long,
        val text: String
    )

    data class ConversationPolicy(
        val keepViewedMessages: Boolean,
        val allowScreenshots: Boolean,
        val keepViewedMessagesOverridden: Boolean,
        val allowScreenshotsOverridden: Boolean,
        val updatedAt: Long
    )

    private val nodeManager = ChatNodeManager
    private val chatStore = ChatStore(application.applicationContext)
    private val profilePrefs = application.applicationContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val routesPrefs = application.applicationContext.getSharedPreferences("routes", Context.MODE_PRIVATE)
    private val routeNamesPrefs = application.applicationContext.getSharedPreferences("route_names", Context.MODE_PRIVATE)
    private val conversationPoliciesPrefs = application.applicationContext.getSharedPreferences("conversation_policies", Context.MODE_PRIVATE)
    private val routeTokensPrefs = application.applicationContext.getSharedPreferences(ROUTE_TOKENS_PREFS, Context.MODE_PRIVATE)
    private val privacyNoticesKey = "privacy_notices"

    private val listener = object : ChatNodeManager.Listener {
        override fun onUsernameReady(username: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                myUsername = username
            }
        }

        override fun onMessage(fromUsername: String, text: String, messageId: String?) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                val fromRoute = canonicalConversationKey(fromUsername)
                val currentTarget = canonicalConversationKey(targetUsername)

                if (!startedConversations.contains(fromRoute)) {
                    startedConversations.add(0, fromRoute)
                }
                if (!inChat || fromRoute != currentTarget) {
                    unreadByPeer[fromRoute] = (unreadByPeer[fromRoute] ?: 0) + 1
                }
                if (fromRoute != currentTarget) {
                    conversationsVersion++
                    refreshConversationPreviewsAsync()
                    return@launch
                }

                if (messageId != null) {
                    updateMessageDelivery(messageId, DeliveryState.Delivered)
                }
                messages.add(
                    Message(
                        id = messageId ?: java.util.UUID.randomUUID().toString(),
                        text = text,
                        isMine = false,
                        delivery = DeliveryState.Delivered
                    )
                )
                refreshConversationPreviewsAsync()
            }
        }

        override fun onDeliveryAck(fromUsername: String, messageId: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                if (canonicalConversationKey(fromUsername) == targetUsername) {
                    updateMessageDelivery(messageId, DeliveryState.Delivered)
                }
                conversationsVersion++
            }
        }

        override fun onOutgoingDeliveryStateChanged(toUsername: String, messageId: String, state: DeliveryState) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                if (canonicalConversationKey(toUsername) == targetUsername) {
                    updateMessageDelivery(messageId, state)
                }
                conversationsVersion++
                refreshConversationPreviewsAsync()
            }
        }

        override fun onChatPresence(fromUsername: String, state: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                val route = canonicalConversationKey(fromUsername)
                if (route.isBlank()) return@launch
                when (state) {
                    "open" -> {
                        partnerChatOpenByPeer[route] = true
                        schedulePartnerPresenceExpiry(route)
                    }
                    "closed" -> {
                        partnerPresenceExpiryJobs.remove(route)?.cancel()
                        partnerChatOpenByPeer[route] = false
                        partnerTypingByPeer[route] = false
                    }
                    "typing" -> {
                        partnerChatOpenByPeer[route] = true
                        partnerTypingByPeer[route] = true
                        schedulePartnerPresenceExpiry(route)
                    }
                    "idle" -> {
                        partnerTypingByPeer[route] = false
                    }
                }
            }
        }

        override fun onPeersChanged(peers: List<String>) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                this@ChatViewModel.peers.clear()
                this@ChatViewModel.peers.addAll(peers)
                refreshRouteLookup()
                refreshConversationPreviewsAsync()
            }
        }

        override fun onProfileNameChanged(name: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                profileName = name
                conversationsVersion++
                routeNamesVersion++
                refreshRouteLookup()
                refreshConversationPreviewsAsync()
            }
        }

        override fun onProfileEmojiChanged(emoji: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                profileEmoji = emoji
                profileEmojiSymbol = emoji
                conversationsVersion++
                routeNamesVersion++
                refreshRouteLookup()
                refreshConversationPreviewsAsync()
            }
        }

        override fun onPublicProfileChanged(route: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                conversationsVersion++
                routeNamesVersion++
                if (canonicalConversationKey(route) == targetUsername) {
                    applyConversationPolicy(targetUsername)
                }
                refreshRouteLookup()
                refreshConversationPreviewsAsync()
            }
        }

        override fun onRouteChanged() {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                refreshRouteLookup()
                refreshConversationPreviewsAsync()
            }
        }
    }

    val messages = mutableStateListOf<Message>()
    val peers = mutableStateListOf<String>()
    val startedConversations = mutableStateListOf<String>()
    private val privacyNotices = mutableStateListOf<PrivacyNotice>()
    private val unreadByPeer = mutableStateMapOf<String, Int>()
    private val seenButNotClearedByPeer = mutableStateMapOf<String, Boolean>()
    private val unreadEntryCountByPeer = mutableStateMapOf<String, Int>()
    private val partnerChatOpenByPeer = mutableStateMapOf<String, Boolean>()
    private val partnerTypingByPeer = mutableStateMapOf<String, Boolean>()
    private val localTypingByPeer = mutableStateMapOf<String, Boolean>()
    private val partnerPresenceExpiryJobs = mutableMapOf<String, Job>()
    private var conversationsVersion by mutableStateOf(0)
    private var defaultKeepViewedMessages by mutableStateOf(true)
    private var defaultAllowScreenshots by mutableStateOf(false)
    private var currentKeepViewedMessages by mutableStateOf(true)
    private var currentAllowScreenshots by mutableStateOf(false)
    private var currentPolicyRoute by mutableStateOf("")
    var currentChatSettingsKeepViewedMessages by mutableStateOf(true)
        private set
    var currentChatSettingsAllowScreenshots by mutableStateOf(false)
        private set
    private var profileEmoji by mutableStateOf("🙂")
    private var profileBio by mutableStateOf("")
    private var routeNamesVersion by mutableStateOf(0)
    private var openChatJob: Job? = null
    private var cleanupChatJob: Job? = null
    private var previewsJob: Job? = null
    private var conversationPreviewCache by mutableStateOf<List<ConversationPreview>>(emptyList())
    private var currentChatLoaded by mutableStateOf(false)
    private var localListsPrepared by mutableStateOf(false)
    private var chatOpenedAtMs by mutableStateOf(0L)

    var myUsername by mutableStateOf("iniciando...")
        private set

    var targetUsername by mutableStateOf("")
        private set
    var inChat by mutableStateOf(false)
        private set

    var profileName by mutableStateOf("RotaSegura")
        private set
    var profileEmojiSymbol by mutableStateOf("🙂")
        private set
    var profileBioText by mutableStateOf("")
        private set

    var needsProfileSetup by mutableStateOf(false)
        private set
    var contactRouteInput by mutableStateOf("")
        private set
    var routeLookup by mutableStateOf<RouteLookup?>(null)
        private set
    var routeStatus by mutableStateOf("")
        private set

    init {
        needsProfileSetup = profilePrefs.getString("display_name", null).isNullOrBlank()
        contactRouteInput = ""
        routesPrefs.edit().remove("contact_route_input").apply()
        defaultKeepViewedMessages = profilePrefs.getBoolean("keep_viewed_messages", true)
        defaultAllowScreenshots = profilePrefs.getBoolean("chat_screenshots_enabled", false)
        currentKeepViewedMessages = defaultKeepViewedMessages
        currentAllowScreenshots = defaultAllowScreenshots
        profileEmoji = profilePrefs.getString("profile_emoji", "🙂")?.takeIf { it.isNotBlank() } ?: "🙂"
        profileEmojiSymbol = profileEmoji
        profileBio = profilePrefs.getString("profile_bio", "")?.orEmpty() ?: ""
        profileBioText = profileBio
        prepareLocalListsSynchronously()
        nodeManager.start(application.applicationContext, autoStartTor = false)
        nodeManager.setProfileEmoji(application.applicationContext, profileEmoji)
        nodeManager.setProfileBio(application.applicationContext, profileBio)
        nodeManager.setProfilePolicy(defaultKeepViewedMessages, defaultAllowScreenshots)
        privacyNotices.addAll(loadPrivacyNotices())
        nodeManager.addListener(listener)
    }

    override fun onCleared() {
        val current = canonicalConversationKey(targetUsername)
        if (current.isNotBlank()) {
            sendChatPresence(current, "idle")
            sendChatPresence(current, "closed")
        }
        nodeManager.removeListener(listener)
        partnerPresenceExpiryJobs.values.forEach { it.cancel() }
        partnerPresenceExpiryJobs.clear()
        super.onCleared()
    }

    fun selectTarget(username: String) {
        val cleanTarget = canonicalConversationKey(username)
        val previousTarget = canonicalConversationKey(targetUsername)
        openChatJob?.cancel()
        if (previousTarget.isNotBlank() && previousTarget != cleanTarget) {
            sendChatPresence(previousTarget, "idle")
            sendChatPresence(previousTarget, "closed")
            localTypingByPeer[previousTarget] = false
        }
        targetUsername = cleanTarget
        messages.clear()
        currentChatLoaded = false
        cleanupChatJob?.cancel()
        cleanupChatJob = null
        chatOpenedAtMs = System.currentTimeMillis()
        if (cleanTarget.isBlank()) return

        ensureConversationPolicy(cleanTarget)
        applyConversationPolicy(cleanTarget)
        if (!startedConversations.contains(cleanTarget)) {
            startedConversations.add(0, cleanTarget)
        }
        nodeManager.requestPublicProfile(cleanTarget)
        val unreadBeforeOpen = unreadByPeer[cleanTarget] ?: 0
        unreadEntryCountByPeer[cleanTarget] = unreadBeforeOpen
        unreadByPeer[cleanTarget] = 0
        seenButNotClearedByPeer[cleanTarget] = true
        inChat = true
        sendChatPresence(cleanTarget, "open")
        conversationsVersion++
        refreshConversationPreviewsAsync()

        openChatJob = viewModelScope.launch(Dispatchers.IO) {
            val loadedMessages = nodeManager.loadMessages(cleanTarget)
            withContext(Dispatchers.Main.immediate) {
                if (targetUsername != cleanTarget || !inChat) return@withContext
                messages.clear()
                messages.addAll(loadedMessages)
                currentChatLoaded = true
                refreshConversationPreviewsAsync()
            }
        }
    }

    fun openHome() {
        val previous = targetUsername
        openChatJob?.cancel()
        openChatJob = null
        if (previous.isNotBlank()) {
            sendChatPresence(previous, "idle")
            sendChatPresence(previous, "closed")
            localTypingByPeer[previous] = false
        }
        inChat = false
        targetUsername = ""
        messages.clear()
        currentChatLoaded = false
        val hasUnreadOnEntry = (unreadEntryCountByPeer[previous] ?: 0) > 0
        if (
            previous.isNotBlank() &&
            !currentKeepViewedMessages &&
            !hasUnreadOnEntry &&
            seenButNotClearedByPeer[previous] == true
        ) {
            scheduleViewedConversationCleanup(previous)
        }
    }

    fun updateKeepViewedMessagesPreference(enabled: Boolean) {
        if (defaultKeepViewedMessages == enabled) return
        defaultKeepViewedMessages = enabled
        profilePrefs.edit().putBoolean("keep_viewed_messages", enabled).apply()
        nodeManager.setProfilePolicy(defaultKeepViewedMessages, defaultAllowScreenshots)
        if (canonicalConversationKey(targetUsername).isNotBlank()) {
            applyConversationPolicy(targetUsername)
        }
        refreshConversationPreviewsAsync()
    }

    fun isKeepViewedMessagesEnabled(): Boolean = defaultKeepViewedMessages

    fun isScreenshotsEnabled(): Boolean = defaultAllowScreenshots

    fun updateScreenshotsPreference(enabled: Boolean) {
        if (defaultAllowScreenshots == enabled) return
        defaultAllowScreenshots = enabled
        profilePrefs.edit().putBoolean("chat_screenshots_enabled", enabled).apply()
        nodeManager.setProfilePolicy(defaultKeepViewedMessages, defaultAllowScreenshots)
        if (canonicalConversationKey(targetUsername).isNotBlank()) {
            applyConversationPolicy(targetUsername)
        }
    }

    fun privacyNotices(): List<PrivacyNotice> {
        val current = canonicalConversationKey(targetUsername)
        if (current.isBlank()) return emptyList()
        return privacyNotices.filter { it.route == routeStorageKey(current) }
    }

    /**
     * Atualiza o nome de perfil em tempo de execução.
     * Não altera diretamente o state — isso vem via callback do nodeManager.
     */
    fun updateProfileName(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return

        nodeManager.setProfileName(getApplication(), cleanName)
        needsProfileSetup = false
    }

    fun updateProfileEmoji(emoji: String) {
        val cleanEmoji = emoji.trim().ifBlank { "🙂" }
        profileEmoji = cleanEmoji
        profileEmojiSymbol = cleanEmoji
        profilePrefs.edit().putString("profile_emoji", cleanEmoji).apply()
        nodeManager.setProfileEmoji(getApplication(), cleanEmoji)
        conversationsVersion++
        routeNamesVersion++
        refreshRouteLookup()
        refreshConversationPreviewsAsync()
    }

    fun updateProfileBio(bio: String) {
        val cleanBio = limitUtf8Bytes(bio, 4 * 1024)
        profileBio = cleanBio
        profileBioText = cleanBio
        profilePrefs.edit().putString("profile_bio", cleanBio).apply()
        nodeManager.setProfileBio(getApplication(), cleanBio)
        conversationsVersion++
        routeNamesVersion++
        refreshRouteLookup()
        refreshConversationPreviewsAsync()
    }

    fun updateContactRouteInput(route: String) {
        val clean = route.trim()
        contactRouteInput = clean
        refreshRouteLookup()
    }

    fun addContactRouteFromInput() {
        refreshRouteLookup(showNotFound = true)
    }

    fun addContact(username: String) {
        val clean = canonicalConversationKey(username)
        if (clean.isBlank()) return
        chatStore.rememberPeer(clean)
        if (!startedConversations.contains(clean)) {
            startedConversations.add(0, clean)
        }
        conversationsVersion++
        refreshConversationPreviewsAsync()
    }

    fun displayNameFor(username: String): String {
        val route = canonicalConversationKey(username)
        routeTokenFor(route)
        return localNameForRoute(route).ifBlank { route }
    }

    fun chatTitleFor(username: String): String {
        val route = canonicalConversationKey(username)
        routeTokenFor(route)
        routeNamesVersion
        return localNameForRoute(route)
            .ifBlank { publicDisplayNameForRoute(route) }
            .ifBlank { route }
    }

    fun emojiForRoute(username: String): String {
        val route = canonicalConversationKey(username)
        routeTokenFor(route)
        return publicEmojiForRoute(route)
    }

    fun isPartnerChatOpen(username: String): Boolean {
        return partnerChatOpenByPeer[canonicalConversationKey(username)] == true
    }

    fun isPartnerTyping(username: String): Boolean {
        val route = canonicalConversationKey(username)
        return partnerChatOpenByPeer[route] == true && partnerTypingByPeer[route] == true
    }

    fun updateLocalTyping(username: String, typing: Boolean) {
        val route = canonicalConversationKey(username)
        if (route.isBlank() || !inChat || route != canonicalConversationKey(targetUsername)) return
        if (localTypingByPeer[route] == typing) return
        localTypingByPeer[route] = typing
        sendChatPresence(route, if (typing) "typing" else "idle")
    }

    fun refreshLocalChatPresence(username: String) {
        val route = canonicalConversationKey(username)
        if (route.isBlank() || !inChat || route != canonicalConversationKey(targetUsername)) return
        sendChatPresence(route, "open")
    }

    fun publicProfileFor(username: String): PublicProfile {
        val route = canonicalConversationKey(username)
        routeTokenFor(route)
        routeNamesVersion
        val localName = localNameForRoute(route)
        val fallbackTitle = route
        val publicName = publicDisplayNameForRoute(route)
        val display = localName.ifBlank { publicName }.ifBlank { fallbackTitle }
        return PublicProfile(
            displayName = display,
            route = route,
            source = "Rede",
            localName = localName,
            emoji = publicEmojiForRoute(route),
            bio = publicBioForRoute(route)
        )
    }

    fun setLocalNameForRoute(route: String, name: String) {
        val cleanRoute = canonicalConversationKey(route)
        val cleanName = name.trim()
        if (cleanRoute.isBlank()) return
        val storageKey = routeStorageKey(cleanRoute)
        val editor = routeNamesPrefs.edit()
        if (cleanName.isBlank()) {
            editor.remove(storageKey).remove(cleanRoute)
        } else {
            editor.putString(storageKey, cleanName).remove(cleanRoute)
        }
        editor.apply()
        routeNamesVersion++
        conversationsVersion++
        refreshConversationPreviewsAsync()
    }

    fun currentPublicRoute(): String {
        return nodeManager.currentPublicRoute()
    }

    fun requestPublicProfile(route: String) {
        val clean = canonicalConversationKey(route)
        if (clean.isNotBlank()) {
            nodeManager.requestPublicProfile(clean)
        }
    }

    fun routeTokenFor(username: String): String {
        val route = canonicalConversationKey(username)
        if (route.isBlank()) return ""
        val token = routeTokenString(route)
        routeTokensPrefs.edit().putString(token.lowercase(), route).apply()
        return token
    }

    fun isLocalRoute(username: String): Boolean {
        val route = canonicalConversationKey(username)
        if (route.isBlank()) return false
        return route == canonicalConversationKey(myUsername) ||
            route == canonicalConversationKey(nodeManager.currentPublicRoute())
    }

    fun isCurrentChatKeepViewedMessagesEnabled(): Boolean = currentKeepViewedMessages

    fun isCurrentChatScreenshotsEnabled(): Boolean = currentAllowScreenshots

    fun isCurrentChatLocalKeepViewedMessagesEnabled(): Boolean {
        return currentChatSettingsKeepViewedMessages
    }

    fun isCurrentChatLocalScreenshotsEnabled(): Boolean {
        return currentChatSettingsAllowScreenshots
    }

    fun updateCurrentChatKeepViewedMessagesPreference(enabled: Boolean) {
        val current = canonicalConversationKey(targetUsername)
        if (current.isBlank()) return
        val basePolicy = baseConversationPolicyFor(current)
        val local = localConversationPolicyFor(current)
        val shouldOverride = enabled != basePolicy.keepViewedMessages
        saveLocalConversationPolicy(
            current,
            local.copy(
                keepViewedMessages = enabled,
                keepViewedMessagesOverridden = shouldOverride,
                updatedAt = System.currentTimeMillis()
            )
        )
        addPrivacyNotice(
            current,
            if (enabled) {
                "A partir daqui, este chat mantem o historico ao sair."
            } else {
                "A partir daqui, este chat apaga o historico ao sair."
            }
        )
        applyConversationPolicy(current)
        currentChatSettingsKeepViewedMessages = effectiveConversationPolicyFor(current).keepViewedMessages
        refreshConversationPreviewsAsync()
    }

    fun updateCurrentChatScreenshotsPreference(enabled: Boolean) {
        val current = canonicalConversationKey(targetUsername)
        if (current.isBlank()) return
        val basePolicy = baseConversationPolicyFor(current)
        val local = localConversationPolicyFor(current)
        val shouldOverride = enabled != basePolicy.allowScreenshots
        saveLocalConversationPolicy(
            current,
            local.copy(
                allowScreenshots = enabled,
                allowScreenshotsOverridden = shouldOverride,
                updatedAt = System.currentTimeMillis()
            )
        )
        addPrivacyNotice(
            current,
            if (enabled) {
                "A partir daqui, prints estao liberados neste chat."
            } else {
                "A partir daqui, prints estao bloqueados neste chat."
            }
        )
        applyConversationPolicy(current)
        currentChatSettingsAllowScreenshots = effectiveConversationPolicyFor(current).allowScreenshots
    }

    fun startTor() {
        viewModelScope.launch {
            prepareLocalListsForTor()
            nodeManager.startTor(getApplication())
        }
    }

    private fun prepareLocalListsSynchronously() {
        if (localListsPrepared) return
        val loadedConversations = chatStore.knownPeers()
            .map { canonicalConversationKey(it) }
            .filter { it.isNotBlank() }
            .distinct()
        startedConversations.clear()
        startedConversations.addAll(loadedConversations)
        conversationPreviewCache = buildConversationPreviews(loadedConversations)
        localListsPrepared = true
        conversationsVersion++
    }

    private suspend fun prepareLocalListsForTor() {
        if (localListsPrepared) return
        val loadedConversations = withContext(Dispatchers.IO) {
            chatStore.knownPeers().map { canonicalConversationKey(it) }.distinct()
        }
        withContext(Dispatchers.Main.immediate) {
            if (localListsPrepared) return@withContext
            val mergedConversations = (startedConversations.toList() + loadedConversations)
                .map { canonicalConversationKey(it) }
                .filter { it.isNotBlank() }
                .distinct()
            startedConversations.clear()
            startedConversations.addAll(mergedConversations)
            conversationPreviewCache = buildConversationPreviews(mergedConversations)
            localListsPrepared = true
        }
    }

    private fun refreshRouteLookup(showNotFound: Boolean = false) {
        val clean = resolveContactRouteQuery(contactRouteInput)
        if (clean.isBlank()) {
            routeLookup = null
            routeStatus = ""
            return
        }
        if (!nodeManager.isValidRoute(clean)) {
            routeLookup = null
            if (showNotFound) routeStatus = "Rota inválida"
            return
        }

        val currentRoute = canonicalConversationKey(nodeManager.currentPublicRoute())
        val fallbackLabel = clean
        val displayLabel = localNameForRoute(clean).ifBlank { fallbackLabel }
        routeLookup = RouteLookup(
            name = fallbackLabel,
            username = clean,
            displayName = localNameForRoute(clean)
                .ifBlank { publicDisplayNameForRoute(clean) }
                .ifBlank { displayLabel },
            emoji = publicEmojiForRoute(clean),
            isLocalOwner = clean == currentRoute || clean == canonicalConversationKey(myUsername),
            source = "Rede"
        )
        routeTokensPrefs.edit().putString(routeTokenString(clean).lowercase(), clean).apply()
        if (showNotFound) {
            nodeManager.requestPublicProfile(clean)
        }
        routeStatus = if (clean == currentRoute || clean == canonicalConversationKey(myUsername)) {
            "Esta rota aponta para este aparelho"
        } else {
            "Rota pronta para conversar"
        }
    }

    fun conversationPreviews(): List<ConversationPreview> {
        conversationsVersion
        routeNamesVersion
        return conversationPreviewCache
    }

    fun isCurrentChatLoaded(): Boolean {
        return currentChatLoaded
    }

    fun clearConversation(username: String) {
        val target = canonicalConversationKey(username)
        if (target.isBlank()) return
        if (hasProtectedUnreadMessages(target)) return

        cleanupChatJob?.cancel()
        nodeManager.clearMessages(target)
        clearPrivacyNotices(target)
        unreadByPeer[target] = 0
        unreadEntryCountByPeer.remove(target)
        seenButNotClearedByPeer.remove(target)
        conversationsVersion++
        refreshConversationPreviewsAsync()
        if (target == targetUsername) {
            messages.clear()
        }
    }

    fun removeConversation(username: String) {
        val target = canonicalConversationKey(username)
        if (target.isBlank()) return
        if (hasProtectedUnreadMessages(target)) return

        cleanupChatJob?.cancel()
        nodeManager.removeConversation(target)
        startedConversations.remove(target)
        unreadByPeer.remove(target)
        unreadEntryCountByPeer.remove(target)
        seenButNotClearedByPeer.remove(target)
        clearPrivacyNotices(target)
        conversationsVersion++
        refreshConversationPreviewsAsync()
        if (target == targetUsername) {
            openHome()
        }
    }

    fun send(text: String) {
        sendTo(targetUsername, text)
    }

    fun sendTo(username: String, text: String) {
        val message = text.trim()
        val target = canonicalConversationKey(username)

        if (message.isBlank()) return

        if (message.startsWith("/")) {
            handleCommand(message, target)
            return
        }

        if (target.isBlank()) return

        val localId = java.util.UUID.randomUUID().toString()
        messages.add(
            Message(
                id = localId,
                text = message,
                isMine = true,
                delivery = DeliveryState.Pending
            )
        )
        conversationsVersion++
        refreshConversationPreviewsAsync()

        viewModelScope.launch {
            applyConversationPolicy(target)
            updateLocalTyping(target, false)
            nodeManager.sendMessage(target, message, localId)
        }
    }

    private fun sendChatPresence(route: String, state: String) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return
        nodeManager.sendChatPresence(clean, state)
    }

    private fun schedulePartnerPresenceExpiry(route: String) {
        partnerPresenceExpiryJobs.remove(route)?.cancel()
        partnerPresenceExpiryJobs[route] = viewModelScope.launch(Dispatchers.Main.immediate) {
            kotlinx.coroutines.delay(45_000)
            partnerChatOpenByPeer[route] = false
            partnerTypingByPeer[route] = false
            partnerPresenceExpiryJobs.remove(route)
        }
    }

    fun unreadEntryCountFor(username: String): Int {
        return unreadEntryCountByPeer[canonicalConversationKey(username)] ?: 0
    }

    fun clearUnreadEntryHint(username: String) {
        unreadEntryCountByPeer.remove(canonicalConversationKey(username))
    }

    fun messagesFor(username: String): List<Message> {
        val target = canonicalConversationKey(username)
        if (target.isBlank()) return emptyList()
        return if (target == targetUsername) {
            messages.toList()
        } else {
            nodeManager.loadMessages(target)
        }
    }

    suspend fun searchExactMessages(query: String): SearchSummary {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return SearchSummary(emptyList(), 0, 0)
        }

        val matches = withContext(Dispatchers.IO) {
            chatStore.knownPeers()
                .map { canonicalConversationKey(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .mapNotNull { peer ->
                    val items = chatStore.load(peer)
                    val hitCount = items.count { message -> message.text == normalizedQuery }
                    if (hitCount <= 0) return@mapNotNull null
                    val preview = ConversationPreview(
                        username = peer,
                        displayName = chatTitleFor(peer),
                        emoji = publicEmojiForRoute(peer),
                        lastTimestamp = items.lastOrNull()?.timestamp ?: 0L,
                        unreadCount = unreadByPeer[peer] ?: 0,
                        previewLine = if (hitCount == 1) "1 resultado" else "$hitCount resultados"
                    )
                    preview to hitCount
                }
                .sortedWith(compareByDescending<Pair<ConversationPreview, Int>> { it.first.lastTimestamp }
                    .thenByDescending { it.second })
        }

        val conversations = matches.map { it.first }
        val resultCount = matches.sumOf { it.second }
        return SearchSummary(
            conversations = conversations,
            conversationCount = conversations.size,
            resultCount = resultCount
        )
    }

    private fun updateMessageDelivery(messageId: String, state: DeliveryState) {
        val index = messages.indexOfFirst { it.id == messageId && it.isMine }
        if (index < 0) return
        val current = messages[index]
        if (current.delivery == state) return
        messages[index] = current.copy(delivery = state)
    }

    private fun refreshConversationPreviewsAsync() {
        previewsJob?.cancel()
        previewsJob = viewModelScope.launch(Dispatchers.IO) {
            val snapshot = buildConversationPreviews(startedConversations.toList())

            withContext(Dispatchers.Main.immediate) {
                conversationPreviewCache = snapshot
            }
        }
    }

    private fun buildConversationPreviews(usernames: List<String>): List<ConversationPreview> {
        return usernames.map { username ->
            val target = canonicalConversationKey(username)
            val items = chatStore.load(target)
            val last = items.lastOrNull()
            ConversationPreview(
                username = target,
                displayName = chatTitleFor(target),
                emoji = publicEmojiForRoute(target),
                lastTimestamp = last?.timestamp ?: 0L,
                unreadCount = unreadByPeer[target] ?: 0,
                previewLine = previewLineFor(items, unreadByPeer[target] ?: 0)
            )
        }.sortedByDescending { it.lastTimestamp }
    }

    private fun previewLineFor(messages: List<Message>, unreadCount: Int): String {
        if (unreadCount > 0) {
            return if (unreadCount == 1) "1 nova" else "$unreadCount nova"
        }

        val last = messages.lastOrNull() ?: return "limpo"
        if (!last.isMine) return "Visto"

        return when (last.delivery) {
            DeliveryState.Pending -> "Em espera"
            DeliveryState.Failed -> "Erro"
            DeliveryState.Sent -> "Enviado ${messages.takeLastWhile { it.isMine }.size.coerceAtLeast(1)}"
            DeliveryState.Delivered -> "Visto"
        }
    }

    private fun addPrivacyNotice(route: String, text: String) {
        val cleanRoute = canonicalConversationKey(route)
        if (cleanRoute.isBlank()) return
        privacyNotices.add(
            PrivacyNotice(
                id = java.util.UUID.randomUUID().toString(),
                route = routeStorageKey(cleanRoute),
                timestamp = System.currentTimeMillis(),
                text = text
            )
        )
        val trimmed = privacyNotices.sortedByDescending { it.timestamp }.take(40).sortedBy { it.timestamp }
        privacyNotices.clear()
        privacyNotices.addAll(trimmed)
        savePrivacyNotices()
    }

    private fun loadPrivacyNotices(): List<PrivacyNotice> {
        val raw = profilePrefs.getString(privacyNoticesKey, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val rawRoute = item.optString("route").trim()
                    val route = if (rawRoute.startsWith("route:")) {
                        rawRoute
                    } else {
                        val cleanRoute = canonicalConversationKey(rawRoute)
                        if (cleanRoute.isBlank()) "" else routeStorageKey(cleanRoute)
                    }
                    val text = item.optString("text").trim()
                    val timestamp = item.optLong("timestamp", 0L)
                    if (id.isNotBlank() && route.isNotBlank() && text.isNotBlank() && timestamp > 0L) {
                        add(PrivacyNotice(id = id, route = route, timestamp = timestamp, text = text))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun savePrivacyNotices() {
        val array = JSONArray()
        privacyNotices.forEach { notice ->
            array.put(
                JSONObject()
                    .put("id", notice.id)
                    .put("route", notice.route)
                    .put("timestamp", notice.timestamp)
                    .put("text", notice.text)
            )
        }
        profilePrefs.edit().putString(privacyNoticesKey, array.toString()).apply()
    }

    private fun clearPrivacyNotices(route: String) {
        val cleanRoute = canonicalConversationKey(route)
        if (cleanRoute.isBlank() || privacyNotices.isEmpty()) return
        privacyNotices.removeAll { it.route == routeStorageKey(cleanRoute) }
        savePrivacyNotices()
    }

    private fun localNameForRoute(route: String): String {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return ""
        val storageKey = routeStorageKey(clean)
        val saved = routeNamesPrefs.getString(storageKey, null)
        if (saved != null) return saved.trim()
        val legacy = routeNamesPrefs.getString(clean, null)?.trim().orEmpty()
        if (legacy.isNotBlank()) {
            routeNamesPrefs.edit().putString(storageKey, legacy).remove(clean).apply()
        }
        return legacy
    }

    private fun publicDisplayNameForRoute(route: String): String {
        val clean = canonicalConversationKey(route)
        return nodeManager.publicProfileFor(clean)?.displayName.orEmpty()
    }

    private fun publicEmojiForRoute(route: String): String {
        val clean = canonicalConversationKey(route)
        if (isLocalRoute(clean)) return profileEmojiSymbol
        return nodeManager.publicProfileFor(clean)?.emoji.orEmpty()
    }

    private fun publicBioForRoute(route: String): String {
        val clean = canonicalConversationKey(route)
        if (isLocalRoute(clean)) return profileBioText
        return nodeManager.publicProfileFor(clean)?.bio.orEmpty()
    }

    private fun localConversationPolicyFor(route: String): ConversationPolicy {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return defaultConversationPolicy()
        val storageKey = routeStorageKey(clean)
        val raw = conversationPoliciesPrefs.getString(storageKey, null)
            ?: conversationPoliciesPrefs.getString(clean, null)?.also { legacy ->
                conversationPoliciesPrefs.edit().putString(storageKey, legacy).remove(clean).apply()
            }
        if (!raw.isNullOrBlank()) {
            return runCatching {
                val json = JSONObject(raw)
                ConversationPolicy(
                    keepViewedMessages = json.optBoolean("keepViewedMessages", defaultKeepViewedMessages),
                    allowScreenshots = json.optBoolean("allowScreenshots", defaultAllowScreenshots),
                    keepViewedMessagesOverridden = json.optBoolean("keepViewedMessagesOverridden", false),
                    allowScreenshotsOverridden = json.optBoolean("allowScreenshotsOverridden", false),
                    updatedAt = json.optLong("updatedAt", 0L)
                )
            }.getOrNull() ?: defaultConversationPolicy()
        }
        return defaultConversationPolicy()
    }

    private fun baseConversationPolicyFor(route: String): ConversationPolicy {
        val cleanRoute = canonicalConversationKey(route)
        val remote = nodeManager.publicProfileFor(cleanRoute)
        val keepViewedMessages = defaultKeepViewedMessages || (remote?.keepViewedMessages ?: false)
        val allowScreenshots = defaultAllowScreenshots || (remote?.allowScreenshots ?: false)
        return ConversationPolicy(
            keepViewedMessages = keepViewedMessages,
            allowScreenshots = allowScreenshots,
            keepViewedMessagesOverridden = false,
            allowScreenshotsOverridden = false,
            updatedAt = remote?.updatedAt ?: 0L
        )
    }

    private fun effectiveConversationPolicyFor(route: String): ConversationPolicy {
        val basePolicy = baseConversationPolicyFor(route)
        val localPolicy = localConversationPolicyFor(route)
        return ConversationPolicy(
            keepViewedMessages = if (localPolicy.keepViewedMessagesOverridden) localPolicy.keepViewedMessages else basePolicy.keepViewedMessages,
            allowScreenshots = if (localPolicy.allowScreenshotsOverridden) localPolicy.allowScreenshots else basePolicy.allowScreenshots,
            keepViewedMessagesOverridden = localPolicy.keepViewedMessagesOverridden,
            allowScreenshotsOverridden = localPolicy.allowScreenshotsOverridden,
            updatedAt = maxOf(basePolicy.updatedAt, localPolicy.updatedAt)
        )
    }

    private fun defaultConversationPolicy(): ConversationPolicy {
        return ConversationPolicy(
            keepViewedMessages = defaultKeepViewedMessages,
            allowScreenshots = defaultAllowScreenshots,
            keepViewedMessagesOverridden = false,
            allowScreenshotsOverridden = false,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun saveLocalConversationPolicy(route: String, policy: ConversationPolicy) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return
        val storageKey = routeStorageKey(clean)
        if (!policy.keepViewedMessagesOverridden && !policy.allowScreenshotsOverridden) {
            conversationPoliciesPrefs.edit().remove(storageKey).remove(clean).apply()
            return
        }
        val json = JSONObject()
            .put("keepViewedMessages", policy.keepViewedMessages)
            .put("allowScreenshots", policy.allowScreenshots)
            .put("keepViewedMessagesOverridden", policy.keepViewedMessagesOverridden)
            .put("allowScreenshotsOverridden", policy.allowScreenshotsOverridden)
            .put("updatedAt", policy.updatedAt)
        conversationPoliciesPrefs.edit().putString(storageKey, json.toString()).remove(clean).apply()
    }

    private fun ensureConversationPolicy(route: String) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return
        localConversationPolicyFor(clean)
    }

    private fun routeStorageKey(route: String): String {
        val clean = canonicalConversationKey(route)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(clean.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return "route:$digest"
    }

    private fun routeTokenString(route: String): String {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return ""
        val value = clean.substringAfter("onion:", missingDelimiterValue = clean)
        val separator = value.lastIndexOf(':')
        if (separator <= 0 || separator == value.lastIndex) return clean
        val host = value.substring(0, separator)
            .removeSuffix(".onion")
            .lowercase()
        val port = value.substring(separator + 1)
            .takeIf { candidate -> candidate.all { it.isDigit() } }
            ?: "5000"
        return "$host#$port"
    }

    private fun resolveContactRouteQuery(query: String): String {
        val clean = canonicalConversationKey(query)
        if (clean.isBlank()) return ""
        if (nodeManager.isValidRoute(clean)) return clean

        compactRouteTokenToOnion(clean)?.let { compactRoute ->
            return compactRoute
        }

        routeTokensPrefs.getString(clean.lowercase(), null)?.let { stored ->
            val storedRoute = canonicalConversationKey(stored)
            if (storedRoute.isNotBlank()) return storedRoute
        }

        val candidates = buildList {
            add(canonicalConversationKey(myUsername))
            add(canonicalConversationKey(nodeManager.currentPublicRoute()))
            addAll(peers.map { canonicalConversationKey(it) })
            addAll(startedConversations.map { canonicalConversationKey(it) })
            addAll(chatStore.knownPeers().map { canonicalConversationKey(it) })
        }.filter { it.isNotBlank() }.distinct()

        val match = candidates.firstOrNull { routeTokenString(it).equals(clean, ignoreCase = true) }
        if (match != null) {
            routeTokensPrefs.edit().putString(routeTokenString(match).lowercase(), match).apply()
        }
        return match.orEmpty()
    }

    private fun compactRouteTokenToOnion(token: String): String? {
        val clean = token.trim()
        val separator = clean.lastIndexOf('#')
        if (separator <= 0 || separator == clean.lastIndex) return null
        val host = clean.substring(0, separator)
            .trim()
            .removePrefix("onion:")
            .removeSuffix(".onion")
            .lowercase()
        val port = clean.substring(separator + 1).trim()
        if (host.isBlank() || port.toIntOrNull() == null) return null
        val route = "onion:$host.onion:$port"
        return canonicalConversationKey(route).takeIf { nodeManager.isValidRoute(it) }
    }

    private fun applyConversationPolicy(route: String) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return
        val effectivePolicy = effectiveConversationPolicyFor(clean)
        currentPolicyRoute = clean
        currentKeepViewedMessages = effectivePolicy.keepViewedMessages
        currentAllowScreenshots = effectivePolicy.allowScreenshots
        currentChatSettingsKeepViewedMessages = effectivePolicy.keepViewedMessages
        currentChatSettingsAllowScreenshots = effectivePolicy.allowScreenshots
        nodeManager.setProfilePolicy(
            keepViewedMessages = effectivePolicy.keepViewedMessages,
            allowScreenshots = effectivePolicy.allowScreenshots
        )
    }

    private fun scheduleViewedConversationCleanup(route: String) {
        val cleanRoute = canonicalConversationKey(route)
        if (cleanRoute.isBlank()) return
        val elapsed = System.currentTimeMillis() - chatOpenedAtMs
        val gracePeriodMs = 2_500L
        if (elapsed < gracePeriodMs) return
        cleanupChatJob?.cancel()
        cleanupChatJob = viewModelScope.launch(Dispatchers.Main.immediate) {
            if (inChat || targetUsername.isNotBlank()) return@launch
            if (seenButNotClearedByPeer[cleanRoute] != true) return@launch
            if (hasProtectedUnreadMessages(cleanRoute)) return@launch
            if (currentKeepViewedMessages) return@launch

            nodeManager.clearViewedMessages(cleanRoute)
            clearPrivacyNotices(cleanRoute)
            unreadByPeer[cleanRoute] = 0
            seenButNotClearedByPeer.remove(cleanRoute)
            conversationsVersion++
            refreshConversationPreviewsAsync()
        }
    }

    private fun hasProtectedUnreadMessages(route: String): Boolean {
        val clean = canonicalConversationKey(route)
        return (unreadByPeer[clean] ?: 0) > 0 || (unreadEntryCountByPeer[clean] ?: 0) > 0
    }

    private fun canonicalConversationKey(route: String): String {
        val clean = route.trim()
        return nodeManager.canonicalRoute(clean) ?: clean
    }

    private fun limitUtf8Bytes(text: String, maxBytes: Int): String {
        val clean = text.trimEnd()
        if (clean.toByteArray(Charsets.UTF_8).size <= maxBytes) return clean
        var end = clean.length
        while (end > 0) {
            val candidate = clean.substring(0, end)
            if (candidate.toByteArray(Charsets.UTF_8).size <= maxBytes) {
                return candidate
            }
            end--
        }
        return ""
    }

    private fun handleCommand(commandLine: String, target: String) {
        when (commandLine.trim()) {
            "/clear" -> {
                if (target.isNotBlank() && !hasProtectedUnreadMessages(target)) {
                    nodeManager.clearMessages(target)
                    clearPrivacyNotices(target)
                    messages.clear()
                    conversationsVersion++
                    refreshConversationPreviewsAsync()
                }
            }
            "/help" -> {
                messages.add(
                    Message(
                        text = "Comandos: /clear limpa este chat neste aparelho",
                        isMine = false
                    )
                )
            }
            else -> {
                messages.add(Message(text = "Comando desconhecido: $commandLine", isMine = false))
            }
        }
    }
}
