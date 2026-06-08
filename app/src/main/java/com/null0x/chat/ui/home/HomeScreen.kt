package com.null0x.chat.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.null0x.chat.network.ChatNodeManager
import com.null0x.chat.network.TorManager
import com.null0x.chat.security.SensitiveClipboard
import com.null0x.chat.security.identity.MessageCrypto
import com.null0x.chat.security.identity.OnionInboxMessage
import com.null0x.chat.security.identity.OnionInboxStore
import com.null0x.chat.security.identity.RouteIdentityRegistry
import com.null0x.chat.ui.common.SwipeToCloseContainer
import com.null0x.chat.ui.maskedRouteLabel
import com.null0x.chat.ui.profile.RouteProfileScreen
import com.null0x.chat.security.AppSecurityManager
import com.null0x.chat.ui.theme.ThemeMode
import com.null0x.chat.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class HomeTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Chats("Chats", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    Contacts("Contatos", Icons.Filled.Contacts, Icons.Outlined.Contacts),
    Profile("Perfil", Icons.Filled.Person, Icons.Outlined.Person),
    Settings("Configurações", Icons.Filled.Settings, Icons.Outlined.Settings)
}

private val HomeHeaderHeight = 86.dp
private val TitleBarColor = Color.Black
private val ShareCardBackground = Color(0xFF87CEEB)

private sealed interface PendingProfileChange {
    data class Identity(val name: String, val emoji: String) : PendingProfileChange
    data class Bio(val bio: String) : PendingProfileChange
}

