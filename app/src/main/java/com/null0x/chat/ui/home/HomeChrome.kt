package com.null0x.chat.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.delay
import androidx.compose.foundation.isSystemInDarkTheme
import com.null0x.chat.ui.theme.ThemePreference
import com.null0x.chat.ui.theme.dockSelectedBackgroundColor
import com.null0x.chat.ui.theme.dockSelectedIconTint
import com.null0x.chat.ui.theme.readableContentColor
import com.null0x.chat.ui.theme.themeBackgroundColor

@Composable
internal fun AppHeader(
    title: String,
    selectedTab: HomeTab,
    badges: Map<HomeTab, Int>,
    onSelectTab: (HomeTab) -> Unit,
    selectedContactUsername: String?,
    selectedChatsCount: Int = 0,
    searchEnabled: Boolean = false,
    searchActive: Boolean = false,
    searchValue: String = "",
    searchPlaceholder: String = "Pesquisar",
    onSearchActiveChange: (Boolean) -> Unit = {},
    onSearchValueChange: (String) -> Unit = {},
    onSearchSubmit: () -> Unit = {},
    onSearchQrClick: () -> Unit = {},
    onDeleteSelectedChats: (() -> Unit)? = null,
    onClearSelectedChats: (() -> Unit)? = null,
    onSelectAllChats: (() -> Unit)? = null,
    onDeleteSelectedContact: () -> Unit,
    onLockApp: () -> Unit
) {
    val showChatActions = selectedChatsCount > 0
    val systemDarkTheme = isSystemInDarkTheme()
    val baseThemeMode = ThemePreference.themeMode.collectAsState().value
    val headerColor = Color.Transparent
    val headerContentColor = readableContentColor(themeBackgroundColor(baseThemeMode, systemDarkTheme))
    val iconContentColor = headerContentColor
    var chatMenuExpanded = remember { mutableStateOf(false) }
    var contactMenuExpanded = remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchContainerColor = themeBackgroundColor(baseThemeMode, systemDarkTheme)
    val searchTextColor = headerContentColor
    val searchPlaceholderColor = searchTextColor.copy(alpha = 0.62f)
    val searchIconColor = searchTextColor.copy(alpha = 0.86f)

    LaunchedEffect(searchActive) {
        if (searchActive) {
            delay(120)
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = headerColor,
        contentColor = headerContentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HomeHeaderHeight)
                .padding(horizontal = 10.dp)
        ) {
            AnimatedVisibility(
                visible = searchActive,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(end = 118.dp),
                enter = fadeIn(animationSpec = tween(140)) +
                    expandHorizontally(
                        expandFrom = Alignment.Start,
                        animationSpec = tween(220)
                    ) +
                    slideInHorizontally(
                        initialOffsetX = { -it / 4 },
                        animationSpec = tween(220)
                    ),
                exit = fadeOut(animationSpec = tween(110)) +
                    shrinkHorizontally(
                        shrinkTowards = Alignment.Start,
                        animationSpec = tween(160)
                    ) +
                    slideOutHorizontally(
                        targetOffsetX = { -it / 5 },
                        animationSpec = tween(160)
                    )
            ) {
                TextField(
                    value = searchValue,
                    onValueChange = onSearchValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .focusRequester(searchFocusRequester),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = searchTextColor
                    ),
                    placeholder = {
                        Text(
                            text = searchPlaceholder,
                            color = searchPlaceholderColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    },
                    leadingIcon = {
                        IconButton(onClick = onSearchQrClick) {
                            Icon(
                                imageVector = Icons.Filled.QrCodeScanner,
                                contentDescription = "Ler QR da rota"
                            )
                        }
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                onSearchValueChange("")
                            }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpar texto")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = searchContainerColor,
                        unfocusedContainerColor = searchContainerColor,
                        focusedTextColor = searchTextColor,
                        unfocusedTextColor = searchTextColor,
                        cursorColor = searchTextColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLeadingIconColor = searchIconColor,
                        unfocusedLeadingIconColor = searchIconColor,
                        focusedTrailingIconColor = searchIconColor,
                        unfocusedTrailingIconColor = searchIconColor,
                        focusedPlaceholderColor = searchPlaceholderColor,
                        unfocusedPlaceholderColor = searchPlaceholderColor
                    )
                )
            }
            AnimatedVisibility(
                visible = !searchActive,
                modifier = Modifier.align(Alignment.CenterStart),
                enter = fadeIn(animationSpec = tween(140)) +
                    slideInHorizontally(
                        initialOffsetX = { -it / 8 },
                        animationSpec = tween(180)
                    ),
                exit = fadeOut(animationSpec = tween(90)) +
                    slideOutHorizontally(
                        targetOffsetX = { -it / 8 },
                        animationSpec = tween(120)
                    )
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (searchEnabled && !showChatActions && selectedContactUsername == null) {
                        IconButton(onClick = { onSearchActiveChange(true) }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Pesquisar",
                                tint = iconContentColor
                            )
                        }
                    }
                    Text(
                        text = title,
                        color = headerContentColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            AnimatedVisibility(
                visible = !searchActive,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val badgeCount = badges[tab] ?: 0
                        val interactionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = if (isSelected) dockSelectedBackgroundColor(baseThemeMode, systemDarkTheme) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { onSelectTab(tab) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(17.dp),
                                tint = if (isSelected) {
                                    dockSelectedIconTint(baseThemeMode, systemDarkTheme)
                                } else {
                                    iconContentColor.copy(alpha = 0.78f)
                                }
                            )
                            AttentionBadge(
                                count = badgeCount,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 3.dp, y = (-3).dp)
                            )
                        }
                    }
                }
            }
            if (searchActive) {
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            onSearchValueChange("")
                            onSearchActiveChange(false)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "Fechar",
                            color = headerContentColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    IconButton(onClick = onLockApp) {
                        Icon(
                            imageVector = Icons.Filled.VpnKey,
                            contentDescription = "Trancar app",
                            tint = iconContentColor
                        )
                    }
                }
            } else {
                if (searchEnabled) {
                    IconButton(
                        onClick = { onSearchActiveChange(true) },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Pesquisar",
                            tint = iconContentColor
                        )
                    }
                }
                IconButton(onClick = onLockApp) {
                    Icon(
                        imageVector = Icons.Filled.VpnKey,
                        contentDescription = "Trancar app",
                        tint = iconContentColor
                    )
                }
            }
            if (showChatActions) {
                IconButton(
                    onClick = {
                        onDeleteSelectedChats?.invoke()
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Excluir chats selecionados",
                        tint = iconContentColor
                    )
                }
                IconButton(
                    onClick = { chatMenuExpanded.value = true },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 88.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Mais ações",
                        tint = iconContentColor
                    )
                }
                DropdownMenu(
                    expanded = chatMenuExpanded.value,
                    onDismissRequest = { chatMenuExpanded.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Limpar chats") },
                        onClick = {
                            chatMenuExpanded.value = false
                            onClearSelectedChats?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Selecionar tudo") },
                        onClick = {
                            chatMenuExpanded.value = false
                            onSelectAllChats?.invoke()
                        }
                    )
                }
            } else if (selectedContactUsername != null) {
                IconButton(
                    onClick = { contactMenuExpanded.value = true },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Mais ações",
                        tint = iconContentColor
                    )
                }
                DropdownMenu(
                    expanded = contactMenuExpanded.value,
                    onDismissRequest = { contactMenuExpanded.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Remover contato") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            contactMenuExpanded.value = false
                            onDeleteSelectedContact()
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = false
) {
    val contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val contentColor = if (prominent) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val buttonModifier = modifier.heightIn(min = 38.dp)
    if (prominent) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            contentPadding = contentPadding
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                color = contentColor
            )
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            contentPadding = contentPadding
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                color = contentColor
            )
        }
    }
}

