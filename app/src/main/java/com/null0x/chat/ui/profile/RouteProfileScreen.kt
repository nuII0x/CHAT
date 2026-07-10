package com.null0x.chat.ui.profile

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.null0x.chat.ui.common.CursorAwareOutlinedTextField
import com.null0x.chat.ui.maskedRouteLabel
import com.null0x.chat.ui.security.ProtectedWindowCapture
import com.null0x.chat.ui.common.PrimalisAlertDialog
import com.null0x.chat.ui.common.WindowDispositionScaffold
import com.null0x.chat.viewmodel.ChatViewModel

@Composable
fun RouteProfileScreen(
    profile: ChatViewModel.PublicProfile,
    profileImagePath: String = "",
    onBack: () -> Unit,
    onSaveLocalName: (String, String) -> Unit,
    contactBlocked: Boolean = false,
    onSendMessage: (() -> Unit)? = null,
    onRecordMedia: (() -> Unit)? = null,
    onRemoveContact: (() -> Unit)? = null,
    onBlockContact: (() -> Unit)? = null,
    onUnblockContact: (() -> Unit)? = null
) {
    var localName by rememberSaveable(profile.route) { mutableStateOf(profile.localName) }
    var showBlockConfirmation by rememberSaveable { mutableStateOf(false) }
    ProtectedWindowCapture(enabled = true)

    LaunchedEffect(profile.route, profile.localName) {
        if (localName.isBlank() && profile.localName.isNotBlank()) {
            localName = profile.localName
        }
    }

    WindowDispositionScaffold(
        title = "Perfil da rota",
        subtitle = maskedRouteLabel(profile.route),
        onBack = onBack,
        windowColor = MaterialTheme.colorScheme.background,
        bottomActions = {
            if (onRemoveContact != null || onBlockContact != null || onUnblockContact != null) {
                ProfileDangerActions(
                    contactBlocked = contactBlocked,
                    onRemoveContact = onRemoveContact,
                    onBlockClick = {
                        if (contactBlocked) {
                            onUnblockContact?.invoke()
                        } else {
                            showBlockConfirmation = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp, end = 8.dp)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProfileAvatar(
                            text = profile.displayName,
                            emoji = profile.emoji,
                            backgroundColor = Color(profile.mapColorArgb),
                            imagePath = profileImagePath
                        )
                        Column(modifier = Modifier.widthIn(max = 260.dp)) {
                            Text(
                                text = profile.displayName.ifBlank { "Rota" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Clip
                            )
                            Text(
                                text = profile.source,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    ProfileInfoBlock(
                        title = "Token da rota",
                        content = maskedRouteLabel(profile.route)
                    )
                    if (onSendMessage != null || onRecordMedia != null) {
                        RouteProfileQuickActions(
                            onRecordMedia = onRecordMedia,
                            onSendMessage = onSendMessage
                        )
                    }
                    Text(
                        text = "Captura protegida enquanto este perfil estiver aberto.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (profile.bio.isNotBlank()) {
                        ProfileInfoBlock(
                            title = "Bio",
                            content = profile.bio,
                            emphasizeContent = true
                        )
                    }

                    CursorAwareOutlinedTextField(
                        value = localName,
                        onValueChange = { localName = it.take(MAX_LOCAL_NAME_CHARS) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Nome local") },
                        supportingText = {
                            Text("${localName.length}/$MAX_LOCAL_NAME_CHARS · salvo só neste aparelho")
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                localName = ""
                                onSaveLocalName(profile.route, "")
                            }
                        ) {
                            Text("Remover nome")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onSaveLocalName(profile.route, localName.trim())
                            }
                        ) {
                            Text("Salvar")
                        }
                    }
                }
            }
        }
    }

    ProfileBlockContactConfirmationDialog(
        visible = showBlockConfirmation,
        onConfirmBlock = {
            showBlockConfirmation = false
            onBlockContact?.invoke()
        },
        onDismiss = {
            showBlockConfirmation = false
        }
    )
}

private const val MAX_LOCAL_NAME_CHARS = 64

@Composable
private fun RouteProfileQuickActions(
    onRecordMedia: (() -> Unit)?,
    onSendMessage: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onRecordMedia != null) {
            OutlinedButton(
                onClick = onRecordMedia,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text("Gravar mídia", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onSendMessage != null) {
            Button(
                onClick = onSendMessage,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ChatBubble,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text("Enviar mensagem", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ProfileDangerActions(
    contactBlocked: Boolean,
    onRemoveContact: (() -> Unit)?,
    onBlockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onRemoveContact != null) {
            OutlinedButton(
                onClick = onRemoveContact,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text("Remover contato")
            }
        }
        Button(
            onClick = onBlockClick,
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
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(if (contactBlocked) "Desbloquear contato" else "Bloquear contato")
        }
    }
}

@Composable
private fun ProfileBlockContactConfirmationDialog(
    visible: Boolean,
    onConfirmBlock: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    PrimalisAlertDialog(
        title = "Bloquear esta rota?",
        message = "As mensagens dessa pessoa param de chegar e o contato sai da sua lista. Para conversar de novo, você precisará adicionar o token novamente.",
        icon = Icons.Filled.Lock,
        confirmLabel = "Bloquear",
        dismissLabel = "Manter contato",
        onConfirm = onConfirmBlock,
        onDismiss = onDismiss,
        destructive = true
    )
}

@Composable
private fun ProfileAvatar(text: String, emoji: String, backgroundColor: Color, imagePath: String = "") {
    val avatarSize = 72.dp
    val avatarBitmap = androidx.compose.runtime.remember(imagePath) {
        imagePath.takeIf { it.isNotBlank() }
            ?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .width(avatarSize)
            .height(avatarSize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .width(42.dp)
                        .height(42.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileInfoBlock(
    title: String,
    content: String,
    emphasizeContent: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                text = content,
                style = if (emphasizeContent) {
                    MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp)
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip
            )
        }
    }
}
