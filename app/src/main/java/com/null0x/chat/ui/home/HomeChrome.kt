package com.null0x.chat.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.null0x.chat.ui.AppDockShape
import com.null0x.chat.ui.AppTitleBarShape

@Composable
internal fun AppHeader(
    title: String,
    selectedContactUsername: String?,
    selectedChatsCount: Int = 0,
    onDeleteSelectedChats: (() -> Unit)? = null,
    onClearSelectedChats: (() -> Unit)? = null,
    onSelectAllChats: (() -> Unit)? = null,
    onDeleteSelectedContact: () -> Unit,
    onLockApp: () -> Unit
) {
    val showChatActions = selectedChatsCount > 0
    val trailingPadding = when {
        showChatActions -> 200.dp
        selectedContactUsername != null -> 104.dp
        else -> 52.dp
    }
    var chatMenuExpanded = remember { mutableStateOf(false) }
    var contactMenuExpanded = remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = AppTitleBarShape,
        color = Color.Black,
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HomeHeaderHeight)
                .padding(horizontal = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = trailingPadding),
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
                }
            }
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
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = { chatMenuExpanded.value = true },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 88.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Mais ações",
                        tint = Color.White
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
                        tint = Color.White
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
    onSelect: (HomeTab) -> Unit
) {
    val idleIconTint = Color.White.copy(alpha = 0.76f)
    val selectedAccent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = AppDockShape,
        color = Color.Black,
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HomeTab.entries.forEach { tab ->
                    val isSelected = selected == tab
                    val interactionSource = remember { MutableInteractionSource() }
                    val badgeCount = badges[tab] ?: 0
                    Box(
                        modifier = Modifier
                            .width(68.dp)
                            .height(70.dp)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelect(tab) }
                            ),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = if (isSelected) {
                                            selectedAccent.copy(alpha = 0.2f)
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
                                    tint = if (isSelected) selectedAccent else idleIconTint
                                )
                            }
                            Text(
                                text = tab.title,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        AttentionBadge(
                            count = badgeCount,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 8.dp)
                        )
                    }
                }
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
