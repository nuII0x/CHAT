package com.null0x.chat.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.null0x.chat.network.TorManager
import com.null0x.chat.viewmodel.ChatViewModel
import com.null0x.chat.viewmodel.ChatViewModel.SnapState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private enum class HomeTab(val title: String, val icon: ImageVector) {
    Chats("Chats", Icons.Filled.Chat),
    Contacts("Contatos", Icons.Filled.Contacts),
    Profile("Perfil", Icons.Filled.Person),
    Settings("Configurações", Icons.Filled.Settings)
}

@Composable
fun HomeScreen(vm: ChatViewModel, onOpenChat: (String) -> Unit) {
    val torStatus by TorManager.status.collectAsState()
    val publicRoute = vm.currentPublicRoute()
    var tab by rememberSaveable { mutableStateOf(HomeTab.Chats) }
    var query by rememberSaveable { mutableStateOf("") }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    val conversations = vm.conversationPreviews()
    val filteredConversations = conversations.filter {
        val clean = query.trim()
        clean.isBlank() ||
            it.displayName.contains(clean, ignoreCase = true) ||
            it.username.contains(clean, ignoreCase = true) ||
            it.lastMessage.contains(clean, ignoreCase = true)
    }

    LaunchedEffect(torStatus) {
        if (torStatus is TorManager.Status.Idle || torStatus is TorManager.Status.Error) {
            vm.startTor()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppHeader(
            title = tab.title,
            profileName = vm.profileName,
            username = vm.myUsername,
            torStatus = torStatus,
            onTorClick = {
                when (torStatus) {
                    is TorManager.Status.Ready,
                    is TorManager.Status.Starting -> vm.stopTor()
                    else -> vm.startTor()
                }
            },
            onSettings = { tab = HomeTab.Settings }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (tab) {
                HomeTab.Chats -> ChatsTab(
                    query = query,
                    onQueryChange = { query = it },
                    conversations = filteredConversations,
                    conversationsCount = conversations.size,
                    peersCount = vm.peers.size,
                    unreadCountFor = vm::unreadCountFor,
                    myUsername = vm.myUsername,
                    hasSearch = query.isNotBlank(),
                    onSelect = {
                        vm.selectTarget(it)
                        onOpenChat(it)
                    },
                    onClear = vm::clearConversation,
                    onRemove = vm::removeConversation,
                    onClearEverywhere = vm::clearConversationEverywhere
                )
                HomeTab.Contacts -> ContactsTab(
                    routeName = vm.uniqueRouteName,
                    onRouteNameChange = vm::updateUniqueRouteName,
                    routeLookup = vm.routeLookup,
                    routeSuggestions = vm.routeSuggestions,
                    routeStatus = vm.routeStatus,
                    onRegisterRouteName = vm::registerUniqueRouteName,
                    onSearchRouteName = vm::searchUniqueRouteName,
                    peers = vm.peers,
                    displayNameFor = vm::displayNameFor,
                    onSelect = {
                        vm.selectTarget(it)
                        onOpenChat(it)
                    }
                )
                HomeTab.Profile -> ProfileTab(
                    profileName = vm.profileName,
                    username = vm.myUsername,
                    publicRoute = publicRoute,
                    conversationsCount = conversations.size,
                    peersCount = vm.peers.size,
                    torStatus = torStatus
                )
                HomeTab.Settings -> SettingsTab(
                    profileName = vm.profileName,
                    username = vm.myUsername,
                    publicRoute = publicRoute,
                    torStatus = torStatus,
                    keepViewedMessages = vm.isKeepViewedMessagesEnabled(),
                    onKeepViewedMessagesChange = vm::updateKeepViewedMessagesPreference,
                    onEditProfile = { showProfileDialog = true },
                    onTorClick = {
                        when (torStatus) {
                            is TorManager.Status.Ready,
                            is TorManager.Status.Starting -> vm.stopTor()
                            else -> vm.startTor()
                        }
                    }
                )
            }
        }

        BottomDock(selected = tab, onSelect = { tab = it })
    }

    if (showProfileDialog) {
        ProfileDialog(
            currentName = vm.profileName,
            onDismiss = { showProfileDialog = false },
            onSave = {
                vm.updateProfileName(it)
                showProfileDialog = false
            }
        )
    }
}

@Composable
private fun AppHeader(
    title: String,
    profileName: String,
    username: String,
    torStatus: TorManager.Status,
    onTorClick: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialAvatar(text = profileName, prominent = true)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = username,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.80f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TorInlineStatus(status = torStatus)
            }
            Text(
                text = "Tor",
                color = if (torStatus is TorManager.Status.Ready) Color(0xFFB5F0C8) else MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onTorClick)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Ajustes",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun BottomDock(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        HomeTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.title) },
                label = { Text(tab.title, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun ChatsTab(
    query: String,
    onQueryChange: (String) -> Unit,
    conversations: List<ChatViewModel.ConversationPreview>,
    conversationsCount: Int,
    peersCount: Int,
    unreadCountFor: (String) -> Int,
    myUsername: String,
    hasSearch: Boolean,
    onSelect: (String) -> Unit,
    onClear: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearEverywhere: (String) -> Unit
) {
    var pendingRemovalUsername by remember { mutableStateOf<String?>(null) }
    val visibleConversations = remember(conversations, pendingRemovalUsername) {
        conversations.filter { it.username != pendingRemovalUsername }
    }

    LaunchedEffect(pendingRemovalUsername) {
        val username = pendingRemovalUsername ?: return@LaunchedEffect
        delay(5_000)
        onRemove(username)
        pendingRemovalUsername = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            value = query,
            onValueChange = onQueryChange,
            conversationsCount = conversationsCount,
            peersCount = peersCount
        )
        ConversationsPanel(
            conversations = visibleConversations,
            unreadCountFor = unreadCountFor,
            myUsername = myUsername,
            hasSearch = hasSearch,
            onSelect = onSelect,
            onClear = onClear,
            onRemove = { username ->
                pendingRemovalUsername?.let { previous ->
                    if (previous != username) {
                        onRemove(previous)
                    }
                }
                pendingRemovalUsername = username
            },
            onClearEverywhere = onClearEverywhere
        )
        pendingRemovalUsername?.let {
            UndoRemoveBar(
                onUndo = { pendingRemovalUsername = null }
            )
        }
    }
}

@Composable
private fun UndoRemoveBar(onUndo: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Conversa removida da lista",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onUndo) {
                Text("Refazer")
            }
        }
    }
}