@Composable
fun HomeScreen(
    vm: ChatViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    backgroundNetworkEnabled: Boolean,
    onBackgroundNetworkChange: (Boolean) -> Unit,
    backgroundRelaunchEnabled: Boolean,
    onBackgroundRelaunchChange: (Boolean) -> Unit,
    onLockApp: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val context = LocalContext.current
    val torStatus by TorManager.status.collectAsState()
    val networkAvailable by TorManager.networkAvailableState.collectAsState()
    val knownRoutesRefreshing by ChatNodeManager.knownRoutesRefreshing.collectAsState()
    val publicRoute = vm.currentPublicRoute()
    val torReady = torStatus is TorManager.Status.Ready
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
    var pendingProfileChange by remember { mutableStateOf<PendingProfileChange?>(null) }
    var profileAuthError by rememberSaveable { mutableStateOf("") }
    val publicRouteToken = remember(publicRoute) { vm.routeTokenFor(publicRoute) }
    val routeLabel = publicRouteToken.ifBlank { publicRoute.trim() }
    val conversations = vm.conversationPreviews()
    val pendingContactRequests = vm.pendingContactRequests()
    var batteryOptimizationIgnored by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimizationIgnored = isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    BackHandler(enabled = showShareRoute) {
        showShareRoute = false
    }
    BackHandler(enabled = selectedContactUsername != null) {
        selectedContactUsername = null
    }
    val dockBadges = remember(
        conversations,
        vm.routeLookup,
        vm.profileName,
        vm.profileEmojiSymbol,
        vm.profileBioText,
        batteryOptimizationIgnored
    ) {
        mapOf(
            HomeTab.Chats to conversations.sumOf { it.unreadCount },
            HomeTab.Contacts to pendingContactRequests.size + if (vm.routeLookup?.isLocalOwner == false) 1 else 0,
            HomeTab.Profile to profileAttentionCount(vm.profileName, vm.profileEmojiSymbol, vm.profileBioText),
            HomeTab.Settings to if (batteryOptimizationIgnored) 0 else 1
        )
    }
    val cleanQuery = query.trim()
    val visibleConversations = searchSummary?.conversations ?: conversations
    var showRefreshingTitle by remember { mutableStateOf(false) }

    LaunchedEffect(networkAvailable, torReady, knownRoutesRefreshing) {
        showRefreshingTitle = false
        if (!networkAvailable || !torReady || !knownRoutesRefreshing) return@LaunchedEffect
        delay(2_000)
        showRefreshingTitle = networkAvailable && torReady && knownRoutesRefreshing
    }

    LaunchedEffect(context) {
        TorManager.ensureNetworkMonitoring(context)
    }

    LaunchedEffect(tab) {
        if (tab != HomeTab.Contacts) {
            selectedContactUsername = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                val headerTitle = when {
                    !networkAvailable -> "Aguardando rede..."
                    !torReady -> "Conectando..."
                    showRefreshingTitle -> "Atualizando..."
                    else -> tab.title
                }
                AppHeader(
                    title = headerTitle,
                    torStatus = torStatus,
                    selectedContactUsername = selectedContactUsername.takeIf { tab == HomeTab.Contacts },
                    onDeleteSelectedContact = {
                        selectedContactUsername?.let(vm::removeConversation)
                        selectedContactUsername = null
                    },
                    onLockApp = onLockApp
                )
            }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = HomeHeaderHeight),
                beyondViewportPageCount = 1
            ) {
                when (HomeTab.entries[it]) {
                    HomeTab.Chats -> ChatsTab(
                        query = query,
                        onQueryChange = {
                            query = it
                            searchSummary = null
                        },
                        conversations = visibleConversations,
                        searchSummary = searchSummary,
                        myUsername = vm.myUsername,
                        profileEmoji = vm.profileEmojiSymbol,
                        hasSearch = searchSummary != null,
                        onSearch = {
                            scope.launch {
                                searchSummary = if (cleanQuery.isBlank()) {
                                    null
                                } else {
                                    vm.searchExactMessages(cleanQuery)
                                }
                            }
                        },
                        onSelect = {
                            vm.selectTarget(it)
                            onOpenChat(it)
                        },
                        onClear = vm::clearConversation,
                        onRemove = vm::removeConversation
                    )
                    HomeTab.Contacts -> ContactsTab(
                        routeName = vm.contactRouteInput,
                        onRouteNameChange = vm::updateContactRouteInput,
                        routeLookup = vm.routeLookup,
                        routeStatus = vm.routeStatus,
                        onSearchRouteName = vm::addContactRouteFromInput,
                        contacts = conversations,
                        pendingRequests = pendingContactRequests,
                        isContactRequested = vm::isContactRequested,
                        isContactAccepted = vm::isContactAccepted,
                        onAddContact = vm::addContact,
                        onAcceptContact = vm::acceptContactRequest,
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
                                vm.selectTarget(it)
                                onOpenChat(it)
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
                    batteryOptimizationIgnored = batteryOptimizationIgnored,
                    bottomPadding = 176.dp,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                        backgroundNetworkEnabled = backgroundNetworkEnabled,
                        onBackgroundNetworkChange = onBackgroundNetworkChange,
                        backgroundRelaunchEnabled = backgroundRelaunchEnabled,
                        onBackgroundRelaunchChange = onBackgroundRelaunchChange,
                        keepViewedMessages = vm.isKeepViewedMessagesEnabled(),
                        onKeepViewedMessagesChange = vm::updateKeepViewedMessagesPreference,
                        screenshotsEnabled = vm.isScreenshotsEnabled(),
                        onScreenshotsEnabledChange = vm::updateScreenshotsPreference,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
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
        RouteShareScreen(
            routeLabel = routeLabel.ifBlank { "Aguardando rota..." },
            onBack = { showShareRoute = false },
            onCopyRoute = {
                if (routeLabel.isNotBlank()) {
                    SensitiveClipboard.copy(context, "NullChat", routeLabel)
                    Toast.makeText(context, "Token sensivel copiado por 60 segundos", Toast.LENGTH_SHORT).show()
                }
            }
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
private fun AppHeader(
    title: String,
    torStatus: TorManager.Status,
    selectedContactUsername: String?,
    onDeleteSelectedContact: () -> Unit,
    onLockApp: () -> Unit
) {
    Surface(
        color = TitleBarColor,
        tonalElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HomeHeaderHeight)
                .padding(horizontal = 14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (selectedContactUsername != null) 104.dp else 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    if (torStatus is TorManager.Status.Ready) {
                        TorInlineStatus(status = torStatus)
                    }
                }
            }
            if (selectedContactUsername != null) {
                TextButton(
                    onClick = onDeleteSelectedContact,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Excluir contato",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Excluir",
                        color = Color.White,
                        maxLines = 1
                    )
                }
            } else {
                IconButton(
                    onClick = onLockApp,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.VpnKey,
                        contentDescription = "Trancar app",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomDock(
    selected: HomeTab,
    badges: Map<HomeTab, Int>,
    onSelect: (HomeTab) -> Unit
) {
    val iconTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                ),
                shape = RoundedCornerShape(34.dp)
            )
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(12.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.62f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                )
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HomeTab.entries.forEach { tab ->
                        val isSelected = selected == tab
                        val interactionSource = remember { MutableInteractionSource() }
                        val badgeCount = badges[tab] ?: 0
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(56.dp)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { onSelect(tab) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else iconTint
                                )
                            }
                            AttentionBadge(
                                count = badgeCount,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 5.dp, end = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return
    val label = if (count > 99) "99+" else count.toString()
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .height(18.dp)
                .widthIn(min = 18.dp)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChatsTab(
    query: String,
    onQueryChange: (String) -> Unit,
    conversations: List<ChatViewModel.ConversationPreview>,
    searchSummary: ChatViewModel.SearchSummary?,
    myUsername: String,
    profileEmoji: String,
    hasSearch: Boolean,
    onSearch: () -> Unit,
    onSelect: (String) -> Unit,
    onClear: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var pendingRemovalUsername by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingRemovalUsername) {
        val username = pendingRemovalUsername ?: return@LaunchedEffect
        delay(5_000)
        onRemove(username)
        pendingRemovalUsername = null
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val listHeight = (maxHeight - 150.dp).coerceAtLeast(260.dp)
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(
                value = query,
                onValueChange = onQueryChange,
                searchSummary = searchSummary,
                onSearch = onSearch
            )
            ConversationsPanel(
                modifier = Modifier
                    .height(listHeight)
                    .imePadding(),
                conversations = conversations.filter { it.username != pendingRemovalUsername },
                myUsername = myUsername,
                profileEmoji = profileEmoji,
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
                }
            )
            pendingRemovalUsername?.let {
                UndoRemoveBar(
                    onUndo = { pendingRemovalUsername = null }
                )
            }
        }
    }
}

@Composable
private fun UndoRemoveBar(onUndo: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Conversa removida da lista",
                modifier = Modifier.align(Alignment.CenterStart),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(
                onClick = onUndo,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
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
    routeStatus: String,
    onSearchRouteName: () -> Unit,
    contacts: List<ChatViewModel.ConversationPreview>,
    pendingRequests: List<ChatViewModel.ConversationPreview>,
    isContactRequested: (String) -> Boolean,
    isContactAccepted: (String) -> Boolean,
    onAddContact: (String) -> Unit,
    onAcceptContact: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onSelect: (String) -> Unit,
    selectedContactUsername: String?,
    onSelectContactForDeletion: (String) -> Unit
) {
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
                contacts = contacts,
                isContactRequested = isContactRequested,
                isContactAccepted = isContactAccepted,
                onAddContact = onAddContact,
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
                        onClick = { onOpenProfile(item.username) },
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
                Box(modifier = Modifier.fillMaxWidth()) {
                    ContactRow(
                        name = item.displayName,
                        username = item.username,
                        emoji = item.emoji,
                        onClick = { onSelect(item.username) },
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
private fun SettingsTab(
    publicRoute: String,
    publicRouteToken: String,
    batteryOptimizationIgnored: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    backgroundNetworkEnabled: Boolean,
    onBackgroundNetworkChange: (Boolean) -> Unit,
    backgroundRelaunchEnabled: Boolean,
    onBackgroundRelaunchChange: (Boolean) -> Unit,
    keepViewedMessages: Boolean,
    onKeepViewedMessagesChange: (Boolean) -> Unit,
    screenshotsEnabled: Boolean,
    onScreenshotsEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val tokenLabel = publicRouteToken.ifBlank { "Aguardando token..." }
    var identityVersion by rememberSaveable { mutableStateOf(0) }
    var showTokensWindow by rememberSaveable { mutableStateOf(false) }
    var showAccountWindow by rememberSaveable { mutableStateOf(false) }
    var showRestoreIdentity by rememberSaveable { mutableStateOf(false) }
    var showPrivateInbox by rememberSaveable { mutableStateOf(false) }
    val onionInboxStore = remember(context) { OnionInboxStore(context) }
    val privateInboxMessages = remember(identityVersion, showPrivateInbox) {
        onionInboxStore.listMessages()
    }
    val signingPublicKey = remember(identityVersion) {
        RouteIdentityRegistry.identityManager().getPublicKey()
    }
    val exchangePublicKey = remember(identityVersion) {
        RouteIdentityRegistry.identityManager().getExchangePublicKey()
    }
    val sendToken = remember(identityVersion) {
        RouteIdentityRegistry.sendTokenManager().ensureToken()
    }
    val publicSendPackage = remember(publicRoute, exchangePublicKey, sendToken) {
        JSONObject()
            .put("endpoint", publicSendEndpoint(publicRoute))
            .put("method", "POST")
            .put("recipientPublicKey", exchangePublicKey)
            .put("antiSpamToken", sendToken)
            .toString()
    }
    BackHandler(enabled = showTokensWindow && !showRestoreIdentity && !showPrivateInbox) {
        showTokensWindow = false
    }
    BackHandler(enabled = showAccountWindow && !showRestoreIdentity && !showPrivateInbox) {
        showAccountWindow = false
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 12.dp,
                end = 12.dp,
                bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("Conta") }
            item {
                SettingsRow(
                    title = "Tokens",
                    subtitle = "Itens para copiar sem bagunçar as ações",
                    leading = Icons.Filled.VpnKey,
                    onClick = { showTokensWindow = true }
                )
            }
            item {
                SettingsRow(
                    title = "Conta",
                    subtitle = "Envio, inbox, rotacao e restauracao",
                    leading = Icons.Filled.Person,
                    onClick = { showAccountWindow = true }
                )
            }
            if (!batteryOptimizationIgnored) {
                item {
                    SettingsRow(
                        title = "Retirar da economia de bateria",
                        subtitle = "Importante para receber mensagens em segundo plano",
                        leading = Icons.Filled.Devices,
                        important = true,
                        trailing = {
                            AttentionBadge(count = 1)
                        },
                        onClick = { openBatteryOptimizationSettings(context) }
                    )
                }
            }
            item { SectionTitle("Tema") }
            item {
                ThemeModeOptions(
                    currentMode = themeMode,
                    onModeSelected = onThemeModeChange
                )
            }
            item { SectionTitle("Segundo plano") }
            item {
                SettingsSwitchRow(
                    title = "Recebimento silencioso",
                    subtitle = "Mantem a rede ativa sem notificacao fixa",
                    checked = backgroundNetworkEnabled,
                    onCheckedChange = onBackgroundNetworkChange
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Retomar app automaticamente",
                    subtitle = "Tenta reabrir se o Android encerrar",
                    checked = backgroundRelaunchEnabled,
                    enabled = backgroundNetworkEnabled,
                    onCheckedChange = onBackgroundRelaunchChange
                )
            }
            item { SectionTitle("Privacidade") }
            item {
                SettingsSwitchRow(
                    title = "Manter historico de mensagens",
                    subtitle = "Padrao para novas conversas",
                    checked = keepViewedMessages,
                    onCheckedChange = onKeepViewedMessagesChange
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Permitir print da tela",
                    subtitle = "Padrao para novas conversas",
                    checked = screenshotsEnabled,
                    onCheckedChange = onScreenshotsEnabledChange
                )
            }
            item {
                Text(
                    text = "Feito com amor e carinho por nuII0x",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showTokensWindow,
            modifier = Modifier.fillMaxSize(),
            enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
        ) {
            AccountTokensScreen(
                tokenLabel = tokenLabel,
                sendToken = sendToken,
                signingPublicKey = signingPublicKey,
                exchangePublicKey = exchangePublicKey,
                onBack = { showTokensWindow = false },
                onCopyToken = {
                    val token = publicRouteToken.trim()
                    if (token.isNotBlank()) {
                        SensitiveClipboard.copy(context, "Token NullChat", token)
                        Toast.makeText(context, "Token sensivel copiado por 60 segundos", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Token ainda indisponivel", Toast.LENGTH_SHORT).show()
                    }
                },
                onCopySendToken = {
                    SensitiveClipboard.copy(context, "Token publico de envio", sendToken)
                    Toast.makeText(context, "Token público copiado por 60 segundos", Toast.LENGTH_SHORT).show()
                },
                onCopySigningKey = {
                    SensitiveClipboard.copy(context, "Chave publica do dono", signingPublicKey)
                    Toast.makeText(context, "Chave copiada por 60 segundos", Toast.LENGTH_SHORT).show()
                },
                onCopyExchangeKey = {
                    SensitiveClipboard.copy(context, "Chave publica de recebimento", exchangePublicKey)
                    Toast.makeText(context, "Chave copiada por 60 segundos", Toast.LENGTH_SHORT).show()
                }
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showAccountWindow,
            modifier = Modifier.fillMaxSize(),
            enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
        ) {
            AccountActionsScreen(
                privateInboxMessages = privateInboxMessages,
                onBack = { showAccountWindow = false },
                onCopyPublicSend = {
                    SensitiveClipboard.copy(context, "Envio publico NullChat", publicSendPackage)
                    Toast.makeText(context, "Dados de envio copiados por 60 segundos", Toast.LENGTH_SHORT).show()
                },
                onOpenInbox = {
                    identityVersion++
                    showPrivateInbox = true
                },
                onRotateToken = {
                    RouteIdentityRegistry.sendTokenManager().rotateToken()
                    identityVersion++
                    Toast.makeText(context, "Token público rotacionado", Toast.LENGTH_SHORT).show()
                },
                onRestoreAccess = { showRestoreIdentity = true }
            )
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showRestoreIdentity,
        modifier = Modifier.fillMaxSize(),
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
    ) {
        RestoreIdentityScreen(
            onBack = { showRestoreIdentity = false },
            onRestored = {
                identityVersion++
                showRestoreIdentity = false
            }
        )
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = showPrivateInbox,
        modifier = Modifier.fillMaxSize(),
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
    ) {
        PrivateInboxScreen(
            messages = privateInboxMessages,
            onBack = { showPrivateInbox = false },
            onDelete = { message ->
                onionInboxStore.delete(message.id)
                identityVersion++
            }
        )
    }
}

@Composable
private fun AccountTokensScreen(
    tokenLabel: String,
    sendToken: String,
    signingPublicKey: String,
    exchangePublicKey: String,
    onBack: () -> Unit,
    onCopyToken: () -> Unit,
    onCopySendToken: () -> Unit,
    onCopySigningKey: () -> Unit,
    onCopyExchangeKey: () -> Unit
) {
    SettingsWindowScaffold(
        title = "Tokens",
        subtitle = "Itens para copiar",
        onBack = onBack
    ) {
        item { SectionTitle("Tokens da rota") }
        item {
            SettingsRow(
                title = "Seu Token",
                subtitle = tokenLabel,
                leading = Icons.Filled.VpnKey,
                onClick = onCopyToken
            )
        }
        item {
            SettingsRow(
                title = "Token público de envio",
                subtitle = if (sendToken.isBlank()) "Aguardando token..." else sendToken,
                leading = Icons.Filled.Lock,
                onClick = onCopySendToken
            )
        }
        item { SectionTitle("Chaves públicas") }
        item {
            SettingsRow(
                title = "Chave pública do dono",
                subtitle = signingPublicKey.ifBlank { "Aguardando chave..." },
                leading = Icons.Filled.VpnKey,
                onClick = onCopySigningKey
            )
        }
        item {
            SettingsRow(
                title = "Chave de recebimento",
                subtitle = exchangePublicKey.ifBlank { "Aguardando chave..." },
                leading = Icons.Filled.Lock,
                onClick = onCopyExchangeKey
            )
        }
    }
}

@Composable
private fun AccountActionsScreen(
    privateInboxMessages: List<OnionInboxMessage>,
    onBack: () -> Unit,
    onCopyPublicSend: () -> Unit,
    onOpenInbox: () -> Unit,
    onRotateToken: () -> Unit,
    onRestoreAccess: () -> Unit
) {
    SettingsWindowScaffold(
        title = "Conta",
        subtitle = "Ações importantes",
        onBack = onBack
    ) {
        item { SectionTitle("Envio") }
        item {
            SettingsRow(
                title = "Envio público",
                subtitle = "Copia endpoint, chave e token para /send",
                leading = Icons.Filled.ChatBubble,
                onClick = onCopyPublicSend
            )
        }
        item {
            SettingsRow(
                title = "Inbox privada",
                subtitle = "${privateInboxMessages.size} mensagens recebidas por /send",
                leading = Icons.Filled.ChatBubble,
                onClick = onOpenInbox
            )
        }
        item { SectionTitle("Segurança") }
        item {
            SettingsRow(
                title = "Rotacionar token público",
                subtitle = "Invalida o token anterior de /send",
                leading = Icons.Filled.Refresh,
                onClick = onRotateToken
            )
        }
        item {
            SettingsRow(
                title = "Restaurar acesso privado",
                subtitle = "Usa as 12 palavras e a senha local",
                leading = Icons.Filled.VpnKey,
                onClick = onRestoreAccess
            )
        }
    }
}

@Composable
private fun SettingsWindowScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    SwipeToCloseContainer(onClose = onBack) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = TitleBarColor) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HomeHeaderHeight)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = Color.White
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.78f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 14.dp,
                        end = 12.dp,
                        bottom = 110.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

private data class ThemeOption(
    val mode: ThemeMode,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
private fun PrivateInboxScreen(
    messages: List<OnionInboxMessage>,
    onBack: () -> Unit,
    onDelete: (OnionInboxMessage) -> Unit
) {
    val exchangePrivateKey = remember {
        runCatching { RouteIdentityRegistry.identityManager().getExchangePrivateKeyB64() }.getOrDefault("")
    }
    SettingsWindowScaffold(
        title = "Inbox privada",
        subtitle = "Mensagens recebidas por /send",
        onBack = onBack
    ) {
        if (messages.isEmpty()) {
            item {
                Text(
                    text = "Nenhuma mensagem recebida por /send.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            itemsIndexed(messages, key = { _, message -> message.id }) { _, message ->
                val plainText = remember(message.id, exchangePrivateKey) {
                    MessageCrypto.decryptMessage(message.ciphertext, exchangePrivateKey)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = plainText ?: "Mensagem criptografada",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatTime(message.receivedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { onDelete(message) }) {
                            Text("Excluir")
                        }
                        ListSeparator()
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoreIdentityScreen(
    onBack: () -> Unit,
    onRestored: () -> Unit
) {
    val context = LocalContext.current
    var mnemonic by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    SettingsWindowScaffold(
        title = "Restaurar acesso privado",
        subtitle = "Use suas 12 palavras e a senha local",
        onBack = onBack
    ) {
        item {
            OutlinedTextField(
                value = mnemonic,
                onValueChange = {
                    mnemonic = it
                    error = ""
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                label = { Text("12 palavras") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text
                )
            )
        }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = ""
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Senha local") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )
        }
        if (error.isNotBlank()) {
            item {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Button(
                onClick = {
                    val result = AppSecurityManager.restoreRouteIdentity(context, mnemonic, password)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Acesso privado restaurado", Toast.LENGTH_SHORT).show()
                        onRestored()
                    } else {
                        error = result.exceptionOrNull()?.message ?: "Senha errada, tente novamente"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restaurar")
            }
        }
    }
}

private fun publicSendEndpoint(route: String): String {
    val clean = route.trim()
    if (!clean.startsWith("onion:", ignoreCase = true)) return "Aguardando rota"
    val value = clean.substringAfter(':')
    val separator = value.lastIndexOf(':')
    if (separator <= 0 || separator == value.lastIndex) return "Aguardando rota"
    val host = value.substring(0, separator)
    val port = value.substring(separator + 1)
    return "http://$host:$port/send"
}

@Composable
private fun ThemeModeOptions(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    val options = remember {
        listOf(
            ThemeOption(ThemeMode.BLUE, "Azul", "Elegante, limpo e principal", Icons.Filled.Lock),
            ThemeOption(ThemeMode.LIGHT, "Claro", "Leve, limpo e sempre legível", Icons.Filled.WbSunny),
            ThemeOption(ThemeMode.DARK, "Escuro", "Contraste suave para uso noturno", Icons.Filled.DarkMode),
            ThemeOption(ThemeMode.PINK, "Rosa", "Blush elegante com toque sofisticado", Icons.Filled.Favorite),
            ThemeOption(ThemeMode.SYSTEM, "Sistema", "Segue a configuração do aparelho", Icons.Filled.Devices)
        )
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedOption = remember(currentMode, options) {
        options.firstOrNull { it.mode == currentMode } ?: options.first()
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        tonalElevation = 0.dp,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = selectedOption.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Column(modifier = Modifier.widthIn(max = 240.dp)) {
                        Text(
                            text = selectedOption.title,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = selectedOption.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Escolher tema",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ListSeparator(modifier = Modifier.padding(start = 70.dp))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.98f)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(option.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = option.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = if (option.mode == currentMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = if (option.mode == currentMode) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    onClick = {
                        expanded = false
                        onModeSelected(option.mode)
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    searchSummary: ChatViewModel.SearchSummary?,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Buscar mensagens exatas") }
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onSearch) {
                Text("Pesquisar")
            }
        }
        searchSummary?.let { summary ->
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoPill(text = pluralize(summary.conversationCount, "conversa", "conversas"))
                InfoPill(text = pluralize(summary.resultCount, "resultado", "resultados"))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationsPanel(
    modifier: Modifier = Modifier,
    conversations: List<ChatViewModel.ConversationPreview>,
    myUsername: String,
    profileEmoji: String,
    hasSearch: Boolean,
    onSelect: (String) -> Unit,
    onClear: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var menuUsername by rememberSaveable { mutableStateOf<String?>(null) }

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
            Box(modifier = Modifier.fillMaxWidth()) {
                ConversationRow(
                    item = item,
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
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    item: ChatViewModel.ConversationPreview,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = Color.Transparent,
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
                    InitialAvatar(text = item.displayName, emoji = item.emoji)
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
                    fontWeight = FontWeight.SemiBold
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
    contacts: List<ChatViewModel.ConversationPreview>,
    isContactRequested: (String) -> Boolean,
    isContactAccepted: (String) -> Boolean,
    onAddContact: (String) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val context = LocalContext.current
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    var routeField by remember {
        mutableStateOf(TextFieldValue(routeName))
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            Toast.makeText(context, "Permita a câmera para ler QR", Toast.LENGTH_SHORT).show()
        }
    }

    fun openQrScanner() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showQrScanner = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(routeName) {
        if (routeName != routeField.text) {
            routeField = TextFieldValue(routeName)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Pesquise contatos apenas pela rota ou token da rota.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = routeField,
                onValueChange = {
                    routeField = it
                    onRouteNameChange(it.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (it.isFocused && routeField.text.isNotBlank()) {
                            routeField = routeField.copy(selection = TextRange(0, routeField.text.length))
                        }
                    },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = {
                    IconButton(onClick = ::openQrScanner) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = "Ler QR da rota"
                        )
                    }
                },
                trailingIcon = {
                    if (routeField.text.isNotBlank()) {
                        IconButton(
                            onClick = {
                                routeField = TextFieldValue("")
                                onRouteNameChange("")
                            }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpar rota")
                        }
                    }
                },
                label = { Text("Rota") }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onSearchRouteName) { Text("Pesquisar") }
            }
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
                    alreadyAdded = contacts.any { contact -> contact.username == lookup.username } || isContactAccepted(lookup.username),
                    requestSent = isContactRequested(lookup.username),
                    onAddContact = onAddContact,
                    onOpenProfile = onOpenProfile
                )
            }
        }
    }

    if (showQrScanner) {
        QrCodeScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrCodeScanned = { scannedCode ->
                val cleanCode = scannedCode.trim()
                routeField = TextFieldValue(cleanCode, selection = TextRange(cleanCode.length))
                onRouteNameChange(cleanCode)
                showQrScanner = false
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
    onOpenProfile: (String) -> Unit
) {
    ContactRow(
        name = routeLookup.displayName,
        username = routeLookup.username,
        emoji = routeLookup.emoji,
        onClick = { onOpenProfile(routeLookup.username) },
        trailingActionLabel = when {
            alreadyAdded -> "Adicionado"
            requestSent -> "Solicitado"
            else -> "Adicionar"
        },
        trailingActionIcon = Icons.Filled.Add,
        trailingActionEnabled = !alreadyAdded && !requestSent,
        trailingActionProminent = !alreadyAdded && !requestSent,
        onTrailingAction = { onAddContact(routeLookup.username) }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    name: String,
    username: String,
    emoji: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailingActionLabel: String? = null,
    trailingActionIcon: ImageVector? = null,
    trailingActionEnabled: Boolean = true,
    trailingActionProminent: Boolean = false,
    onTrailingAction: (() -> Unit)? = null,
    selected: Boolean = false
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
                InitialAvatar(text = name, emoji = emoji)
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
                    if (trailingActionProminent) {
                        Button(
                            onClick = onTrailingAction,
                            enabled = trailingActionEnabled,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = trailingActionIcon,
                                contentDescription = trailingActionLabel,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = trailingActionLabel,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    } else {
                        TextButton(
                            onClick = onTrailingAction,
                            enabled = trailingActionEnabled,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = trailingActionIcon,
                                contentDescription = trailingActionLabel,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = trailingActionLabel,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
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
            Text(
                text = "Nenhum contato salvo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Pesquise uma rota acima e adicione no card do resultado.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    leading: ImageVector,
    leadingEmoji: String? = null,
    important: Boolean = false,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val iconColor = if (important) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leadingEmoji.isNullOrBlank()) {
                        Icon(
                            imageVector = leading,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        InitialAvatar(text = title, emoji = leadingEmoji)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.widthIn(max = 220.dp)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            color = if (important) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
                if (trailing != null) {
                    Box(contentAlignment = Alignment.CenterEnd) {
                        trailing()
                    }
                }
            }
            ListSeparator(modifier = Modifier.padding(start = 50.dp))
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.widthIn(max = 220.dp)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
                    )
                }
                Switch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onCheckedChange
                )
            }
            ListSeparator()
        }
    }
}

@Composable
private fun ListSeparator(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
    )
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
private fun TorInlineStatus(status: TorManager.Status) {
    val label = torLabel(status)
    if (label.isBlank()) return
    Row(
        modifier = Modifier.padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TorStatusDot(status)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (status is TorManager.Status.Ready) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun TorStatusDot(status: TorManager.Status) {
    val dotColor = when (status) {
        is TorManager.Status.Ready -> MaterialTheme.colorScheme.onSurface
        is TorManager.Status.Error -> MaterialTheme.colorScheme.onSurfaceVariant
        is TorManager.Status.Starting,
        is TorManager.Status.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = dotColor, modifier = Modifier.size(8.dp)) {}
}

private fun torLabel(status: TorManager.Status): String {
    return when (status) {
        is TorManager.Status.Starting -> "Aguardando rede..."
        is TorManager.Status.Ready -> ""
        is TorManager.Status.Error -> "Aguardando rede..."
        is TorManager.Status.Idle -> "Aguardando rede..."
    }
}

private fun pluralize(count: Int, singular: String, plural: String): String {
    return if (count == 1) "1 $singular" else "$count $plural"
}

@Composable
private fun PasswordConfirmDialog(
    title: String,
    message: String,
    error: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (password.isNotBlank()) {
            onConfirm(password)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    supportingText = {
                        Text("Entrada privada: sem sugestões do teclado.")
                    },
                    shape = RoundedCornerShape(14.dp)
                )
                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun RouteShareScreen(
    routeLabel: String,
    onBack: () -> Unit,
    onCopyRoute: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = TitleBarColor) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HomeHeaderHeight)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Compartilhar",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Mostra o QR da rota para outro dispositivo",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Compartilhar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Acesso rapido via QR",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black.copy(alpha = 0.72f),
                                textAlign = TextAlign.Center
                            )
                        }

                        RouteTokenQr(
                            token = routeLabel,
                            backgroundColor = ShareCardBackground,
                            foregroundColor = Color.Black,
                            displaySize = 240.dp
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = routeLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = onCopyRoute) { Text("Copiar rota", color = Color.Black) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteTokenQr(
    token: String,
    backgroundColor: Color,
    foregroundColor: Color,
    displaySize: Dp
) {
    if (token.isBlank()) return
    val background = backgroundColor
    val foreground = foregroundColor
    val bitmap = remember(token, foreground, background) {
        generateQrBitmap(
            text = token,
            sizePx = 360,
            foregroundArgb = foreground.toArgb(),
            backgroundArgb = background.toArgb()
        )
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "QR do token",
            modifier = Modifier.size(displaySize)
        )
    }
}

private fun generateQrBitmap(
    text: String,
    sizePx: Int,
    foregroundArgb: Int,
    backgroundArgb: Int
): Bitmap? {
    return runCatching {
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    setPixel(
                        x,
                        y,
                        if (bitMatrix[x, y]) foregroundArgb else backgroundArgb
                    )
                }
            }
        }
    }.getOrNull()
}

private fun openBatteryOptimizationSettings(context: Context) {
    val appContext = context.applicationContext
    if (isBatteryOptimizationIgnored(appContext)) {
        Toast.makeText(appContext, "O app ja esta fora da economia de bateria", Toast.LENGTH_SHORT).show()
        return
    }

    val directIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${appContext.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        null
    }
    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val targetIntent = directIntent?.takeIf { it.resolveActivity(appContext.packageManager) != null }
        ?: fallbackIntent
    runCatching {
        appContext.startActivity(targetIntent)
    }.onFailure {
        Toast.makeText(appContext, "Abra Bateria nas configuracoes do Android", Toast.LENGTH_SHORT).show()
    }
}

private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val appContext = context.applicationContext
    val powerManager = appContext.getSystemService(PowerManager::class.java) ?: return false
    return powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
}

private fun profileAttentionCount(name: String, emoji: String, bio: String): Int {
    var count = 0
    if (name.trim().isBlank()) count++
    if (emoji.trim().isBlank()) count++
    if (bio.trim().isBlank()) count++
    return count
}

private fun limitUtf8Bytes(text: String, maxBytes: Int): String {
    if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
    var end = text.length
    while (end > 0) {
        val candidate = text.substring(0, end)
        if (candidate.toByteArray(Charsets.UTF_8).size <= maxBytes) {
            return candidate
        }
        end--
    }
    return ""
}

@Composable
private fun InitialAvatar(
    text: String,
    emoji: String? = null,
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
                text = if (emoji.isNullOrBlank()) {
                    text.trim().take(1).ifBlank { "P" }.uppercase()
                } else {
                    emoji
                },
                color = textColor,
                fontWeight = FontWeight.Bold,
                style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ProfileIdentityDialog(
    currentName: String,
    currentEmoji: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    var emoji by rememberSaveable { mutableStateOf(currentEmoji.ifBlank { "🙂" }) }

    LaunchedEffect(currentName) {
        name = currentName
    }
    LaunchedEffect(currentEmoji) {
        emoji = currentEmoji.ifBlank { "🙂" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar perfil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Ajuste apenas o nome e o emoji de exibição.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Nome do perfil") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(16) },
                    singleLine = true,
                    label = { Text("Emoji do perfil") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = name.trim()
                if (clean.isNotBlank()) onSave(clean, emoji.trim().ifBlank { "🙂" })
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