@Composable
internal fun BottomDock(
    selected: HomeTab,
    badges: Map<HomeTab, Int>,
    searchEnabled: Boolean = false,
    searchActive: Boolean = false,
    searchValue: String = "",
    searchPlaceholder: String = "Pesquisar",
    searchFocusRequester: FocusRequester? = null,
    onSearchValueChange: (String) -> Unit = {},
    onSearchSubmit: () -> Unit = {},
    onSearchToggle: () -> Unit = {},
    selectedContactUsername: String? = null,
    onDeleteSelectedContact: () -> Unit = {},
    onLockApp: () -> Unit,
    onSelect: (HomeTab) -> Unit
) {
    val systemDarkTheme = isSystemInDarkTheme()
    val baseThemeMode = ThemePreference.themeMode.collectAsState().value
    val dockColor = themeBackgroundColor(baseThemeMode, systemDarkTheme)
    val dockContentColor = readableContentColor(dockColor)
    val idleIconTint = dockContentColor.copy(alpha = 0.78f)
    val selectedIconTint = dockSelectedIconTint(baseThemeMode, systemDarkTheme)
    val selectedBackground = dockSelectedBackgroundColor(baseThemeMode, systemDarkTheme)
    val accumulatedDockDragX = remember { mutableStateOf(0f) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(selected) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        accumulatedDockDragX.value += dragAmount
                    },
                    onDragEnd = {
                        val threshold = 48f
                        val currentIndex = selected.ordinal
                        when {
                            accumulatedDockDragX.value > threshold -> {
                                HomeTab.entries.getOrNull(currentIndex - 1)?.let(onSelect)
                            }
                            accumulatedDockDragX.value < -threshold -> {
                                HomeTab.entries.getOrNull(currentIndex + 1)?.let(onSelect)
                            }
                        }
                        accumulatedDockDragX.value = 0f
                    },
                    onDragCancel = {
                        accumulatedDockDragX.value = 0f
                    }
                )
            },
        shape = RoundedCornerShape(0.dp),
        color = dockColor,
        contentColor = dockContentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                if (searchEnabled && searchActive) {
                    val fieldModifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .then(searchFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    TextField(
                        value = searchValue,
                        onValueChange = onSearchValueChange,
                        modifier = fieldModifier,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = dockContentColor),
                        placeholder = {
                            Text(
                                text = searchPlaceholder,
                                color = dockContentColor.copy(alpha = 0.62f),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (searchValue.isBlank()) {
                                        onSearchToggle()
                                    } else {
                                        onSearchValueChange("")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = if (searchValue.isBlank()) "Fechar pesquisa" else "Limpar pesquisa"
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = dockContentColor,
                            unfocusedTextColor = dockContentColor,
                            cursorColor = dockContentColor,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedLeadingIconColor = idleIconTint,
                            unfocusedLeadingIconColor = idleIconTint,
                            focusedTrailingIconColor = idleIconTint,
                            unfocusedTrailingIconColor = idleIconTint,
                            focusedPlaceholderColor = dockContentColor.copy(alpha = 0.62f),
                            unfocusedPlaceholderColor = dockContentColor.copy(alpha = 0.62f)
                        )
                    )
                } else {
                    if (searchEnabled) {
                        IconButton(
                            onClick = onSearchToggle,
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Pesquisar",
                                tint = idleIconTint
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeTab.entries.forEach { tab ->
                            val isSelected = selected == tab
                            val interactionSource = remember { MutableInteractionSource() }
                            val badgeCount = badges[tab] ?: 0
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        color = if (isSelected) selectedBackground else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = { onSelect(tab) }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(17.dp),
                                    tint = if (isSelected) selectedIconTint else idleIconTint
                                )
                                AttentionBadge(
                                    count = badgeCount,
                                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp)
                                )
                            }
                        }
                    }
                }
            }

            if (!searchActive && selectedContactUsername != null) {
                IconButton(onClick = onDeleteSelectedContact) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remover contato selecionado",
                        tint = idleIconTint
                    )
                }
            }
            IconButton(onClick = onLockApp) {
                Icon(
                    imageVector = Icons.Filled.VpnKey,
                    contentDescription = "Trancar app",
                    tint = idleIconTint
                )
            }
        }
    }
}

@Composable
internal fun AttentionBadge(
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