@Composable
private fun ContactsTab(
    routeName: String,
    onRouteNameChange: (String) -> Unit,
    routeLookup: ChatViewModel.RouteLookup?,
    routeSuggestions: List<ChatViewModel.RouteLookup>,
    routeStatus: String,
    onRegisterRouteName: () -> Unit,
    onSearchRouteName: () -> Unit,
    peers: List<String>,
    displayNameFor: (String) -> String,
    onSelect: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionTitle("Adicionar contato")
            RouteSearchPanel(
                routeName = routeName,
                onRouteNameChange = onRouteNameChange,
                routeLookup = routeLookup,
                routeSuggestions = routeSuggestions,
                routeStatus = routeStatus,
                onRegisterRouteName = onRegisterRouteName,
                onSearchRouteName = onSearchRouteName,
                onSelect = onSelect
            )
        }
        item { SectionTitle("Rede local") }
        if (peers.isEmpty()) {
            item { EmptyInline("Nenhum contato online encontrado") }
        } else {
            itemsIndexed(peers, key = { _, item -> item }) { _, username ->
                val name = displayNameFor(username)
                ContactRow(
                    name = name,
                    username = username,
                    source = "LAN",
                    onClick = { onSelect(username) }
                )
            }
        }
    }
}

@Composable
private fun ProfileTab(
    profileName: String,
    username: String,
    publicRoute: String,
    conversationsCount: Int,
    peersCount: Int,
    torStatus: TorManager.Status
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(text = profileName, prominent = false, large = true)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profileName.ifBlank { "DoveChat" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(label = "Conversas", value = conversationsCount.toString(), modifier = Modifier.weight(1f))
                StatTile(label = "Contatos", value = peersCount.toString(), modifier = Modifier.weight(1f))
            }
        }
        item {
            SettingsRow(
                title = "Rota segura",
                subtitle = if (torStatus is TorManager.Status.Ready) {
                    "Endereco: $publicRoute"
                } else {
                    torLabel(torStatus)
                },
                leading = Icons.Filled.Lock,
                onClick = {
                    if (torStatus is TorManager.Status.Ready && publicRoute.isNotBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("DoveChat Onion", publicRoute))
                        Toast.makeText(context, "Endereco copiado", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsTab(
    profileName: String,
    username: String,
    publicRoute: String,
    torStatus: TorManager.Status,
    keepViewedMessages: Boolean,
    onKeepViewedMessagesChange: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onTorClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("Conta") }
        item {
            SettingsRow(
                title = "Perfil",
                subtitle = "$profileName - $username",
                leading = Icons.Filled.Person,
                onClick = onEditProfile
            )
        }
        item { SectionTitle("Privacidade e rede") }
        item {
            SettingsSwitchRow(
                title = "Manter historico de mensagens",
                subtitle = "Desative para apagar automaticamente ao sair do chat",
                checked = keepViewedMessages,
                onCheckedChange = onKeepViewedMessagesChange
            )
        }
        item {
            SettingsRow(
                title = "Tor",
                subtitle = if (torStatus is TorManager.Status.Ready) publicRoute else torLabel(torStatus),
                leading = Icons.Filled.Lock,
                onClick = onTorClick,
                trailing = { TorStatusDot(torStatus) }
            )
        }
    }
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    conversationsCount: Int,
    peersCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Buscar") }
        )
        Row(
            modifier = Modifier.padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoPill(text = "$conversationsCount conversas")
            InfoPill(text = "$peersCount online")
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationsPanel(
    conversations: List<ChatViewModel.ConversationPreview>,
    unreadCountFor: (String) -> Int,
    myUsername: String,
    hasSearch: Boolean,
    onSelect: (String) -> Unit,
    onClear: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearEverywhere: (String) -> Unit
) {
    var menuUsername by rememberSaveable { mutableStateOf<String?>(null) }

    if (conversations.isEmpty()) {
        EmptyState(myUsername = myUsername, hasSearch = hasSearch)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(conversations, key = { _, item -> item.username }) { _, item ->
            Box(modifier = Modifier.fillMaxWidth()) {
                ConversationRow(
                    item = item,
                    unread = unreadCountFor(item.username),
                    onClick = { onSelect(item.username) },
                    onLongClick = { menuUsername = item.username }
                )
                DropdownMenu(
                    expanded = menuUsername == item.username,
                    onDismissRequest = { menuUsername = null }
                ) {
                    DropdownMenuItem(text = { Text("Abrir conversa") }, onClick = {
                        menuUsername = null
                        onSelect(item.username)
                    })
                    DropdownMenuItem(text = { Text("Limpar conversa") }, onClick = {
                        menuUsername = null
                        onClear(item.username)
                    })
                    DropdownMenuItem(text = { Text("Remover da lista") }, onClick = {
                        menuUsername = null
                        onRemove(item.username)
                    })
                    DropdownMenuItem(text = { Text("Limpar nos dois lados") }, onClick = {
                        menuUsername = null
                        onClearEverywhere(item.username)
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    item: ChatViewModel.ConversationPreview,
    unread: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialAvatar(text = item.displayName)
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = snapLine(item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatTime(item.lastTimestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SnapCountBadge(unread = unread)
            }
        }
    }
}

@Composable
private fun SnapCountBadge(unread: Int) {
    if (unread <= 0) return
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.ChatBubble,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = unread.toString(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun snapLine(item: ChatViewModel.ConversationPreview): String {
    val state = when (item.snapState) {
        SnapState.Entered -> "Entrou"
        SnapState.Seen -> "Viu"
        SnapState.Cleared -> "Saiu"
    }
    val textLabel = if (item.unreadCount > 0) "${item.unreadCount} balao(oes)" else "balao de texto"
    return "$state - $textLabel"
}

@Composable
private fun EmptyState(myUsername: String, hasSearch: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (hasSearch) "Nada encontrado" else "Nenhuma conversa",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = myUsername,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RouteSearchPanel(
    routeName: String,
    onRouteNameChange: (String) -> Unit,
    routeLookup: ChatViewModel.RouteLookup?,
    routeSuggestions: List<ChatViewModel.RouteLookup>,
    routeStatus: String,
    onRegisterRouteName: () -> Unit,
    onSearchRouteName: () -> Unit,
    onSelect: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = routeName,
                onValueChange = onRouteNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                label = { Text("Nome, usuário ou rota") }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onSearchRouteName) { Text("Buscar") }
                Button(onClick = onRegisterRouteName) { Text("Registrar") }
            }
            if (routeStatus.isNotBlank()) {
                Text(
                    text = routeStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            routeSuggestions.forEach { RouteLookupRow(routeLookup = it, onSelect = onSelect) }
            routeLookup?.let { RouteLookupRow(routeLookup = it, onSelect = onSelect) }
        }
    }
}

@Composable
private fun RouteLookupRow(
    routeLookup: ChatViewModel.RouteLookup,
    onSelect: (String) -> Unit
) {
    ContactRow(
        name = routeLookup.displayName,
        username = routeLookup.username,
        source = routeLookup.source,
        onClick = { onSelect(routeLookup.username) }
    )
}

@Composable
private fun ContactRow(
    name: String,
    username: String,
    source: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialAvatar(text = name)
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$source - $username",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    leading: ImageVector,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leading,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyInline(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TorInlineStatus(status: TorManager.Status) {
    Row(
        modifier = Modifier.padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TorStatusDot(status)
        Spacer(Modifier.width(6.dp))
        Text(
            text = torLabel(status),
            color = if (status is TorManager.Status.Ready) Color(0xFFB5F0C8) else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun TorStatusDot(status: TorManager.Status) {
    val dotColor = when (status) {
        is TorManager.Status.Ready -> Color(0xFF48D873)
        is TorManager.Status.Error -> Color(0xFFE05A47)
        is TorManager.Status.Starting,
        is TorManager.Status.Idle -> Color(0xFFB7BDBA)
    }
    Surface(shape = CircleShape, color = dotColor, modifier = Modifier.size(8.dp)) {}
}

private fun torLabel(status: TorManager.Status): String {
    return when (status) {
        is TorManager.Status.Starting -> "Iniciando Tor..."
        is TorManager.Status.Ready -> "Conectado"
        is TorManager.Status.Error -> "Erro"
        is TorManager.Status.Idle -> "Desligado"
    }
}

@Composable
private fun InitialAvatar(
    text: String,
    prominent: Boolean = false,
    large: Boolean = false
) {
    val size = when {
        large -> 72.dp
        prominent -> 44.dp
        else -> 42.dp
    }
    val color = if (prominent) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.20f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    }
    val textColor = if (prominent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

    Surface(shape = CircleShape, color = color, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text.trim().take(1).ifBlank { "P" }.uppercase(),
                color = textColor,
                fontWeight = FontWeight.Bold,
                style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ProfileDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Perfil") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nome do perfil") }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = name.trim()
                if (clean.isNotBlank()) onSave(clean)
            }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
