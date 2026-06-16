package com.null0x.chat.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.null0x.chat.network.ChatNodeManager
import com.null0x.chat.network.TorManager
import com.null0x.chat.ui.common.CursorAwareOutlinedTextField
import com.null0x.chat.ui.maskedRouteLabel
import com.null0x.chat.ui.profile.RouteProfileScreen
import com.null0x.chat.security.AppSecurityManager
import com.null0x.chat.ui.theme.ThemePreference
import com.null0x.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface PendingProfileChange {
    data class Identity(val name: String, val emoji: String) : PendingProfileChange
    data class Bio(val bio: String) : PendingProfileChange
}

@Composable
fun HomeScreen(
    vm: ChatViewModel,
    onLockApp: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val context = LocalContext.current
    val serviceStatus by TorManager.status.collectAsState()
    val networkAvailable by TorManager.networkAvailableState.collectAsState()
    val knownRoutesRefreshing by ChatNodeManager.knownRoutesRefreshing.collectAsState()
    val themeMode by ThemePreference.themeMode.collectAsState()
    val publicRoute = vm.currentPublicRoute()
    val serviceReady = serviceStatus is TorManager.Status.Ready
    val serviceStarting = serviceStatus is TorManager.Status.Starting
    val pagerState = rememberPagerState(initialPage = HomeTab.Chats.ordinal) { HomeTab.entries.size }
    val scope = rememberCoroutineScope()
    val tabIndex by remember {
        derivedStateOf {
            val resolvedPage = if (pagerState.isScrollInProgress) {
                pagerState.targetPage
            } else {
                pagerState.settledPage
            }
            resolvedPage.coerceIn(0, HomeTab.entries.lastIndex)
        }
    }
    val tab = HomeTab.entries[tabIndex]
    var query by rememberSaveable { mutableStateOf("") }
    var searchSummary by remember { mutableStateOf<ChatViewModel.SearchSummary?>(null) }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showRouteProfile by rememberSaveable { mutableStateOf(false) }
    var showShareRoute by rememberSaveable { mutableStateOf(false) }
    var routeProfileTarget by rememberSaveable { mutableStateOf("") }
    var selectedContactUsername by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedChatUsernames by remember { mutableStateOf(setOf<String>()) }
    var pendingProfileChange by remember { mutableStateOf<PendingProfileChange?>(null) }
    var profileAuthError by rememberSaveable { mutableStateOf("") }
    var headerSearchActive by rememberSaveable { mutableStateOf(false) }
    var showRouteQrScanner by rememberSaveable { mutableStateOf(false) }
    val routeCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showRouteQrScanner = true
        } else {
            Toast.makeText(context, "Permita a câmera para ler QR", Toast.LENGTH_SHORT).show()
        }
    }

    fun openRouteQrScanner() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showRouteQrScanner = true
        } else {
            routeCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val publicRouteToken = remember(publicRoute) { vm.routeTokenFor(publicRoute) }
    val routeLabel = publicRouteToken.ifBlank { publicRoute.trim() }
    val conversations = vm.conversationPreviews()
    val contacts = vm.contactPreviews()
    val pendingContactRequests = vm.pendingContactRequests()
    val blockedContacts = vm.blockedContactPreviews()
    BackHandler(enabled = showShareRoute) {
        showShareRoute = false
    }
    BackHandler(enabled = selectedContactUsername != null) {
        selectedContactUsername = null
    }
    BackHandler(enabled = selectedChatUsernames.isNotEmpty()) {
        selectedChatUsernames = emptySet()
    }
    val dockBadges = remember(
        conversations,
        contacts,
        vm.routeLookup,
        vm.profileName,
        vm.profileEmojiSymbol,
        vm.profileBioText,
    ) {
        mapOf(
            HomeTab.Chats to conversations.sumOf { it.unreadCount },
            HomeTab.Contacts to pendingContactRequests.size + if (vm.routeLookup?.isLocalOwner == false) 1 else 0,
            HomeTab.Profile to profileAttentionCount(vm.profileName, vm.profileEmojiSymbol, vm.profileBioText),
            HomeTab.Settings to 0
        )
    }
    val visibleConversations = searchSummary?.conversations ?: conversations
    var showRefreshingTitle by remember { mutableStateOf(false) }
    var showStartingTitle by remember { mutableStateOf(false) }

    LaunchedEffect(networkAvailable, serviceReady, knownRoutesRefreshing) {
        showRefreshingTitle = false
        if (!networkAvailable || !serviceReady || !knownRoutesRefreshing) return@LaunchedEffect
        delay(2_000)
        showRefreshingTitle = networkAvailable && serviceReady && knownRoutesRefreshing
    }

    LaunchedEffect(serviceStarting, networkAvailable) {
        showStartingTitle = networkAvailable && serviceStarting
        if (!showStartingTitle) return@LaunchedEffect
        delay(10_000)
        showStartingTitle = networkAvailable && serviceStarting
    }

    LaunchedEffect(context) {
        TorManager.ensureNetworkMonitoring(context)
    }

    LaunchedEffect(tab) {
        if (tab != HomeTab.Chats && tab != HomeTab.Contacts) {
            headerSearchActive = false
        }
        if (tab != HomeTab.Contacts) {
            selectedContactUsername = null
        }
        if (tab != HomeTab.Chats) {
            selectedChatUsernames = emptySet()
        }
    }

    LaunchedEffect(selectedContactUsername, selectedChatUsernames) {
        if (selectedContactUsername != null || selectedChatUsernames.isNotEmpty()) {
            headerSearchActive = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                val headerTitle = when (tab) {
                    HomeTab.Chats, HomeTab.Contacts -> when {
                        !networkAvailable -> "Aguardando rede..."
                        showStartingTitle -> "Iniciando..."
                        showRefreshingTitle -> "Atualizando..."
                        else -> tab.title
                    }
                    else -> tab.title
                }
                val searchEnabled = tab == HomeTab.Chats || tab == HomeTab.Contacts
                val headerSearchValue = when (tab) {
                    HomeTab.Chats -> query
                    HomeTab.Contacts -> vm.contactRouteInput
                    else -> ""
                }
                val headerSearchPlaceholder = when (tab) {
                    HomeTab.Chats -> "Buscar mensagens"
                    HomeTab.Contacts -> "Rota ou token"
                    else -> "Pesquisar"
                }
                fun submitHeaderSearch() {
                    when (tab) {
                        HomeTab.Chats -> {
                            scope.launch {
                                searchSummary = if (query.trim().isBlank()) {
                                    null
                                } else {
                                    vm.searchExactMessages(query.trim())
                                }
                            }
                        }
                        HomeTab.Contacts -> vm.addContactRouteFromInput()
                        else -> Unit
                    }
                }
                AppHeader(
                    title = if (tab == HomeTab.Chats && selectedChatUsernames.isNotEmpty()) {
                        "${selectedChatUsernames.size} selecionados"
                    } else {
                        headerTitle
                    },
                    selectedContactUsername = selectedContactUsername.takeIf { tab == HomeTab.Contacts },
                    selectedChatsCount = selectedChatUsernames.takeIf { tab == HomeTab.Chats }?.size ?: 0,
                    searchEnabled = searchEnabled,
                    searchActive = headerSearchActive && searchEnabled,
                    searchValue = headerSearchValue,
                    searchPlaceholder = headerSearchPlaceholder,
                    onSearchActiveChange = { active ->
                        headerSearchActive = active
                        if (!active && tab == HomeTab.Chats) {
                            searchSummary = null
                        }
                    },
                    onSearchValueChange = { value ->
                        when (tab) {
                            HomeTab.Chats -> {
                                query = value
                                searchSummary = null
                            }
                            HomeTab.Contacts -> vm.updateContactRouteInput(value)
                            else -> Unit
                        }
                    },
                    onSearchSubmit = { submitHeaderSearch() },
                    onSearchQrClick = { openRouteQrScanner() },
                    onDeleteSelectedChats = {
                        selectedChatUsernames.forEach(vm::removeConversation)
                        selectedChatUsernames = emptySet()
                    },
                    onClearSelectedChats = {
                        selectedChatUsernames.forEach(vm::clearConversation)
                        selectedChatUsernames = emptySet()
                    },
                    onSelectAllChats = {
                        selectedChatUsernames = visibleConversations.map { it.username }.toSet()
                    },
                    onDeleteSelectedContact = {
                        selectedContactUsername?.let(vm::removeContact)
                        selectedContactUsername = null
                    },
                    onLockApp = onLockApp
                )
            },
            bottomBar = {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    BottomDock(
                        selected = tab,
                        badges = dockBadges,
                        onSelect = { targetTab ->
                            scope.launch {
                                val currentPage = pagerState.currentPage
                                val targetPage = targetTab.ordinal
                                if (targetPage == currentPage) return@launch
                                pagerState.animateScrollToPage(targetPage)
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                beyondViewportPageCount = 1
            ) {
                when (HomeTab.entries[it]) {
                    HomeTab.Chats -> ChatsTabSelectionAware(
                        conversations = visibleConversations,
                        searchSummary = searchSummary,
                        myUsername = vm.myUsername,
                        profileEmoji = vm.profileEmojiSymbol,
                        hasSearch = searchSummary != null,
                        selectedChatUsernames = selectedChatUsernames,
                        isRouteActive = vm::isPartnerOnline,
                        onSelect = {
                            vm.selectTarget(it)
                            onOpenChat(it)
                        },
                        onToggleSelection = { username ->
                            selectedChatUsernames = if (selectedChatUsernames.contains(username)) {
                                selectedChatUsernames - username
                            } else {
                                selectedChatUsernames + username
                            }
                        }
                    )
                    HomeTab.Contacts -> ContactsTab(
                        routeName = vm.contactRouteInput,
                        onRouteNameChange = vm::updateContactRouteInput,
                        routeLookup = vm.routeLookup,
                        routeStatus = vm.routeStatus,
                        onSearchRouteName = vm::addContactRouteFromInput,
                        routeSearchInHeader = headerSearchActive && tab == HomeTab.Contacts,
                        showQrScanner = showRouteQrScanner,
                        onShowQrScannerChange = { showRouteQrScanner = it },
                        contacts = contacts,
                        pendingRequests = pendingContactRequests,
                        isValidQrCode = vm::isValidNullChatQrToken,
                        isContactRequested = vm::isContactRequested,
                        isContactAccepted = vm::isContactAccepted,
                        isContactActive = vm::isContactActive,
                        isRouteActive = vm::isPartnerOnline,
                        onAddContact = vm::addContact,
                        onCancelContact = vm::cancelContactRequest,
                        onRemoveContact = vm::removeContact,
                        onAcceptContact = vm::acceptContactRequest,
                        shouldShowAcceptedNotice = vm::shouldShowAcceptedNotice,
                        onAcceptedNoticeShown = vm::markAcceptedNoticeShown,
                        onOpenProfile = { username ->
                            selectedContactUsername = null
                            routeProfileTarget = username
                            showRouteProfile = true
                        },
                        onSelect = {
                            if (selectedContactUsername == it) {
                                selectedContactUsername = null
                            } else if (selectedContactUsername != null) {
                                selectedContactUsername = it
                            } else {
                                if (vm.isContactAccepted(it) && vm.isContactActive(it)) {
                                    vm.selectTarget(it)
                                    onOpenChat(it)
                                } else {
                                    routeProfileTarget = it
                                    showRouteProfile = true
                                }
                            }
                        },
                        selectedContactUsername = selectedContactUsername,
                        onSelectContactForDeletion = { selectedContactUsername = it }
                    )
                    HomeTab.Profile -> ProfileTab(
                        profileName = vm.profileName,
                        profileEmoji = vm.profileEmojiSymbol,
                        publicRoute = publicRoute,
                        publicRouteToken = publicRouteToken,
                        routeLabel = routeLabel,
                        profileBio = vm.profileBioText,
                        onProfileBioSave = { bio ->
                            profileAuthError = ""
                            pendingProfileChange = PendingProfileChange.Bio(bio)
                        },
                        onEditProfile = { showProfileDialog = true },
                        onShareRoute = { showShareRoute = true }
                    )
                HomeTab.Settings -> SettingsTab(
                    publicRoute = publicRoute,
                    publicRouteToken = publicRouteToken,
                    bottomPadding = 176.dp,
                    themeMode = themeMode,
                    onThemeModeChange = { ThemePreference.setThemeMode(context, it) },
                    keepViewedMessages = vm.isKeepViewedMessagesEnabled(),
                    onKeepViewedMessagesChange = vm::updateKeepViewedMessagesPreference,
                    screenshotsEnabled = vm.isScreenshotsEnabled(),
                    onScreenshotsEnabledChange = vm::updateScreenshotsPreference,
                    blockedContacts = blockedContacts,
                    onUnblockContact = vm::unblockContact,
                    onContactsBackupRequested = vm::contactsBackupJson,
                    onLockApp = onLockApp,
                )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 0.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            BottomDock(
                selected = tab,
                badges = dockBadges,
                onSelect = { targetTab ->
                    scope.launch {
                        val currentPage = pagerState.currentPage
                        val targetPage = targetTab.ordinal
                        if (targetPage == currentPage) return@launch
                        pagerState.animateScrollToPage(targetPage)
                    }
                }
            )
        }
    }

    if (showProfileDialog) {
        ProfileIdentityDialog(
            currentName = vm.profileName,
            currentEmoji = vm.profileEmojiSymbol,
            onDismiss = { showProfileDialog = false },
            onSave = { name, emoji ->
                profileAuthError = ""
                pendingProfileChange = PendingProfileChange.Identity(name, emoji)
                showProfileDialog = false
            }
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showShareRoute,
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
    ) {
        val shareToken = remember(publicRoute) {
            vm.routeTokenFor(publicRoute).replace(":", "#")
        }
        RouteShareScreen(
            tokenLabel = shareToken.ifBlank { "Aguardando parceiro..." },
            onBack = { showShareRoute = false },
            onLockApp = onLockApp
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showRouteProfile,
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
    ) {
        val target = routeProfileTarget.trim()
        val profile = vm.publicProfileFor(target)
        LaunchedEffect(target) {
            if (target.isNotBlank()) {
                vm.requestPublicProfile(target)
            }
        }
        RouteProfileScreen(
            profile = profile,
            onBack = { showRouteProfile = false },
            onLockApp = onLockApp,
            onSaveLocalName = { route, name -> vm.setLocalNameForRoute(route, name) }
        )
    }

    pendingProfileChange?.let { change ->
        PasswordConfirmDialog(
            title = "Confirmar edição pública",
            message = "Digite a senha alfanumérica para publicar alterações no perfil.",
            error = profileAuthError,
            onDismiss = {
                pendingProfileChange = null
                profileAuthError = ""
            },
            onConfirm = { password ->
                val result = AppSecurityManager.verifyPassword(context, password)
                if (result.isSuccess) {
                    when (change) {
                        is PendingProfileChange.Identity -> {
                            vm.updateProfileName(change.name)
                            vm.updateProfileEmoji(change.emoji)
                        }
                        is PendingProfileChange.Bio -> vm.updateProfileBio(change.bio)
                    }
                    pendingProfileChange = null
                    profileAuthError = ""
                    Toast.makeText(context, "Perfil atualizado", Toast.LENGTH_SHORT).show()
                } else {
                    profileAuthError = "Senha errada, tente novamente"
                }
            }
        )
    }
}

@Composable
private fun ChatsTabSelectionAware(
    conversations: List<ChatViewModel.ConversationPreview>,
    searchSummary: ChatViewModel.SearchSummary?,
    myUsername: String,
    profileEmoji: String,
    hasSearch: Boolean,
    selectedChatUsernames: Set<String>,
    isRouteActive: (String) -> Boolean,
    onSelect: (String) -> Unit,
    onToggleSelection: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val summaryHeight = if (searchSummary != null) 42.dp else 0.dp
        val listHeight = (maxHeight - summaryHeight).coerceAtLeast(260.dp)
        Column(modifier = Modifier.fillMaxSize()) {
            SearchSummaryRow(searchSummary)
            ConversationsPanelSelection(
                modifier = Modifier
                    .height(listHeight)
                    .imePadding(),
                conversations = conversations,
                myUsername = myUsername,
                profileEmoji = profileEmoji,
                hasSearch = hasSearch,
                selectedChatUsernames = selectedChatUsernames,
                isRouteActive = isRouteActive,
                onSelect = onSelect,
                onToggleSelection = onToggleSelection
            )
        }
    }
}

@Composable
private fun SearchSummaryRow(searchSummary: ChatViewModel.SearchSummary?) {
    searchSummary ?: return
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoPill(text = pluralize(searchSummary.conversationCount, "conversa", "conversas"))
        InfoPill(text = pluralize(searchSummary.resultCount, "resultado", "resultados"))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationsPanelSelection(
    modifier: Modifier = Modifier,
    conversations: List<ChatViewModel.ConversationPreview>,
    myUsername: String,
    profileEmoji: String,
    hasSearch: Boolean,
    selectedChatUsernames: Set<String>,
    isRouteActive: (String) -> Boolean,
    onSelect: (String) -> Unit,
    onToggleSelection: (String) -> Unit
) {
    if (conversations.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth()) {
            EmptyState(myUsername = myUsername, profileEmoji = profileEmoji, hasSearch = hasSearch)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(conversations, key = { _, item -> item.username }) { _, item ->
            val selected = selectedChatUsernames.contains(item.username)
            ConversationRowSelectable(
                item = item,
                selected = selected,
                active = isRouteActive(item.username),
                onClick = {
                    if (selectedChatUsernames.isNotEmpty()) {
                        onToggleSelection(item.username)
                    } else {
                        onSelect(item.username)
                    }
                },
                onLongClick = { onToggleSelection(item.username) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRowSelectable(
    item: ChatViewModel.ConversationPreview,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialAvatar(text = item.displayName, emoji = item.emoji, active = active)
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.widthIn(max = 220.dp)) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Clip
                        )
                        Text(
                            text = snapLine(item),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatTime(item.lastTimestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SnapCountBadge(unread = item.unreadCount)
                }
            }
            ListSeparator(modifier = Modifier.padding(start = 64.dp))
        }
    }
}

@Composable
private fun ContactsTab(
    routeName: String,
    onRouteNameChange: (String) -> Unit,
    routeLookup: ChatViewModel.RouteLookup?,
    routeStatus: String,
    onSearchRouteName: () -> Unit,
    routeSearchInHeader: Boolean,
    showQrScanner: Boolean,
    onShowQrScannerChange: (Boolean) -> Unit,
    contacts: List<ChatViewModel.ContactPreview>,
    pendingRequests: List<ChatViewModel.ConversationPreview>,
    isValidQrCode: (String) -> Boolean,
    isContactRequested: (String) -> Boolean,
    isContactAccepted: (String) -> Boolean,
    isContactActive: (String) -> Boolean,
    isRouteActive: (String) -> Boolean,
    onAddContact: (String) -> Unit,
    onCancelContact: (String) -> Unit,
    onRemoveContact: (String) -> Unit,
    onAcceptContact: (String) -> Unit,
    shouldShowAcceptedNotice: (String) -> Boolean,
    onAcceptedNoticeShown: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onSelect: (String) -> Unit,
    selectedContactUsername: String?,
    onSelectContactForDeletion: (String) -> Unit
) {
    val acceptedNoticeShown = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("Adicionar contato")
            RouteSearchPanel(
                routeName = routeName,
                onRouteNameChange = onRouteNameChange,
                routeLookup = routeLookup,
                routeStatus = routeStatus,
                onSearchRouteName = onSearchRouteName,
                searchInHeader = routeSearchInHeader,
                showQrScanner = showQrScanner,
                onShowQrScannerChange = onShowQrScannerChange,
                isValidQrCode = isValidQrCode,
                isContactRequested = isContactRequested,
                isContactAccepted = isContactAccepted,
                isContactActive = isContactActive,
                onAddContact = onAddContact,
                onRemoveContact = onRemoveContact,
                onOpenProfile = onOpenProfile
            )
        }
        if (pendingRequests.isNotEmpty()) {
            item { SectionTitle("Solicitações") }
            itemsIndexed(pendingRequests, key = { _, item -> "request:${item.username}" }) { _, item ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    ContactRow(
                        name = item.displayName,
                        username = item.username,
                        emoji = item.emoji,
                        active = isRouteActive(item.username),
                        onClick = { onOpenProfile(item.username) },
                        statusLabel = "Pedido recebido",
                        trailingActionLabel = "Aceitar",
                        trailingActionIcon = Icons.Filled.Done,
                        trailingActionProminent = true,
                        onTrailingAction = { onAcceptContact(item.username) },
                        onLongClick = { onSelectContactForDeletion(item.username) },
                        selected = selectedContactUsername == item.username
                    )
                }
            }
        }
        item { SectionTitle("Contatos") }
        if (contacts.isEmpty()) {
            item {
                ContactsEmptyState()
            }
        } else {
            itemsIndexed(contacts, key = { _, item -> item.username }) { _, item ->
                val showAcceptedNotice = item.accepted &&
                    shouldShowAcceptedNotice(item.username) &&
                    acceptedNoticeShown[item.username] != true
                LaunchedEffect(item.username, item.accepted, showAcceptedNotice) {
                    if (!item.accepted) {
                        acceptedNoticeShown.remove(item.username)
                    } else if (showAcceptedNotice) {
                        acceptedNoticeShown[item.username] = true
                        onAcceptedNoticeShown(item.username)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    ContactRow(
                        name = item.displayName,
                        username = item.username,
                        emoji = item.emoji,
                        active = item.accepted && isRouteActive(item.username),
                        onClick = { onSelect(item.username) },
                        statusLabel = when {
                            showAcceptedNotice -> "Aceito"
                            item.accepted -> null
                            else -> "Pedido"
                        },
                        onLongClick = { onSelectContactForDeletion(item.username) },
                        selected = selectedContactUsername == item.username
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTab(
    profileName: String,
    profileEmoji: String,
    publicRoute: String,
    publicRouteToken: String,
    routeLabel: String,
    profileBio: String,
    onProfileBioSave: (String) -> Unit,
    onEditProfile: () -> Unit,
    onShareRoute: () -> Unit
) {
    val context = LocalContext.current
    var bioDraft by rememberSaveable { mutableStateOf(profileBio) }
    LaunchedEffect(profileBio) {
        if (profileBio != bioDraft) {
            bioDraft = profileBio
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 52.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            InitialAvatar(
                                text = profileName.ifBlank { publicRouteToken.ifBlank { publicRoute } },
                                emoji = profileEmoji,
                                prominent = false,
                                large = true
                            )
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = profileName.ifBlank { publicRouteToken.ifBlank { "Token da rota" } },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Perfil pessoal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(44.dp)
                                .clickable(onClick = onEditProfile),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            tonalElevation = 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Editar perfil",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            enabled = routeLabel.isNotBlank(),
                            onClick = onShareRoute,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Compartilhar")
                        }
                        TextButton(
                            enabled = bioDraft != profileBio,
                            onClick = { onProfileBioSave(bioDraft) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Salvar bio")
                        }
                    }
                    Text(
                        text = "Bio do perfil",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = bioDraft,
                        onValueChange = { bioDraft = limitUtf8Bytes(it, 4 * 1024) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        maxLines = 10,
                        label = { Text("Escreva tudo que quiser") },
                        placeholder = { Text("Até 4 KB visíveis para quem visitar a rota.") },
                        shape = RoundedCornerShape(16.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${bioDraft.toByteArray(Charsets.UTF_8).size.coerceAtMost(4 * 1024)} / 4096 bytes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SnapCountBadge(unread: Int) {
    if (unread <= 0) return
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
    return item.previewLine
}

@Composable
private fun EmptyState(myUsername: String, profileEmoji: String, hasSearch: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.padding(24.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                InitialAvatar(
                    text = if (hasSearch) "?" else myUsername,
                    emoji = if (hasSearch) null else profileEmoji,
                    prominent = false,
                    large = true
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (hasSearch) "Nada encontrado" else "Nenhuma conversa ainda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (hasSearch) {
                        "Tente outro nome, username ou rota."
                    } else {
                        "Suas conversas aparecerão aqui assim que alguém falar com você."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = maskedRouteLabel(myUsername),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun RouteSearchPanel(
    routeName: String,
    onRouteNameChange: (String) -> Unit,
    routeLookup: ChatViewModel.RouteLookup?,
    routeStatus: String,
    onSearchRouteName: () -> Unit,
    searchInHeader: Boolean,
    showQrScanner: Boolean,
    onShowQrScannerChange: (Boolean) -> Unit,
    isValidQrCode: (String) -> Boolean,
    isContactRequested: (String) -> Boolean,
    isContactAccepted: (String) -> Boolean,
    isContactActive: (String) -> Boolean,
    onAddContact: (String) -> Unit,
    onRemoveContact: (String) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (routeStatus.isNotBlank()) {
            Text(
                text = routeStatus,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        routeLookup?.let { lookup ->
            RouteLookupRow(
                routeLookup = lookup,
                alreadyAdded = isContactActive(lookup.username) && isContactAccepted(lookup.username),
                requestSent = isContactActive(lookup.username) && isContactRequested(lookup.username),
                onAddContact = onAddContact,
                onRemoveContact = onRemoveContact,
                onOpenProfile = onOpenProfile
            )
        }
    }

    if (showQrScanner) {
        QrCodeScannerDialog(
            onDismiss = { onShowQrScannerChange(false) },
            isValidQrCode = isValidQrCode,
            onQrCodeScanned = { scannedCode ->
                val cleanCode = scannedCode.trim()
                onRouteNameChange(cleanCode)
                onShowQrScannerChange(false)
                onSearchRouteName()
            }
        )
    }
}

@Composable
private fun RouteLookupRow(
    routeLookup: ChatViewModel.RouteLookup,
    alreadyAdded: Boolean,
    requestSent: Boolean,
    onAddContact: (String) -> Unit,
    onRemoveContact: (String) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val added = alreadyAdded || requestSent
    var showRemoveConfirm by rememberSaveable(routeLookup.username) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenProfile(routeLookup.username) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InitialAvatar(text = routeLookup.displayName, emoji = routeLookup.emoji)
                Column(
                    modifier = Modifier.weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = routeLookup.displayName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (added) {
                        Text(
                            text = "Adicionado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = maskedRouteLabel(routeLookup.username),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (added) {
                    HomeActionButton(
                        label = "Adicionado",
                        icon = Icons.Filled.Done,
                        onClick = { showRemoveConfirm = true }
                    )
                } else {
                    HomeActionButton(
                        label = "Adicionar",
                        icon = Icons.Filled.Add,
                        onClick = { onAddContact(routeLookup.username) },
                        prominent = true
                    )
                }
            }
            ListSeparator(modifier = Modifier.padding(start = 64.dp))
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = {
                Text("Quer remover?")
            },
            text = {
                Text("Ao remover, o botão volta para Adicionar imediatamente.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveConfirm = false
                        onRemoveContact(routeLookup.username)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Sim")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveConfirm = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Não")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    name: String,
    username: String,
    emoji: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    statusLabel: String? = null,
    trailingActionLabel: String? = null,
    trailingActionIcon: ImageVector? = null,
    trailingActionEnabled: Boolean = true,
    trailingActionProminent: Boolean = false,
    onTrailingAction: (() -> Unit)? = null,
    selected: Boolean = false,
    active: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
        tonalElevation = if (selected) 1.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InitialAvatar(text = name, emoji = emoji, active = active)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (name.isNotBlank()) {
                        Text(
                            text = name,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!statusLabel.isNullOrBlank()) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = maskedRouteLabel(username),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onTrailingAction != null && trailingActionLabel != null && trailingActionIcon != null) {
                    HomeActionButton(
                        label = trailingActionLabel,
                        icon = trailingActionIcon,
                        onClick = onTrailingAction,
                        enabled = trailingActionEnabled,
                        prominent = trailingActionProminent
                    )
                }
            }
            ListSeparator(modifier = Modifier.padding(start = 68.dp))
        }
    }
}

@Composable
private fun ContactsEmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Contacts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Nenhum contato salvo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Pesquise uma rota acima, abra o card do resultado e toque em Adicionar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
