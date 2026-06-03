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
import com.null0x.chat.network.TorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    enum class SnapState {
        Entered,
        Seen,
        Cleared
    }

    data class ConversationPreview(
        val username: String,
        val displayName: String,
        val lastMessage: String,
        val lastTimestamp: Long,
        val unreadCount: Int,
        val snapState: SnapState
    )

    data class RouteLookup(
        val name: String,
        val username: String,
        val displayName: String,
        val isLocalOwner: Boolean,
        val source: String
    )

    private val nodeManager = ChatNodeManager
    private val profilePrefs = application.applicationContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val routesPrefs = application.applicationContext.getSharedPreferences("routes", Context.MODE_PRIVATE)

    private val listener = object : ChatNodeManager.Listener {
        override fun onUsernameReady(username: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                myUsername = username
            }
        }

        override fun onMessage(fromUsername: String, text: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                if (!startedConversations.contains(fromUsername)) {
                    startedConversations.add(0, fromUsername)
                }
                if (!inChat || fromUsername != targetUsername) {
                    unreadByPeer[fromUsername] = (unreadByPeer[fromUsername] ?: 0) + 1
                }
                if (fromUsername != targetUsername) return@launch

                if (text == ChatNodeManager.CLEAR_HISTORY_COMMAND) {
                    messages.clear()
                } else {
                    messages.add(
                        Message(
                            text = "[${displayNameFor(fromUsername)}] $text",
                            isMine = false,
                            delivery = DeliveryState.Delivered
                        )
                    )
                }
            }
        }

        override fun onDeliveryAck(fromUsername: String, messageId: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                if (fromUsername == targetUsername) {
                    updateMessageDelivery(messageId, DeliveryState.Delivered)
                }
            }
        }

        override fun onPeersChanged(peersList: List<String>) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                peers.clear()
                peers.addAll(peersList)
                refreshRouteLookup()
            }
        }

        override fun onProfileNameChanged(name: String) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                profileName = name
            }
        }

        override fun onNameRegistryChanged() {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                refreshRouteLookup()
            }
        }
    }

    val messages = mutableStateListOf<Message>()
    val peers = mutableStateListOf<String>()
    val startedConversations = mutableStateListOf<String>()
    private val unreadByPeer = mutableStateMapOf<String, Int>()
    private val seenButNotClearedByPeer = mutableStateMapOf<String, Boolean>()
    private var conversationsVersion by mutableStateOf(0)
    private var keepViewedMessages by mutableStateOf(true)

    var myUsername by mutableStateOf("iniciando...")
        private set

    var targetUsername by mutableStateOf("")
        private set
    var inChat by mutableStateOf(false)
        private set

    var profileName by mutableStateOf("DoveChat")
        private set

    var needsProfileSetup by mutableStateOf(false)
        private set
    var uniqueRouteName by mutableStateOf("")
        private set
    var routeLookup by mutableStateOf<RouteLookup?>(null)
        private set
    val routeSuggestions = mutableStateListOf<RouteLookup>()
    var routeStatus by mutableStateOf("")
        private set

    init {
        needsProfileSetup = profilePrefs.getString("display_name", null).isNullOrBlank()
        uniqueRouteName = routesPrefs.getString("unique_route_name", "") ?: ""
        keepViewedMessages = profilePrefs.getBoolean("keep_viewed_messages", true)
        nodeManager.start(application.applicationContext)
        nodeManager.addListener(listener)
        startedConversations.addAll(nodeManager.knownPeers())
    }

    fun selectTarget(username: String) {
        targetUsername = username.trim()
        messages.clear()
        if (targetUsername.isNotBlank()) {
            if (!startedConversations.contains(targetUsername)) {
                startedConversations.add(0, targetUsername)
            }
            unreadByPeer[targetUsername] = 0
            seenButNotClearedByPeer[targetUsername] = true
            messages.addAll(nodeManager.loadMessages(targetUsername))
            inChat = true
            conversationsVersion++
        }
    }

    fun openHome() {
        val previous = targetUsername
        inChat = false
        targetUsername = ""
        messages.clear()
        if (previous.isNotBlank() && !keepViewedMessages && seenButNotClearedByPeer[previous] == true) {
            nodeManager.clearMessages(previous)
            unreadByPeer[previous] = 0
            seenButNotClearedByPeer.remove(previous)
            conversationsVersion++
        }
    }

    fun updateKeepViewedMessagesPreference(enabled: Boolean) {
        keepViewedMessages = enabled
        profilePrefs.edit().putBoolean("keep_viewed_messages", enabled).apply()
    }

    fun isKeepViewedMessagesEnabled(): Boolean = keepViewedMessages

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

    fun updateUniqueRouteName(name: String) {
        val clean = name.trim()
        uniqueRouteName = clean
        routesPrefs.edit().putString("unique_route_name", clean).apply()
        refreshRouteLookup()
    }

    fun registerUniqueRouteName() {
        val clean = uniqueRouteName.trim()
        if (clean.isBlank()) {
            routeStatus = "Digite um nome unico"
            return
        }
        nodeManager.registerUniqueName(clean).onSuccess { record ->
            routesPrefs.edit().putString("unique_route_name", record.normalizedName).apply()
            uniqueRouteName = record.normalizedName
            routeStatus = "Nome registrado nesta rede"
            refreshRouteLookup()
        }.onFailure { error ->
            routeStatus = error.message ?: "Falha ao registrar nome"
        }
    }

    fun searchUniqueRouteName() {
        refreshRouteLookup(showNotFound = true)
    }

    fun displayNameFor(username: String): String {
        return nodeManager.displayNameFor(username)
    }

    fun chatTitleFor(username: String): String {
        val display = displayNameFor(username)
        if (display != username) return display
        return compactOnionLabel(username)
    }

    fun currentPublicRoute(): String {
        return nodeManager.currentPublicRoute()
    }

    fun startTor() {
        nodeManager.startTor(getApplication())
    }

    fun stopTor() {
        TorManager.stop(getApplication())
    }

    private fun refreshRouteLookup(showNotFound: Boolean = false) {
        val clean = uniqueRouteName.trim()
        if (clean.isBlank()) {
            routeLookup = null
            routeSuggestions.clear()
            routeStatus = ""
            return
        }
        if (nodeManager.isValidRoute(clean)) {
            val currentRoute = nodeManager.currentPublicRoute()
            routeLookup = RouteLookup(
                name = "rota direta",
                username = clean,
                displayName = if (clean.startsWith("onion:", ignoreCase = true)) "Contato Onion" else "Contato LAN",
                isLocalOwner = clean == currentRoute || clean == myUsername,
                source = if (clean.startsWith("onion:", ignoreCase = true)) "Onion" else "LAN"
            )
            routeSuggestions.clear()
            routeStatus = "Rota pronta para conversar"
            return
        }
        val query = clean.lowercase()
        routeSuggestions.clear()
        routeSuggestions.addAll(
            peers.mapNotNull { peer ->
                val name = displayNameFor(peer)
                val matches = peer.contains(query, ignoreCase = true) ||
                    name.contains(query, ignoreCase = true)
                if (!matches) return@mapNotNull null
                RouteLookup(
                    name = name,
                    username = peer,
                    displayName = name,
                    isLocalOwner = peer == myUsername,
                    source = "LAN"
                )
            }.take(5)
        )
        val record = nodeManager.resolveUniqueName(clean)
        if (record == null) {
            routeLookup = null
            if (showNotFound) {
                routeStatus = if (routeSuggestions.isEmpty()) {
                    "Nome não encontrado na rede"
                } else {
                    "Sugestões encontradas na LAN"
                }
            }
            return
        }
        val currentRoute = nodeManager.currentPublicRoute()
        routeLookup = RouteLookup(
            name = record.normalizedName,
            username = record.username,
            displayName = record.displayName,
            isLocalOwner = record.username == currentRoute || record.username == myUsername,
            source = if (record.username.startsWith("onion:", ignoreCase = true)) "Onion" else "LAN"
        )
        routeStatus = if (record.username == currentRoute || record.username == myUsername) {
            "Este nome aponta para este aparelho"
        } else {
            "Nome encontrado"
        }
    }

    fun conversationPreviews(): List<ConversationPreview> {
        conversationsVersion
        return startedConversations.map { username ->
            val items = nodeManager.loadMessages(username)
            val last = items.lastOrNull()
            ConversationPreview(
                username = username,
                displayName = chatTitleFor(username),
                lastMessage = last?.text?.take(80) ?: "Sem mensagens",
                lastTimestamp = last?.timestamp ?: 0L,
                unreadCount = unreadByPeer[username] ?: 0,
                snapState = when {
                    (unreadByPeer[username] ?: 0) > 0 -> SnapState.Entered
                    seenButNotClearedByPeer[username] == true -> SnapState.Seen
                    else -> SnapState.Cleared
                }
            )
        }.sortedByDescending { it.lastTimestamp }
    }

    fun unreadCountFor(username: String): Int = unreadByPeer[username] ?: 0

    fun clearConversation(username: String) {
        val target = username.trim()
        if (target.isBlank()) return

        nodeManager.clearMessages(target)
        unreadByPeer[target] = 0
        seenButNotClearedByPeer.remove(target)
        conversationsVersion++
        if (target == targetUsername) {
            messages.clear()
        }
    }

    fun removeConversation(username: String) {
        val target = username.trim()
        if (target.isBlank()) return

        nodeManager.removeConversation(target)
        startedConversations.remove(target)
        unreadByPeer.remove(target)
        seenButNotClearedByPeer.remove(target)
        conversationsVersion++
        if (target == targetUsername) {
            openHome()
        }
    }

    fun clearConversationEverywhere(username: String) {
        val target = username.trim()
        if (target.isBlank()) return

        nodeManager.clearMessages(target)
        unreadByPeer[target] = 0
        seenButNotClearedByPeer.remove(target)
        if (target == targetUsername) {
            messages.clear()
        }
        conversationsVersion++
        viewModelScope.launch {
            nodeManager.sendMessage(target, ChatNodeManager.CLEAR_HISTORY_COMMAND).onSuccess {
                withContext(Dispatchers.Main.immediate) {
                    conversationsVersion++
                    if (target == targetUsername) {
                        messages.clear()
                    }
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main.immediate) {
                    messages.add(Message(text = "Falha: ${error.message}", isMine = false))
                }
            }
        }
    }

    fun send(text: String) {
        val message = text.trim()
        val target = targetUsername.trim()

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

        viewModelScope.launch {
            nodeManager.sendMessage(target, message, localId).onSuccess {
                withContext(Dispatchers.Main.immediate) {
                    updateMessageDelivery(localId, DeliveryState.Sent)
                    conversationsVersion++
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main.immediate) {
                    updateMessageDelivery(localId, DeliveryState.Failed)
                    messages.add(Message(text = "Falha: ${error.message}", isMine = false))
                }
            }
        }
    }

    private fun updateMessageDelivery(messageId: String, state: DeliveryState) {
        val index = messages.indexOfFirst { it.id == messageId && it.isMine }
        if (index < 0) return
        val current = messages[index]
        if (current.delivery == state) return
        messages[index] = current.copy(delivery = state)
    }

    private fun compactOnionLabel(value: String): String {
        if (!value.startsWith("onion:", ignoreCase = true)) return value
        val raw = value.removePrefix("onion:")
        val host = raw.substringBeforeLast(':', missingDelimiterValue = raw)
            .removeSuffix(".onion")
            .trim()
        if (host.isBlank()) return "onion"
        return if (host.length <= 10) host else host.take(10) + "..."
    }

    private fun handleCommand(commandLine: String, target: String) {
        when (commandLine.trim()) {
            "/clear" -> {
                if (target.isNotBlank()) {
                    nodeManager.clearMessages(target)
                }
                messages.clear()
            }
            "/clear-all" -> {
                if (target.isBlank()) return
                messages.clear()
                nodeManager.clearMessages(target)
                viewModelScope.launch {
                    nodeManager.sendMessage(target, ChatNodeManager.CLEAR_HISTORY_COMMAND).onFailure { error ->
                        withContext(Dispatchers.Main.immediate) {
                            messages.add(Message(text = "Falha: ${error.message}", isMine = false))
                        }
                    }
                }
            }
            "/help" -> {
                messages.add(
                    Message(
                        text = "Comandos: /clear limpa este chat, /clear-all limpa este chat nos dois lados",
                        isMine = false
                    )
                )
            }
            else -> {
                messages.add(Message(text = "Comando desconhecido: $commandLine", isMine = false))
            }
        }
    }

    override fun onCleared() {
        nodeManager.removeListener(listener)
        super.onCleared()
    }
}
