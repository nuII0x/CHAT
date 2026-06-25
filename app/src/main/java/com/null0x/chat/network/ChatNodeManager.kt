package com.null0x.chat.network

import android.content.Context
import android.util.Base64
import android.util.Log
import com.null0x.chat.AppVisibility
import com.null0x.chat.model.DeliveryState
import com.null0x.chat.model.Message
import com.null0x.chat.AppBranding
import com.null0x.chat.notification.MessageNotifier
import com.null0x.chat.security.AppSecurityManager
import com.null0x.chat.security.identity.AckManager
import com.null0x.chat.security.identity.DistributedMessageStore
import com.null0x.chat.security.identity.InboxController
import com.null0x.chat.security.identity.MessageEnvelope
import com.null0x.chat.security.identity.MessageSyncWorker
import com.null0x.chat.security.identity.OnionHttpResponse
import com.null0x.chat.security.identity.OnionInboxStore
import com.null0x.chat.security.identity.P2PMessageRouter
import com.null0x.chat.security.identity.PeerDiscoveryManager
import com.null0x.chat.security.identity.PullRequest
import com.null0x.chat.security.identity.PrivateAuthMiddleware
import com.null0x.chat.security.identity.RouteIdentityRegistry
import com.null0x.chat.security.identity.SendRouteController
import com.null0x.chat.security.identity.StoredEnvelopeRecord
import com.null0x.chat.security.identity.FileDistributedMessageStore
import com.null0x.chat.storage.ChatStore
import com.null0x.chat.storage.LocalStoreCipher
import com.null0x.chat.util.normalizeProfileEmojiInput
import com.null0x.chat.util.normalizeProfileNameInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

object ChatNodeManager {

    data class PublicRouteProfile(
        val route: String,
        val displayName: String,
        val emoji: String,
        val bio: String,
        val signingPublicKey: String,
        val exchangePublicKey: String,
        val publicKeyHash: String,
        val keepViewedMessages: Boolean,
        val allowScreenshots: Boolean,
        val latitude: Double?,
        val longitude: Double?,
        val accuracyMeters: Float?,
        val locationUpdatedAt: Long,
        val updatedAt: Long
    )

    const val CLEAR_HISTORY_COMMAND = "CHAT_CMD|CLEAR_HISTORY"
    private const val APP_CHAT_PORT = 5000
    private const val LEGACY_TRUNCATED_CHAT_PORT = 50
    private const val MAX_MESSAGE_ID_CHARS = 128
    private const val CHAT_MSG_PREFIX = "CHAT_MSG|"
    private const val CHAT_ACK_PREFIX = "CHAT_ACK|"
    private const val CHAT_DELETE_PREFIX = "CHAT_DELETE|"
    private const val CHAT_PROFILE_REQUEST_PREFIX = "CHAT_PROFILE_REQUEST|"
    private const val CHAT_PROFILE_PREFIX = "CHAT_PROFILE|"
    private const val CHAT_PRESENCE_PREFIX = "CHAT_PRESENCE|"
    private const val CHAT_PRESENCE_IDLE = "idle"
    private const val CONTACT_CONTROL_PREFIX = "[Null0xChat:contact-"
    private const val ROUTE_PROFILES_PREFS = "route_public_profiles"
    private const val LAST_PUBLIC_ROUTE_KEY = "last_public_route"
    private val ONION_HOST_REGEX = Regex("^[a-z2-7]{56}\\.onion$")

    interface Listener {
        fun onUsernameReady(username: String)
        fun onMessage(fromUsername: String, text: String, messageId: String? = null, timestamp: Long? = null)
        fun onMessageDeleted(fromUsername: String, messageId: String) {}
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
    private val _knownRoutesRefreshing = MutableStateFlow(false)
    val knownRoutesRefreshing: StateFlow<Boolean> = _knownRoutesRefreshing.asStateFlow()

    @Volatile
    private var started = false

    @Volatile
    private var notifier: MessageNotifier? = null

    @Volatile
    private var chatStore: ChatStore? = null
    private var onionInboxStore: OnionInboxStore? = null
    private var distributedMessageStore: DistributedMessageStore? = null
    private var peerDiscoveryManager: PeerDiscoveryManager? = null
    private var messageSyncWorker: MessageSyncWorker? = null
    private var sendRouteController: SendRouteController? = null
    private var inboxController: InboxController? = null
    private var profilePrefs: android.content.SharedPreferences? = null
    private var routeNamesPrefs: android.content.SharedPreferences? = null
    private var routeProfilesPrefs: android.content.SharedPreferences? = null
    @Volatile
    private var chatMessageAuthorization: ((String) -> Boolean)? = null

