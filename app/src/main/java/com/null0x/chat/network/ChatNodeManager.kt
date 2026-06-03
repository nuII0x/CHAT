package com.null0x.chat.network

import android.content.Context
import android.util.Base64
import com.null0x.chat.AppVisibility
import com.null0x.chat.model.DeliveryState
import com.null0x.chat.model.Message
import com.null0x.chat.notification.MessageNotifier
import com.null0x.chat.storage.ChatStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArraySet

object ChatNodeManager {

    data class PublicRouteProfile(
        val route: String,
        val displayName: String,
        val emoji: String,
        val bio: String,
        val keepViewedMessages: Boolean,
        val allowScreenshots: Boolean,
        val updatedAt: Long
    )

    const val CLEAR_HISTORY_COMMAND = "CHAT_CMD|CLEAR_HISTORY"
    private const val APP_CHAT_PORT = 5000
    private const val LEGACY_TRUNCATED_CHAT_PORT = 50
    private const val MAX_MESSAGE_ID_CHARS = 128
    private const val CHAT_MSG_PREFIX = "CHAT_MSG|"
    private const val CHAT_ACK_PREFIX = "CHAT_ACK|"
    private const val CHAT_PROFILE_REQUEST_PREFIX = "CHAT_PROFILE_REQUEST|"
    private const val CHAT_PROFILE_PREFIX = "CHAT_PROFILE|"
    private const val CHAT_PRESENCE_PREFIX = "CHAT_PRESENCE|"
    private const val ROUTE_PROFILES_PREFS = "route_public_profiles"
    private val ONION_HOST_REGEX = Regex("^[a-z2-7]{56}\\.onion$")

    interface Listener {
        fun onUsernameReady(username: String)
        fun onMessage(fromUsername: String, text: String, messageId: String? = null)
        fun onDeliveryAck(fromUsername: String, messageId: String) {}
        fun onOutgoingDeliveryStateChanged(toUsername: String, messageId: String, state: DeliveryState) {}
        fun onChatPresence(fromUsername: String, state: String) {}
        fun onPeersChanged(peers: List<String>)
        fun onProfileNameChanged(name: String) {}
        fun onProfileEmojiChanged(emoji: String) {}
        fun onPublicProfileChanged(route: String) {}
        fun onRouteChanged() {}
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val node = P2PNode()
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val startLock = Any()

    @Volatile
    private var started = false

    @Volatile
    private var notifier: MessageNotifier? = null

    @Volatile
    private var chatStore: ChatStore? = null
    private var profilePrefs: android.content.SharedPreferences? = null
    private var routeProfilesPrefs: android.content.SharedPreferences? = null

    private var retryJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var publicRoute: String = ""

    @Volatile
    var username: String = "Aguardando rede..."
        private set

    @Volatile
    var peers: List<String> = emptyList()
        private set

    @Volatile
    var profileName: String = "RotaSegura"
        private set
    @Volatile
    var profileEmoji: String = "🙂"
        private set
    @Volatile
    var profileBio: String = ""
        private set
    @Volatile
    var profileKeepViewedMessages: Boolean = true
        private set
    @Volatile
    var profileAllowScreenshots: Boolean = false
        private set

