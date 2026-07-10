package com.null0x.chat.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.null0x.chat.network.ChatNodeManager
import com.null0x.chat.network.TorManager
import com.null0x.chat.ui.common.PrimalisAlertDialog
import com.null0x.chat.ui.common.SystemBarsColorEffect
import com.null0x.chat.ui.common.primalisBareOutlinedTextFieldColors
import com.null0x.chat.ui.maskedRouteLabel
import com.null0x.chat.ui.profile.RouteProfileScreen
import com.null0x.chat.security.SensitiveClipboard
import com.null0x.chat.security.AppSecurityManager
import com.null0x.chat.ui.theme.themeBackgroundColor
import com.null0x.chat.ui.theme.ThemePreference
import com.null0x.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface PendingProfileChange {
    data class Identity(val name: String) : PendingProfileChange
    data class Bio(val bio: String) : PendingProfileChange
}

private sealed interface PendingConversationAccess {
    val username: String

    data class Open(override val username: String) : PendingConversationAccess
}

private enum class ReviewCueKind {
    Chat,
    ContactRequest,
    ContactLookup,
    ProfileIdentity,
    ProfileBio
}

private data class ReviewCue(
    val key: String,
    val title: String,
    val detail: String,
    val kind: ReviewCueKind
)

private const val HOME_ONBOARDING_PREFS = "home_onboarding"
private const val CHATS_TAB_FIRST_REVIEW_SEEN_KEY = "chats_tab_first_review_seen"
@Composable
fun HomeScreen(
    vm: ChatViewModel,
    onSignOut: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onboardingPrefs = remember(context) {
        context.applicationContext.getSharedPreferences(HOME_ONBOARDING_PREFS, Context.MODE_PRIVATE)
    }
    val serviceStatus by TorManager.status.collectAsState()
    val networkAvailable by TorManager.networkAvailableState.collectAsState()
    val knownRoutesRefreshing by ChatNodeManager.knownRoutesRefreshing.collectAsState()
    val themeMode by ThemePreference.themeMode.collectAsState()
    val systemDarkTheme = isSystemInDarkTheme()
    val homeBackgroundColor = themeBackgroundColor(themeMode, systemDarkTheme)
    SystemBarsColorEffect(
        statusBarColor = Color.Transparent,
        navigationBarColor = homeBackgroundColor,
        statusBarDarkIcons = homeBackgroundColor.luminance() > 0.5f
    )
    val publicRoute = vm.currentPublicRoute()
    val myProfileImagePath = vm.profileImagePathFor(publicRoute).ifBlank { vm.profileImagePath }
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
    var showProfileSheet by rememberSaveable { mutableStateOf(false) }
    var showProfilePhotoPreview by rememberSaveable { mutableStateOf(false) }
    var showRouteProfile by rememberSaveable { mutableStateOf(false) }
    var showShareRoute by rememberSaveable { mutableStateOf(false) }
    var routeProfileTarget by rememberSaveable { mutableStateOf("") }
    var selectedContactUsername by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedChatUsernames by remember { mutableStateOf(setOf<String>()) }
    var pendingConversationAccess by remember { mutableStateOf<PendingConversationAccess?>(null) }
    var conversationAccessError by rememberSaveable { mutableStateOf("") }
    var pendingProfileChange by remember { mutableStateOf<PendingProfileChange?>(null) }
    var profileAuthError by rememberSaveable { mutableStateOf("") }
    var headerSearchActive by remember { mutableStateOf(false) }
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
    val profileImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { imageUri ->
        if (imageUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            vm.importProfileImageFromGallery(imageUri)
                .onSuccess {
                    Toast.makeText(context, "Imagem do perfil atualizada", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: "Falha ao importar imagem",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
    val publicRouteToken = remember(publicRoute) { vm.routeTokenFor(publicRoute) }
    val routeLabel = publicRouteToken.ifBlank { publicRoute.trim() }
    val conversations = vm.conversationPreviews()
    val contacts = vm.contactPreviews()
    val pendingContactRequests = vm.pendingContactRequests()
    val blockedContacts = vm.blockedContactPreviews()
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshProfileImagesInForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val distanceLocation = rememberGpsLocation(
        context = context,
        enabled = context.hasLocationPermission() && context.isGpsEnabled()
    )
    LaunchedEffect(distanceLocation, vm.locationSharingMode) {
        if (
            distanceLocation != null &&
            vm.locationSharingMode != ChatViewModel.LocationSharingMode.UNSET &&
            vm.locationSharingMode != ChatViewModel.LocationSharingMode.NONE
        ) {
            vm.updateSharedLocation(distanceLocation)
        }
    }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    DisposableEffect(lifecycleOwner, tab) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && tab == HomeTab.Settings) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
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
    BackHandler(enabled = selectedChatUsernames.isNotEmpty()) {
        selectedChatUsernames = emptySet()
    }
    BackHandler(enabled = headerSearchActive) {
        headerSearchActive = false
        query = ""
        searchSummary = null
        vm.updateContactRouteInput("")
        keyboardController?.hide()
    }
    BackHandler(enabled = tab != HomeTab.Chats && !showShareRoute && selectedContactUsername == null && selectedChatUsernames.isEmpty()) {
        scope.launch {
            pagerState.animateScrollToPage(HomeTab.Chats.ordinal)
        }
    }
    val dockBadges = remember(
        conversations,
        contacts,
        vm.routeLookup,
        vm.profileName,
        vm.profileBioText,
    ) {
        mapOf(
            HomeTab.Chats to conversations.sumOf { it.unreadCount },
            HomeTab.Contacts to pendingContactRequests.size + if (vm.routeLookup?.isLocalOwner == false) 1 else 0,
            HomeTab.Map to 0,
            HomeTab.Profile to profileAttentionCount(vm.profileName, vm.profileBioText),
            HomeTab.Settings to 0
        )
    }
    val visibleConversations = searchSummary?.conversations ?: conversations
    var showInitialChatReviewCue by remember {
        mutableStateOf(!onboardingPrefs.getBoolean(CHATS_TAB_FIRST_REVIEW_SEEN_KEY, false))
    }
    val chatReviewCues = remember(showInitialChatReviewCue, searchSummary) {
        if (showInitialChatReviewCue && searchSummary == null) {
            listOf(
                ReviewCue(
                    key = "first-open:chats",
                    title = "Conversas",
                    detail = "Aba pronta para seus chats",
                    kind = ReviewCueKind.Chat
                )
            )
        } else {
            emptyList()
        }
    }
    fun openConversation(username: String) {
        val clean = username.trim()
        if (clean.isBlank()) return
        vm.selectTarget(clean)
        onOpenChat(clean)
    }
    LaunchedEffect(tab, showInitialChatReviewCue) {
        if (tab == HomeTab.Chats && showInitialChatReviewCue) {
            onboardingPrefs.edit().putBoolean(CHATS_TAB_FIRST_REVIEW_SEEN_KEY, true).apply()
            delay(1_800)
            showInitialChatReviewCue = false
        }
    }
    val contactReviewCues = remember(pendingContactRequests, vm.routeLookup) {
        buildList {
            vm.routeLookup
                ?.takeIf { !it.isLocalOwner }
                ?.let { lookup ->
                    add(
                        ReviewCue(
                            key = "lookup:${lookup.username}",
                            title = lookup.displayName,
                            detail = "Resultado pronto para revisar",
                            kind = ReviewCueKind.ContactLookup
                        )
                    )
                }
            pendingContactRequests.forEach { request ->
                add(
                    ReviewCue(
                        key = "request:${request.username}",
                        title = request.displayName,
                        detail = "Solicitacao aguardando resposta",
                        kind = ReviewCueKind.ContactRequest
                    )
                )
            }
        }
    }
    val profileReviewCues = remember(vm.profileName, vm.profileBioText) {
        buildList {
            if (vm.profileName.trim().isBlank()) {
                add(
                    ReviewCue(
                        key = "profile:name",
                        title = "Nome do perfil",
                        detail = "Defina um nome para sua rota",
                        kind = ReviewCueKind.ProfileIdentity
                    )
                )
            }
            if (vm.profileBioText.trim().isBlank()) {
                add(
                    ReviewCue(
                        key = "profile:bio",
                        title = "Bio do perfil",
                        detail = "Escreva uma bio para quem abrir sua rota",
                        kind = ReviewCueKind.ProfileBio
                    )
                )
            }
        }
    }
    var chatReviewIndex by rememberSaveable { mutableStateOf(0) }
    var contactReviewIndex by rememberSaveable { mutableStateOf(0) }
    var profileReviewIndex by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(chatReviewCues.size) {
        chatReviewIndex = chatReviewIndex.coerceIn(0, (chatReviewCues.size - 1).coerceAtLeast(0))
    }
    LaunchedEffect(contactReviewCues.size) {
        contactReviewIndex = contactReviewIndex.coerceIn(0, (contactReviewCues.size - 1).coerceAtLeast(0))
    }
    LaunchedEffect(profileReviewCues.size) {
        profileReviewIndex = profileReviewIndex.coerceIn(0, (profileReviewCues.size - 1).coerceAtLeast(0))
    }
    var showRefreshingTitle by remember { mutableStateOf(false) }
    var showStartingTitle by remember { mutableStateOf(false) }
    var mapTitle by rememberSaveable { mutableStateOf("Mapa") }
    var displayedMapTitle by rememberSaveable { mutableStateOf("Terra") }

    LaunchedEffect(mapTitle) {
        delay(180)
        displayedMapTitle = mapTitle.takeIf { it.isNotBlank() && it != "Mapa" } ?: "Terra"
    }

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

    LaunchedEffect(headerSearchActive, query, tab) {
        if (!headerSearchActive) {
            searchSummary = null
            query = ""
            vm.updateContactRouteInput("")
            return@LaunchedEffect
        }
        if (tab != HomeTab.Chats) {
            searchSummary = null
            return@LaunchedEffect
        }
        delay(220)
        searchSummary = if (query.isBlank()) {
            null
        } else {
            vm.searchExactMessages(query)
        }
    }

    LaunchedEffect(headerSearchActive) {
        if (headerSearchActive) {
            delay(120)
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HomeHeaderHeight)
                            .padding(horizontal = 8.dp),
                    ) {
                        if (tab == HomeTab.Map) {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 16.dp)
                            )
                            Crossfade(
                                targetState = displayedMapTitle,
                                label = "map-title-location",
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 96.dp)
                            ) { title ->
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Text(
                                text = when (tab) {
                                    HomeTab.Chats, HomeTab.Contacts -> when {
                                        !networkAvailable -> "Aguardando rede..."
                                        showStartingTitle -> "Iniciando..."
                                        showRefreshingTitle -> "Atualizando..."
                                        else -> tab.title
                                    }
                                    else -> tab.title
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 16.dp)
                            )
                        }
                    }
                }
            },
            bottomBar = {
                BottomDock(
                    selected = tab,
                    badges = dockBadges,
                    searchEnabled = tab == HomeTab.Chats || tab == HomeTab.Contacts,
                    searchActive = headerSearchActive,
                    searchValue = if (tab == HomeTab.Contacts) vm.contactRouteInput else query,
                    searchPlaceholder = "Pesquisar",
                    searchFocusRequester = searchFocusRequester,
                    onSearchValueChange = { value ->
                        if (tab == HomeTab.Contacts) {
                            vm.updateContactRouteInput(value.take(180))
                        } else {
                            query = value.take(120)
                        }
                    },
                    onSearchSubmit = {
                        if (tab == HomeTab.Contacts) {
                            vm.addContactRouteFromInput()
                        }
                        keyboardController?.hide()
                    },
                    searchQrEnabled = tab == HomeTab.Contacts,
                    onSearchQrClick = {
                        keyboardController?.hide()
                        openRouteQrScanner()
                    },
                    onSearchToggle = {
                        if (headerSearchActive) {
                            headerSearchActive = false
                            query = ""
                            searchSummary = null
                            vm.updateContactRouteInput("")
                            keyboardController?.hide()
                        } else {
                            headerSearchActive = true
                        }
                    },
                    selectedChatsCount = selectedChatUsernames.size,
                    onDeleteSelectedChats = {
                        selectedChatUsernames.forEach { username ->
                            if (vm.isNullAiConversation(username)) {
                                vm.disableNullAi()
                            } else {
                                vm.removeConversation(username)
                            }
                        }
                        selectedChatUsernames = emptySet()
                    },
                    selectedContactUsername = selectedContactUsername,
                    onDeleteSelectedContact = {
                        selectedContactUsername?.let { username ->
                            vm.removeContact(username)
                            selectedContactUsername = null
                        }
                    },
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
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .clipToBounds(),
                beyondViewportPageCount = 2,
                userScrollEnabled = pagerState.settledPage != HomeTab.Map.ordinal
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (HomeTab.entries[it]) {
                        HomeTab.Chats -> ChatsTabSelectionAware(
                            conversations = visibleConversations,
                            searchSummary = searchSummary,
                            myUsername = vm.myUsername,
                            profileEmoji = vm.profileEmojiSymbol,
                            profileMapColor = Color(vm.profileMapColorArgb),
                            hasSearch = searchSummary != null,
                            publicRouteToken = publicRouteToken,
                            profileImagePathForRoute = vm::profileImagePathFor,
                            selectedChatUsernames = selectedChatUsernames,
                            reviewCues = chatReviewCues,
                            pointedReviewCue = chatReviewCues.getOrNull(chatReviewIndex),
                            onPreviousReviewCue = {
                                if (chatReviewCues.isNotEmpty()) {
                                    chatReviewIndex = (chatReviewIndex - 1 + chatReviewCues.size) % chatReviewCues.size
                                }
                            },
                            onNextReviewCue = {
                                if (chatReviewCues.isNotEmpty()) {
                                    chatReviewIndex = (chatReviewIndex + 1) % chatReviewCues.size
                                }
                            },
                            isRouteActive = vm::isPartnerOnline,
                            onSelect = { item ->
                                if (item.locked) {
                                    pendingConversationAccess = PendingConversationAccess.Open(item.username)
                                    conversationAccessError = ""
                                } else {
                                    openConversation(item.username)
                                }
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
                            onRouteNameChange = vm::updateContactRouteInput,
                            routeLookup = vm.routeLookup,
                            routeStatus = vm.routeStatus,
                            onSearchRouteName = vm::addContactRouteFromInput,
                            showQrScanner = showRouteQrScanner,
                            onShowQrScannerChange = { showRouteQrScanner = it },
                            contacts = contacts,
                            pendingRequests = pendingContactRequests,
                            reviewCues = contactReviewCues,
                            pointedReviewCue = contactReviewCues.getOrNull(contactReviewIndex),
                            onPreviousReviewCue = {
                                if (contactReviewCues.isNotEmpty()) {
                                    contactReviewIndex = (contactReviewIndex - 1 + contactReviewCues.size) % contactReviewCues.size
                                }
                            },
                            onNextReviewCue = {
                                if (contactReviewCues.isNotEmpty()) {
                                    contactReviewIndex = (contactReviewIndex + 1) % contactReviewCues.size
                                }
                            },
                            isValidQrCode = vm::isValidNoChatQrToken,
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
                            profileImagePathForRoute = vm::profileImagePathFor,
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
                        HomeTab.Map -> MapTab(
                            active = tab == HomeTab.Map,
                            preheat = pagerState.currentPage == HomeTab.Map.ordinal ||
                                pagerState.targetPage == HomeTab.Map.ordinal ||
                                pagerState.settledPage == HomeTab.Map.ordinal,
                            myName = vm.profileName.ifBlank { "Você" },
                            myEmoji = vm.profileEmojiSymbol,
                            publicRoute = publicRoute,
                            profileMapColor = Color(vm.profileMapColorArgb),
                            useDarkMapColors = themeMode == com.null0x.chat.ui.theme.ThemeMode.DARK ||
                                (themeMode == com.null0x.chat.ui.theme.ThemeMode.SYSTEM && systemDarkTheme),
                            contacts = contacts,
                            conversations = conversations,
                            isRouteActive = vm::isPartnerOnline,
                            mapColorForRoute = { route -> vm.publicProfileFor(route).mapColorArgb },
                            locationSharingMode = vm.locationSharingMode,
                            locationSharingAllowedRoutes = vm.locationSharingAllowedRoutes,
                            sharedLocationForRoute = vm::sharedLocationForRoute,
                            onShareLocationWithAll = vm::shareLocationWithAllContacts,
                            onShareLocationWithSelected = vm::shareLocationWithSelectedContacts,
                            onDisableLocationSharing = vm::disableLocationSharing,
                            onLocationReady = vm::updateSharedLocation,
                            onMapTitleChange = { mapTitle = it.ifBlank { "Terra" } },
                            onOpenChat = {
                                vm.selectTarget(it)
                                onOpenChat(it)
                            },
                            onRecordMedia = {
                                vm.requestMediaRecorderOnOpen(it)
                                vm.selectTarget(it)
                                onOpenChat(it)
                            }
                        )
                        HomeTab.Profile -> ProfileTab(
                            profileName = vm.profileName,
                            publicRoute = publicRoute,
                            publicRouteToken = publicRouteToken,
                            routeLabel = routeLabel,
                            profileBio = vm.profileBioText,
                            profileImagePath = myProfileImagePath,
                            reviewCues = profileReviewCues,
                            pointedReviewCue = profileReviewCues.getOrNull(profileReviewIndex),
                            onPreviousReviewCue = {
                                if (profileReviewCues.isNotEmpty()) {
                                    profileReviewIndex = (profileReviewIndex - 1 + profileReviewCues.size) % profileReviewCues.size
                                }
                            },
                            onNextReviewCue = {
                                if (profileReviewCues.isNotEmpty()) {
                                    profileReviewIndex = (profileReviewIndex + 1) % profileReviewCues.size
                                }
                            },
                            onProfileBioSave = { bio ->
                                profileAuthError = ""
                                pendingProfileChange = PendingProfileChange.Bio(bio)
                            },
                            onProfileImageOpen = { showProfilePhotoPreview = true },
                            onProfileImagePick = { profileImagePickerLauncher.launch("image/*") },
                            onEditProfile = { showProfileSheet = true },
                            onShareRoute = { showShareRoute = true }
                        )
                        HomeTab.Settings -> SettingsTab(
                            publicRoute = publicRoute,
                            publicRouteToken = publicRouteToken,
                            bottomPadding = 24.dp,
                            themeMode = themeMode,
                            onThemeModeChange = { ThemePreference.setThemeMode(context, it) },
                            keepViewedMessages = vm.isKeepViewedMessagesEnabled(),
                            onKeepViewedMessagesChange = vm::updateKeepViewedMessagesPreference,
                            screenshotsEnabled = vm.isScreenshotsEnabled(),
                            onScreenshotsEnabledChange = vm::updateScreenshotsPreference,
                            showChatPresenceStatus = vm.showChatPresenceStatus,
                            onShowChatPresenceStatusChange = vm::updateChatPresenceStatusVisibility,
                            showChatLastActivity = vm.showChatLastActivity,
                            onShowChatLastActivityChange = vm::updateChatLastActivityVisibility,
                            nullAiEnabled = vm.nullAiEnabled,
                            nullAiUnavailableReason = vm.nullAiUnavailableReason(),
                            onNullAiEnabledChange = vm::updateNullAiEnabled,
                            contacts = contacts,
                            locationSharingMode = vm.locationSharingMode,
                            locationSharingAllowedRoutes = vm.locationSharingAllowedRoutes,
                            locationEmergencyAllowedRoutes = vm.locationEmergencyAllowedRoutes,
                            onShareLocationWithAll = { vm.shareLocationWithAllContacts(distanceLocation) },
                            onShareLocationWithSelected = { vm.shareLocationWithSelectedContacts(it, distanceLocation) },
                            onShareLocationInEmergency = { vm.shareLocationInEmergency(it) },
                            onDisableLocationSharing = vm::disableLocationSharing,
                            blockedContacts = blockedContacts,
                            onUnblockContact = vm::unblockContact,
                            onContactsBackupRequested = vm::contactsBackupJson,
                            onSignOut = onSignOut,
                        )
                    }
                }
            }
        }
    }

    if (showProfileSheet) {
        ProfileIdentityBottomSheet(
            currentName = vm.profileName,
            onDismiss = { showProfileSheet = false },
            onSave = { name ->
                profileAuthError = ""
                pendingProfileChange = PendingProfileChange.Identity(name)
                showProfileSheet = false
            }
        )
    }

    if (showProfilePhotoPreview) {
        ProfilePhotoPreviewOverlay(
            profileImagePath = myProfileImagePath,
            onDismiss = { showProfilePhotoPreview = false },
            onChangePhoto = {
                showProfilePhotoPreview = false
                profileImagePickerLauncher.launch("image/*")
            }
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showShareRoute,
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
    ) {
        val shareToken = remember(publicRoute) {
            vm.routeTokenFor(publicRoute)
        }
        RouteShareScreen(
            tokenLabel = shareToken.ifBlank { "Aguardando parceiro..." },
            onBack = { showShareRoute = false }
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
            profileImagePath = vm.profileImagePathFor(target),
            onBack = { showRouteProfile = false },
            onSaveLocalName = { route, name -> vm.setLocalNameForRoute(route, name) },
            contactBlocked = vm.isContactBlocked(target),
            onSendMessage = {
                vm.selectTarget(target)
                showRouteProfile = false
                onOpenChat(target)
            },
            onRecordMedia = {
                vm.requestMediaRecorderOnOpen(target)
                vm.selectTarget(target)
                showRouteProfile = false
                onOpenChat(target)
            },
            onRemoveContact = {
                vm.removeContact(target)
                showRouteProfile = false
            },
            onBlockContact = {
                vm.blockContact(target)
                showRouteProfile = false
            },
            onUnblockContact = {
                vm.unblockContact(target)
            }
        )
    }

    pendingProfileChange?.let { change ->
        PasswordConfirmDialog(
            title = "Confirmar edição pública",
            message = "Digite a senha alfanumérica para publicar alterações no perfil.",
            error = profileAuthError,
            baseThemeMode = themeMode,
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

    pendingConversationAccess?.let { request ->
        val target = request.username
        PasswordConfirmDialog(
            title = "Entrar no chat trancado",
            message = "Digite a senha para abrir esta conversa. O app continua aberto.",
            error = conversationAccessError,
            baseThemeMode = themeMode,
            onDismiss = {
                pendingConversationAccess = null
                conversationAccessError = ""
            },
            onConfirm = { password ->
                val result = AppSecurityManager.verifyPassword(context, password)
                if (result.isSuccess) {
                    vm.setConversationLocked(target, false)
                    openConversation(target)
                    pendingConversationAccess = null
                    conversationAccessError = ""
                } else {
                    conversationAccessError = "Senha errada, tente novamente"
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
    profileMapColor: Color,
    hasSearch: Boolean,
    publicRouteToken: String,
    profileImagePathForRoute: (String) -> String,
    selectedChatUsernames: Set<String>,
    reviewCues: List<ReviewCue>,
    pointedReviewCue: ReviewCue?,
    onPreviousReviewCue: () -> Unit,
    onNextReviewCue: () -> Unit,
    isRouteActive: (String) -> Boolean,
    onSelect: (ChatViewModel.ConversationPreview) -> Unit,
    onToggleSelection: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val summaryHeight = if (searchSummary != null) 42.dp else 0.dp
    val cueHeight = if (reviewCues.isNotEmpty()) 86.dp else 0.dp
    val listHeight = (maxHeight - summaryHeight - cueHeight).coerceAtLeast(260.dp)
        Column(modifier = Modifier.fillMaxSize()) {
            SearchSummaryRow(searchSummary)
            ReviewCueCard(
                cues = reviewCues,
                pointedCue = pointedReviewCue,
                onPrevious = onPreviousReviewCue,
                onNext = onNextReviewCue
            )
            ConversationsPanelSelection(
                modifier = Modifier
                    .height(listHeight)
                    .imePadding(),
                conversations = conversations,
                myUsername = myUsername,
                profileEmoji = profileEmoji,
                profileMapColor = profileMapColor,
                hasSearch = hasSearch,
                publicRouteToken = publicRouteToken,
                profileImagePathForRoute = profileImagePathForRoute,
                selectedChatUsernames = selectedChatUsernames,
                highlightedUsernames = reviewCues.map { it.key }.toSet(),
                pointedUsername = pointedReviewCue?.key,
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
    profileMapColor: Color,
    hasSearch: Boolean,
    publicRouteToken: String,
    profileImagePathForRoute: (String) -> String,
    selectedChatUsernames: Set<String>,
    highlightedUsernames: Set<String>,
    pointedUsername: String?,
    isRouteActive: (String) -> Boolean,
    onSelect: (ChatViewModel.ConversationPreview) -> Unit,
    onToggleSelection: (String) -> Unit
) {
    if (conversations.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth()) {
            EmptyState(
                myUsername = myUsername,
                profileEmoji = profileEmoji,
                profileMapColor = profileMapColor,
                hasSearch = hasSearch,
                publicRouteToken = publicRouteToken
            )
        }
        return
    }

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
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
                    locked = item.locked,
                    highlighted = highlightedUsernames.contains(item.username),
                    pointed = pointedUsername == item.username,
                    active = if (item.isAi) false else isRouteActive(item.username),
                    imagePath = profileImagePathForRoute(item.username),
                    onClick = {
                        if (selectedChatUsernames.isNotEmpty()) {
                            onToggleSelection(item.username)
                        } else {
                            onSelect(item)
                        }
                    },
                    onLongClick = {
                        onToggleSelection(item.username)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRowSelectable(
    item: ChatViewModel.ConversationPreview,
    selected: Boolean,
    locked: Boolean,
    highlighted: Boolean,
    pointed: Boolean,
    active: Boolean,
    imagePath: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val highlightAlpha = pulsingHighlightAlpha(highlighted)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha)
            locked -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)
            else -> Color.Transparent
        },
        tonalElevation = if (highlighted || locked) 1.dp else 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isAi) {
                        NullAiNetworkAvatar(backgroundColor = Color(item.mapColorArgb))
                    } else {
                        InitialAvatar(
                            text = item.displayName,
                            emoji = item.emoji,
                            backgroundColor = Color(item.mapColorArgb),
                            active = active,
                            imagePath = imagePath
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
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
                    if (pointed) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = formatTime(item.lastTimestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SnapCountBadge(unread = item.unreadCount)
                    if (!item.isAi) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (locked) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Conversa trancada",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            ListSeparator(modifier = Modifier.padding(start = 64.dp))
        }
    }
}

@Composable
private fun ContactsTab(
    onRouteNameChange: (String) -> Unit,
    routeLookup: ChatViewModel.RouteLookup?,
    routeStatus: String,
    onSearchRouteName: () -> Unit,
    showQrScanner: Boolean,
    onShowQrScannerChange: (Boolean) -> Unit,
    contacts: List<ChatViewModel.ContactPreview>,
    pendingRequests: List<ChatViewModel.ConversationPreview>,
    reviewCues: List<ReviewCue>,
    pointedReviewCue: ReviewCue?,
    onPreviousReviewCue: () -> Unit,
    onNextReviewCue: () -> Unit,
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
    profileImagePathForRoute: (String) -> String,
    onOpenProfile: (String) -> Unit,
    onSelect: (String) -> Unit,
    selectedContactUsername: String?,
    onSelectContactForDeletion: (String) -> Unit
) {
    val acceptedNoticeShown = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ReviewCueCard(
                cues = reviewCues,
                pointedCue = pointedReviewCue,
                onPrevious = onPreviousReviewCue,
                onNext = onNextReviewCue
            )
        }
        item {
            RouteSearchPanel(
                onRouteNameChange = onRouteNameChange,
                routeLookup = routeLookup,
                routeStatus = routeStatus,
                highlighted = reviewCues.any { it.kind == ReviewCueKind.ContactLookup },
                pointed = pointedReviewCue?.kind == ReviewCueKind.ContactLookup,
                onSearchRouteName = onSearchRouteName,
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
                        mapColorArgb = item.mapColorArgb,
                        imagePath = profileImagePathForRoute(item.username),
                        active = isRouteActive(item.username),
                        onClick = { onOpenProfile(item.username) },
                        statusLabel = "Pedido recebido",
                        trailingActionLabel = "Aceitar",
                        trailingActionIcon = Icons.Filled.Done,
                        trailingActionProminent = true,
                        onTrailingAction = { onAcceptContact(item.username) },
                        onLongClick = { onSelectContactForDeletion(item.username) },
                        selected = selectedContactUsername == item.username,
                        highlighted = reviewCues.any { it.key == "request:${item.username}" },
                        pointed = pointedReviewCue?.key == "request:${item.username}"
                    )
                }
            }
        }
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
                        mapColorArgb = item.mapColorArgb,
                        imagePath = profileImagePathForRoute(item.username),
                        active = item.accepted && isRouteActive(item.username),
                        onClick = { onSelect(item.username) },
                        onAvatarClick = { onOpenProfile(item.username) },
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
    publicRoute: String,
    publicRouteToken: String,
    routeLabel: String,
    profileBio: String,
    profileImagePath: String,
    reviewCues: List<ReviewCue>,
    pointedReviewCue: ReviewCue?,
    onPreviousReviewCue: () -> Unit,
    onNextReviewCue: () -> Unit,
    onProfileBioSave: (String) -> Unit,
    onProfileImageOpen: () -> Unit,
    onProfileImagePick: () -> Unit,
    onEditProfile: () -> Unit,
    onShareRoute: () -> Unit
) {
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
            ReviewCueCard(
                cues = reviewCues,
                pointedCue = pointedReviewCue,
                onPrevious = onPreviousReviewCue,
                onNext = onNextReviewCue
            )
        }
        item {
            val identityHighlighted = reviewCues.any { it.kind == ReviewCueKind.ProfileIdentity }
            val identityPointed = pointedReviewCue?.kind == ReviewCueKind.ProfileIdentity
            val bioHighlighted = reviewCues.any { it.kind == ReviewCueKind.ProfileBio }
            val bioPointed = pointedReviewCue?.kind == ReviewCueKind.ProfileBio
            val identityAlpha = pulsingHighlightAlpha(identityHighlighted)
            val bioAlpha = pulsingHighlightAlpha(bioHighlighted)
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
                            Box {
                                Box(modifier = Modifier.clickable(onClick = onProfileImageOpen)) {
                                    InitialAvatar(
                                        text = profileName.ifBlank { publicRouteToken.ifBlank { publicRoute } },
                                        prominent = false,
                                        large = true,
                                        imagePath = profileImagePath
                                    )
                                }
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(30.dp)
                                        .clickable(onClick = onProfileImagePick),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    tonalElevation = 3.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Trocar foto",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = profileName.ifBlank { publicRouteToken.ifBlank { "Token da rota" } },
                                    style = MaterialTheme.typography.titleMedium,
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
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (identityHighlighted) identityAlpha else 0.14f),
                            tonalElevation = if (identityHighlighted) 1.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Editar perfil",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                if (identityPointed) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(16.dp)
                                    )
                                }
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
                            Text(
                                text = "Compartilhar",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
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
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (bioHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = bioAlpha) else Color.Transparent,
                        tonalElevation = if (bioHighlighted) 1.dp else 0.dp
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = bioDraft,
                                onValueChange = { bioDraft = limitUtf8Bytes(it, 4 * 1024) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 5,
                                maxLines = 10,
                                label = { Text("Escreva tudo que quiser") },
                                placeholder = { Text("Até 4 KB visíveis para quem visitar a rota.") },
                                shape = RoundedCornerShape(14.dp),
                                colors = primalisBareOutlinedTextFieldColors()
                            )
                            if (bioPointed) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(18.dp)
                                )
                            }
                        }
                    }
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
private fun ProfilePhotoPreviewOverlay(
    profileImagePath: String,
    onDismiss: () -> Unit,
    onChangePhoto: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val avatarBitmap = remember(profileImagePath) {
        profileImagePath.takeIf { it.isNotBlank() }
            ?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.96f),
        contentColor = Color.White
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap.asImageBitmap(),
                    contentDescription = "Foto do perfil",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 10.dp, vertical = 72.dp)
                )
            } else {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(220.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Foto do perfil",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(120.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.14f),
                    tonalElevation = 0.dp
                ) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Opções da foto",
                            tint = Color.White
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Trocar foto") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onChangePhoto()
                        }
                    )
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
private fun EmptyState(
    myUsername: String,
    profileEmoji: String,
    profileMapColor: Color,
    hasSearch: Boolean,
    publicRouteToken: String
) {
    val context = LocalContext.current
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
                    backgroundColor = if (hasSearch) null else profileMapColor,
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
                if (!hasSearch && publicRouteToken.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            SensitiveClipboard.copy(context, "Token de rota", publicRouteToken)
                            Toast.makeText(context, "Token copiado por 60 segundos", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Copiar token")
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteSearchPanel(
    onRouteNameChange: (String) -> Unit,
    routeLookup: ChatViewModel.RouteLookup?,
    routeStatus: String,
    highlighted: Boolean,
    pointed: Boolean,
    onSearchRouteName: () -> Unit,
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
                highlighted = highlighted,
                pointed = pointed,
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
    highlighted: Boolean,
    pointed: Boolean,
    onAddContact: (String) -> Unit,
    onRemoveContact: (String) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val added = alreadyAdded || requestSent
    var showRemoveConfirm by rememberSaveable(routeLookup.username) { mutableStateOf(false) }
    val highlightAlpha = pulsingHighlightAlpha(highlighted)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha) else Color.Transparent,
        tonalElevation = if (highlighted) 1.dp else 0.dp,
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
                InitialAvatar(
                    text = routeLookup.displayName,
                    emoji = routeLookup.emoji,
                    backgroundColor = Color(routeLookup.mapColorArgb)
                )
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
                if (pointed) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
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
        PrimalisAlertDialog(
            title = "Remover este contato?",
            message = "Ele sai da sua lista agora e o botão volta para Adicionar. As conversas existentes continuam disponíveis no histórico.",
            icon = Icons.Filled.Delete,
            confirmLabel = "Remover",
            dismissLabel = "Manter",
            onConfirm = {
                showRemoveConfirm = false
                onRemoveContact(routeLookup.username)
            },
            onDismiss = { showRemoveConfirm = false },
            destructive = true
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    name: String,
    username: String,
    emoji: String,
    mapColorArgb: Int,
    imagePath: String = "",
    onClick: () -> Unit,
    onAvatarClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    statusLabel: String? = null,
    trailingActionLabel: String? = null,
    trailingActionIcon: ImageVector? = null,
    trailingActionEnabled: Boolean = true,
    trailingActionProminent: Boolean = false,
    onTrailingAction: (() -> Unit)? = null,
    selected: Boolean = false,
    highlighted: Boolean = false,
    pointed: Boolean = false,
    active: Boolean = false
) {
    val highlightAlpha = pulsingHighlightAlpha(highlighted)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = when {
            selected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha)
            else -> Color.Transparent
        },
        tonalElevation = if (selected || highlighted) 1.dp else 0.dp,
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
                Box(
                    modifier = if (onAvatarClick != null) {
                        Modifier.clickable(onClick = onAvatarClick)
                    } else {
                        Modifier
                    }
                ) {
                    InitialAvatar(
                        text = name,
                        emoji = emoji,
                        backgroundColor = Color(mapColorArgb),
                        active = active,
                        imagePath = imagePath
                    )
                }
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
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
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
                if (pointed) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            ListSeparator(modifier = Modifier.padding(start = 68.dp))
        }
    }
}

