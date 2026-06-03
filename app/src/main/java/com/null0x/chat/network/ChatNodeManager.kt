package com.null0x.chat.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.null0x.chat.AppVisibility
import com.null0x.chat.model.DeliveryState
import com.null0x.chat.model.Message
import com.null0x.chat.notification.MessageNotifier
import com.null0x.chat.storage.ChatStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

object ChatNodeManager {

    const val CLEAR_HISTORY_COMMAND = "CHAT_CMD|CLEAR_HISTORY"
    private const val NAME_REGISTRY_PREFIX = "PRIMO_NAME_REGISTRY|"
    private const val CHAT_MSG_PREFIX = "CHAT_MSG|"
    private const val CHAT_ACK_PREFIX = "CHAT_ACK|"

    interface Listener {
        fun onUsernameReady(username: String)
        fun onMessage(fromUsername: String, text: String)
        fun onDeliveryAck(fromUsername: String, messageId: String) {}
        fun onPeersChanged(peers: List<String>)
        fun onProfileNameChanged(name: String) {}
        fun onNameRegistryChanged() {}
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

    @Volatile
    private var nameRegistry: NameRegistry? = null
    private var routesPrefs: SharedPreferences? = null

    @Volatile
    private var publicRoute: String = ""

    @Volatile
    var username: String = "iniciando..."
        private set

    @Volatile
    var peers: List<String> = emptyList()
        private set

    @Volatile
    var peerNames: Map<String, String> = emptyMap()
        private set

    @Volatile
    var profileName: String = "DoveChat"
        private set

    fun start(context: Context) {
        synchronized(startLock) {
            if (started) return
            started = true
            val appContext = context.applicationContext
            notifier = MessageNotifier(appContext)
            chatStore = ChatStore(appContext)
            nameRegistry = NameRegistry(appContext)
            routesPrefs = appContext.getSharedPreferences("routes", Context.MODE_PRIVATE)
            profileName = appContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
                .getString("display_name", null)
                ?.takeIf { it.isNotBlank() }
                ?: "DoveChat"
            node.setDisplayName(profileName)
            scope.launch {
                TorManager.status.collectLatest { status ->
                    val torReady = status is TorManager.Status.Ready
                    node.setTransportViaSocks(
                        enabled = torReady,
                        host = TorManager.socksHost(),
                        port = TorManager.socksPort()
                    )
                    updatePublicRoute()
                }
            }

            node.start(
                scope = scope,
                onUsernameReady = { user ->
                    username = user
                    updatePublicRoute()
                    listeners.forEach { it.onUsernameReady(user) }
                },
                onMessage = onMessage@{ from, text ->
                    if (text.startsWith(NAME_REGISTRY_PREFIX)) {
                        val changed = nameRegistry?.importPayload(text.removePrefix(NAME_REGISTRY_PREFIX)) == true
                        if (changed) {
                            listeners.forEach { it.onNameRegistryChanged() }
                            broadcastNameRegistry()
                        }
                        return@onMessage
                    }

                    if (text.startsWith(CHAT_ACK_PREFIX)) {
                        val ackId = text.removePrefix(CHAT_ACK_PREFIX).trim()
                        if (ackId.isNotBlank()) {
                            chatStore?.updateDeliveryStatus(from, ackId, DeliveryState.Delivered)
                            listeners.forEach { it.onDeliveryAck(from, ackId) }
                        }
                        return@onMessage
                    }

                    val parsed = parseChatMessage(text)
                    val rawText = parsed?.text ?: text
                    val incomingMessageId = parsed?.id

                    if (rawText == CLEAR_HISTORY_COMMAND) {
                        chatStore?.clear(from)
                    } else {
                        val name = displayNameFor(from)
                        chatStore?.append(
                            from,
                            Message(
                                id = incomingMessageId ?: java.util.UUID.randomUUID().toString(),
                                text = "[$name] $rawText",
                                isMine = false,
                                delivery = DeliveryState.Delivered
                            )
                        )
                        if (!AppVisibility.isVisible) {
                            notifier?.showMessage(from, name, rawText)
                        }
                        incomingMessageId?.let { id ->
                            scope.launch { node.sendMessage(from, "$CHAT_ACK_PREFIX$id") }
                        }
                    }
                    listeners.forEach { it.onMessage(from, rawText) }
                },
                onPeersChanged = { users, names ->
                    peers = users
                    peerNames = names
                    listeners.forEach { it.onPeersChanged(users) }
                    syncNameRegistryTo(users)
                }
            )
        }
    }

    fun setProfileName(context: Context, name: String) {
        val cleanName = name.trim().ifBlank { "DoveChat" }
        profileName = cleanName
        context.applicationContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
            .edit()
            .putString("display_name", cleanName)
            .apply()
        node.setDisplayName(cleanName)
        listeners.forEach { it.onProfileNameChanged(cleanName) }
    }

