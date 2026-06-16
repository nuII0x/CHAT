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
import com.null0x.chat.AppVisibility
import com.null0x.chat.model.DeliveryState
import com.null0x.chat.model.Message
import com.null0x.chat.network.ChatNodeManager
import com.null0x.chat.security.identity.RouteIdentityRegistry
import com.null0x.chat.storage.ChatStore
import com.null0x.chat.util.normalizeProfileEmojiInput
import com.null0x.chat.util.normalizeProfileNameInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val ROUTE_TOKENS_PREFS = "route_tokens"
        private const val LAST_PUBLIC_ROUTE_KEY = "last_public_route"
        private const val CONVERSATION_STATE_PREFS = "conversation_state"
        private const val CONTACT_REQUEST_PREFIX = "[NullChat:contact-request]"
        private const val CONTACT_ACCEPT_PREFIX = "[NullChat:contact-accept]"
        private const val CONTACT_ACCEPT_NOTICE_PREFIX = "accepted_notice"
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

    data class ContactPreview(
        val username: String,
        val displayName: String,
        val emoji: String,
        val accepted: Boolean
    )

    private val nodeManager = ChatNodeManager
    private val chatStore = ChatStore(application.applicationContext)
    private val profilePrefs = application.applicationContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val contactsPrefs = application.applicationContext.getSharedPreferences("contacts", Context.MODE_PRIVATE)
    private val routeNamesPrefs = application.applicationContext.getSharedPreferences("route_names", Context.MODE_PRIVATE)
    private val conversationPoliciesPrefs = application.applicationContext.getSharedPreferences("conversation_policies", Context.MODE_PRIVATE)
    private val routeTokensPrefs = application.applicationContext.getSharedPreferences(ROUTE_TOKENS_PREFS, Context.MODE_PRIVATE)
    private val conversationStatePrefs = application.applicationContext.getSharedPreferences(CONVERSATION_STATE_PREFS, Context.MODE_PRIVATE)
    private val conversationDraftsPrefs = application.applicationContext.getSharedPreferences("conversation_drafts", Context.MODE_PRIVATE)
    private val privacyNoticesKey = "privacy_notices"
    private val outgoingSendJobs = mutableMapOf<String, Job>()
    private val outgoingSendLock = Any()

    private val listener = object : ChatNodeManager.Listener {
        override fun onUsernameReady(username: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                myUsername = username
                rememberKnownRouteLabel(username)
                routeNamesVersion++
                refreshRouteLookup()
                refreshConversationPreviewsAsync()
            }
        }

        override fun onMessage(fromUsername: String, text: String, messageId: String?, timestamp: Long?) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                val fromRoute = canonicalConversationKey(fromUsername)
                markPartnerReachable(fromRoute)
                if (handleContactControlMessage(fromRoute, text)) {
                    return@launch
                }
                if (!canReceiveMessageFrom(fromRoute)) {
                    return@launch
                }
                setConversationHidden(fromRoute, false)
                val currentTarget = canonicalConversationKey(targetUsername)
                val incomingMessage = Message(
                    id = messageId ?: java.util.UUID.randomUUID().toString(),
                    text = text,
                    isMine = false,
                    timestamp = timestamp?.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    delivery = DeliveryState.Delivered
                )
                if (!appendConversationMessage(fromRoute, incomingMessage)) {
                    return@launch
                }

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
                appendVisibleMessage(incomingMessage)
                refreshConversationPreviewsAsync()
            }
        }

        override fun onDeliveryAck(fromUsername: String, messageId: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                val route = canonicalConversationKey(fromUsername)
                markPartnerReachable(route)
                updateConversationCacheDelivery(route, messageId, DeliveryState.Delivered)
                if (route == targetUsername) {
                    updateMessageDelivery(messageId, DeliveryState.Delivered)
                }
                conversationsVersion++
            }
        }

        override fun onMessageDeleted(fromUsername: String, messageId: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                val route = canonicalConversationKey(fromUsername)
                if (route.isBlank()) return@launch
                deleteConversationMessage(route, messageId, broadcastDeletion = false)
            }
        }

        override fun onOutgoingDeliveryStateChanged(toUsername: String, messageId: String, state: DeliveryState) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                val route = canonicalConversationKey(toUsername)
                if (state == DeliveryState.Sent || state == DeliveryState.Delivered) {
                    markPartnerReachable(route)
                }
                updateConversationCacheDelivery(route, messageId, state)
                if (route == targetUsername) {
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
                partnerOnlineByPeer[route] = true
                schedulePartnerPresenceExpiry(route)
                when (state) {
                    "open" -> {
                        partnerChatOpenByPeer[route] = true
                    }
                    "closed" -> {
                        partnerChatOpenByPeer[route] = false
                        partnerTypingByPeer[route] = false
                    }
                    "typing" -> {
                        partnerChatOpenByPeer[route] = true
                        partnerTypingByPeer[route] = true
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
                this@ChatViewModel.peers.addAll(
                    peers.map { canonicalConversationKey(it) }
                        .filter { it.isNotBlank() }
                        .distinct()
                )
                refreshRouteLookup()
                if (peers.isNotEmpty()) {
                    refreshConversationPreviewsAsync()
                }
            }
        }

        override fun onProfileNameChanged(name: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                profileName = name
                rememberKnownRouteLabel(myUsername)
                rememberKnownRouteLabel(currentPublicRoute())
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
                val cleanRoute = canonicalConversationKey(route)
                chatStore.rememberPeer(
                    cleanRoute,
                    publicDisplayNameForRoute(cleanRoute).ifBlank { routeTokenString(cleanRoute) }
                )
                conversationsVersion++
                routeNamesVersion++
                if (cleanRoute == targetUsername) {
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
    private val partnerOnlineByPeer = mutableStateMapOf<String, Boolean>()
    private val partnerChatOpenByPeer = mutableStateMapOf<String, Boolean>()
    private val partnerTypingByPeer = mutableStateMapOf<String, Boolean>()
    private val localTypingByPeer = mutableStateMapOf<String, Boolean>()
    private val partnerPresenceExpiryJobs = mutableMapOf<String, Job>()
    private var conversationsVersion by mutableStateOf(0)
    private var contactsVersion by mutableStateOf(0)
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
    private var warmCacheJob: Job? = null
    private var conversationPreviewCache by mutableStateOf<List<ConversationPreview>>(emptyList())
    private val conversationHistoryLock = Any()
    private val conversationHistoryCache = mutableMapOf<String, MutableList<Message>>()
    private var currentChatLoaded by mutableStateOf(false)
    private var localListsPrepared by mutableStateOf(false)
    private var chatOpenedAtMs by mutableStateOf(0L)

    var myUsername by mutableStateOf("iniciando...")
        private set

    var targetUsername by mutableStateOf("")
        private set
    var inChat by mutableStateOf(false)
        private set

    var profileName by mutableStateOf("")
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
        profileName = profilePrefs
            .getString("display_name", null)
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
        contactRouteInput = ""
        defaultKeepViewedMessages = profilePrefs.getBoolean("keep_viewed_messages", true)
        defaultAllowScreenshots = profilePrefs.getBoolean("chat_screenshots_enabled", false)
        currentKeepViewedMessages = defaultKeepViewedMessages
        currentAllowScreenshots = defaultAllowScreenshots
        profileEmoji = profilePrefs.getString("profile_emoji", "🙂")?.takeIf { it.isNotBlank() } ?: "🙂"
        profileEmojiSymbol = profileEmoji
        profileBio = profilePrefs.getString("profile_bio", "")?.orEmpty() ?: ""
        profileBioText = profileBio
        myUsername = lastKnownOwnRoute().ifBlank { myUsername }
        nodeManager.setChatMessageAuthorization { route -> canMessageContact(route) }
        prepareLocalListsSynchronously()
        warmConversationHistoryCacheAsync()
        nodeManager.start(application.applicationContext, autoStartTor = true)
        nodeManager.setProfileEmoji(application.applicationContext, profileEmoji)
        nodeManager.setProfileBio(application.applicationContext, profileBio)
        nodeManager.setProfilePolicy(defaultKeepViewedMessages, defaultAllowScreenshots)
        privacyNotices.addAll(loadPrivacyNotices())
        nodeManager.addListener(listener)
        schedulePendingOutgoingMessages()
    }

    override fun onCleared() {
        val current = canonicalConversationKey(targetUsername)
        if (current.isNotBlank()) {
            AppVisibility.markChatClosed(current)
            sendChatPresence(current, "idle")
            sendChatPresence(current, "closed")
        }
        nodeManager.removeListener(listener)
        nodeManager.setChatMessageAuthorization(null)
        partnerPresenceExpiryJobs.values.forEach { it.cancel() }
        partnerPresenceExpiryJobs.clear()
        super.onCleared()
    }

    fun selectTarget(username: String) {
        val cleanTarget = canonicalConversationKey(username)
        val previousTarget = canonicalConversationKey(targetUsername)
        if (cleanTarget.isNotBlank() && cleanTarget == previousTarget && inChat && currentChatLoaded) {
            return
        }
        openChatJob?.cancel()
        if (previousTarget.isNotBlank() && previousTarget != cleanTarget) {
            AppVisibility.markChatClosed(previousTarget)
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

        messages.addAll(conversationMessagesFor(cleanTarget))
        currentChatLoaded = true

        AppVisibility.markChatOpen(cleanTarget)
        setConversationHidden(cleanTarget, false)
        nodeManager.cancelNotification(cleanTarget)
        ensureConversationPolicy(cleanTarget)
        applyConversationPolicy(cleanTarget)
        chatStore.rememberPeer(cleanTarget, conversationLabelFor(cleanTarget))
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
    }

    fun openHome() {
        val previous = targetUsername
        openChatJob?.cancel()
        openChatJob = null
        if (previous.isNotBlank()) {
            AppVisibility.markChatClosed(previous)
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
        val cleanName = normalizeProfileNameInput(name)
        if (cleanName.isBlank()) return

        nodeManager.setProfileName(getApplication(), cleanName)
        needsProfileSetup = false
    }

    fun updateProfileEmoji(emoji: String) {
        val cleanEmoji = normalizeProfileEmojiInput(emoji).ifBlank { "🙂" }
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
        if (clean.isBlank() || isContactBlocked(clean)) return
        val localOwner = isLocalOwnerRoute(clean)
        setConversationHidden(clean, false)

        val editor = contactsPrefs.edit()
            .putBoolean(contactRequestedKey(clean), true)
            .putString(contactRouteKey(clean), clean)
            .remove(contactRemovedKey(clean))
            .remove(contactAcceptedNoticeKey(clean))
        if (localOwner) {
            editor.putBoolean(contactAcceptedKey(clean), true)
        } else {
            editor.remove(contactAcceptedKey(clean))
        }
        editor.apply()
        chatStore.rememberPeer(clean, conversationLabelFor(clean))
        if (localOwner && !startedConversations.contains(clean)) {
            startedConversations.add(0, clean)
        }

        contactsVersion++
        conversationsVersion++
        refreshRouteLookup()
        refreshConversationPreviewsAsync()

        viewModelScope.launch {
            nodeManager.requestPublicProfile(clean)
            if (!localOwner && !isContactAccepted(clean)) {
                nodeManager.sendMessage(
                    clean,
                    CONTACT_REQUEST_PREFIX,
                    java.util.UUID.randomUUID().toString()
                )
            }
        }
    }

    fun cancelContactRequest(username: String) {
        val clean = canonicalConversationKey(username)
        if (clean.isBlank()) return
        contactsPrefs.edit()
            .remove(contactAcceptedKey(clean))
            .remove(contactAcceptedNoticeKey(clean))
            .remove(contactRequestedKey(clean))
            .remove(contactPendingKey(clean))
            .putBoolean(contactRemovedKey(clean), true)
            .putString(contactRouteKey(clean), clean)
            .apply()
        setConversationHidden(clean, true)
        removeLocalConversation(clean)
        contactsVersion++
        conversationsVersion++
        refreshRouteLookup()
        refreshConversationPreviewsAsync()
    }

    fun removeContact(username: String) {
        val clean = canonicalConversationKey(username)
        if (clean.isBlank()) return
        contactsPrefs.edit()
            .remove(contactAcceptedKey(clean))
            .remove(contactAcceptedNoticeKey(clean))
            .remove(contactRequestedKey(clean))
            .remove(contactPendingKey(clean))
            .putBoolean(contactRemovedKey(clean), true)
            .putString(contactRouteKey(clean), clean)
            .apply()
        setConversationHidden(clean, true)
        removeLocalConversation(clean)
        contactsVersion++
        conversationsVersion++
        routeNamesVersion++
        refreshRouteLookup()
        refreshConversationPreviewsAsync()
    }

    private fun removeLocalConversation(route: String) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return
        cleanupChatJob?.cancel()
        nodeManager.removeConversation(clean)
        chatStore.remove(clean)
        startedConversations.remove(clean)
        peers.remove(clean)
        clearConversationCache(clean)
        unreadByPeer.remove(clean)
        seenButNotClearedByPeer.remove(clean)
        unreadEntryCountByPeer.remove(clean)
        partnerPresenceExpiryJobs.remove(clean)?.cancel()
        partnerOnlineByPeer.remove(clean)
        partnerChatOpenByPeer.remove(clean)
        partnerTypingByPeer.remove(clean)
        localTypingByPeer.remove(clean)
        clearPrivacyNotices(clean)
        if (targetUsername == clean) {
            openHome()
        }
    }

    fun pendingContactRequests(): List<ConversationPreview> {
        contactsVersion
        routeNamesVersion
        return contactRoutesWithPrefix("pending:")
            .filterNot { isLocalOwnerRoute(it) }
            .filterNot { isContactAccepted(it) }
            .filterNot { isContactBlocked(it) }
            .filterNot { contactsPrefs.getBoolean(contactRemovedKey(it), false) }
            .map { route ->
                ConversationPreview(
                    username = route,
                    displayName = chatTitleFor(route),
                    emoji = publicEmojiForRoute(route),
                    lastTimestamp = 0L,
                    unreadCount = 0,
                    previewLine = "Solicitou contato"
                )
            }
            .sortedBy { it.displayName.lowercase() }
    }

    fun acceptContactRequest(username: String) {
        val clean = canonicalConversationKey(username)
        if (clean.isBlank()) return
        contactsPrefs.edit()
            .putBoolean(contactAcceptedKey(clean), true)
            .putBoolean(contactRequestedKey(clean), true)
            .putString(contactRouteKey(clean), clean)
            .remove(contactAcceptedNoticeKey(clean))
            .remove(contactPendingKey(clean))
            .remove(contactBlockedKey(clean))
            .remove(contactRemovedKey(clean))
            .apply()
        setConversationHidden(clean, false)
        chatStore.rememberPeer(clean, conversationLabelFor(clean))
        if (!startedConversations.contains(clean)) {
            startedConversations.add(0, clean)
        }
        contactsVersion++
        conversationsVersion++
        refreshConversationPreviewsAsync()
        viewModelScope.launch {
            nodeManager.sendMessage(clean, CONTACT_ACCEPT_PREFIX, java.util.UUID.randomUUID().toString())
            retryAcceptedPendingMessagesFor(clean)
        }
    }

    fun blockContact(username: String) {
        val clean = canonicalConversationKey(username)
        if (clean.isBlank()) return
        contactsPrefs.edit()
            .putBoolean(contactBlockedKey(clean), true)
            .putString(contactRouteKey(clean), clean)
            .remove(contactAcceptedKey(clean))
            .remove(contactAcceptedNoticeKey(clean))
            .remove(contactRequestedKey(clean))
            .remove(contactPendingKey(clean))
            .remove(contactRemovedKey(clean))
            .apply()
        setConversationHidden(clean, true)
        removeLocalConversation(clean)
        if (routeLookup?.username == clean || canonicalConversationKey(contactRouteInput) == clean) {
            routeLookup = null
            contactRouteInput = ""
            routeStatus = ""
        }
        contactsVersion++
        conversationsVersion++
        routeNamesVersion++
        refreshConversationPreviewsAsync()
    }

    fun unblockContact(username: String) {
        val clean = canonicalConversationKey(username)
        if (clean.isBlank()) return
        contactsPrefs.edit()
            .remove(contactBlockedKey(clean))
            .remove(contactAcceptedKey(clean))
            .remove(contactAcceptedNoticeKey(clean))
            .putBoolean(contactRequestedKey(clean), true)
            .remove(contactPendingKey(clean))
            .remove(contactRemovedKey(clean))
            .putString(contactRouteKey(clean), clean)
            .apply()
        setConversationHidden(clean, false)
        contactsVersion++
        conversationsVersion++
        refreshConversationPreviewsAsync()
    }

    fun isContactAccepted(username: String): Boolean {
        val clean = canonicalConversationKey(username)
        if (clean.isBlank()) return false
        return contactsPrefs.getBoolean(contactAcceptedKey(clean), false)
    }

    fun shouldShowAcceptedNotice(username: String): Boolean {
        val clean = canonicalConversationKey(username)
        if (clean.isBlank()) return false
        return isContactAccepted(clean) && !contactsPrefs.getBoolean(contactAcceptedNoticeKey(clean), false)
    }

    fun markAcceptedNoticeShown(username: String) {
        val clean = canonicalConversationKey(username)
        if (clean.isBlank()) return
        if (!isContactAccepted(clean)) return
        contactsPrefs.edit().putBoolean(contactAcceptedNoticeKey(clean), true).apply()
    }

    fun isContactPending(username: String): Boolean {
        val clean = canonicalConversationKey(username)
        if (clean.isBlank()) return false
        return contactsPrefs.getBoolean(contactPendingKey(clean), false)
    }

    fun isContactRequested(username: String): Boolean {
        val clean = canonicalConversationKey(username)
        if (clean.isBlank()) return false
        return contactsPrefs.getBoolean(contactRequestedKey(clean), false)
    }

    fun isContactActive(username: String): Boolean {
        val clean = canonicalConversationKey(username)
        if (clean.isBlank()) return false
        if (isContactBlocked(clean)) return false
        return !contactsPrefs.getBoolean(contactRemovedKey(clean), false) &&
            (isContactRequested(clean) || isContactAccepted(clean))
    }

    fun isContactBlocked(username: String): Boolean {
        val clean = canonicalConversationKey(username)
        if (clean.isBlank()) return false
        return contactsPrefs.getBoolean(contactBlockedKey(clean), false)
    }

    fun contactPreviews(): List<ContactPreview> {
        contactsVersion
        routeNamesVersion
        return (contactRoutesWithPrefix("requested:") + contactRoutesWithPrefix("accepted:"))
            .map { canonicalConversationKey(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { isContactActive(it) }
            .filterNot { isContactBlocked(it) }
            .map { route ->
                ContactPreview(
                    username = route,
                    displayName = chatTitleFor(route),
                    emoji = publicEmojiForRoute(route),
                    accepted = isContactAccepted(route)
                )
            }
            .sortedWith(compareByDescending<ContactPreview> { it.accepted }.thenBy { it.displayName.lowercase() })
    }

    fun blockedContactPreviews(): List<ContactPreview> {
        contactsVersion
        routeNamesVersion
        return contactRoutesWithPrefix("blocked:")
            .map { canonicalConversationKey(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .map { route ->
                ContactPreview(
                    username = route,
                    displayName = chatTitleFor(route),
                    emoji = publicEmojiForRoute(route),
                    accepted = false
                )
            }
            .sortedBy { it.displayName.lowercase() }
    }

    fun contactsBackupJson(): String {
        contactsVersion
        routeNamesVersion
        val routes = (
            contactRoutesWithPrefix("requested:") +
                contactRoutesWithPrefix("accepted:") +
                contactRoutesWithPrefix("pending:") +
                contactRoutesWithPrefix("blocked:")
            )
            .map { canonicalConversationKey(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { routeTokenString(it).lowercase() }
        val contacts = JSONArray()
        routes.forEach { route ->
            contacts.put(
                JSONObject()
                    .put("route", route)
                    .put("token", routeTokenString(route))
                    .put("displayName", chatTitleFor(route))
                    .put("accepted", isContactAccepted(route))
                    .put("requested", isContactRequested(route))
                    .put("pending", isContactPending(route))
                    .put("blocked", isContactBlocked(route))
            )
        }
        return JSONObject()
            .put("format", "nullchat.contacts.v1")
            .put("createdAt", System.currentTimeMillis())
            .put("contacts", contacts)
            .toString(2)
    }

    fun displayNameFor(username: String): String {
        val route = canonicalConversationKey(username)
        routeTokenFor(route)
        return localNameForRoute(route)
            .ifBlank { publicDisplayNameForRoute(route) }
            .ifBlank { chatStore.preferredLabel(route).orEmpty() }
            .ifBlank { routeTokenString(route) }
    }

    fun chatTitleFor(username: String): String {
        val route = canonicalConversationKey(username)
        routeTokenFor(route)
        routeNamesVersion
        return localNameForRoute(route)
            .ifBlank { publicDisplayNameForRoute(route) }
            .ifBlank { chatStore.preferredLabel(route).orEmpty() }
            .ifBlank { routeTokenString(route) }
    }

    fun emojiForRoute(username: String): String {
        val route = canonicalConversationKey(username)
        routeTokenFor(route)
        return publicEmojiForRoute(route)
    }

    fun isPartnerChatOpen(username: String): Boolean {
        return partnerChatOpenByPeer[canonicalConversationKey(username)] == true
    }

    fun isPartnerOnline(username: String): Boolean {
        return partnerOnlineByPeer[canonicalConversationKey(username)] == true
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
        val publicName = publicDisplayNameForRoute(route)
        val display = localName
            .ifBlank { publicName }
            .ifBlank { chatStore.preferredLabel(route).orEmpty() }
            .ifBlank { routeTokenString(route) }
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
        if (cleanName.isBlank()) {
            chatStore.removePreferredLabel(cleanRoute)
            chatStore.rememberPeer(cleanRoute, publicDisplayNameForRoute(cleanRoute).ifBlank { routeTokenString(cleanRoute) })
        } else {
            chatStore.rememberPeer(cleanRoute, cleanName)
        }
        routeNamesVersion++
        conversationsVersion++
        refreshConversationPreviewsAsync()
    }

    fun currentPublicRoute(): String {
        return nodeManager.currentPublicRoute().ifBlank { lastKnownOwnRoute() }
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
        val token = routeTokenStringForDisplay(route)
        routeTokensPrefs.edit().putString(token.lowercase(), route).apply()
        return token
    }

    fun isValidNullChatQrToken(token: String): Boolean {
        val clean = canonicalConversationKey(token)
        if (clean.isBlank()) return false
        return resolveContactRouteQuery(clean).isNotBlank()
    }

    fun isLocalRoute(username: String): Boolean {
        return isLocalOwnerRoute(username)
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
        nodeManager.startTor(getApplication())
    }

    private fun prepareLocalListsSynchronously() {
        if (localListsPrepared) return
        val loadedConversations = chatStore.knownPeers()
            .map { canonicalConversationKey(it) }
            .filter { it.isNotBlank() }
            .distinct()
        loadedConversations.forEach { peer ->
            chatStore.rememberPeer(peer, conversationLabelFor(peer))
        }
        startedConversations.clear()
        startedConversations.addAll(loadedConversations)
        conversationPreviewCache = buildConversationPreviews(loadedConversations)
        localListsPrepared = true
        conversationsVersion++
    }

    private fun warmConversationHistoryCacheAsync() {
        if (warmCacheJob?.isActive == true) return
        warmCacheJob = viewModelScope.launch(Dispatchers.IO) {
            val peers = chatStore.knownPeers()
                .map { canonicalConversationKey(it) }
                .filter { it.isNotBlank() }
                .distinct()
            val snapshot = peers.associateWith { peer -> chatStore.load(peer).toMutableList() }
            withContext(Dispatchers.Main.immediate) {
                synchronized(conversationHistoryLock) {
                    snapshot.forEach { (peer, messages) ->
                        conversationHistoryCache[peer] = messages
                    }
                }
            }
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
        if (isContactBlocked(clean)) {
            routeLookup = null
            routeStatus = ""
            return
        }

        val fallbackLabel = routeTokenString(clean)
        val displayLabel = localNameForRoute(clean)
            .ifBlank { publicDisplayNameForRoute(clean) }
            .ifBlank { chatStore.preferredLabel(clean).orEmpty() }
            .ifBlank { fallbackLabel }
        routeLookup = RouteLookup(
            name = displayLabel,
            username = clean,
            displayName = displayLabel,
            emoji = publicEmojiForRoute(clean),
            isLocalOwner = isLocalOwnerRoute(clean),
            source = "Rede"
        )
        routeTokensPrefs.edit().putString(routeTokenStringForDisplay(clean).lowercase(), clean).apply()
        if (showNotFound) {
            nodeManager.requestPublicProfile(clean)
        }
        routeStatus = if (isLocalOwnerRoute(clean)) {
            "Esta rota aponta para este aparelho"
        } else if (isContactActive(clean) && isContactAccepted(clean)) {
            "Contato aceito"
        } else if (isContactActive(clean) && isContactRequested(clean)) {
            "Pedido enviado"
        } else {
            "Rota pronta para conversar"
        }
    }

    fun conversationPreviews(): List<ConversationPreview> {
        conversationsVersion
        routeNamesVersion
        return conversationPreviewCache
    }

    fun draftFor(username: String): String {
        val target = canonicalConversationKey(username)
        if (target.isBlank()) return ""
        return conversationDraftsPrefs.getString(conversationDraftKey(target), null).orEmpty()
    }

    fun updateDraft(username: String, draft: String) {
        val target = canonicalConversationKey(username)
        if (target.isBlank()) return
        val cleanDraft = draft.trimEnd()
        val editor = conversationDraftsPrefs.edit()
        if (cleanDraft.isBlank()) {
            editor.remove(conversationDraftKey(target))
        } else {
            editor.putString(conversationDraftKey(target), cleanDraft)
        }
        editor.apply()
        refreshConversationPreviewsAsync()
    }

    fun clearDraft(username: String) {
        val target = canonicalConversationKey(username)
        if (target.isBlank()) return
        conversationDraftsPrefs.edit().remove(conversationDraftKey(target)).apply()
        refreshConversationPreviewsAsync()
    }

    fun hasDraft(username: String): Boolean {
        return draftFor(username).isNotBlank()
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
        clearConversationCache(target)
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
        setConversationHidden(target, true)
        nodeManager.removeConversation(target)
        startedConversations.remove(target)
        clearConversationCache(target)
        unreadByPeer.remove(target)
        unreadEntryCountByPeer.remove(target)
        seenButNotClearedByPeer.remove(target)
        partnerPresenceExpiryJobs.remove(target)?.cancel()
        partnerOnlineByPeer.remove(target)
        partnerChatOpenByPeer.remove(target)
        partnerTypingByPeer.remove(target)
        localTypingByPeer.remove(target)
        clearPrivacyNotices(target)
        contactsVersion++
        conversationsVersion++
        refreshConversationPreviewsAsync()
        if (target == targetUsername) {
            openHome()
        }
    }

    fun deleteMessage(message: Message) {
        val target = canonicalConversationKey(targetUsername)
        if (target.isBlank() || message.id.isBlank()) return
        deleteConversationMessage(
            target,
            message.id,
            broadcastDeletion = true,
            messageWasMine = message.isMine
        )
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
        if (isContactBlocked(target)) return
        if (!canQueueOutgoingMessageFor(target)) return
        setConversationHidden(target, false)

        val isSelfRoute = isLocalOwnerRoute(target)
        val localId = java.util.UUID.randomUUID().toString()
        clearDraft(target)
        chatStore.rememberPeer(target, conversationLabelFor(target))
        if (isSelfRoute) {
            val pendingMessage = Message(
                id = localId,
                text = message,
                isMine = true,
                delivery = DeliveryState.Pending
            )
            if (appendConversationMessage(target, pendingMessage)) {
                appendVisibleMessage(pendingMessage)
            }
            chatStore.upsert(target, pendingMessage)
            conversationsVersion++
            refreshConversationPreviewsAsync()
            nodeManager.requestPublicProfile(target)
            sendChatPresence(target, "open")
            viewModelScope.launch {
                nodeManager.sendChatMessage(target, message, localId)
            }
            return
        }

        val pendingMessage = Message(
            id = localId,
            text = message,
            isMine = true,
            delivery = DeliveryState.Pending
        )
        if (appendConversationMessage(target, pendingMessage)) {
            appendVisibleMessage(pendingMessage)
        }
        chatStore.upsert(target, pendingMessage)
        conversationsVersion++
        refreshConversationPreviewsAsync()

        if (!canMessageContact(target)) return

        nodeManager.requestPublicProfile(target)
        sendChatPresence(target, "open")
        scheduleOutgoingDelivery(target, priorityMessageId = localId)
    }

    private fun sendChatPresence(route: String, state: String) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return
        nodeManager.sendChatPresence(clean, state)
    }

    private fun schedulePartnerPresenceExpiry(route: String) {
        partnerPresenceExpiryJobs.remove(route)?.cancel()
        partnerPresenceExpiryJobs[route] = viewModelScope.launch(Dispatchers.Main.immediate) {
            kotlinx.coroutines.delay(70_000)
            partnerOnlineByPeer[route] = false
            partnerChatOpenByPeer[route] = false
            partnerTypingByPeer[route] = false
            partnerPresenceExpiryJobs.remove(route)
        }
    }

    private fun markPartnerReachable(route: String) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return
        partnerOnlineByPeer[clean] = true
        schedulePartnerPresenceExpiry(clean)
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
            conversationMessagesFor(target)
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
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val current = messages[index]
        if (current.delivery == state) return
        messages[index] = current.copy(delivery = state)
        updateConversationCacheDelivery(targetUsername, messageId, state)
    }

    private fun refreshConversationPreviewsAsync() {
        previewsJob?.cancel()
        previewsJob = viewModelScope.launch(Dispatchers.IO) {
            delay(80)
            val usernames = conversationPreviewCandidates()
            val snapshot = buildConversationPreviews(usernames)

            withContext(Dispatchers.Main.immediate) {
                val missingLocalItems = usernames.filterNot { startedConversations.contains(it) }
                if (missingLocalItems.isNotEmpty()) {
                    startedConversations.addAll(missingLocalItems)
                }
                conversationPreviewCache = snapshot
            }
        }
    }

    private fun buildConversationPreviews(usernames: List<String>): List<ConversationPreview> {
        return usernames.map { username ->
            val target = canonicalConversationKey(username)
            val items = conversationMessagesFor(target).takeLast(16)
            val last = items.lastOrNull()
            val fallbackLine = when {
                hasDraft(target) -> "Rascunho"
                isContactBlocked(target) -> "Bloqueado"
                isContactPending(target) -> "Pedido pendente"
                isContactAccepted(target) -> "limpo"
                isContactRequested(target) -> "Pedido enviado"
                else -> "Sem mensagens"
            }
            ConversationPreview(
                username = target,
                displayName = chatTitleFor(target),
                emoji = publicEmojiForRoute(target),
                lastTimestamp = last?.timestamp ?: 0L,
                unreadCount = unreadByPeer[target] ?: 0,
                previewLine = if (hasDraft(target)) {
                    "Rascunho"
                } else if (items.isEmpty()) {
                    fallbackLine
                } else {
                    previewLineFor(items, unreadByPeer[target] ?: 0)
                }
            )
        }.sortedWith(
            compareByDescending<ConversationPreview> { it.lastTimestamp }
                .thenByDescending { conversationPriority(it.username) }
                .thenBy { it.displayName.lowercase() }
        )
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

    private fun conversationPreviewCandidates(): List<String> {
        val contactRoutes = contactRoutesWithPrefix("requested:") +
            contactRoutesWithPrefix("accepted:") +
            contactRoutesWithPrefix("pending:")
        return buildList {
            addAll(startedConversations.map { canonicalConversationKey(it) })
            addAll(chatStore.knownPeers().map { canonicalConversationKey(it) })
            addAll(contactRoutes.map { canonicalConversationKey(it) })
        }
            .map { canonicalConversationKey(it) }
            .filter { it.isNotBlank() }
            .filterNot { isContactBlocked(it) }
            .filterNot { contactsPrefs.getBoolean(contactRemovedKey(it), false) }
            .filterNot { isConversationHidden(it) }
            .distinct()
    }

    private fun conversationPriority(route: String): Int {
        val clean = canonicalConversationKey(route)
        return when {
            isContactAccepted(clean) -> 3
            isContactRequested(clean) -> 2
            isContactPending(clean) -> 1
            startedConversations.contains(clean) -> 1
            else -> 0
        }
    }

    private fun conversationHiddenKey(route: String): String = "hidden:${routeStorageKey(route)}"
    private fun conversationDraftKey(route: String): String = "draft:${routeStorageKey(route)}"

    private fun isConversationHidden(route: String): Boolean {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return false
        return conversationStatePrefs.getBoolean(conversationHiddenKey(clean), false)
    }

    private fun setConversationHidden(route: String, hidden: Boolean) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return
        conversationStatePrefs.edit().putBoolean(conversationHiddenKey(clean), hidden).apply()
    }

    private fun handleContactControlMessage(fromRoute: String, text: String): Boolean {
        val clean = canonicalConversationKey(fromRoute)
        if (clean.isBlank()) return false
        if (isContactBlocked(clean)) return true
        return when (text.trim()) {
            CONTACT_REQUEST_PREFIX -> {
                if (!isContactBlocked(clean) && !isContactAccepted(clean)) {
                    contactsPrefs.edit()
                        .putBoolean(contactPendingKey(clean), true)
                        .putString(contactRouteKey(clean), clean)
                        .apply()
                    setConversationHidden(clean, false)
                    nodeManager.requestPublicProfile(clean)
                    contactsVersion++
                    conversationsVersion++
                }
                true
            }
            CONTACT_ACCEPT_PREFIX -> {
                if (!isContactBlocked(clean)) {
                    val wasRemoved = contactsPrefs.getBoolean(contactRemovedKey(clean), false)
                    val editor = contactsPrefs.edit()
                        .putBoolean(contactAcceptedKey(clean), true)
                        .putBoolean(contactRequestedKey(clean), true)
                        .putString(contactRouteKey(clean), clean)
                        .remove(contactPendingKey(clean))
                    if (!wasRemoved) {
                        editor.remove(contactRemovedKey(clean))
                        chatStore.rememberPeer(clean, conversationLabelFor(clean))
                        if (!startedConversations.contains(clean)) {
                            startedConversations.add(0, clean)
                        }
                    }
                    editor.apply()
                    setConversationHidden(clean, false)
                    contactsVersion++
                    conversationsVersion++
                    refreshConversationPreviewsAsync()
                    viewModelScope.launch {
                        retryAcceptedPendingMessagesFor(clean)
                    }
                }
                true
            }
            else -> false
        }
    }

    private fun canReceiveMessageFrom(route: String): Boolean {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return false
        return canMessageContact(clean)
    }

    private fun canQueueOutgoingMessageFor(route: String): Boolean {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return false
        return !isContactBlocked(clean) && (isLocalOwnerRoute(clean) || isContactActive(clean))
    }

    private fun canMessageContact(route: String): Boolean {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return false
        return !isContactBlocked(clean) && (isLocalOwnerRoute(clean) || (isContactActive(clean) && isContactAccepted(clean)))
    }

    private suspend fun retryAcceptedPendingMessagesFor(route: String) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank() || !canMessageContact(clean)) return
        scheduleOutgoingDelivery(clean)
    }

    private fun schedulePendingOutgoingMessages() {
        chatStore.pendingOutgoing(limit = 250)
            .map { (peer, _) -> canonicalConversationKey(peer) }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { scheduleOutgoingDelivery(it) }
    }

    private suspend fun deliverPendingMessage(route: String, messageId: String, text: String): Result<Unit> {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank() || !canMessageContact(clean)) return Result.failure(IllegalStateException("Rota indisponivel"))
        applyConversationPolicy(clean)
        updateLocalTyping(clean, false)
        return nodeManager.sendChatMessage(clean, text, messageId)
    }

    private fun scheduleOutgoingDelivery(route: String, priorityMessageId: String? = null) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return
        synchronized(outgoingSendLock) {
            if (outgoingSendJobs[clean]?.isActive == true) return
            outgoingSendJobs[clean] = viewModelScope.launch {
                try {
                    processOutgoingQueue(clean, priorityMessageId)
                } finally {
                    synchronized(outgoingSendLock) {
                        outgoingSendJobs.remove(clean)
                    }
                }
            }
        }
    }

    private suspend fun processOutgoingQueue(route: String, priorityMessageId: String? = null) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return
        if (!priorityMessageId.isNullOrBlank()) {
            val priority = chatStore.pendingOutgoingForPeer(clean)
                .firstOrNull { it.id == priorityMessageId }
            if (priority != null) {
                if (chatStore.hasMessage(clean, priority.id, isMine = true) != true) return
                if (!canMessageContact(clean)) return
                val result = deliverPendingMessage(clean, priority.id, priority.text)
                if (result.isFailure) return
            }
        }
        while (true) {
            if (!canMessageContact(clean)) return
            val next = chatStore.pendingOutgoingForPeer(clean, limit = 1).firstOrNull() ?: return
            if (chatStore.hasMessage(clean, next.id, isMine = true) != true) return
            val result = deliverPendingMessage(clean, next.id, next.text)
            if (result.isFailure) return
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
        if (legacy.isNotBlank()) return legacy
        return ""
    }

    private fun publicDisplayNameForRoute(route: String): String {
        val clean = canonicalConversationKey(route)
        if (isLocalOwnerRoute(clean)) return profileName
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

    private fun conversationLabelFor(route: String): String {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return ""
        return localNameForRoute(clean)
            .ifBlank { publicDisplayNameForRoute(clean) }
            .ifBlank { chatStore.preferredLabel(clean).orEmpty() }
            .ifBlank { routeTokenString(clean) }
    }

    private fun rememberKnownRouteLabel(route: String) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank() || !startedConversations.contains(clean)) return
        chatStore.rememberPeer(clean, conversationLabelFor(clean))
    }

    private fun conversationMessagesFor(route: String): List<Message> {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return emptyList()
        synchronized(conversationHistoryLock) {
            val cached = conversationHistoryCache[clean]
            if (cached != null) {
                val normalized = normalizeMessages(cached)
                if (normalized != cached) {
                    conversationHistoryCache[clean] = normalized.toMutableList()
                }
                return normalized.toList()
            }
            val loaded = normalizeMessages(chatStore.load(clean)).toMutableList()
            conversationHistoryCache[clean] = loaded
            return loaded.toList()
        }
    }

    private fun deleteConversationMessage(
        route: String,
        messageId: String,
        broadcastDeletion: Boolean,
        messageWasMine: Boolean? = null
    ) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank() || messageId.isBlank()) return

        val removedMessages = removeMessageFromConversationCache(clean, messageId)
        val removedFromStore = chatStore.removeMessage(clean, messageId)
        if (removedMessages.isEmpty() && !removedFromStore) {
            return
        }

        if (clean == targetUsername) {
            messages.removeAll { it.id == messageId }
        }

        if (messageWasMine == false || removedMessages.any { !it.isMine }) {
            val unread = (unreadByPeer[clean] ?: 0).coerceAtLeast(0)
            if (unread > 0) {
                unreadByPeer[clean] = (unread - 1).coerceAtLeast(0)
            }
            val hint = (unreadEntryCountByPeer[clean] ?: 0).coerceAtLeast(0)
            if (hint > 0) {
                unreadEntryCountByPeer[clean] = (hint - 1).coerceAtLeast(0)
            }
        }

        if (broadcastDeletion) {
            nodeManager.requestMessageDeletion(clean, messageId)
        }

        conversationsVersion++
        refreshConversationPreviewsAsync()
    }

    private fun removeMessageFromConversationCache(route: String, messageId: String): List<Message> {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank() || messageId.isBlank()) return emptyList()
        synchronized(conversationHistoryLock) {
            val cache = conversationHistoryCache[clean] ?: return emptyList()
            val removed = cache.filter { it.id == messageId }
            if (removed.isEmpty()) return emptyList()
            cache.removeAll { it.id == messageId }
            return removed
        }
    }

    private fun appendConversationMessage(route: String, message: Message): Boolean {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return false
        if (conversationMessageExists(clean, message)) return false
        synchronized(conversationHistoryLock) {
            val cache = conversationHistoryCache.getOrPut(clean) { mutableListOf() }
            cache.add(message)
            cache.sortWith(messageComparator())
        }
        return true
    }

    private fun appendVisibleMessage(message: Message) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index >= 0) {
            messages[index] = mergeMessage(messages[index], message)
        } else {
            messages.add(message)
        }
        messages.sortWith(messageComparator())
    }

    private fun normalizeMessages(items: List<Message>): List<Message> {
        return items
            .groupBy { it.id }
            .map { (_, group) -> group.reduce { acc, item -> mergeMessage(acc, item) } }
            .sortedWith(messageComparator())
    }

    private fun messageComparator(): Comparator<Message> {
        return compareBy<Message> { it.timestamp }
            .thenBy { it.id }
    }

    private fun updateConversationCacheDelivery(route: String, messageId: String, state: DeliveryState) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank() || messageId.isBlank()) return
        synchronized(conversationHistoryLock) {
            val cache = conversationHistoryCache[clean] ?: return
            val index = cache.indexOfFirst { it.id == messageId && it.isMine }
            if (index < 0) return
            val current = cache[index]
            if (current.delivery == state) return
            cache[index] = current.copy(delivery = state)
        }
    }

    private fun clearConversationCache(route: String) {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return
        synchronized(conversationHistoryLock) {
            conversationHistoryCache.remove(clean)
        }
    }

    private fun conversationMessageExists(route: String, message: Message): Boolean {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return false
        return synchronized(conversationHistoryLock) {
            conversationHistoryCache[clean]
                ?.any { it.id == message.id } == true
        }
    }

    private fun mergeMessage(existing: Message, incoming: Message): Message {
        val timestamp = when {
            existing.timestamp > 0L && incoming.timestamp > 0L -> minOf(existing.timestamp, incoming.timestamp)
            existing.timestamp > 0L -> existing.timestamp
            else -> incoming.timestamp
        }
        val delivery = if (incoming.delivery.ordinal > existing.delivery.ordinal) {
            incoming.delivery
        } else {
            existing.delivery
        }
        val text = when {
            existing.text.isNotBlank() -> existing.text
            else -> incoming.text
        }
        return existing.copy(
            text = text,
            isMine = existing.isMine || incoming.isMine,
            timestamp = timestamp,
            delivery = delivery
        )
    }

    private fun isLocalOwnerRoute(route: String): Boolean {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return false
        return localOwnerRouteCandidates().any { it == clean }
    }

    private fun localOwnerRouteCandidates(): List<String> {
        return listOf(
            myUsername,
            nodeManager.currentPublicRoute(),
            profilePrefs.getString(LAST_PUBLIC_ROUTE_KEY, null).orEmpty()
        )
            .map { canonicalConversationKey(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun lastKnownOwnRoute(): String {
        return profilePrefs
            .getString(LAST_PUBLIC_ROUTE_KEY, null)
            .orEmpty()
            .let { canonicalConversationKey(it) }
            .takeIf { it.isNotBlank() && nodeManager.isValidRoute(it) }
            .orEmpty()
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

    private fun contactAcceptedKey(route: String): String = "accepted:${routeStorageKey(route)}"

    private fun contactAcceptedNoticeKey(route: String): String = "$CONTACT_ACCEPT_NOTICE_PREFIX:${routeStorageKey(route)}"

    private fun contactRequestedKey(route: String): String = "requested:${routeStorageKey(route)}"

    private fun contactPendingKey(route: String): String = "pending:${routeStorageKey(route)}"

    private fun contactBlockedKey(route: String): String = "blocked:${routeStorageKey(route)}"

    private fun contactRemovedKey(route: String): String = "removed:${routeStorageKey(route)}"

    private fun contactRouteKey(route: String): String = "route:${routeStorageKey(route)}"

    private fun contactRoutesWithPrefix(prefix: String): List<String> {
        return contactsPrefs.all.keys
            .filter { it.startsWith(prefix) }
            .mapNotNull { key ->
                val storageKey = key.removePrefix(prefix)
                routeForStorageKey(storageKey)
            }
            .distinct()
    }

    private fun routeForStorageKey(storageKey: String): String? {
        contactsPrefs.getString("route:$storageKey", null)
            ?.let { stored -> canonicalConversationKey(stored).takeIf { it.isNotBlank() } }
            ?.let { return it }
        val candidates = buildList {
            add(canonicalConversationKey(myUsername))
            add(canonicalConversationKey(currentPublicRoute()))
            add(canonicalConversationKey(lastKnownOwnRoute()))
            addAll(peers.map { canonicalConversationKey(it) })
            addAll(startedConversations.map { canonicalConversationKey(it) })
            addAll(chatStore.knownPeers().map { canonicalConversationKey(it) })
            routeTokensPrefs.all.values.forEach { value ->
                add(canonicalConversationKey(value?.toString().orEmpty()))
            }
        }.filter { it.isNotBlank() }.distinct()
        return candidates.firstOrNull { routeStorageKey(it) == storageKey }
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
        return "$host:$port"
    }

    private fun routeTokenStringForDisplay(route: String): String {
        val clean = canonicalConversationKey(route)
        if (clean.isBlank()) return ""
        return routeTokenString(clean)
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
            add(canonicalConversationKey(currentPublicRoute()))
            add(canonicalConversationKey(lastKnownOwnRoute()))
            addAll(peers.map { canonicalConversationKey(it) })
            addAll(startedConversations.map { canonicalConversationKey(it) })
            addAll(chatStore.knownPeers().map { canonicalConversationKey(it) })
        }.filter { it.isNotBlank() }.distinct()

        val match = candidates.firstOrNull {
            routeTokenString(it).equals(clean, ignoreCase = true) ||
                routeTokenStringForDisplay(it).equals(clean, ignoreCase = true)
        }
        if (match != null) {
            routeTokensPrefs.edit().putString(routeTokenStringForDisplay(match).lowercase(), match).apply()
        }
        return match.orEmpty()
    }

    private fun compactRouteTokenToOnion(token: String): String? {
        val clean = token.trim()
        val separator = clean.lastIndexOf(':').takeIf { it > 0 && it < clean.lastIndex }
            ?: clean.lastIndexOf('#')
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
        cleanupChatJob?.cancel()
        cleanupChatJob = null
        if (inChat || targetUsername.isNotBlank()) return
        if (seenButNotClearedByPeer[cleanRoute] != true) return
        if (hasProtectedUnreadMessages(cleanRoute)) return
        if (currentKeepViewedMessages) return

        val keepIncomingSince = System.currentTimeMillis() - 1_000L
        val remainingIncoming = conversationMessagesFor(cleanRoute)
            .count { !it.isMine && it.timestamp >= keepIncomingSince }

        nodeManager.clearViewedMessages(cleanRoute, keepIncomingSince)
        clearConversationCache(cleanRoute)
        clearPrivacyNotices(cleanRoute)
        unreadByPeer[cleanRoute] = remainingIncoming
        if (remainingIncoming <= 0) {
            seenButNotClearedByPeer.remove(cleanRoute)
        }
        conversationsVersion++
        refreshConversationPreviewsAsync()
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
                    clearConversationCache(target)
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