    fun start(context: Context, autoStartTor: Boolean = false) {
        synchronized(startLock) {
            if (started) return
            started = true
            val appContext = context.applicationContext
            this.appContext = appContext
            notifier = MessageNotifier(appContext)
            chatStore = ChatStore(appContext)
            profilePrefs = appContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
            routeProfilesPrefs = appContext.getSharedPreferences(ROUTE_PROFILES_PREFS, Context.MODE_PRIVATE)
            profileName = profilePrefs
                ?.getString("display_name", null)
                ?.takeIf { it.isNotBlank() }
                ?: "RotaSegura"
            profileEmoji = profilePrefs
                ?.getString("profile_emoji", null)
                ?.takeIf { it.isNotBlank() }
                ?: "🙂"
            profileBio = profilePrefs
                ?.getString("profile_bio", null)
                .orEmpty()
            scope.launch {
                TorManager.status.collectLatest { status ->
                    val torReady = status is TorManager.Status.Ready
                    node.setTransportViaSocks(
                        enabled = torReady,
                        host = TorManager.socksHost(),
                        port = TorManager.socksPort()
                    )
                    updatePublicRoute()
                    if (torReady) {
                        retryPendingMessagesOnce()
                    } else if (autoStartTor && (status is TorManager.Status.Idle || status is TorManager.Status.Error)) {
                        startTor(appContext)
                    }
                }
            }
            startRetryLoop()

            node.start(
                scope = scope,
                onUsernameReady = { user ->
                    username = user
                    updatePublicRoute()
                    listeners.forEach { it.onUsernameReady(user) }
                },
                onMessage = onMessage@{ from, text ->
                    val peer = canonicalPeer(from)
                    migratePeerIfNeeded(from, peer)

                    if (text.startsWith(CHAT_PROFILE_REQUEST_PREFIX)) {
                        scope.launch { publishLocalProfileTo(peer) }
                        return@onMessage
                    }

                    if (text.startsWith(CHAT_PROFILE_PREFIX)) {
                        importPublicProfile(peer, text.removePrefix(CHAT_PROFILE_PREFIX).trim())
                        return@onMessage
                    }

                    if (text.startsWith(CHAT_PRESENCE_PREFIX)) {
                        val state = text.removePrefix(CHAT_PRESENCE_PREFIX).trim()
                        if (state in setOf("open", "closed", "typing", "idle")) {
                            listeners.forEach { it.onChatPresence(peer, state) }
                        }
                        return@onMessage
                    }

                    if (text.startsWith(CHAT_ACK_PREFIX)) {
                        val ackId = text.removePrefix(CHAT_ACK_PREFIX).trim()
                        if (ackId.isNotBlank() && ackId.length <= MAX_MESSAGE_ID_CHARS) {
                            chatStore?.updateDeliveryStatus(peer, ackId, DeliveryState.Delivered)
                            listeners.forEach { it.onDeliveryAck(peer, ackId) }
                        }
                        return@onMessage
                    }

                    val parsed = parseChatMessage(text)
                    val rawText = parsed?.text ?: text
                    val incomingMessageId = parsed?.id
                    val normalizedFrom = normalizeRoute(from)
                    val messagePeer = normalizedFrom ?: peer
                    val normalizedPublicRoute = normalizeRoute(publicRoute)
                    val isSelfEcho = incomingMessageId != null &&
                        normalizedFrom != null &&
                        normalizedFrom == normalizedPublicRoute &&
                        chatStore?.hasMessage(messagePeer, incomingMessageId, isMine = true) == true

                    if (rawText == CLEAR_HISTORY_COMMAND) {
                        return@onMessage
                    } else {
                        if (isSelfEcho) {
                            chatStore?.updateDeliveryStatus(messagePeer, incomingMessageId, DeliveryState.Delivered)
                            listeners.forEach { it.onDeliveryAck(messagePeer, incomingMessageId) }
                        }
                        val isDuplicate = incomingMessageId?.let { id ->
                            chatStore?.hasMessage(messagePeer, id, isMine = false) == true
                        } == true
                        if (isDuplicate) {
                            incomingMessageId?.let { id ->
                                scope.launch { node.sendMessage(messagePeer, "$CHAT_ACK_PREFIX$id") }
                            }
                            return@onMessage
                        }
                        requestPublicProfile(messagePeer)
                        val name = displayNameFor(messagePeer)
                        chatStore?.append(
                            messagePeer,
                            Message(
                                id = incomingMessageId ?: java.util.UUID.randomUUID().toString(),
                                text = rawText,
                                isMine = false,
                                delivery = DeliveryState.Delivered
                            )
                        )
                        if (!AppVisibility.isVisible) {
                            notifier?.showMessage(messagePeer, name, rawText)
                        }
                        incomingMessageId?.let { id ->
                            scope.launch { node.sendMessage(messagePeer, "$CHAT_ACK_PREFIX$id") }
                        }
                    }
                    listeners.forEach { it.onMessage(messagePeer, rawText, incomingMessageId) }
                },
                onPeersChanged = { users, _ ->
                    peers = users
                    listeners.forEach { it.onPeersChanged(users) }
                }
            )
        }
    }

    fun setProfileName(context: Context, name: String) {
        val cleanName = name.trim().ifBlank { "RotaSegura" }
        profileName = cleanName
        val prefs = profilePrefs ?: context.applicationContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
        profilePrefs = prefs
        prefs.edit()
            .putString("display_name", cleanName)
            .apply()
        listeners.forEach { it.onProfileNameChanged(cleanName) }
        publishLocalProfile()
    }

