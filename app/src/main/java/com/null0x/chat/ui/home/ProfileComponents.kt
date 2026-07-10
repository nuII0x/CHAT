package com.null0x.chat.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.null0x.chat.ui.common.CursorAwareOutlinedTextField
import com.null0x.chat.util.normalizeProfileNameInput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun profileAttentionCount(name: String, bio: String): Int {
    var count = 0
    if (name.trim().isBlank()) count++
    if (bio.trim().isBlank()) count++
    return count
}

internal fun limitUtf8Bytes(text: String, maxBytes: Int): String {
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

internal fun readableProfileAvatarContentColor(backgroundColor: Color): Color {
    return if (backgroundColor.luminance() > 0.56f) Color(0xFF111111) else Color.White
}

@Composable
internal fun InitialAvatar(
    text: String,
    emoji: String? = null,
    backgroundColor: Color? = null,
    prominent: Boolean = false,
    large: Boolean = false,
    active: Boolean = false,
    imagePath: String = ""
) {
    val size = when {
        large -> 72.dp
        prominent -> 44.dp
        else -> 42.dp
    }
    val color = backgroundColor ?: MaterialTheme.colorScheme.surfaceVariant
    val iconColor = backgroundColor?.let { readableProfileAvatarContentColor(it) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier.size(size + 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(size)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                val avatarBitmap = androidx.compose.runtime.remember(imagePath) {
                    imagePath.takeIf { it.isNotBlank() }
                        ?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
                }
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
                        tint = iconColor,
                        modifier = Modifier.size(size * 0.56f)
                    )
                }
            }
        }
        if (active) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF2ECC71),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 0.dp, y = 0.dp)
                    .size(10.dp)
                    .zIndex(1f)
            ) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileIdentityBottomSheet(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(normalizeProfileNameInput(currentName)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(currentName) {
        name = normalizeProfileNameInput(currentName)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Editar perfil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Ajuste o nome público do perfil.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            CursorAwareOutlinedTextField(
                value = name,
                onValueChange = { name = normalizeProfileNameInput(it) },
                singleLine = true,
                label = { Text("Nome do perfil") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                TextButton(
                    onClick = {
                        val clean = normalizeProfileNameInput(name)
                        if (clean.isNotBlank()) onSave(clean)
                    }
                ) {
                    Text("Salvar")
                }
            }
        }
    }
}

internal fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
