package com.null0x.chat.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.null0x.chat.ui.common.CursorAwareOutlinedTextField
import com.null0x.chat.ui.maskedRouteLabel
import com.null0x.chat.ui.common.SwipeToCloseContainer
import com.null0x.chat.ui.security.ProtectedWindowCapture
import com.null0x.chat.viewmodel.ChatViewModel

@Composable
fun RouteProfileScreen(
    profile: ChatViewModel.PublicProfile,
    onBack: () -> Unit,
    onLockApp: () -> Unit,
    onSaveLocalName: (String, String) -> Unit
) {
    var localName by rememberSaveable(profile.route) { mutableStateOf(profile.localName) }
    ProtectedWindowCapture(enabled = true)

    LaunchedEffect(profile.route, profile.localName) {
        if (localName.isBlank() && profile.localName.isNotBlank()) {
            localName = profile.localName
        }
    }

    SwipeToCloseContainer(onClose = onBack) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Surface(
                    color = Color.Black,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "Perfil da rota",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = onLockApp,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(
                                Icons.Filled.VpnKey,
                                contentDescription = "Trancar app",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
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
                            ProfileAvatar(text = profile.displayName, emoji = profile.emoji)
                            Column(modifier = Modifier.widthIn(max = 260.dp)) {
                                Text(
                                    text = profile.displayName.ifBlank { "Rota" },
                                    style = MaterialTheme.typography.titleLarge,
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
    }
}

private const val MAX_LOCAL_NAME_CHARS = 64

@Composable
private fun ProfileAvatar(text: String, emoji: String) {
    val avatarSize = 72.dp
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        modifier = Modifier
            .width(avatarSize)
            .height(avatarSize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (emoji.isBlank()) {
                    text.trim().take(1).ifBlank { "P" }.uppercase()
                } else {
                    emoji
                },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium
            )
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
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip
            )
        }
    }
}