    fun setProfileEmoji(context: Context, emoji: String) {
        val cleanEmoji = emoji.trim().ifBlank { "🙂" }
        profileEmoji = cleanEmoji
        val prefs = profilePrefs ?: context.applicationContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
        profilePrefs = prefs
        prefs.edit()
            .putString("profile_emoji", cleanEmoji)
            .apply()
        listeners.forEach { it.onProfileEmojiChanged(cleanEmoji) }
        publishLocalProfile()
    }

    fun setProfileBio(context: Context, bio: String) {
        val cleanBio = limitUtf8Bytes(bio.trimEnd(), 4 * 1024)
        profileBio = cleanBio
        val prefs = profilePrefs ?: context.applicationContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
        profilePrefs = prefs
        prefs.edit()
            .putString("profile_bio", cleanBio)
            .apply()
        publishLocalProfile()
    }

    fun setProfilePolicy(keepViewedMessages: Boolean, allowScreenshots: Boolean) {
        profileKeepViewedMessages = keepViewedMessages
        profileAllowScreenshots = allowScreenshots
        publishLocalProfile()
    }

    fun startTor(context: Context) {
        val appContext = context.applicationContext
        TorManager.configureOnionService(appContext, APP_CHAT_PORT)
        TorManager.ensureStarted(appContext)
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
        listener.onUsernameReady(username)
        listener.onPeersChanged(peers)
        listener.onProfileNameChanged(profileName)
        listener.onProfileEmojiChanged(profileEmoji)
        listener.onRouteChanged()
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    suspend fun sendMessage(toUsername: String, text: String, messageId: String? = null): Result<Unit> {
        val peer = normalizeRoute(toUsername) ?: toUsername.trim()
        val id = messageId ?: java.util.UUID.randomUUID().toString()
        if (peer.isBlank()) {
            return Result.failure(IllegalArgumentException("Rota onion inválida"))
        }
        migratePeerIfNeeded(toUsername, peer)
        publishLocalProfileTo(peer)
        val existing = chatStore?.load(peer)?.firstOrNull { it.isMine && it.id == id }
        val pendingMessage = Message(
            id = id,
            text = text,
            isMine = true,
            timestamp = existing?.timestamp ?: System.currentTimeMillis(),
            delivery = DeliveryState.Pending
        )
        chatStore?.upsert(peer, pendingMessage)

        return trySendStoredMessage(peer, id, text)
    }

    fun loadMessages(peer: String): List<Message> {
        val cleanPeer = canonicalPeer(peer)
        migratePeerIfNeeded(peer, cleanPeer)
        return chatStore?.load(cleanPeer) ?: emptyList()
    }

    fun clearMessages(peer: String) {
        chatStore?.clear(canonicalPeer(peer))
    }

    fun clearViewedMessages(peer: String) {
        chatStore?.clearViewedMessages(canonicalPeer(peer))
    }

    fun removeConversation(peer: String) {
        chatStore?.remove(canonicalPeer(peer))
    }

    fun knownPeers(): List<String> {
        val store = chatStore ?: return emptyList()
        return store.knownPeers()
            .map { peer ->
                val canonical = canonicalPeer(peer)
                migratePeerIfNeeded(peer, canonical)
                canonical
            }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun displayNameFor(username: String): String {
        return publicProfileFor(username)?.displayName?.takeIf { it.isNotBlank() } ?: username
    }

    fun publicProfileFor(route: String): PublicRouteProfile? {
        val cleanRoute = canonicalPeer(route)
        if (cleanRoute.isBlank()) return null
        if (cleanRoute == canonicalPeer(publicRoute) || cleanRoute == canonicalPeer(username)) {
            return PublicRouteProfile(
                route = cleanRoute,
                displayName = profileName,
                emoji = profileEmoji,
                bio = profileBio,
                keepViewedMessages = profileKeepViewedMessages,
                allowScreenshots = profileAllowScreenshots,
                updatedAt = System.currentTimeMillis()
            )
        }
        val prefs = routeProfilesPrefs ?: return null
        val storageKey = routeStorageKey(cleanRoute)
        val raw = prefs.getString(storageKey, null)
            ?: prefs.getString(cleanRoute, null)?.also { legacy ->
                prefs.edit().putString(storageKey, legacy).remove(cleanRoute).apply()
            }
            ?: return null
        return runCatching {
            val json = JSONObject(raw)
            PublicRouteProfile(
                route = cleanRoute,
                displayName = json.optString("displayName").trim(),
                emoji = json.optString("emoji").trim(),
                bio = json.optString("bio").trim(),
                keepViewedMessages = json.optBoolean("keepViewedMessages", true),
                allowScreenshots = json.optBoolean("allowScreenshots", true),
                updatedAt = json.optLong("updatedAt", 0L)
            )
        }.getOrNull()
    }

    fun requestPublicProfile(route: String) {
        val peer = normalizeRoute(route) ?: return
        scope.launch {
            node.sendMessage(peer, "$CHAT_PROFILE_REQUEST_PREFIX${java.util.UUID.randomUUID()}")
        }
    }

    fun sendChatPresence(route: String, state: String) {
        val peer = normalizeRoute(route) ?: return
        if (state !in setOf("open", "closed", "typing", "idle")) return
        scope.launch {
            node.sendMessage(peer, "$CHAT_PRESENCE_PREFIX$state")
        }
    }

    fun currentPublicRoute(): String {
        updatePublicRoute()
        return publicRoute
    }

    fun canonicalRoute(route: String): String? = normalizeRoute(route)

    fun isValidRoute(route: String): Boolean {
        return normalizeRoute(route) != null
    }

    private fun updatePublicRoute() {
        val route = TorManager.onionRoute(APP_CHAT_PORT)
        val resolvedRoute = route.ifBlank { publicRoute }
        val changed = resolvedRoute != publicRoute
        if (route.isNotBlank()) {
            publicRoute = route
            node.setPublicRoute(route)
        }
        if (changed) {
            username = resolvedRoute.ifBlank { "Aguardando rede..." }
            listeners.forEach { listener ->
                listener.onUsernameReady(username)
                listener.onRouteChanged()
            }
        }
    }

    private fun startRetryLoop() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            while (isActive) {
                retryPendingMessagesOnce()
                delay(8_000)
            }
        }
    }