@Composable
private fun NullAiNetworkAvatar(
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val contentColor = readableProfileAvatarContentColor(backgroundColor)
    Box(
        modifier = modifier.size(52.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = backgroundColor,
            modifier = Modifier.size(42.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val nodes = listOf(
                    Offset(w * 0.24f, h * 0.50f),
                    Offset(w * 0.48f, h * 0.34f),
                    Offset(w * 0.48f, h * 0.66f),
                    Offset(w * 0.74f, h * 0.24f),
                    Offset(w * 0.74f, h * 0.50f),
                    Offset(w * 0.74f, h * 0.76f)
                )
                val lines = listOf(0 to 1, 0 to 2, 1 to 3, 1 to 4, 2 to 4, 2 to 5)
                val lineColor = contentColor.copy(alpha = 0.68f)
                lines.forEach { (from, to) ->
                    drawLine(
                        color = lineColor,
                        start = nodes[from],
                        end = nodes[to],
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                nodes.forEachIndexed { index, offset ->
                    drawCircle(
                        color = contentColor,
                        radius = if (index == 0) 3.6.dp.toPx() else 3.1.dp.toPx(),
                        center = offset
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewCueCard(
    cues: List<ReviewCue>,
    pointedCue: ReviewCue?,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    if (cues.isEmpty()) return
    val cue = pointedCue ?: cues.first()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = cues.size > 1,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Item anterior"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (cue.key.startsWith("first-open:")) "Primeiro acesso" else "Revisar novidade",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = cue.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = cue.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            IconButton(
                onClick = onNext,
                enabled = cues.size > 1,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Proximo item"
                )
            }
        }
    }
}

@Composable
private fun pulsingHighlightAlpha(enabled: Boolean): Float {
    if (!enabled) return 0f
    val transition = rememberInfiniteTransition(label = "review-highlight")
    return transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 920),
            repeatMode = RepeatMode.Reverse
        ),
        label = "review-highlight-alpha"
    ).value
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
