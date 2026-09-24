package com.null0x.chat.ui.chat

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import com.null0x.chat.model.DeliveryState
import com.null0x.chat.model.Message
import com.null0x.chat.network.TorManager
import com.null0x.chat.security.SensitiveClipboard
import com.null0x.chat.ui.common.PrimalisAlertDialog
import com.null0x.chat.ui.common.SystemBarsColorEffect
import com.null0x.chat.ui.common.WindowDispositionScaffold
import com.null0x.chat.ui.common.primalisBareOutlinedTextFieldColors
import com.null0x.chat.ui.home.InitialAvatar
import com.null0x.chat.ui.maskedRouteLabel
import com.null0x.chat.ui.profile.RouteProfileScreen
import com.null0x.chat.ui.security.ProtectedWindowCapture
import com.null0x.chat.viewmodel.ChatViewModel
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.null0x.chat.ui.theme.AppearancePreference
import com.null0x.chat.ui.theme.ThemeMode
import com.null0x.chat.ui.theme.readableContentColor
import com.null0x.chat.ui.theme.themeBackgroundColor

private val ChatHeaderHeight = 86.dp
private val ChatDockButtonOffset = 40.dp
private val ChatDockBottomLift = 10.dp
private val ChatDockActionReserve = 160.dp
private val ChatDockMediaOpenEndOffset = (-112).dp
private val MessageBarThickness = 4.dp
private val MessageBodyInset = 4.dp
private const val ChatSettingsRoute = "chat_settings"
private const val BlockDecisionHelpRoute = "block_decision_help"
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    themeMode: ThemeMode,
    openChatRequestVersion: Int = 0,
    onBack: () -> Unit
) {
    var input by rememberSaveable(vm.targetUsername) { mutableStateOf(vm.draftFor(vm.targetUsername)) }
    var showTextInput by rememberSaveable(vm.targetUsername) { mutableStateOf(input.isNotBlank()) }
    var profileRoute by rememberSaveable { mutableStateOf("") }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showChatSettings by rememberSaveable { mutableStateOf(false) }
    var chatSettingsRoute by rememberSaveable { mutableStateOf(ChatSettingsRoute) }
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    var commentTarget by remember { mutableStateOf<Message?>(null) }
    var selectableMessageIds by remember { mutableStateOf(emptySet<String>()) }
    var showEphemeralCamera by rememberSaveable { mutableStateOf(false) }
    var cameraOpenedFromProfile by rememberSaveable { mutableStateOf(false) }
    var cameraOpenedForRoute by rememberSaveable { mutableStateOf("") }
    var isAudioRecording by remember { mutableStateOf(false) }
    var composerFocusRequestVersion by rememberSaveable { mutableStateOf(0) }
    var unreadComposerFocusKey by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val appearance by AppearancePreference.appearance.collectAsState()
    val cameraSystemBarColor = Color.Black
    val chatNavigationBarColor = when {
        showEphemeralCamera -> cameraSystemBarColor
        else -> MaterialTheme.colorScheme.background
    }
    SystemBarsColorEffect(
        statusBarColor = Color.Transparent,
        navigationBarColor = chatNavigationBarColor,
        statusBarDarkIcons = !showEphemeralCamera && MaterialTheme.colorScheme.background.luminance() > 0.5f
    )
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val composerFocusRequester = remember { FocusRequester() }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cameraGranted = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted) {
            cameraOpenedForRoute = vm.targetUsername
            showEphemeralCamera = true
        } else {
            cameraOpenedFromProfile = false
            cameraOpenedForRoute = ""
            Toast.makeText(context, "Permita a câmera", Toast.LENGTH_SHORT).show()
        }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Permita o microfone", Toast.LENGTH_SHORT).show()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            deleteEphemeralMedia(context)
        }
    }
    fun openEphemeralMediaRecorder() {
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted) {
            keyboardController?.hide()
            cameraOpenedForRoute = vm.targetUsername
            showEphemeralCamera = true
        } else {
            cameraOpenedForRoute = vm.targetUsername
            mediaPermissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA)
            )
        }
    }
    fun closeEphemeralMediaRecorder() {
        showEphemeralCamera = false
        if (cameraOpenedFromProfile) {
            showProfile = true
        }
        cameraOpenedFromProfile = false
        cameraOpenedForRoute = ""
    }
    val peers = remember(vm.startedConversations.toList(), vm.targetUsername) {
        val ordered = vm.startedConversations.map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        val current = vm.targetUsername.trim()
        if (current.isNotBlank() && !ordered.contains(current)) {
            ordered.add(0, current)
        }
        ordered.distinct()
    }
    val selectedIndex = peers.indexOf(vm.targetUsername).let { if (it >= 0) it else 0 }
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { if (peers.isEmpty()) 1 else peers.size }
    )

    BackHandler(enabled = showChatSettings) {
        if (chatSettingsRoute == BlockDecisionHelpRoute) {
            chatSettingsRoute = ChatSettingsRoute
        } else {
            showChatSettings = false
        }
    }
    BackHandler(enabled = showProfile && !showChatSettings) {
        showProfile = false
    }
    BackHandler(enabled = !showProfile && !showChatSettings && selectedMessage != null) {
        selectedMessage = null
    }
    BackHandler(enabled = !showProfile && !showChatSettings && selectedMessage == null && showTextInput) {
        showTextInput = false
        keyboardController?.hide()
    }
    BackHandler(enabled = !showProfile && !showChatSettings && selectedMessage == null && !showTextInput) {
        keyboardController?.hide()
        onBack()
    }
    ProtectedWindowCapture(enabled = !vm.isCurrentChatScreenshotsEnabled())

    LaunchedEffect(peers, vm.targetUsername) {
        val target = vm.targetUsername.trim()
        val index = peers.indexOf(target)
        if (index >= 0 && pagerState.currentPage != index) {
            pagerState.scrollToPage(index)
        }
    }

    LaunchedEffect(peers, pagerState) {
        snapshotFlow { peers.getOrNull(pagerState.settledPage) }
            .filterNotNull()
            .map { it.trim() }
            .distinctUntilChanged()
            .collect { username ->
                if (username.isNotBlank() && username != vm.targetUsername) {
                    vm.selectTarget(username)
                }
            }
    }

    val headerUser = peers.getOrNull(pagerState.currentPage)?.trim().orEmpty().ifBlank { vm.targetUsername }
    val isSelfChat = vm.isSelfConversation(headerUser)
    val chatTitle = if (isSelfChat) {
        vm.selfChatDisplayName()
    } else {
        vm.chatTitleFor(headerUser).ifBlank { maskedRouteLabel(headerUser) }
    }
    val headerConversation = remember(headerUser, vm.conversationPreviews()) {
        vm.conversationPreviews().firstOrNull { it.username == headerUser }
    }
    val headerUserAvailable = !isSelfChat && vm.showChatPresenceStatus && vm.isPartnerOnline(headerUser)
    val chatPresenceLabel = if (headerUserAvailable) {
        "disponível"
    } else {
        ""
    }
    val chatLastActivityLabel = if (!headerUserAvailable && vm.showChatLastActivity && (headerConversation?.lastTimestamp ?: 0L) > 0L) {
        "Última atividade: ${chatActivityElapsedLabel(headerConversation?.lastTimestamp ?: 0L)}"
    } else {
        ""
    }
    val chatHeaderSubtitle = listOfNotNull(
        chatPresenceLabel.takeIf { it.isNotBlank() },
        chatLastActivityLabel.takeIf { it.isNotBlank() }
    ).joinToString(" • ")
    val unreadHintCount = vm.unreadEntryCountFor(headerUser)
    val currentChatLoaded = vm.isCurrentChatLoaded()
    val currentMessages = vm.messagesFor(vm.targetUsername)
    val displayedMessages = currentMessages

    LaunchedEffect(headerUser) {
        val draft = vm.draftFor(headerUser)
        input = draft
        val shouldOpenForUnread = unreadHintCount > 0
        showTextInput = draft.isNotBlank() || shouldOpenForUnread
        if (shouldOpenForUnread) {
            unreadComposerFocusKey = "$headerUser:$unreadHintCount"
            composerFocusRequestVersion += 1
        }
        if (vm.consumeMediaRecorderOnOpen(headerUser)) {
            openEphemeralMediaRecorder()
        }
    }

    LaunchedEffect(headerUser, unreadHintCount) {
        if (unreadHintCount <= 0) return@LaunchedEffect
        val focusKey = "$headerUser:$unreadHintCount"
        if (focusKey != unreadComposerFocusKey) {
            showTextInput = true
            unreadComposerFocusKey = focusKey
            composerFocusRequestVersion += 1
        }
    }

    LaunchedEffect(context) {
        TorManager.ensureNetworkMonitoring(context)
    }

    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previousMode = activity?.window?.attributes?.softInputMode
        activity?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {
            if (previousMode != null) {
                activity?.window?.setSoftInputMode(previousMode)
            }
        }
    }

    LaunchedEffect(showChatSettings) {
        if (showChatSettings) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    LaunchedEffect(showEphemeralCamera) {
        if (showEphemeralCamera) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    DisposableEffect(lifecycleOwner, showEphemeralCamera, showChatSettings) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && (showEphemeralCamera || showChatSettings)) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(vm.targetUsername) {
        if (showEphemeralCamera && cameraOpenedForRoute.isNotBlank() && cameraOpenedForRoute != vm.targetUsername) {
            showEphemeralCamera = false
            cameraOpenedFromProfile = false
            cameraOpenedForRoute = ""
        }
    }

    LaunchedEffect(openChatRequestVersion) {
        if (openChatRequestVersion > 0 && showEphemeralCamera) {
            showEphemeralCamera = false
            cameraOpenedFromProfile = false
            cameraOpenedForRoute = ""
        }
    }

    LaunchedEffect(headerUser, input) {
        val target = headerUser.trim()
        if (target.isBlank()) return@LaunchedEffect
        if (input.isBlank()) {
            vm.updateLocalTyping(target, false)
            return@LaunchedEffect
        }
        vm.updateLocalTyping(target, true)
        delay(1_000)
        vm.updateLocalTyping(target, false)
    }

    LaunchedEffect(headerUser) {
        val target = headerUser.trim()
        if (target.isBlank()) return@LaunchedEffect
        while (true) {
            vm.refreshLocalChatPresence(target)
            delay(15_000)
        }
    }

    LaunchedEffect(currentMessages, selectedMessage?.id) {
        val selectedId = selectedMessage?.id ?: return@LaunchedEffect
        if (currentMessages.none { it.id == selectedId }) {
            selectedMessage = null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val dragScope = rememberCoroutineScope()
        val visibleBottomPadding = if (showTextInput) {
            188.dp
        } else {
            170.dp
        }
        val screenWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val closeThreshold = screenWidthPx * 0.18f
        val flingThreshold = 700f
        val offsetX = remember(screenWidthPx) { Animatable(screenWidthPx) }
        var dragX by remember { mutableFloatStateOf(0f) }
        var keyboardHiddenDuringDrag by remember { mutableStateOf(false) }

        LaunchedEffect(screenWidthPx) {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .draggable(
                    enabled = !showEphemeralCamera && !isAudioRecording,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (!keyboardHiddenDuringDrag) {
                            keyboardController?.hide()
                            keyboardHiddenDuringDrag = true
                        }
                        dragX += delta
                        dragScope.launch {
                            val nextOffset = (offsetX.value + delta).coerceIn(-screenWidthPx, screenWidthPx)
                            offsetX.snapTo(nextOffset)
                        }
                    },
                    onDragStopped = { velocity ->
                        val shouldClose = kotlin.math.abs(dragX) >= closeThreshold ||
                            kotlin.math.abs(velocity) >= flingThreshold
                        val direction = when {
                            kotlin.math.abs(velocity) >= flingThreshold -> velocity
                            else -> dragX
                        }

                        if (shouldClose) {
                            val target = if (direction >= 0f) screenWidthPx else -screenWidthPx
                            dragScope.launch {
                                offsetX.animateTo(target, animationSpec = tween(durationMillis = 170))
                                onBack()
                            }
                        } else {
                            dragScope.launch {
                                offsetX.animateTo(0f, animationSpec = tween(durationMillis = 170))
                            }
                        }
                        dragX = 0f
                        keyboardHiddenDuringDrag = false
                    }
                )
                .background(MaterialTheme.colorScheme.background)
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    Column {
                        if (selectedMessage != null) {
                            MessageActionHeader(
                                message = selectedMessage,
                                selectionEnabled = selectedMessage?.let { selectableMessageIds.contains(it.id) } == true,
                                onClose = { selectedMessage = null },
                                onComment = {
                                    if (selectedMessage?.text?.isNotBlank() == true) {
                                        commentTarget = selectedMessage
                                        showTextInput = true
                                    }
                                    selectedMessage = null
                                },
                                onToggleSelection = {
                                    selectedMessage?.let { message ->
                                        selectableMessageIds = if (selectableMessageIds.contains(message.id)) {
                                            selectableMessageIds - message.id
                                        } else {
                                            selectableMessageIds + message.id
                                        }
                                    }
                                    selectedMessage = null
                                },
                                onDelete = {
                                    selectedMessage?.let(vm::deleteMessage)
                                    selectedMessage = null
                                }
                            )
                        } else {
                            ChatHeader(
                                title = chatTitle,
                                subtitle = chatHeaderSubtitle,
                                onBack = onBack,
                                onOpenProfile = {
                                    profileRoute = headerUser
                                    showProfile = true
                                },
                                onCopyRoute = {
                                    SensitiveClipboard.copy(context, "Rota contato", maskedRouteLabel(headerUser))
                                    Toast.makeText(context, "Token sensível copiado por 60 segundos", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        if (unreadHintCount > 0) {
                            UnreadHintBar(
                                count = unreadHintCount,
                                onDismiss = { vm.clearUnreadEntryHint(headerUser) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding()),
                    beyondViewportPageCount = 2,
                    flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
                    userScrollEnabled = false
                ) { page ->
                    val user = peers.getOrNull(page)?.trim().orEmpty()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        MessageList(
                            conversationKey = user,
                            messages = if (user == vm.targetUsername) displayedMessages else vm.messagesFor(user),
                            privacyNotices = vm.privacyNotices(),
                            loaded = currentChatLoaded && user == vm.targetUsername,
                            emptyStateText = "Sem mensagens ainda",
                            unreadHintCount = unreadHintCount,
                            selectableMessageIds = selectableMessageIds,
                            onMessageLongPress = { selectedMessage = it },
                            ownerTitle = { message ->
                                if (message.isMine) "Voce" else chatTitleForMessage(vm, user)
                            },
                            themeMode = themeMode,
                            appearance = appearance,
                            modifier = Modifier.fillMaxSize(),
                            bottomContentPadding = visibleBottomPadding,
                            composerExpanded = showTextInput
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                val marker = vm.emojiForRoute(headerUser)
                    .ifBlank { compactOnionRoute(vm.chatTitleFor(headerUser)).take(1).ifBlank { "?" }.uppercase() }
                val markerBackgroundColor = Color(vm.publicProfileFor(headerUser).mapColorArgb)
                val markerImagePath = vm.profileImagePathFor(headerUser)
                val showPresenceBadge = !isSelfChat && vm.isPartnerChatOpen(headerUser)
                if (marker.isNotBlank() && showPresenceBadge) {
                    PartnerMarkerBadge(
                        text = vm.chatTitleFor(headerUser).ifBlank { headerUser },
                        marker = marker,
                        backgroundColor = markerBackgroundColor,
                        imagePath = markerImagePath,
                        typing = vm.isPartnerTyping(headerUser),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                start = 18.dp,
                                bottom = if (showTextInput) 78.dp else 4.dp
                            )
                    )
                }

                MessageComposer(
                    input = input,
                    showTextInput = showTextInput,
                    focusRequester = composerFocusRequester,
                    focusRequestVersion = composerFocusRequestVersion,
                    appearance = appearance,
                    trailingActionSpace = if (showTextInput) ChatDockActionReserve else 0.dp,
                    commentTarget = commentTarget,
                    commentOwnerTitle = commentTarget?.let { message ->
                        if (message.isMine) "Voce" else chatTitleForMessage(vm, headerUser)
                    }.orEmpty(),
                    enabled = true,
                    placeholder = "Mensagem",
                    onInputChange = {
                        input = it
                        vm.updateDraft(headerUser, it)
                    },
                    onShowTextInputChange = {
                        if (it && !showTextInput) {
                            composerFocusRequestVersion += 1
                        }
                        showTextInput = it
                    },
                    onSend = {
                        val text = input.trim()
                        if (text.isNotBlank()) {
                            vm.updateLocalTyping(headerUser, false)
                            vm.sendTo(headerUser, commentTarget?.let { formatCommentMessage(it, text, vm, headerUser) } ?: text)
                            commentTarget = null
                            input = ""
                            vm.clearDraft(headerUser)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -ChatDockBottomLift),
                    buttonOffsetX = -ChatDockButtonOffset,
                    showButtonWhenOpen = !showTextInput,
                    onClearComment = { commentTarget = null }
                )

                FloatingMediaButtonOverlay(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = ChatDockBottomLift),
                    buttonAlignment = if (showTextInput) Alignment.BottomEnd else Alignment.BottomCenter,
                    buttonOffsetX = if (showTextInput) ChatDockMediaOpenEndOffset else ChatDockButtonOffset,
                    isAudioRecording = isAudioRecording,
                    onAudioRecordingStateChange = { isAudioRecording = it },
                    onMediaClick = {
                        openEphemeralMediaRecorder()
                    },
                    onAudioHoldStart = {
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    },
                    onAudioRecorded = {
                        vm.sendEphemeralMediaTo(headerUser, ChatViewModel.EphemeralMediaType.AUDIO)
                    },
                    onAudioPermissionNeeded = {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )

                ChatBottomActions(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = ChatDockBottomLift),
                    themeMode = themeMode,
                    chatLocked = vm.isConversationLocked(headerUser),
                    chatLockEnabled = true,
                    onLockChat = {
                        vm.setConversationLocked(headerUser, true)
                        keyboardController?.hide()
                        Toast.makeText(context, "Entrada do chat trancada", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    onClear = { vm.clearConversation(headerUser) },
                    onOpenSettings = {
                        keyboardController?.hide()
                        chatSettingsRoute = ChatSettingsRoute
                        showChatSettings = true
                    },
                    settingsEnabled = true
                )
            }
        }
    }

    if (showEphemeralCamera) {
        BackHandler {
            closeEphemeralMediaRecorder()
        }
        EphemeralCameraOverlay(
            onDismiss = {
                closeEphemeralMediaRecorder()
            },
            onReviewSend = { isVideo ->
                vm.sendEphemeralMediaTo(
                    headerUser,
                    if (isVideo) {
                        ChatViewModel.EphemeralMediaType.VIDEO
                    } else {
                        ChatViewModel.EphemeralMediaType.PHOTO
                    }
                )
                showEphemeralCamera = false
                cameraOpenedFromProfile = false
                cameraOpenedForRoute = ""
            }
        )
    }

    AnimatedVisibility(
        visible = showProfile,
        enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + fadeOut()
    ) {
        val profileTarget = profileRoute.ifBlank { vm.targetUsername }
        val profile = vm.publicProfileFor(profileTarget)
        LaunchedEffect(profileTarget) {
            if (profileTarget.isNotBlank()) {
                vm.requestPublicProfile(profileTarget)
            }
        }
        RouteProfileScreen(
            profile = profile,
            profileImagePath = vm.profileImagePathFor(profileTarget),
            onBack = { showProfile = false },
            onSaveLocalName = { route, name -> vm.setLocalNameForRoute(route, name) },
            contactBlocked = vm.isContactBlocked(profileTarget),
            onSendMessage = {
                showProfile = false
            },
            onRecordMedia = {
                showProfile = false
                cameraOpenedFromProfile = true
                openEphemeralMediaRecorder()
            },
            onRemoveContact = {
                vm.removeContact(profileTarget)
                showProfile = false
            },
            onBlockContact = {
                vm.blockContact(profileTarget)
                showProfile = false
                onBack()
            },
            onUnblockContact = {
                vm.unblockContact(profileTarget)
            }
        )
    }

    AnimatedVisibility(
        visible = showChatSettings,
        enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + fadeOut()
    ) {
        ChatSettingsScreen(
            chatTitle = vm.chatTitleFor(vm.targetUsername),
            keepViewedMessages = vm.isCurrentChatLocalKeepViewedMessagesEnabled(),
            screenshotsEnabled = vm.isCurrentChatLocalScreenshotsEnabled(),
            contactBlocked = vm.isContactBlocked(vm.targetUsername),
            onKeepViewedMessagesChange = vm::updateCurrentChatKeepViewedMessagesPreference,
            onScreenshotsEnabledChange = vm::updateCurrentChatScreenshotsPreference,
            onBlockContact = {
                vm.blockContact(vm.targetUsername)
                onBack()
            },
            onUnblockContact = {
                vm.unblockContact(vm.targetUsername)
            },
            onRemoveContact = {
                vm.removeContact(vm.targetUsername)
                showChatSettings = false
            },
            onOpenBlockDecisionHelp = {
                chatSettingsRoute = BlockDecisionHelpRoute
            },
            onBack = { showChatSettings = false }
        )
        AnimatedVisibility(
            visible = chatSettingsRoute == BlockDecisionHelpRoute,
            enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + fadeOut()
        ) {
            BlockDecisionHelpScreen(
                onBack = { chatSettingsRoute = ChatSettingsRoute }
            )
        }
    }

}

private fun chatTitleForMessage(vm: ChatViewModel, route: String): String {
    return vm.chatTitleFor(route)
}

private fun formatCommentMessage(
    target: Message,
    body: String,
    vm: ChatViewModel,
    route: String
): String {
    val owner = if (target.isMine) "Voce" else chatTitleForMessage(vm, route)
    val quote = compactCommentPreview(target.text, maxLength = 120)
    return "Comentario sobre $owner: \"$quote\"\n\n$body"
}

private data class CommentedMessageParts(
    val owner: String,
    val quote: String,
    val body: String
)

private fun parseCommentedMessage(text: String): CommentedMessageParts? {
    val separator = "\n\n"
    val header = text.substringBefore(separator, missingDelimiterValue = "")
    if (header.isBlank()) return null
    val body = text.substringAfter(separator, missingDelimiterValue = "").trim()
    if (body.isBlank()) return null
    val prefix = "Comentario sobre "
    if (!header.startsWith(prefix)) return null
    val ownerAndQuote = header.removePrefix(prefix)
    val owner = ownerAndQuote.substringBefore(": \"", missingDelimiterValue = "").trim()
    val quote = ownerAndQuote.substringAfter(": \"", missingDelimiterValue = "")
        .removeSuffix("\"")
        .trim()
    if (owner.isBlank() || quote.isBlank()) return null
    return CommentedMessageParts(owner = owner, quote = quote, body = body)
}

private fun compactCommentPreview(text: String, maxLength: Int = 96): String {
    val compact = text.replace(Regex("\\s+"), " ").trim()
    return if (compact.length <= maxLength) compact else compact.take(maxLength - 1).trimEnd() + "..."
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onCopyRoute: () -> Unit
) {
    val titleBarContentColor = readableContentColor(MaterialTheme.colorScheme.background)
    val iconTint = titleBarContentColor
    Surface(
        shape = RoundedCornerShape(0.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChatHeaderHeight)
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = iconTint
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .combinedClickable(
                        onClick = onOpenProfile,
                        onLongClick = onCopyRoute
                    ),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = titleBarContentColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = titleBarContentColor.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionHeader(
    message: Message?,
    selectionEnabled: Boolean,
    onClose: () -> Unit,
    onComment: () -> Unit,
    onToggleSelection: () -> Unit,
    onDelete: () -> Unit
) {
    val titleBarContentColor = readableContentColor(MaterialTheme.colorScheme.background)
    Surface(
        shape = RoundedCornerShape(0.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChatHeaderHeight)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Fechar ações",
                    tint = titleBarContentColor
                )
            }
            Text(
                text = if (message?.isMine == true) "Mensagem enviada" else "Mensagem recebida",
                modifier = Modifier.weight(1f),
                color = titleBarContentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            IconButton(onClick = onComment) {
                DoubleBubbleCommentIcon(
                    tint = titleBarContentColor,
                    modifier = Modifier.size(25.dp)
                )
            }
            IconButton(onClick = onToggleSelection) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = if (selectionEnabled) "Bloquear seleção" else "Liberar seleção",
                    tint = titleBarContentColor
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Apagar",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DoubleBubbleCommentIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.semantics { contentDescription = "Comentar" },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(width = 16.dp, height = 12.dp),
            shape = RoundedCornerShape(5.dp),
            color = tint.copy(alpha = 0.42f),
            tonalElevation = 0.dp
        ) {}
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(width = 18.dp, height = 14.dp),
            shape = RoundedCornerShape(5.dp),
            color = tint,
            tonalElevation = 0.dp
        ) {}
    }
}

@Composable
private fun ChatBottomActions(
    themeMode: ThemeMode,
    chatLocked: Boolean,
    chatLockEnabled: Boolean,
    onLockChat: () -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    settingsEnabled: Boolean = true
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    val systemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val actionContentColor = readableContentColor(themeBackgroundColor(themeMode, systemDarkTheme))

    Row(
        modifier = modifier.zIndex(1f),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (chatLockEnabled) {
            IconButton(
                enabled = !chatLocked,
                onClick = onLockChat
            ) {
                Icon(
                    imageVector = if (chatLocked) Icons.Filled.Lock else Icons.Filled.VpnKey,
                    contentDescription = if (chatLocked) "Chat já trancado" else "Trancar chat",
                    tint = actionContentColor.copy(alpha = if (chatLocked) 0.56f else 1f)
                )
            }
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Menu",
                    tint = actionContentColor
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Limpar conversa") },
                    onClick = {
                        showMenu = false
                        onClear()
                    }
                )
                if (settingsEnabled) {
                    DropdownMenuItem(
                        text = { Text("Configuração") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onOpenSettings()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSettingsScreen(
    chatTitle: String,
    keepViewedMessages: Boolean,
    screenshotsEnabled: Boolean,
    contactBlocked: Boolean,
    onKeepViewedMessagesChange: (Boolean) -> Unit,
    onScreenshotsEnabledChange: (Boolean) -> Unit,
    onBlockContact: () -> Unit,
    onUnblockContact: () -> Unit,
    onRemoveContact: () -> Unit,
    onOpenBlockDecisionHelp: () -> Unit,
    onBack: () -> Unit
) {
    var showBlockConfirmation by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) {
        keyboardController?.hide()
        onDispose { }
    }
    WindowDispositionScaffold(
        title = "Configuração do chat",
        subtitle = chatTitle,
        onBack = onBack,
        windowColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Privacidade desta conversa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ChatSettingsChoiceRow(
                    title = "Limpar histórico ao sair?",
                    subtitle = if (keepViewedMessages) "Mantém mensagens ao fechar" else "Apaga mensagens lidas ao sair",
                    checked = !keepViewedMessages,
                    onCheckedChange = { onKeepViewedMessagesChange(!it) }
                )
                ChatSettingsRow(
                    title = "Print da tela",
                    subtitle = if (screenshotsEnabled) "Permitido neste chat" else "Bloqueado neste chat",
                    checked = screenshotsEnabled,
                    onCheckedChange = onScreenshotsEnabledChange
                )
            }
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRemoveContact,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Remover contato")
                }
                Button(
                    onClick = {
                        if (contactBlocked) {
                            onUnblockContact()
                        } else {
                            showBlockConfirmation = true
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (contactBlocked) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        contentColor = if (contactBlocked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onError
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (contactBlocked) "Desbloquear contato" else "Bloquear contato")
                }
            }
        }
    }

    BlockContactConfirmationDialog(
        visible = showBlockConfirmation,
        onConfirmBlock = {
            showBlockConfirmation = false
            onBlockContact()
        },
        onDismiss = {
            showBlockConfirmation = false
        },
        onMaybe = {
            showBlockConfirmation = false
            onOpenBlockDecisionHelp()
        }
    )
}

@Composable
private fun BlockContactConfirmationDialog(
    visible: Boolean,
    onConfirmBlock: () -> Unit,
    onDismiss: () -> Unit,
    onMaybe: () -> Unit
) {
    if (!visible) return

    PrimalisAlertDialog(
        title = "Bloquear este contato?",
        message = "Você deixará de receber mensagens dessa rota e ela sairá da sua lista. Se mudar de ideia depois, será preciso adicionar o token novamente.",
        icon = Icons.Filled.Lock,
        confirmLabel = "Bloquear",
        dismissLabel = "Manter",
        neutralLabel = "Talvez",
        onNeutral = onMaybe,
        onConfirm = onConfirmBlock,
        onDismiss = onDismiss,
        destructive = true
    )
}

@Composable
private fun BlockDecisionHelpScreen(
    onBack: () -> Unit
) {
    WindowDispositionScaffold(
        title = "Quando vale a pena bloquear alguém?",
        onBack = onBack,
        windowColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BlockDecisionBulletList(
                items = listOf(
                    "Quando a pessoa está assediando você.",
                    "Quando existem ameaças ou ofensas recorrentes.",
                    "Quando o contato está prejudicando seu bem-estar.",
                    "Quando você não deseja mais manter contato."
                )
            )
            Text(
                text = "Talvez seja melhor esperar um pouco se:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            BlockDecisionBulletList(
                items = listOf(
                    "Você está irritado neste momento.",
                    "Acabou de ocorrer uma discussão.",
                    "A decisão pode gerar consequências profissionais ou familiares.",
                    "Você acredita que pode se arrepender depois."
                )
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Bloquear é uma ferramenta para proteger sua experiência. A decisão é sua.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BlockDecisionBulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ChatSettingsRow(
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
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
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
            ChatListSeparator()
        }
    }
}

@Composable
private fun ChatSettingsChoiceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    tonalElevation = 0.dp,
                    modifier = Modifier.width(104.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (checked) {
                            Text(
                                text = "Sim",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            SwitchKnob(active = true)
                        } else {
                            SwitchKnob(active = false)
                            Text(
                                text = "Não",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            ChatListSeparator()
        }
    }
}

@Composable
private fun ChatListSeparator() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
    )
}

@Composable
private fun SwitchKnob(active: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp)
    ) {}
}

@Composable
private fun MessageList(
    conversationKey: String,
    messages: List<Message>,
    privacyNotices: List<ChatViewModel.PrivacyNotice>,
    loaded: Boolean,
    emptyStateText: String,
    unreadHintCount: Int,
    selectableMessageIds: Set<String>,
    onMessageLongPress: (Message) -> Unit,
    ownerTitle: (Message) -> String,
    themeMode: ThemeMode,
    appearance: com.null0x.chat.ui.theme.AppearanceSettings,
    modifier: Modifier = Modifier,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 10.dp,
    composerExpanded: Boolean = false
) {
    val timelineItems = remember(messages, privacyNotices) {
        val rawItems = buildList {
            messages.forEach { message ->
                add(TimelineItem.MessageItem(message))
            }
            privacyNotices
                .sortedBy { it.timestamp }
                .forEach { add(TimelineItem.PrivacyNoticeItem(it)) }
        }
            .sortedBy { it.timestamp }

        buildList {
            var currentDateKey = ""
            rawItems.forEach { item ->
                val dateKey = formatDateKey(item.timestamp)
                if (dateKey != currentDateKey) {
                    currentDateKey = dateKey
                    add(TimelineItem.DateHeaderItem(dateKey, item.timestamp, formatDateHeader(item.timestamp)))
                }
                add(item)
            }
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var previousTimelineSize by remember(conversationKey) { mutableStateOf(0) }
    var previousMessageSize by remember(conversationKey) { mutableStateOf(0) }
    var hiddenNewMessages by remember(conversationKey) { mutableStateOf(0) }
    var enteringMessageIds by remember(conversationKey) { mutableStateOf(emptySet<String>()) }
    var messageAnimationsReady by remember(conversationKey) { mutableStateOf(false) }
    val lastVisibleIndex by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }
    val isAtBottom by remember(conversationKey, timelineItems.size) {
        derivedStateOf {
            timelineItems.isNotEmpty() && lastVisibleIndex >= timelineItems.lastIndex
        }
    }
    val immediateEnteringMessageIds = run {
        val messageDelta = (messages.size - previousMessageSize).coerceAtLeast(0)
        if (messageAnimationsReady && messageDelta > 0) {
            messages.takeLast(messageDelta).mapTo(mutableSetOf()) { it.id }
        } else {
            emptySet()
        }
    }
    LaunchedEffect(conversationKey, loaded) {
        if (!loaded) {
            previousTimelineSize = 0
            previousMessageSize = 0
            hiddenNewMessages = 0
            enteringMessageIds = emptySet()
            messageAnimationsReady = false
        }
    }
    LaunchedEffect(conversationKey, loaded, timelineItems.size, messages.size, unreadHintCount) {
        if (!loaded) return@LaunchedEffect
        if (!messageAnimationsReady) {
            messageAnimationsReady = true
            previousTimelineSize = timelineItems.size
            previousMessageSize = messages.size
            if (timelineItems.isNotEmpty()) {
                val initialIndex = if (unreadHintCount > 0) {
                    (timelineItems.size - unreadHintCount).coerceIn(0, timelineItems.lastIndex)
                } else {
                    timelineItems.lastIndex
                }
                listState.scrollToItem(initialIndex)
            }
            return@LaunchedEffect
        }
        if (timelineItems.isEmpty()) return@LaunchedEffect
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val timelineSizeBeforeUpdate = previousTimelineSize
        val messageSizeBeforeUpdate = previousMessageSize
        val wasAtBottom = timelineSizeBeforeUpdate == 0 ||
            lastVisibleIndex >= (timelineSizeBeforeUpdate - 1).coerceAtLeast(0)
        val messageDelta = (messages.size - messageSizeBeforeUpdate).coerceAtLeast(0)
        val incomingDelta = if (messageDelta > 0) {
            messages.takeLast(messageDelta).count { !it.isMine }
        } else {
            0
        }
        if (messageDelta > 0) {
            enteringMessageIds += messages.takeLast(messageDelta).map { it.id }
        }
        val targetIndex = when {
            unreadHintCount > 0 -> (timelineItems.size - unreadHintCount).coerceIn(0, timelineItems.lastIndex)
            timelineSizeBeforeUpdate == 0 -> timelineItems.lastIndex
            timelineItems.size > timelineSizeBeforeUpdate && wasAtBottom -> timelineItems.lastIndex
            else -> timelineItems.lastIndex
        }
        previousTimelineSize = timelineItems.size
        previousMessageSize = messages.size
        when {
            timelineSizeBeforeUpdate == 0 -> listState.scrollToItem(targetIndex)
            timelineItems.size > timelineSizeBeforeUpdate && wasAtBottom -> {
                hiddenNewMessages = 0
                listState.animateScrollToItem(targetIndex)
            }
            timelineItems.size > timelineSizeBeforeUpdate && messageDelta > incomingDelta -> {
                hiddenNewMessages = 0
                listState.animateScrollToItem(timelineItems.lastIndex)
            }
            timelineItems.size > timelineSizeBeforeUpdate && incomingDelta > 0 -> {
                hiddenNewMessages += incomingDelta
            }
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            hiddenNewMessages = 0
        }
    }

    LaunchedEffect(composerExpanded, timelineItems.size) {
        if (composerExpanded && timelineItems.isNotEmpty()) {
            listState.animateScrollToItem(timelineItems.lastIndex)
        }
    }

    val effectiveBottomPadding = bottomContentPadding

    if (messages.isEmpty() && privacyNotices.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = effectiveBottomPadding),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                Text(
                    text = emptyStateText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            reverseLayout = false,
            contentPadding = PaddingValues(top = 10.dp, bottom = effectiveBottomPadding)
        ) {
            itemsIndexed(
                items = timelineItems,
                key = { index, item -> "${item.id}:${item.timestamp}:$index" }
            ) { index, item ->
                when (item) {
                    is TimelineItem.MessageItem -> {
                        val previousMessage = (timelineItems.getOrNull(index - 1) as? TimelineItem.MessageItem)?.message
                        val nextMessage = (timelineItems.getOrNull(index + 1) as? TimelineItem.MessageItem)?.message
                        val continuesPreviousOwner = previousMessage?.isMine == item.message.isMine
                        val continuesNextOwner = nextMessage?.isMine == item.message.isMine
                        ChatMessageBubble(
                            msg = item.message,
                            ownerTitle = ownerTitle(item.message),
                            showOwnerTitle = !continuesPreviousOwner,
                            continuesPreviousOwner = continuesPreviousOwner,
                            continuesNextOwner = continuesNextOwner,
                            allowTextSelection = selectableMessageIds.contains(item.message.id),
                            appearance = appearance,
                            animateIn = enteringMessageIds.contains(item.message.id) ||
                                immediateEnteringMessageIds.contains(item.message.id),
                            onEntranceAnimationFinished = {
                                enteringMessageIds -= item.message.id
                            },
                            onLongPress = { onMessageLongPress(item.message) }
                        )
                    }
                    is TimelineItem.DateHeaderItem -> DateHeaderDivider(item.text)
                    is TimelineItem.PrivacyNoticeItem -> PrivacyNoticeDivider(item.notice.text)
                }
            }
        }
        if (hiddenNewMessages > 0) {
            NewMessagesBadge(
                count = hiddenNewMessages,
                onClick = {
                    hiddenNewMessages = 0
                    scope.launch {
                        listState.animateScrollToItem(timelineItems.lastIndex)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 86.dp)
            )
        }
    }
}

@Composable
private fun NewMessagesBadge(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Ir para novas mensagens",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = count.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private sealed class TimelineItem {
    abstract val id: String
    abstract val timestamp: Long

    data class MessageItem(val message: Message) : TimelineItem() {
        override val id: String = "message:${message.id}"
        override val timestamp: Long = message.timestamp
    }

    data class PrivacyNoticeItem(val notice: ChatViewModel.PrivacyNotice) : TimelineItem() {
        override val id: String = "privacy:${notice.id}"
        override val timestamp: Long = notice.timestamp
    }

    data class DateHeaderItem(
        val key: String,
        override val timestamp: Long,
        val text: String
    ) : TimelineItem() {
        override val id: String = "date:$key"
    }
}

@Composable
private fun DateHeaderDivider(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
            tonalElevation = 0.dp
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PrivacyNoticeDivider(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ) {
            Spacer(Modifier.size(1.dp))
        }
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
            maxLines = 3
        )
        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ) {
            Spacer(Modifier.size(1.dp))
        }
    }
}

@Composable
private fun UnreadHintBar(count: Int, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (count == 1) "1 mensagem nao lida" else "$count mensagens nao lidas",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onDismiss) {
                Text("Certo")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatMessageBubble(
    msg: Message,
    ownerTitle: String,
    showOwnerTitle: Boolean = true,
    continuesPreviousOwner: Boolean = false,
    continuesNextOwner: Boolean = false,
    allowTextSelection: Boolean,
    appearance: com.null0x.chat.ui.theme.AppearanceSettings,
    animateIn: Boolean = false,
    onEntranceAnimationFinished: () -> Unit = {},
    onLongPress: () -> Unit
) {
    val density = LocalDensity.current
    val messageTextColor = readableContentColor(MaterialTheme.colorScheme.background)
    val sideBarColor = messageSideBarColor(msg.isMine)
    val deliveryTint = messageTextColor.copy(alpha = 0.62f)
    val entranceProgress = remember(msg.id) { Animatable(if (animateIn) 0f else 1f) }
    val sourceOffsetX = with(density) { if (msg.isMine) 0.dp.toPx() else (-56).dp.toPx() }
    val sourceOffsetY = with(density) { if (msg.isMine) 154.dp.toPx() else 46.dp.toPx() }
    val progress = entranceProgress.value

    LaunchedEffect(msg.id, animateIn) {
        if (animateIn) {
            entranceProgress.snapTo(0f)
            entranceProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing)
            )
            onEntranceAnimationFinished()
        } else {
            entranceProgress.snapTo(1f)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = 0.16f + (0.84f * progress)
                translationX = sourceOffsetX * (1f - progress)
                translationY = sourceOffsetY * (1f - progress)
                val scale = 0.74f + (0.26f * progress)
                scaleX = scale
                scaleY = scale
                transformOrigin = if (msg.isMine) {
                    TransformOrigin(1f, 1f)
                } else {
                    TransformOrigin(0f, 1f)
                }
            },
        horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier.widthIn(max = 312.dp),
            contentAlignment = if (msg.isMine) Alignment.BottomEnd else Alignment.BottomStart
        ) {
            Surface(
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                ),
                color = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                MessageBubbleBody(
                    msg = msg,
                    ownerTitle = ownerTitle,
                    showOwnerTitle = showOwnerTitle,
                    allowTextSelection = allowTextSelection,
                    textColor = messageTextColor,
                    metaColor = deliveryTint,
                    sideBarColor = sideBarColor,
                    modifier = Modifier.padding(
                        start = MessageBodyInset,
                        end = MessageBodyInset,
                        top = if (continuesPreviousOwner) 0.dp else MessageBodyInset,
                        bottom = if (continuesNextOwner) 0.dp else MessageBodyInset
                    )
                )
            }
        }
    }
}

@Composable
private fun MessageBubbleBody(
    msg: Message,
    ownerTitle: String,
    showOwnerTitle: Boolean,
    allowTextSelection: Boolean,
    textColor: Color,
    metaColor: Color,
    sideBarColor: Color,
    modifier: Modifier = Modifier
) {
    val sideBarWidth = MessageBarThickness
    val sideBarGap = MessageBodyInset
    Layout(
        content = {
            if (msg.isMine) {
                MessageLineContent(
                    msg = msg,
                    ownerTitle = ownerTitle,
                    showOwnerTitle = showOwnerTitle,
                    allowTextSelection = allowTextSelection,
                    textColor = textColor,
                    metaColor = metaColor,
                    titleColor = sideBarColor,
                    modifier = Modifier.animateContentSize()
                )
                MessageSideBar(color = sideBarColor)
            } else {
                MessageSideBar(color = sideBarColor)
                MessageLineContent(
                    msg = msg,
                    ownerTitle = ownerTitle,
                    showOwnerTitle = showOwnerTitle,
                    allowTextSelection = allowTextSelection,
                    textColor = textColor,
                    metaColor = metaColor,
                    titleColor = sideBarColor,
                    modifier = Modifier.animateContentSize()
                )
            }
        }
    , modifier = modifier) { measurables, constraints ->
        val barWidthPx = with(this) { sideBarWidth.roundToPx() }
        val gapPx = with(this) { sideBarGap.roundToPx() }
        val contentIndex = if (msg.isMine) 0 else 1
        val barIndex = if (msg.isMine) 1 else 0
        val contentMeasurable = measurables[contentIndex]
        val barMeasurable = measurables[barIndex]

        val contentConstraints = constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxWidth = (constraints.maxWidth - barWidthPx - gapPx).coerceAtLeast(0)
        )
        val contentPlaceable = contentMeasurable.measure(contentConstraints)
        val barPlaceable = barMeasurable.measure(
            constraints.copy(
                minWidth = barWidthPx,
                maxWidth = barWidthPx,
                minHeight = contentPlaceable.height,
                maxHeight = contentPlaceable.height
            )
        )

        val width = contentPlaceable.width + gapPx + barPlaceable.width
        val height = maxOf(contentPlaceable.height, barPlaceable.height)

        layout(width, height) {
            if (msg.isMine) {
                contentPlaceable.placeRelative(0, 0)
                barPlaceable.placeRelative(contentPlaceable.width + gapPx, 0)
            } else {
                barPlaceable.placeRelative(0, 0)
                contentPlaceable.placeRelative(barPlaceable.width + gapPx, 0)
            }
        }
    }
}

@Composable
private fun MessageSideBar(color: Color) {
    Surface(
        modifier = Modifier.width(MessageBarThickness),
        shape = RoundedCornerShape(0.dp),
        color = color,
        tonalElevation = 0.dp
    ) {}
}

@Composable
private fun MessageLineContent(
    msg: Message,
    ownerTitle: String,
    showOwnerTitle: Boolean,
    allowTextSelection: Boolean,
    textColor: Color,
    metaColor: Color,
    titleColor: Color,
    modifier: Modifier = Modifier
) {
    val commentedParts = remember(msg.text) { parseCommentedMessage(msg.text) }
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyLarge
    BoxWithConstraints(
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        val density = LocalDensity.current
        val availableWidthPx = with(density) { maxWidth.toPx() }
        val metaReservePx = with(density) { if (msg.isMine) 78.dp.toPx() else 48.dp.toPx() }
        val spacingPx = with(density) { 6.dp.toPx() }
        val textWidthPx = textMeasurer.measure(
            text = AnnotatedString(commentedParts?.body ?: msg.text),
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Clip
        ).size.width.toFloat()
        val keepMetaInline = commentedParts == null && textWidthPx + metaReservePx + spacingPx <= availableWidthPx

        Column(
            horizontalAlignment = if (msg.isMine) Alignment.End else Alignment.Start
        ) {
            if (showOwnerTitle) {
                Text(
                    text = ownerTitle,
                    color = titleColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            if (commentedParts != null) {
                CommentedMessageContent(
                    parts = commentedParts,
                    isMine = msg.isMine,
                    allowTextSelection = allowTextSelection,
                    textColor = textColor,
                    metaColor = metaColor,
                    accentColor = titleColor,
                    msg = msg
                )
            } else if (keepMetaInline) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (allowTextSelection) {
                        SelectionContainer(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = msg.text,
                                color = textColor
                            )
                        }
                    } else {
                        Text(
                            text = msg.text,
                            modifier = Modifier.weight(1f, fill = false),
                            color = textColor
                        )
                    }
                    MessageInlineMeta(
                        msg = msg,
                        color = metaColor
                    )
                }
            } else {
                Column(
                    horizontalAlignment = if (msg.isMine) Alignment.End else Alignment.Start
                ) {
                    if (allowTextSelection) {
                        SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = msg.text,
                                color = textColor
                            )
                        }
                    } else {
                        Text(
                            text = msg.text,
                            modifier = Modifier.fillMaxWidth(),
                            color = textColor
                        )
                    }
                    MessageInlineMeta(
                        msg = msg,
                        color = metaColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentedMessageContent(
    parts: CommentedMessageParts,
    isMine: Boolean,
    allowTextSelection: Boolean,
    textColor: Color,
    metaColor: Color,
    accentColor: Color,
    msg: Message
) {
    Column(
        modifier = Modifier.widthIn(min = 176.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        QuotedMessageCard(
            owner = parts.owner,
            quote = parts.quote,
            accentColor = accentColor,
            modifier = Modifier.fillMaxWidth()
        )
        if (allowTextSelection) {
            SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = parts.body,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Text(
                text = parts.body,
                modifier = Modifier.fillMaxWidth(),
                color = textColor,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        MessageInlineMeta(
            msg = msg,
            color = metaColor,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

@Composable
private fun QuotedMessageCard(
    owner: String,
    quote: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val quoteBackground = surface.mixWith(accentColor, 0.08f).copy(alpha = 0.82f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = quoteBackground,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 7.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .height(38.dp)
                    .width(4.dp),
                shape = RoundedCornerShape(99.dp),
                color = accentColor,
                tonalElevation = 0.dp
            ) {}
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = owner,
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = quote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun messageSideBarColor(isMine: Boolean): Color {
    return if (isMine) Color(0xFF0057FF) else Color(0xFFE00022)
}

private fun Color.mixWith(other: Color, amount: Float): Color {
    val safeAmount = amount.coerceIn(0f, 1f)
    val keep = 1f - safeAmount
    return Color(
        red = red * keep + other.red * safeAmount,
        green = green * keep + other.green * safeAmount,
        blue = blue * keep + other.blue * safeAmount,
        alpha = alpha
    )
}

@Composable
private fun MessageInlineMeta(
    msg: Message,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = formatMessageTime(msg.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1
        )
        if (msg.isMine) {
            val tint = when (msg.delivery) {
                DeliveryState.Pending -> color.copy(alpha = 0.60f)
                DeliveryState.Sent -> color
                DeliveryState.Delivered -> color
                DeliveryState.Failed -> MaterialTheme.colorScheme.error
            }
            when (msg.delivery) {
                DeliveryState.Pending -> Text("...", color = tint, style = MaterialTheme.typography.labelSmall)
                DeliveryState.Sent -> Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
                DeliveryState.Delivered -> {
                    Icon(
                        imageVector = Icons.Filled.Done,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp)
                    )
                    Icon(
                        imageVector = Icons.Filled.Done,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp)
                    )
                }
                DeliveryState.Failed -> Text(
                    "!",
                    color = tint,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
}

private fun formatDateKey(timestamp: Long): String {
    return java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
}

private fun formatDateHeader(timestamp: Long): String {
    val now = java.util.Calendar.getInstance()
    val target = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val yesterday = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    return when {
        sameDay(now, target) -> "Hoje"
        sameDay(yesterday, target) -> "Ontem"
        else -> java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}

private fun sameDay(a: java.util.Calendar, b: java.util.Calendar): Boolean {
    return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
        a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
}

@Composable
private fun MessageComposer(
    input: String,
    showTextInput: Boolean,
    focusRequester: FocusRequester,
    focusRequestVersion: Int,
    appearance: com.null0x.chat.ui.theme.AppearanceSettings,
    trailingActionSpace: Dp,
    commentTarget: Message?,
    commentOwnerTitle: String,
    enabled: Boolean,
    placeholder: String,
    onInputChange: (String) -> Unit,
    onShowTextInputChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    buttonOffsetX: Dp = 0.dp,
    showButtonWhenOpen: Boolean = true,
    onClearComment: () -> Unit = {}
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val morphBackground = MaterialTheme.colorScheme.surface
    val fieldTextColor = MaterialTheme.colorScheme.onSurface
    val fieldPlaceholderColor = fieldTextColor.copy(alpha = 0.58f)
    val presenceButtonColor = Color(0xFF8A8A8A)
    val presenceButtonTint = readableContentColor(presenceButtonColor)
    val sendMorphProgress = remember { Animatable(1f) }
    var morphingMessageText by remember { mutableStateOf<String?>(null) }
    var handledFocusRequestVersion by remember { mutableStateOf(0) }
    fun sendWithMorph() {
        val text = input.trim()
        if (text.isBlank() || !enabled || morphingMessageText != null) return
        animationScope.launch {
            morphingMessageText = text
            sendMorphProgress.snapTo(0f)
            onSend()
            sendMorphProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
            )
            morphingMessageText = null
        }
    }
    LaunchedEffect(input) {
        if (input.isNotBlank()) {
            onShowTextInputChange(true)
        }
    }
    LaunchedEffect(showTextInput, enabled, focusRequestVersion) {
        if (showTextInput && enabled && focusRequestVersion > handledFocusRequestVersion) {
            handledFocusRequestVersion = focusRequestVersion
            delay(120)
            var focused = false
            repeat(4) {
                if (!focused) {
                    focused = runCatching { focusRequester.requestFocus() }.isSuccess
                    if (!focused) {
                        delay(16)
                    }
                }
            }
            keyboardController?.show()
        } else if (!showTextInput || !enabled) {
            keyboardController?.hide()
        }
    }
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = showTextInput,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 12.dp)
                .padding(bottom = 0.dp),
            enter = fadeIn(animationSpec = tween(140)) +
                expandVertically(
                    expandFrom = Alignment.Bottom,
                    animationSpec = tween(220)
                ) +
                slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(220)
                ),
            exit = fadeOut(animationSpec = tween(110)) +
                shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    animationSpec = tween(160)
                ) +
                slideOutVertically(
                    targetOffsetY = { it / 4 },
                    animationSpec = tween(160)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = trailingActionSpace)
            ) {
                if (commentTarget != null) {
                    CommentAttachmentPreview(
                        message = commentTarget,
                        ownerTitle = commentOwnerTitle.ifBlank {
                            if (commentTarget.isMine) "Voce" else "Contato"
                        },
                        onClear = onClearComment,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        maxLines = 4,
                        enabled = enabled,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text(placeholder) },
                        colors = primalisBareOutlinedTextFieldColors(
                            textColor = fieldTextColor,
                            placeholderColor = fieldPlaceholderColor
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendWithMorph() })
                    )
                    morphingMessageText?.let { text ->
                        val progress = sendMorphProgress.value
                        val yOffset = with(density) { (-88).dp.toPx() * progress }
                        val xOffset = with(density) { 34.dp.toPx() * progress }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomEnd)
                                .graphicsLayer {
                                    alpha = 1f - progress
                                    scaleX = 1f - (0.18f * progress)
                                    scaleY = 1f - (0.08f * progress)
                                    translationX = xOffset
                                    translationY = yOffset
                                    transformOrigin = TransformOrigin(1f, 1f)
                                }
                                .zIndex(2f),
                            shape = RoundedCornerShape(20.dp),
                            color = morphBackground,
                            tonalElevation = 2.dp,
                            shadowElevation = 6.dp
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                color = fieldTextColor,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showButtonWhenOpen && !showTextInput,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(90))
        ) {
            Surface(
                shape = CircleShape,
                color = presenceButtonColor.copy(alpha = if (enabled) 1f else 0.45f),
                modifier = Modifier
                    .offset(x = buttonOffsetX)
                    .size(54.dp)
            ) {
                IconButton(
                    enabled = enabled,
                    onClick = { onShowTextInputChange(true) }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Abrir texto",
                        tint = presenceButtonTint.copy(alpha = if (enabled) 1f else 0.58f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentAttachmentPreview(
    message: Message,
    ownerTitle: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = messageSideBarColor(message.isMine)
    val background = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = background,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .height(34.dp)
                    .width(4.dp),
                shape = RoundedCornerShape(99.dp),
                color = accent,
                tonalElevation = 0.dp
            ) {}
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 9.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = ownerTitle,
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = compactCommentPreview(message.text),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClear, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remover comentario",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PartnerMarkerBadge(
    text: String,
    marker: String,
    backgroundColor: Color,
    imagePath: String,
    typing: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        InitialAvatar(
            text = text.ifBlank { marker.ifBlank { "?" } },
            emoji = marker,
            backgroundColor = backgroundColor,
            active = true,
            imagePath = imagePath
        )
        if (typing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 3.dp
            ) {
                TypingDots(modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun TypingDots(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.size(4.dp)
            ) {}
        }
    }
}

private fun compactOnionRoute(value: String): String {
    val clean = value.trim()
    if (!clean.startsWith("onion:", ignoreCase = true)) return clean
    val route = clean.substringAfter(':')
    val separator = route.lastIndexOf(':')
    if (separator <= 0 || separator == route.lastIndex) return clean
    val host = route.substring(0, separator)
    val port = route.substring(separator + 1)
    if (host.length <= 28) return "onion:$host:$port"
    return "onion:${host.take(12)}...${host.takeLast(10)}:$port"
}

private fun chatActivityElapsedLabel(timestamp: Long): String {
    val elapsedSeconds = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 1_000L).coerceAtLeast(1L)
    return when {
        elapsedSeconds < 60L -> "$elapsedSeconds ${if (elapsedSeconds == 1L) "segundo" else "segundos"}"
        elapsedSeconds < 3_600L -> {
            val minutes = elapsedSeconds / 60L
            "$minutes ${if (minutes == 1L) "minuto" else "minutos"}"
        }
        elapsedSeconds < 86_400L -> {
            val hours = elapsedSeconds / 3_600L
            "$hours ${if (hours == 1L) "hora" else "horas"}"
        }
        else -> {
            val days = elapsedSeconds / 86_400L
            "$days ${if (days == 1L) "dia" else "dias"}"
        }
    }
}