    private suspend fun retryPendingMessagesOnce() {
        val store = chatStore ?: return
        val pending = store.pendingOutgoing(limit = 25)
        if (pending.isEmpty()) return
        pending.forEach { (peer, message) ->
            if (message.text == CLEAR_HISTORY_COMMAND || message.delivery == DeliveryState.Delivered) {
                return@forEach
            }
            trySendStoredMessage(peer, message.id, message.text)
        }
    }

    private suspend fun trySendStoredMessage(peer: String, messageId: String, text: String): Result<Unit> {
        val store = chatStore
        val cleanPeer = normalizeRoute(peer)
        if (cleanPeer == null) {
            store?.updateDeliveryStatus(peer, messageId, DeliveryState.Failed)
            listeners.forEach { it.onOutgoingDeliveryStateChanged(peer, messageId, DeliveryState.Failed) }
            return Result.failure(IllegalArgumentException("Rota onion inválida"))
        }
        migratePeerIfNeeded(peer, cleanPeer)

        if (!node.isServerReady()) {
            store?.updateDeliveryStatus(cleanPeer, messageId, DeliveryState.Pending)
            listeners.forEach { it.onOutgoingDeliveryStateChanged(cleanPeer, messageId, DeliveryState.Pending) }
            return Result.failure(IllegalStateException("Servidor local ainda nao esta pronto"))
        }

        val wrapped = "$CHAT_MSG_PREFIX$messageId|${encodePayloadText(text)}"
        val result = node.sendMessage(cleanPeer, wrapped)
        val state = if (result.isSuccess) DeliveryState.Sent else DeliveryState.Pending
        store?.updateDeliveryStatus(cleanPeer, messageId, state)
        listeners.forEach { it.onOutgoingDeliveryStateChanged(cleanPeer, messageId, state) }
        return result
    }