    fun startTor(context: Context) {
        val appContext = context.applicationContext
        TorManager.configureOnionService(appContext, 5000)
        TorManager.ensureStarted(appContext)
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
        listener.onUsernameReady(username)
        listener.onPeersChanged(peers)
        listener.onProfileNameChanged(profileName)
        listener.onNameRegistryChanged()
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    suspend fun sendMessage(toUsername: String, text: String, messageId: String? = null): Result<Unit> {
        val wrapped = messageId?.let { "$CHAT_MSG_PREFIX$it|${encodePayloadText(text)}" } ?: text
        val result = node.sendMessage(toUsername, wrapped)
        if (result.isSuccess) {
            if (text == CLEAR_HISTORY_COMMAND) {
                chatStore?.clear(toUsername)
            } else {
                chatStore?.append(
                    toUsername,
                    Message(
                        id = messageId ?: java.util.UUID.randomUUID().toString(),
                        text = text,
                        isMine = true,
                        delivery = DeliveryState.Sent
                    )
                )
            }
        }
        return result
    }

    fun registerUniqueName(name: String): Result<NameRegistry.Record> {
        updatePublicRoute()
        val route = publicRoute.ifBlank { username }
        val result = nameRegistry?.register(name, route, profileName)
            ?: Result.failure(IllegalStateException("Registro de nomes ainda nao iniciou"))
        if (result.isSuccess) {
            broadcastNameRegistry()
            listeners.forEach { it.onNameRegistryChanged() }
        }
        return result
    }

    fun resolveUniqueName(name: String): NameRegistry.Record? {
        return nameRegistry?.resolve(name)
    }

    fun knownUniqueNames(): List<NameRegistry.Record> {
        return nameRegistry?.allRecords().orEmpty()
    }

    fun loadMessages(peer: String): List<Message> {
        return chatStore?.load(peer) ?: emptyList()
    }

    fun clearMessages(peer: String) {
        chatStore?.clear(peer)
    }

    fun removeConversation(peer: String) {
        chatStore?.remove(peer)
    }

    fun knownPeers(): List<String> {
        return chatStore?.knownPeers() ?: emptyList()
    }

    fun displayNameFor(username: String): String {
        return peerNames[username].takeUnless { it.isNullOrBlank() } ?: username
    }

    fun currentPublicRoute(): String {
        updatePublicRoute()
        return publicRoute.ifBlank { username }
    }

    fun isValidRoute(route: String): Boolean {
        return NameRegistry.isValidRoute(route)
    }

    private fun broadcastNameRegistry() {
        syncNameRegistryTo(peers + knownOnionRoutes())
    }

    private fun syncNameRegistryTo(targets: List<String>) {
        val payload = nameRegistry?.exportPayload()?.takeIf { it != "[]" } ?: return
        val ownRoute = publicRoute
        scope.launch {
            targets.distinct()
                .filter { it.isNotBlank() && it != ownRoute && it != username }
                .forEach { peer ->
                node.sendMessage(peer, "$NAME_REGISTRY_PREFIX$payload")
            }
        }
    }

    private fun knownOnionRoutes(): List<String> {
        return nameRegistry?.allRecords()
            .orEmpty()
            .map { it.username }
            .filter { it.startsWith("onion:", ignoreCase = true) }
    }

    private fun updatePublicRoute() {
        val route = TorManager.onionRoute(5000).ifBlank { username }
        val changed = route != publicRoute
        publicRoute = route
        node.setPublicRoute(route)
        if (changed && route.startsWith("onion:", ignoreCase = true)) {
            republishSavedUniqueName()
        }
    }

    private fun republishSavedUniqueName() {
        val name = routesPrefs?.getString("unique_route_name", null)?.trim().orEmpty()
        if (name.isBlank()) return
        nameRegistry?.register(name, publicRoute, profileName)?.onSuccess {
            broadcastNameRegistry()
            listeners.forEach { listener -> listener.onNameRegistryChanged() }
        }
    }

    private fun parseChatMessage(raw: String): ParsedIncomingMessage? {
        if (!raw.startsWith(CHAT_MSG_PREFIX)) return null
        val payload = raw.removePrefix(CHAT_MSG_PREFIX)
        val firstSep = payload.indexOf('|')
        if (firstSep <= 0) return null
        val id = payload.substring(0, firstSep).trim()
        val encodedText = payload.substring(firstSep + 1)
        if (id.isBlank()) return null
        val text = runCatching {
            String(Base64.decode(encodedText, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return null
        return ParsedIncomingMessage(id = id, text = text)
    }

    private fun encodePayloadText(text: String): String {
        return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private data class ParsedIncomingMessage(
        val id: String,
        val text: String
    )
}