    private var retryJob: kotlinx.coroutines.Job? = null
    private var torStartJob: kotlinx.coroutines.Job? = null
    private var presenceHeartbeatJob: kotlinx.coroutines.Job? = null
    private var knownRoutesRefreshJob: Job? = null
    private var knownRoutesIndicatorJob: Job? = null
    private var fastRelayPullJob: Job? = null
    @Volatile
    private var lastTransportRecoveryAtMs: Long = 0L
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var appRequestsPaused: Boolean = false

    @Volatile
    private var publicRoute: String = ""

    @Volatile
    var username: String = "Aguardando rede..."
        private set

    @Volatile
    var peers: List<String> = emptyList()
        private set

    @Volatile
    var profileName: String = ""
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
    private var shareLocationWithAll: Boolean = false
    private var locationShareAllowedRoutes: Set<String> = emptySet()
    private var profileLatitude: Double? = null
    private var profileLongitude: Double? = null
    private var profileLocationAccuracyMeters: Float? = null
    private var profileLocationUpdatedAt: Long = 0L

    fun ensureBackgroundNetwork(context: Context, startSyncLoop: Boolean = true): Boolean {
        val appContext = context.applicationContext
        AppSecurityManager.initialize(appContext)
        if (
            AppSecurityManager.currentState() == AppSecurityManager.GateState.Locked ||
            AppSecurityManager.currentState() == AppSecurityManager.GateState.SetupRequired ||
            AppSecurityManager.currentState() == AppSecurityManager.GateState.PrivateAccessRequired
        ) {
            return false
        }
        start(appContext, autoStartTor = true, startSyncLoop = startSyncLoop)
        return true
    }

    fun setChatMessageAuthorization(gate: ((String) -> Boolean)?) {
        chatMessageAuthorization = gate
    }

    fun setAppRequestsPaused(paused: Boolean) {
        appRequestsPaused = paused
    }