    private fun parseChatMessage(raw: String): ParsedIncomingMessage? {
        if (!raw.startsWith(CHAT_MSG_PREFIX)) return null
        val payload = raw.removePrefix(CHAT_MSG_PREFIX)
        val firstSep = payload.indexOf('|')
        if (firstSep <= 0) return null
        val id = payload.substring(0, firstSep).trim()
        val encodedText = payload.substring(firstSep + 1)
        if (id.isBlank() || id.length > MAX_MESSAGE_ID_CHARS) return null
        val text = runCatching {
            String(Base64.decode(encodedText, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return null
        return ParsedIncomingMessage(id = id, text = text)
    }

    private fun encodePayloadText(text: String): String {
        return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun importPublicProfile(fromPeer: String, encodedProfile: String) {
        val route = canonicalPeer(fromPeer)
        if (route.isBlank()) return
        val profile = runCatching {
            val raw = String(Base64.decode(encodedProfile, Base64.DEFAULT), Charsets.UTF_8)
            val json = JSONObject(raw)
            PublicRouteProfile(
                route = normalizeRoute(json.optString("route")) ?: route,
                displayName = json.optString("displayName").trim().take(80),
                emoji = json.optString("emoji").trim().take(16),
                bio = limitUtf8Bytes(json.optString("bio").trim(), 4 * 1024),
                keepViewedMessages = json.optBoolean("keepViewedMessages", true),
                allowScreenshots = json.optBoolean("allowScreenshots", true),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }.getOrNull() ?: return
        if (profile.route != route || profile.displayName.isBlank()) return
        val current = publicProfileFor(route)
        if (current != null && current.updatedAt > profile.updatedAt) return
        val json = JSONObject()
            .put("displayName", profile.displayName)
            .put("emoji", profile.emoji)
            .put("bio", profile.bio)
            .put("keepViewedMessages", profile.keepViewedMessages)
            .put("allowScreenshots", profile.allowScreenshots)
            .put("updatedAt", profile.updatedAt)
        routeProfilesPrefs?.edit()
            ?.putString(routeStorageKey(route), json.toString())
            ?.remove(route)
            ?.apply()
        listeners.forEach { it.onPublicProfileChanged(route) }
    }

    fun publishLocalProfile() {
        scope.launch {
            knownPeers().forEach { peer ->
                publishLocalProfileTo(peer)
            }
        }
    }

    private suspend fun publishLocalProfileTo(route: String): Result<Unit> {
        val peer = normalizeRoute(route) ?: return Result.failure(IllegalArgumentException("Rota onion inválida"))
        if (publicRoute.isBlank() || !node.isServerReady()) {
            return Result.failure(IllegalStateException("Perfil local ainda nao esta pronto"))
        }
        val json = JSONObject()
            .put("route", publicRoute)
            .put("displayName", profileName)
            .put("emoji", profileEmoji)
            .put("bio", profileBio)
            .put("keepViewedMessages", profileKeepViewedMessages)
            .put("allowScreenshots", profileAllowScreenshots)
            .put("updatedAt", System.currentTimeMillis())
        val encoded = Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return node.sendMessage(peer, "$CHAT_PROFILE_PREFIX$encoded")
    }

    private fun normalizeRoute(route: String): String? {
        val clean = route.trim()
        if (!clean.startsWith("onion:", ignoreCase = true)) return null
        val value = clean.substringAfter(':')
        val separator = value.lastIndexOf(':')
        if (separator <= 0 || separator == value.lastIndex) return null
        val host = value.substring(0, separator).lowercase()
        val port = value.substring(separator + 1).toIntOrNull() ?: return null
        if (!ONION_HOST_REGEX.matches(host) || port !in 1..65535) return null
        val normalizedPort = if (port == LEGACY_TRUNCATED_CHAT_PORT) APP_CHAT_PORT else port
        return "onion:$host:$normalizedPort"
    }

    private fun canonicalPeer(peer: String): String {
        val clean = peer.trim()
        return normalizeRoute(clean) ?: clean
    }

    private fun routeStorageKey(route: String): String {
        val clean = canonicalPeer(route)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(clean.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return "route:$digest"
    }

    private fun migratePeerIfNeeded(originalPeer: String, canonicalPeer: String) {
        val original = originalPeer.trim()
        if (original.isBlank() || canonicalPeer.isBlank() || original == canonicalPeer) return
        chatStore?.migratePeer(original, canonicalPeer)
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

    private data class ParsedIncomingMessage(
        val id: String,
        val text: String
    )
}
