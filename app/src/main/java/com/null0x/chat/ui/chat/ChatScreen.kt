package com.null0x.chat.ui.chat

import android.content.Context
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import com.null0x.chat.model.DeliveryState
import com.null0x.chat.model.Message
import com.null0x.chat.network.TorManager
import com.null0x.chat.security.SensitiveClipboard
import com.null0x.chat.ui.common.SwipeToCloseContainer
import com.null0x.chat.ui.maskedRouteLabel
import com.null0x.chat.ui.profile.RouteProfileScreen
import com.null0x.chat.ui.security.ProtectedWindowCapture
import com.null0x.chat.viewmodel.ChatViewModel
import android.widget.Toast
import androidx.compose.material3.AlertDialog

private val ChatHeaderHeight = 86.dp
private val TitleBarColor = Color.Black

@Composable
fun ChatScreen(vm: ChatViewModel, onBack: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var profileRoute by rememberSaveable { mutableStateOf("") }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showChatSettings by rememberSaveable { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    var selectableMessageIds by remember { mutableStateOf(emptySet<String>()) }
    val context = LocalContext.current
    val torStatus by TorManager.status.collectAsState()
    val networkAvailable by TorManager.networkAvailableState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val composerFocusRequester = remember { FocusRequester() }
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
        showChatSettings = false
    }
    BackHandler(enabled = showProfile && !showChatSettings) {
        showProfile = false
    }
    BackHandler(enabled = !showProfile && !showChatSettings) {
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
    val chatTitle = vm.chatTitleFor(headerUser)
    val sourceTitle = "Chats"
    val chatRouteLabel = chatRoutePresenceLabel(
        networkAvailable = networkAvailable,
        torReady = torStatus is TorManager.Status.Ready,
        partnerConnected = vm.isPartnerOnline(headerUser)
    )
    val unreadHintCount = vm.unreadEntryCountFor(headerUser)
    val currentChatLoaded = vm.isCurrentChatLoaded()

    LaunchedEffect(context) {
        TorManager.ensureNetworkMonitoring(context)
    }

    LaunchedEffect(headerUser) {
        composerFocusRequester.requestFocus()
        keyboardController?.hide()
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val screenWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val closeThreshold = screenWidthPx * 0.18f
        val flingThreshold = 700f
        val offsetX = remember(screenWidthPx) { Animatable(screenWidthPx) }
        var dragX by remember { mutableFloatStateOf(0f) }
        val openingProgress = ((screenWidthPx - offsetX.value) / screenWidthPx).coerceIn(0f, 1f)

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
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        dragX += delta
                        scope.launch {
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
                            scope.launch {
                                offsetX.animateTo(target, animationSpec = tween(durationMillis = 170))
                                onBack()
                            }
                        } else {
                            scope.launch {
                                offsetX.animateTo(0f, animationSpec = tween(durationMillis = 170))
                            }
                        }
                        dragX = 0f
                    }
                )
                .background(MaterialTheme.colorScheme.background)
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    Column {
                        ChatHeader(
                            title = chatTitle,
                            subtitle = chatRouteLabel,
                            sourceTitle = sourceTitle,
                            sourceSubtitle = chatRouteLabel,
                            sourceEmoji = vm.profileEmojiSymbol,
                            targetEmoji = vm.emojiForRoute(headerUser),
                            openingProgress = openingProgress,
                            onBack = onBack,
                            onClear = { vm.clearConversation(headerUser) },
                            contactBlocked = vm.isContactBlocked(headerUser),
                            onToggleContactBlock = {
                                if (vm.isContactBlocked(headerUser)) {
                                    vm.unblockContact(headerUser)
                                } else {
                                    vm.blockContact(headerUser)
                                }
                            },
                            onOpenProfile = {
                                profileRoute = headerUser
                                showProfile = true
                            },
                            onCopyRoute = {
                                SensitiveClipboard.copy(context, "Rota contato", maskedRouteLabel(headerUser))
                                Toast.makeText(context, "Token sensível copiado por 60 segundos", Toast.LENGTH_SHORT).show()
                            },
                            onOpenSettings = { showChatSettings = true }
                        )
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
                            messages = vm.messagesFor(user),
                            privacyNotices = vm.privacyNotices(),
                            loaded = currentChatLoaded && user == vm.targetUsername,
                            unreadHintCount = unreadHintCount,
                            selectableMessageIds = selectableMessageIds,
                            onMessageClick = { selectedMessage = it },
                            modifier = Modifier.fillMaxSize(),
                            bottomContentPadding = 154.dp
                        )
                        val marker = vm.emojiForRoute(headerUser)
                            .ifBlank { compactOnionRoute(vm.chatTitleFor(headerUser)).take(1).ifBlank { "?" }.uppercase() }
                        if (user == headerUser && marker.isNotBlank() && vm.isPartnerChatOpen(headerUser)) {
                            PartnerMarkerBadge(
                                marker = marker,
                                typing = vm.isPartnerTyping(headerUser),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 18.dp, bottom = 86.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
            ) {
                MessageComposer(
                    input = input,
                    focusRequester = composerFocusRequester,
                    onInputChange = { input = it },
                    onSend = {
                        val text = input.trim()
                        if (text.isNotBlank()) {
                            vm.updateLocalTyping(headerUser, false)
                            vm.sendTo(headerUser, text)
                            input = ""
                        }
                    }
                )
            }
        }
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
            onBack = { showProfile = false },
            onSaveLocalName = { route, name -> vm.setLocalNameForRoute(route, name) }
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
            onKeepViewedMessagesChange = vm::updateCurrentChatKeepViewedMessagesPreference,
            onScreenshotsEnabledChange = vm::updateCurrentChatScreenshotsPreference,
            onBack = { showChatSettings = false }
        )
    }

    selectedMessage?.let { message ->
        MessageDetailDialog(
            message = message,
            selectionEnabled = selectableMessageIds.contains(message.id),
            onToggleSelection = {
                selectableMessageIds = if (selectableMessageIds.contains(message.id)) {
                    selectableMessageIds - message.id
                } else {
                    selectableMessageIds + message.id
                }
            },
            onDismiss = { selectedMessage = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatHeader(
    title: String,
    subtitle: String,
    sourceTitle: String,
    sourceSubtitle: String,
    sourceEmoji: String,
    targetEmoji: String,
    openingProgress: Float,
    onBack: () -> Unit,
    onClear: () -> Unit,
    contactBlocked: Boolean,
    onToggleContactBlock: () -> Unit,
    onOpenProfile: () -> Unit,
    onCopyRoute: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    val sourceAlpha = (1f - openingProgress).coerceIn(0f, 1f)
    val targetAlpha = openingProgress.coerceIn(0f, 1f)
    Surface(
        color = TitleBarColor,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChatHeaderHeight)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.alpha(targetAlpha)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White
                )
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.18f),
                modifier = Modifier
                    .size(38.dp)
                    .combinedClickable(
                        onClick = onOpenProfile,
                        onLongClick = onCopyRoute
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        modifier = Modifier.alpha(sourceAlpha),
                        text = sourceEmoji.ifBlank { sourceTitle.trim().take(1).ifBlank { "P" }.uppercase() },
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        modifier = Modifier.alpha(targetAlpha),
                        text = targetEmoji.ifBlank { title.trim().take(1).ifBlank { "P" }.uppercase() },
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
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
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier.alpha(sourceAlpha),
                        text = sourceTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                    Text(
                        modifier = Modifier.alpha(targetAlpha),
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier.alpha(sourceAlpha),
                        text = sourceSubtitle,
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Clip
                    )
                    Text(
                        modifier = Modifier.alpha(targetAlpha),
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Clip
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.alpha(targetAlpha)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Menu",
                        tint = Color.White
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
                    DropdownMenuItem(
                        text = { Text(if (contactBlocked) "Desbloquear" else "Bloquear") },
                        onClick = {
                            showMenu = false
                            onToggleContactBlock()
                        }
                    )
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
    onKeepViewedMessagesChange: (Boolean) -> Unit,
    onScreenshotsEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit
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
                            .height(ChatHeaderHeight)
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
                                text = "Configuração do chat",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = chatTitle,
                                color = Color.White.copy(alpha = 0.78f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
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
            }
        }
    }
}

@Composable
private fun ChatSettingsRow(
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
                Switch(
                    checked = checked,
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
    unreadHintCount: Int,
    selectableMessageIds: Set<String>,
    onMessageClick: (Message) -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 10.dp
) {
    if (messages.isEmpty() && privacyNotices.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = bottomContentPadding),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                Text(
                    text = "Sem mensagens ainda",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
        }
        return
    }

    val timelineItems = remember(messages, privacyNotices) {
        (
            messages.map { TimelineItem.MessageItem(it) } +
                privacyNotices.map { TimelineItem.PrivacyNoticeItem(it) }
        ).sortedBy { it.timestamp }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var previousTimelineSize by remember(conversationKey) { mutableStateOf(0) }
    var previousMessageSize by remember(conversationKey) { mutableStateOf(0) }
    var hiddenNewMessages by remember(conversationKey) { mutableStateOf(0) }
    LaunchedEffect(conversationKey, loaded) {
        if (!loaded) {
            previousTimelineSize = 0
            previousMessageSize = 0
            hiddenNewMessages = 0
        }
    }
    LaunchedEffect(conversationKey, loaded, timelineItems.size, messages.size, unreadHintCount) {
        if (!loaded || timelineItems.isEmpty()) return@LaunchedEffect
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val wasAtBottom = previousTimelineSize == 0 ||
            lastVisibleIndex >= (previousTimelineSize - 1).coerceAtLeast(0)
        val messageDelta = (messages.size - previousMessageSize).coerceAtLeast(0)
        val incomingDelta = if (messageDelta > 0) {
            messages.takeLast(messageDelta).count { !it.isMine }
        } else {
            0
        }
        val targetIndex = when {
            unreadHintCount > 0 -> (timelineItems.size - unreadHintCount).coerceIn(0, timelineItems.lastIndex)
            previousTimelineSize == 0 -> timelineItems.lastIndex
            timelineItems.size > previousTimelineSize && wasAtBottom -> timelineItems.lastIndex
            else -> timelineItems.lastIndex
        }
        when {
            previousTimelineSize == 0 -> listState.scrollToItem(targetIndex)
            timelineItems.size > previousTimelineSize && wasAtBottom -> {
                hiddenNewMessages = 0
                listState.animateScrollToItem(targetIndex)
            }
            timelineItems.size > previousTimelineSize && messageDelta > incomingDelta -> {
                hiddenNewMessages = 0
                listState.animateScrollToItem(timelineItems.lastIndex)
            }
            timelineItems.size > previousTimelineSize && incomingDelta > 0 -> {
                hiddenNewMessages += incomingDelta
            }
        }
        previousTimelineSize = timelineItems.size
        previousMessageSize = messages.size
    }

    LaunchedEffect(listState, timelineItems.size) {
        snapshotFlow {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex >= timelineItems.lastIndex
        }.collect { isAtBottom ->
            if (isAtBottom) hiddenNewMessages = 0
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            reverseLayout = false,
            contentPadding = PaddingValues(top = 10.dp, bottom = bottomContentPadding)
        ) {
            itemsIndexed(
                items = timelineItems,
                key = { index, item -> "${item.id}:${item.timestamp}:$index" }
            ) { _, item ->
                when (item) {
                    is TimelineItem.MessageItem -> {
                        MessageBubble(
                            msg = item.message,
                            allowTextSelection = selectableMessageIds.contains(item.message.id),
                            onClick = { onMessageClick(item.message) }
                        )
                    }
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
private fun MessageBubble(
    msg: Message,
    allowTextSelection: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .padding(vertical = 3.dp)
                .widthIn(max = 312.dp)
                .clickable(onClick = onClick),
            color = if (msg.isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.animateContentSize()) {
                if (allowTextSelection) {
                    SelectionContainer {
                        Text(
                            text = msg.text,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            color = if (msg.isMine) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                } else {
                    Text(
                        text = msg.text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        color = if (msg.isMine) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (msg.isMine) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 8.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tint = when (msg.delivery) {
                            DeliveryState.Pending -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                            DeliveryState.Sent -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            DeliveryState.Delivered -> MaterialTheme.colorScheme.onSurface
                            DeliveryState.Failed -> MaterialTheme.colorScheme.error
                        }
                        when (msg.delivery) {
                            DeliveryState.Pending -> Text("...", color = tint)
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
                            DeliveryState.Failed -> Text("!", color = tint, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageDetailDialog(
    message: Message,
    selectionEnabled: Boolean,
    onToggleSelection: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (message.isMine) "Mensagem enviada" else "Mensagem recebida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Horário: ${formatTimestamp(message.timestamp)}")
                Text("Status: ${if (message.isMine) "Enviada por você" else "Recebida"}")
                Text("Texto: ${message.text}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        },
        dismissButton = {
            if (message.isMine) {
                TextButton(onClick = onToggleSelection) {
                    Text(if (selectionEnabled) "Bloquear seleção" else "Liberar seleção")
                }
            }
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    return java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
}

@Composable
private fun MessageComposer(
    input: String,
    focusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val isLightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val fieldBackground = if (isLightSurface) Color.White else Color.Black
    val fieldForeground = if (isLightSurface) Color.Black else Color.White
    val fieldBorder = fieldForeground.copy(alpha = 0.72f)
    val fieldMuted = fieldForeground.copy(alpha = 0.58f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = fieldBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    placeholder = { Text("Mensagem") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = fieldForeground,
                        unfocusedTextColor = fieldForeground,
                        focusedContainerColor = fieldBackground,
                        unfocusedContainerColor = fieldBackground,
                        disabledContainerColor = fieldBackground,
                        cursorColor = fieldForeground,
                        focusedBorderColor = fieldBorder,
                        unfocusedBorderColor = fieldBorder,
                        focusedPlaceholderColor = fieldMuted,
                        unfocusedPlaceholderColor = fieldMuted
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )
                Spacer(Modifier.width(8.dp))
                Surface(shape = CircleShape, color = fieldForeground) {
                    IconButton(onClick = onSend) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Enviar",
                            tint = fieldBackground
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnerMarkerBadge(marker: String, typing: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            tonalElevation = 1.dp
        ) {
            Text(
                text = marker.ifBlank { "?" },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (typing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 11.dp, y = (-8).dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 3.dp
            ) {
                Text(
                    text = "💭",
                    modifier = Modifier.padding(2.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
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

private fun chatRoutePresenceLabel(
    networkAvailable: Boolean,
    torReady: Boolean,
    partnerConnected: Boolean
): String {
    val networkStatus = chatNetworkStatusLabel(
        networkAvailable = networkAvailable,
        torReady = torReady
    )
    if (networkStatus.isNotBlank()) return networkStatus
    return if (partnerConnected) "disponível" else ""
}

private fun chatNetworkStatusLabel(
    networkAvailable: Boolean,
    torReady: Boolean
): String {
    return when {
        !networkAvailable -> "Aguardando rede..."
        !torReady -> "Conectando..."
        else -> ""
    }
}