    fun start(context: Context, autoStartTor: Boolean = false, startSyncLoop: Boolean = true) {
        val appContext = context.applicationContext
        if (!AppSecurityManager.isUnlocked()) {
            return
        }
        val shouldEnsureTor = autoStartTor
        synchronized(startLock) {
            if (started) {
                this.appContext = appContext
                return@synchronized
            }
            started = true
            this.appContext = appContext
            RouteIdentityRegistry.initialize(appContext)
            FastRelayTransport.initialize(appContext)
            RouteIdentityRegistry.sendTokenManager().ensureToken()
            notifier = MessageNotifier(appContext)
            chatStore = ChatStore(appContext)
            onionInboxStore = OnionInboxStore(appContext)
            distributedMessageStore = FileDistributedMessageStore(
                File(appContext.filesDir, "p2p_store"),
                identityProvider = { RouteIdentityRegistry.identityManager() },
                localRouteProvider = { currentPublicRoute() }
            )
            peerDiscoveryManager = PeerDiscoveryManager(
                localRouteProvider = { currentPublicRoute() },
                knownPeersProvider = { knownPeers() },
                activePeersProvider = { peers }
            )
            sendRouteController = SendRouteController(
                inboxStore = onionInboxStore!!,
                sendTokenManager = RouteIdentityRegistry.sendTokenManager(),
                recipientPublicKey = { RouteIdentityRegistry.identityManager().getExchangePublicKey() }
            )
            inboxController = InboxController(
                inboxStore = onionInboxStore!!,
                sendTokenManager = RouteIdentityRegistry.sendTokenManager(),
                auth = {
                    PrivateAuthMiddleware(RouteIdentityRegistry.identityManager().getPublicKey())
                }
            )
            profilePrefs = appContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
            routeNamesPrefs = appContext.getSharedPreferences("route_names", Context.MODE_PRIVATE)
            routeProfilesPrefs = appContext.getSharedPreferences(ROUTE_PROFILES_PREFS, Context.MODE_PRIVATE)
            profileName = profilePrefs
                ?.getString("display_name", null)
                ?.takeIf { it.isNotBlank() }
                .orEmpty()
            profileEmoji = profilePrefs
                ?.getString("profile_emoji", null)
                ?.takeIf { it.isNotBlank() }
                ?: "🙂"
            profileBio = profilePrefs
                ?.getString("profile_bio", null)
                .orEmpty()
            val savedRoute = profilePrefs
                ?.getString(LAST_PUBLIC_ROUTE_KEY, null)
                ?.let { normalizeRoute(it) }
                .orEmpty()
            if (savedRoute.isNotBlank()) {
                publicRoute = savedRoute
                username = savedRoute
                node.setPublicRoute(savedRoute)
            }
            if (startSyncLoop) {
                startRetryLoop()
                startPresenceHeartbeatLoop()
                startFastRelayPullLoop()
            }
            messageSyncWorker = MessageSyncWorker(
                scope = scope,
                store = distributedMessageStore!!,
                peerDiscoveryManager = peerDiscoveryManager!!,
                sendMessage = { peer, text -> sendTransportText(peer, text) },
                onLocalMessage = { peer, envelope, text ->
                    handleDeliveredEnvelope(peer, envelope, text)
                },
                localRecipientHash = { RouteIdentityRegistry.identityManager().getPublicKeyHash() }
            ).also {
                if (startSyncLoop) {
                    it.start()
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
                    if (P2PMessageRouter.isProtocolMessage(text)) {
                        if (handleP2PProtocol(from, text)) {
                            return@onMessage
                        }
                    }
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

                    if (text.startsWith(CHAT_DELETE_PREFIX)) {
                        val messageId = text.removePrefix(CHAT_DELETE_PREFIX).trim()
                        if (messageId.isNotBlank() && messageId.length <= MAX_MESSAGE_ID_CHARS) {
                            distributedMessageStore?.remove(messageId)
                            chatStore?.removeMessage(peer, messageId)
                            notifier?.cancelMessage(peer)
                            listeners.forEach { it.onMessageDeleted(peer, messageId) }
                        }
                        return@onMessage
                    }

                    val parsed = parseChatMessage(text)
                    val rawText = parsed?.text ?: text
                    val incomingMessageId = parsed?.id
                    val incomingTimestamp = parsed?.timestamp ?: System.currentTimeMillis()
                    val normalizedFrom = normalizeRoute(from)
                    val messagePeer = normalizedFrom ?: peer
                    val normalizedPublicRoute = normalizeRoute(publicRoute)
                    val isSelfEcho = incomingMessageId != null &&
                        normalizedFrom != null &&
                        normalizedFrom == normalizedPublicRoute &&
                        chatStore?.hasMessage(messagePeer, incomingMessageId, isMine = true) == true

                    if (rawText == CLEAR_HISTORY_COMMAND) {
                        return@onMessage
                    }

                    if (isContactControlMessage(rawText)) {
                        incomingMessageId?.let { id ->
                            scope.launch { sendTransportText(messagePeer, "$CHAT_ACK_PREFIX$id") }
                        }
                        listeners.forEach { it.onMessage(messagePeer, rawText, incomingMessageId, incomingTimestamp) }
                        return@onMessage
                    }

                    if (!isChatMessageAuthorized(messagePeer)) {
                        return@onMessage
                    }

                    if (isSelfEcho) {
                        chatStore?.updateDeliveryStatus(messagePeer, incomingMessageId, DeliveryState.Delivered)
                        listeners.forEach { it.onDeliveryAck(messagePeer, incomingMessageId) }
                        return@onMessage
                    }
                    val isDuplicate = incomingMessageId?.let { id ->
                        chatStore?.hasMessage(messagePeer, id) == true
                    } == true
                    if (isDuplicate) {
                        incomingMessageId?.let { id ->
                            scope.launch { sendTransportText(messagePeer, "$CHAT_ACK_PREFIX$id") }
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
                            timestamp = incomingTimestamp,
                            delivery = DeliveryState.Delivered
                        )
                    )
                    if (AppVisibility.isChatOpen(messagePeer)) {
                        notifier?.cancelMessage(messagePeer)
                    } else {
                        notifier?.showMessage(messagePeer, name, rawText)
                    }
                    incomingMessageId?.let { id ->
                        scope.launch { sendTransportText(messagePeer, "$CHAT_ACK_PREFIX$id") }
                    }
                    listeners.forEach { it.onMessage(messagePeer, rawText, incomingMessageId, incomingTimestamp) }
                },
                onPeersChanged = { users, _ ->
                    peers = users
                    listeners.forEach { it.onPeersChanged(users) }
                },
                onHttpRequest = { request ->
                    when {
                        request.method == "POST" && request.path == "/send" -> {
                            sendRouteController?.sendMessage(request)
                                ?: OnionHttpResponse(503, """{"ok":false,"error":"Servico indisponivel"}""")
                        }
                        else -> {
                            inboxController?.handle(request)
                                ?: OnionHttpResponse(503, """{"ok":false,"error":"Servico indisponivel"}""")
                        }
                    }
                }
            )
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
                        refreshKnownRouteProfiles()
                    } else if (
                        autoStartTor &&
                        AppSecurityManager.isUnlocked() &&
                        (status is TorManager.Status.Idle || status is TorManager.Status.Error)
                    ) {
                        startTor(appContext)
                    }
                }
            }
        }
        if (shouldEnsureTor) {
            ensureTorRunning(appContext)
        }
    }

    suspend fun runBackgroundSyncCycle() {
        messageSyncWorker?.runCycle()
    }

    fun stop(context: Context) {
        synchronized(startLock) {
            messageSyncWorker?.stop()
            messageSyncWorker = null
            retryJob?.cancel()
            retryJob = null
            presenceHeartbeatJob?.cancel()
            presenceHeartbeatJob = null
            knownRoutesRefreshJob?.cancel()
            knownRoutesRefreshJob = null
            knownRoutesIndicatorJob?.cancel()
            knownRoutesIndicatorJob = null
            fastRelayPullJob?.cancel()
            fastRelayPullJob = null
            torStartJob?.cancel()
            torStartJob = null
            node.stop()
            TorManager.stop(context.applicationContext)
            started = false
        }
    }

    fun setProfileName(context: Context, name: String) {
        val cleanName = normalizeProfileNameInput(name)
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
        val cleanEmoji = normalizeProfileEmojiInput(emoji).ifBlank { "🙂" }
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

    fun setLocationSharingPolicy(shareWithAll: Boolean, allowedRoutes: Set<String>) {
        shareLocationWithAll = shareWithAll
        locationShareAllowedRoutes = allowedRoutes
            .mapNotNull { normalizeRoute(it) }
            .toSet()
        publishLocalProfile()
    }

    fun setSharedLocation(latitude: Double, longitude: Double, accuracyMeters: Float?, updatedAt: Long) {
        profileLatitude = latitude
        profileLongitude = longitude
        profileLocationAccuracyMeters = accuracyMeters
        profileLocationUpdatedAt = updatedAt
        if (shareLocationWithAll || locationShareAllowedRoutes.isNotEmpty()) {
            publishLocalProfile()
        }
    }

    fun startTor(context: Context) {
        val appContext = context.applicationContext
        if (!node.isServerReady()) {
            if (torStartJob?.isActive == true) return
            torStartJob = scope.launch {
                repeat(40) {
                    if (node.isServerReady()) {
                        startTor(appContext)
                        return@launch
                    }
                    delay(100)
                }
                TorManager.configureOnionService(appContext, APP_CHAT_PORT)
                TorManager.ensureStarted(appContext)
            }
            return
        }
        torStartJob?.cancel()
        TorManager.configureOnionService(appContext, APP_CHAT_PORT)
        TorManager.ensureStarted(appContext)
    }

    private fun ensureTorRunning(context: Context) {
        val status = TorManager.status.value
        if (status is TorManager.Status.Ready || status is TorManager.Status.Starting) {
            return
        }
        startTor(context)
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

    private fun handleDeliveredEnvelope(peer: String, envelope: MessageEnvelope, text: String) {
        val conversationPeer = canonicalPeer(envelope.senderRoute ?: peer)
        if (conversationPeer.isBlank()) return
        if (!isChatMessageAuthorized(conversationPeer)) return
        chatStore?.rememberPeer(conversationPeer, displayNameFor(conversationPeer))
        val alreadyStored = chatStore?.hasMessage(conversationPeer, envelope.messageId) == true
        chatStore?.upsert(
            conversationPeer,
            Message(
                id = envelope.messageId,
                text = text,
                isMine = false,
                timestamp = envelope.timestamp,
                delivery = DeliveryState.Delivered
            )
        )
        distributedMessageStore?.markDelivered(envelope.messageId)
        val ack = AckManager { RouteIdentityRegistry.identityManager() }
            .createAck(envelope.messageId, envelope.recipientPublicKeyHash)
        distributedMessageStore?.acknowledge(ack)
        scope.launch {
            (peerDiscoveryManager?.discoverPeers() ?: knownPeers()).forEach { knownPeer ->
                sendTransportText(knownPeer, P2PMessageRouter.encodeAck(ack))
            }
        }
        if (alreadyStored) return
        if (AppVisibility.isChatOpen(conversationPeer)) {
            notifier?.cancelMessage(conversationPeer)
        } else {
            notifier?.showMessage(conversationPeer, displayNameFor(conversationPeer), text)
        }
        listeners.forEach { it.onMessage(conversationPeer, text, envelope.messageId, envelope.timestamp) }
    }

    private fun handleP2PProtocol(fromPeer: String, rawText: String): Boolean {
        P2PMessageRouter.decodeEnvelope(rawText)?.let { envelope ->
            scope.launch {
                distributedMessageStore?.ingestEnvelope(envelope)
                if (envelope.recipientPublicKeyHash == RouteIdentityRegistry.identityManager().getPublicKeyHash()) {
                    val plain = distributedMessageStore?.decryptForLocal(
                        StoredEnvelopeRecord(envelope = envelope)
                    )
                    if (!plain.isNullOrBlank()) {
                        handleDeliveredEnvelope(fromPeer, envelope, plain)
                    }
                }
            }
            return true
        }

        P2PMessageRouter.decodePullRequest(rawText)?.let { request ->
            scope.launch {
                val localHash = RouteIdentityRegistry.identityManager().getPublicKeyHash()
                if (localHash.isBlank() || request.recipientPublicKeyHash != localHash) return@launch
                val matches = distributedMessageStore?.listForRecipient(localHash, request.limit)
                    .orEmpty()
                    .asSequence()
                    .filter { it.envelope.timestamp >= request.sinceTimestamp }
                    .map { it.envelope }
                    .toList()
                if (matches.isNotEmpty()) {
                    sendTransportText(fromPeer, P2PMessageRouter.encodePullResponse(matches))
                }
            }
            return true
        }

        val pulled = P2PMessageRouter.decodePullResponse(rawText)
        if (pulled.isNotEmpty()) {
            scope.launch {
                pulled.forEach { envelope ->
                    distributedMessageStore?.ingestEnvelope(envelope)
                    if (envelope.recipientPublicKeyHash == RouteIdentityRegistry.identityManager().getPublicKeyHash()) {
                        val plain = distributedMessageStore?.decryptForLocal(
                            StoredEnvelopeRecord(envelope = envelope)
                        )
                        if (!plain.isNullOrBlank()) {
                            handleDeliveredEnvelope(fromPeer, envelope, plain)
                        }
                    }
                }
            }
            return true
        }

        P2PMessageRouter.decodeAck(rawText)?.let { ack ->
            scope.launch {
                distributedMessageStore?.acknowledge(ack)
            }
            return true
        }

        return false
    }

    suspend fun sendMessage(toUsername: String, text: String, messageId: String? = null): Result<Unit> {
        if (appRequestsPaused) {
            return Result.failure(IllegalStateException("App bloqueado"))
        }
        val peer = normalizeRoute(toUsername) ?: toUsername.trim()
        val id = messageId ?: java.util.UUID.randomUUID().toString()
        if (peer.isBlank()) {
            return Result.failure(IllegalArgumentException("Rota onion inválida"))
        }
        migratePeerIfNeeded(toUsername, peer)
        val selfRoute = canonicalPeer(publicRoute)
        val selfUser = canonicalPeer(username)
        val isSelfRoute = peer == selfRoute || peer == selfUser
        if (isSelfRoute) {
            if (!node.isServerReady()) {
                return Result.failure(IllegalStateException("Servidor local ainda nao esta pronto"))
            }
            val wrapped = "$CHAT_MSG_PREFIX$id|${System.currentTimeMillis()}|${encodePayloadText(text)}"
            val result = node.sendMessage(peer, wrapped)
            if (result.isFailure) {
                requestTorRecoveryAfterSendFailure(result.exceptionOrNull())
            }
            return result
        }
        val existing = chatStore?.load(peer)?.firstOrNull { it.isMine && it.id == id }
        val pendingMessage = Message(
            id = id,
            text = text,
            isMine = true,
            timestamp = existing?.timestamp ?: System.currentTimeMillis(),
            delivery = DeliveryState.Pending
        )
        chatStore?.upsert(peer, pendingMessage)

        val publicProfile = publicProfileFor(peer)
        if (
            publicProfile?.exchangePublicKey?.isNotBlank() == true &&
            publicProfile.publicKeyHash.isNotBlank() &&
            RouteIdentityRegistry.identityManager().isUnlocked()
        ) {
            distributedMessageStore?.createOutgoingEnvelope(
                recipientRoute = peer,
                recipientPublicKeyHash = publicProfile.publicKeyHash,
                recipientSigningPublicKey = publicProfile.signingPublicKey,
                recipientExchangePublicKey = publicProfile.exchangePublicKey,
                plaintext = text,
                ttlMs = 86_400_000L,
                messageId = id
            )
            scope.launch {
                messageSyncWorker?.replicatePending()
            }
        }

        return trySendStoredMessage(peer, id, text)
    }

    suspend fun sendChatMessage(toUsername: String, text: String, messageId: String? = null): Result<Unit> {
        val peer = normalizeRoute(toUsername) ?: toUsername.trim()
        if (peer.isBlank()) {
            return Result.failure(IllegalArgumentException("Rota onion inválida"))
        }
        if (!canSendChatMessage(peer)) {
            return Result.failure(IllegalStateException("Rota não autorizada para envio"))
        }
        if (!isChatMessageAuthorized(peer)) {
            return Result.failure(IllegalStateException("Contato ainda não aceitou mensagens"))
        }
        return sendMessage(peer, text, messageId)
    }

    fun loadMessages(peer: String): List<Message> {
        val cleanPeer = canonicalPeer(peer)
        migratePeerIfNeeded(peer, cleanPeer)
        return chatStore?.load(cleanPeer) ?: emptyList()
    }

    fun cancelNotification(peer: String) {
        notifier?.cancelMessage(canonicalPeer(peer))
    }

    fun clearMessages(peer: String) {
        chatStore?.clear(canonicalPeer(peer))
    }

    fun requestMessageDeletion(peer: String, messageId: String) {
        val cleanPeer = normalizeRoute(peer) ?: return
        val cleanMessageId = messageId.trim()
        if (cleanMessageId.isBlank()) return
        distributedMessageStore?.remove(cleanMessageId)
        val selfRoute = canonicalPeer(publicRoute)
        val selfUser = canonicalPeer(username)
        if (cleanPeer == selfRoute || cleanPeer == selfUser) {
            return
        }
        scope.launch {
            sendTransportText(cleanPeer, "$CHAT_DELETE_PREFIX$cleanMessageId")
        }
    }

    fun clearViewedMessages(peer: String, keepIncomingSince: Long = 0L) {
        chatStore?.clearViewedMessages(canonicalPeer(peer), keepIncomingSince)
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
        val route = canonicalPeer(username)
        return localRouteNameFor(route)
            .ifBlank { publicProfileFor(route)?.displayName.orEmpty() }
            .ifBlank { tokenLabelFor(route) }
    }

    private fun localRouteNameFor(route: String): String {
        val clean = canonicalPeer(route)
        if (clean.isBlank()) return ""
        val prefs = routeNamesPrefs ?: return ""
        val storageKey = routeStorageKey(clean)
        val saved = prefs.getString(storageKey, null)?.trim().orEmpty()
        if (saved.isNotBlank()) return saved
        val legacy = prefs.getString(clean, null)?.trim().orEmpty()
        if (legacy.isNotBlank()) {
            prefs.edit().putString(storageKey, legacy).remove(clean).apply()
        }
        return legacy
    }

    private fun tokenLabelFor(route: String): String {
        val clean = canonicalPeer(route)
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

    fun publicProfileFor(route: String): PublicRouteProfile? {
        val cleanRoute = canonicalPeer(route)
        if (cleanRoute.isBlank()) return null
        if (cleanRoute == canonicalPeer(publicRoute) || cleanRoute == canonicalPeer(username)) {
            return PublicRouteProfile(
                route = cleanRoute,
                displayName = profileName,
                emoji = profileEmoji,
                bio = profileBio,
                signingPublicKey = RouteIdentityRegistry.identityManager().getPublicKey(),
                exchangePublicKey = RouteIdentityRegistry.identityManager().getExchangePublicKey(),
                publicKeyHash = RouteIdentityRegistry.identityManager().getPublicKeyHash(),
                keepViewedMessages = profileKeepViewedMessages,
                allowScreenshots = profileAllowScreenshots,
                latitude = if (shareLocationWithAll || locationShareAllowedRoutes.isNotEmpty()) profileLatitude else null,
                longitude = if (shareLocationWithAll || locationShareAllowedRoutes.isNotEmpty()) profileLongitude else null,
                accuracyMeters = if (shareLocationWithAll || locationShareAllowedRoutes.isNotEmpty()) profileLocationAccuracyMeters else null,
                locationUpdatedAt = if (shareLocationWithAll || locationShareAllowedRoutes.isNotEmpty()) profileLocationUpdatedAt else 0L,
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
                signingPublicKey = json.optString("signingPublicKey").trim(),
                exchangePublicKey = json.optString("exchangePublicKey").trim(),
                publicKeyHash = json.optString("publicKeyHash").trim(),
                keepViewedMessages = json.optBoolean("keepViewedMessages", true),
                allowScreenshots = json.optBoolean("allowScreenshots", true),
                latitude = json.optionalDouble("latitude"),
                longitude = json.optionalDouble("longitude"),
                accuracyMeters = json.optionalDouble("accuracyMeters")?.toFloat(),
                locationUpdatedAt = json.optLong("locationUpdatedAt", 0L),
                updatedAt = json.optLong("updatedAt", 0L)
            )
        }.getOrNull()
    }

    fun requestPublicProfile(route: String) {
        val peer = normalizeRoute(route) ?: return
        if (appRequestsPaused) return
        scope.launch {
            markKnownRoutesRefreshing()
            sendTransportText(peer, "$CHAT_PROFILE_REQUEST_PREFIX${java.util.UUID.randomUUID()}")
        }
    }

    private fun refreshKnownRouteProfiles() {
        if (knownRoutesRefreshJob?.isActive == true) return
        if (appRequestsPaused) return
        knownRoutesRefreshJob = scope.launch {
            val peers = knownPeers()
                .mapNotNull { normalizeRoute(it) }
                .filter { it.isNotBlank() && it != publicRoute }
                .distinct()
            if (peers.isEmpty()) return@launch
            _knownRoutesRefreshing.value = true
            try {
                peers.forEach { peer ->
                    sendTransportText(peer, "$CHAT_PROFILE_REQUEST_PREFIX${java.util.UUID.randomUUID()}")
                    delay(120)
                }
                delay(900)
            } finally {
                _knownRoutesRefreshing.value = false
            }
        }
    }

    private fun markKnownRoutesRefreshing() {
        knownRoutesIndicatorJob?.cancel()
        knownRoutesIndicatorJob = scope.launch {
            _knownRoutesRefreshing.value = true
            delay(1_200)
            if (knownRoutesRefreshJob?.isActive != true) {
                _knownRoutesRefreshing.value = false
            }
        }
    }

    fun sendChatPresence(route: String, state: String) {
        val peer = normalizeRoute(route) ?: return
        if (state !in setOf("open", "closed", "typing", "idle")) return
        if (appRequestsPaused) return
        scope.launch {
            sendTransportText(peer, "$CHAT_PRESENCE_PREFIX$state")
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
            profilePrefs?.edit()
                ?.putString(LAST_PUBLIC_ROUTE_KEY, route)
                ?.apply()
        }
        if (changed || (resolvedRoute.isNotBlank() && username != resolvedRoute)) {
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

    private fun startPresenceHeartbeatLoop() {
        if (presenceHeartbeatJob?.isActive == true) return
        presenceHeartbeatJob = scope.launch {
            while (isActive) {
                delay(25_000)
                if (appRequestsPaused) continue
                if (publicRoute.isBlank() || !node.isServerReady()) continue
                knownPeers().forEach { peer ->
                    sendTransportText(peer, "$CHAT_PRESENCE_PREFIX$CHAT_PRESENCE_IDLE")
                }
            }
        }
    }

    private fun startFastRelayPullLoop() {
        if (fastRelayPullJob?.isActive == true) return
        val context = appContext ?: return
        fastRelayPullJob = scope.launch {
            while (isActive) {
                if (appRequestsPaused) {
                    delay(1_000)
                    continue
                }
                val route = currentPublicRoute()
                if (route.isNotBlank()) {
                    FastRelayTransport.pull(context, route)
                        .getOrDefault(emptyList())
                        .forEach { packet ->
                            node.handleEncryptedTransportLine(packet)
                        }
                }
                delay(if (FastRelayTransport.currentConfig(context).active) 1_200 else 5_000)
            }
        }
    }

    private suspend fun retryPendingMessagesOnce() {
        if (appRequestsPaused) return
        val store = chatStore ?: return
        val pending = store.pendingOutgoing(limit = 25)
        if (pending.isEmpty()) return
        pending.groupBy { it.first }.forEach { (peer, items) ->
            for ((_, message) in items) {
                if (message.text == CLEAR_HISTORY_COMMAND || message.delivery == DeliveryState.Delivered) {
                    continue
                }
                if (!isContactControlMessage(message.text) && !isChatMessageAuthorized(peer)) {
                    break
                }
                val result = trySendStoredMessage(peer, message.id, message.text)
                if (result.isFailure) {
                    break
                }
            }
        }
    }

    private fun isChatMessageAuthorized(peer: String): Boolean {
        val cleanPeer = canonicalPeer(peer)
        if (cleanPeer.isBlank()) return false
        return chatMessageAuthorization?.invoke(cleanPeer) ?: true
    }

    private fun isContactControlMessage(text: String): Boolean {
        return text.startsWith(CONTACT_CONTROL_PREFIX)
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
        if (appRequestsPaused) {
            store?.updateDeliveryStatus(cleanPeer, messageId, DeliveryState.Pending)
            listeners.forEach { it.onOutgoingDeliveryStateChanged(cleanPeer, messageId, DeliveryState.Pending) }
            return Result.failure(IllegalStateException("App bloqueado"))
        }

        if (!node.isServerReady()) {
            store?.updateDeliveryStatus(cleanPeer, messageId, DeliveryState.Pending)
            listeners.forEach { it.onOutgoingDeliveryStateChanged(cleanPeer, messageId, DeliveryState.Pending) }
            return Result.failure(IllegalStateException("Servidor local ainda nao esta pronto"))
        }

        val timestamp = store?.load(cleanPeer)
            ?.firstOrNull { it.isMine && it.id == messageId }
            ?.timestamp
            ?: System.currentTimeMillis()
        val wrapped = "$CHAT_MSG_PREFIX$messageId|$timestamp|${encodePayloadText(text)}"
        val result = sendTransportText(cleanPeer, wrapped)
        val state = if (result.isSuccess) DeliveryState.Sent else DeliveryState.Pending
        store?.updateDeliveryStatus(cleanPeer, messageId, state)
        listeners.forEach { it.onOutgoingDeliveryStateChanged(cleanPeer, messageId, state) }
        if (result.isFailure) {
            requestTorRecoveryAfterSendFailure(result.exceptionOrNull())
        }
        return result
    }

    private fun requestTorRecoveryAfterSendFailure(error: Throwable?) {
        val context = appContext ?: return
        val now = System.currentTimeMillis()
        if (now - lastTransportRecoveryAtMs < 60_000L) return
        lastTransportRecoveryAtMs = now
        val reason = error?.message.orEmpty()
        Log.w("${AppBranding.APP_NAME}Transport", "Falha ao enviar via Tor; tentando recuperar transporte: $reason")
        TorManager.recoverAfterTransportFailure(context, reason)
    }

    private suspend fun sendTransportText(peer: String, text: String): Result<Unit> {
        if (appRequestsPaused) {
            return Result.failure(IllegalStateException("App bloqueado"))
        }
        val cleanPeer = normalizeRoute(peer)
            ?: return Result.failure(IllegalArgumentException("Rota onion inválida"))
        val context = appContext
        val fromRoute = publicRoute
        if (context != null && fromRoute.isNotBlank()) {
            val fastResult = FastRelayTransport.send(context, cleanPeer, fromRoute, text)
            if (fastResult.isSuccess) return fastResult
        }
        return node.sendMessage(cleanPeer, text)
    }

    private fun parseChatMessage(raw: String): ParsedIncomingMessage? {
        if (!raw.startsWith(CHAT_MSG_PREFIX)) return null
        val payload = raw.removePrefix(CHAT_MSG_PREFIX)
        val firstSep = payload.indexOf('|')
        if (firstSep <= 0) return null
        val id = payload.substring(0, firstSep).trim()
        val remaining = payload.substring(firstSep + 1)
        if (id.isBlank() || id.length > MAX_MESSAGE_ID_CHARS) return null
        val secondSep = remaining.indexOf('|')
        val timestamp = if (secondSep > 0) {
            remaining.substring(0, secondSep).toLongOrNull()?.takeIf { it > 0L }
        } else {
            null
        }
        val encodedText = if (timestamp != null) {
            remaining.substring(secondSep + 1)
        } else {
            remaining
        }
        val text = runCatching {
            String(Base64.decode(encodedText, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return null
        return ParsedIncomingMessage(id = id, timestamp = timestamp, text = text)
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
                signingPublicKey = json.optString("signingPublicKey").trim(),
                exchangePublicKey = json.optString("exchangePublicKey").trim(),
                publicKeyHash = json.optString("publicKeyHash").trim(),
                keepViewedMessages = json.optBoolean("keepViewedMessages", true),
                allowScreenshots = json.optBoolean("allowScreenshots", true),
                latitude = json.optionalDouble("latitude"),
                longitude = json.optionalDouble("longitude"),
                accuracyMeters = json.optionalDouble("accuracyMeters")?.toFloat(),
                locationUpdatedAt = json.optLong("locationUpdatedAt", 0L),
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
            .put("signingPublicKey", profile.signingPublicKey)
            .put("exchangePublicKey", profile.exchangePublicKey)
            .put("publicKeyHash", profile.publicKeyHash)
            .put("keepViewedMessages", profile.keepViewedMessages)
            .put("allowScreenshots", profile.allowScreenshots)
            .put("updatedAt", profile.updatedAt)
        profile.latitude?.let { json.put("latitude", it) }
        profile.longitude?.let { json.put("longitude", it) }
        profile.accuracyMeters?.let { json.put("accuracyMeters", it) }
        if (profile.locationUpdatedAt > 0L) json.put("locationUpdatedAt", profile.locationUpdatedAt)
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
        val includeLocation = shareLocationWithAll || locationShareAllowedRoutes.contains(canonicalPeer(peer))
        val json = JSONObject()
            .put("route", publicRoute)
            .put("displayName", profileName)
            .put("emoji", profileEmoji)
            .put("bio", profileBio)
            .put("signingPublicKey", RouteIdentityRegistry.identityManager().getPublicKey())
            .put("exchangePublicKey", RouteIdentityRegistry.identityManager().getExchangePublicKey())
            .put("publicKeyHash", RouteIdentityRegistry.identityManager().getPublicKeyHash())
            .put("keepViewedMessages", profileKeepViewedMessages)
            .put("allowScreenshots", profileAllowScreenshots)
            .put("updatedAt", System.currentTimeMillis())
        if (includeLocation && profileLatitude != null && profileLongitude != null) {
            json.put("latitude", profileLatitude)
                .put("longitude", profileLongitude)
                .put("locationUpdatedAt", profileLocationUpdatedAt)
            profileLocationAccuracyMeters?.let { json.put("accuracyMeters", it) }
        }
        val encoded = Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return sendTransportText(peer, "$CHAT_PROFILE_PREFIX$encoded")
    }

    private fun canSendChatMessage(peer: String): Boolean {
        val cleanPeer = canonicalPeer(peer)
        if (cleanPeer.isBlank()) return false
        val selfRoute = canonicalPeer(publicRoute)
        val selfUser = canonicalPeer(username)
        if (cleanPeer == selfRoute || cleanPeer == selfUser) {
            return true
        }
        val profile = publicProfileFor(cleanPeer) ?: return false
        return profile.exchangePublicKey.isNotBlank() &&
            profile.publicKeyHash.isNotBlank() &&
            RouteIdentityRegistry.identityManager().isUnlocked()
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
        if (!LocalStoreCipher.canDecrypt()) return
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

    private fun JSONObject.optionalDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return optDouble(name).takeIf { !it.isNaN() }
    }

    private data class ParsedIncomingMessage(
        val id: String,
        val timestamp: Long?,
        val text: String
    )
}
