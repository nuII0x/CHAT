package com.null0x.chat.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.null0x.chat.ui.common.CursorAwareOutlinedTextField
import com.null0x.chat.util.normalizeProfileEmojiInput
import com.null0x.chat.util.normalizeProfileNameInput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun profileAttentionCount(name: String, emoji: String, bio: String): Int {
    var count = 0
    if (name.trim().isBlank()) count++
    if (emoji.trim().isBlank()) count++
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

@Composable
internal fun InitialAvatar(
    text: String,
    emoji: String? = null,
    prominent: Boolean = false,
    large: Boolean = false,
    active: Boolean = false
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

    Box(
        modifier = Modifier.size(size + 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(size)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
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

@Composable
internal fun ProfileIdentityDialog(
    currentName: String,
    currentEmoji: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(normalizeProfileNameInput(currentName)) }
    var emoji by rememberSaveable { mutableStateOf(normalizeProfileEmojiInput(currentEmoji).ifBlank { "🙂" }) }

    LaunchedEffect(currentName) {
        name = normalizeProfileNameInput(currentName)
    }
    LaunchedEffect(currentEmoji) {
        emoji = normalizeProfileEmojiInput(currentEmoji).ifBlank { "🙂" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar perfil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Ajuste apenas o nome e o emoji de exibição.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
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
                CursorAwareOutlinedTextField(
                    value = emoji,
                    onValueChange = {
                        emoji = normalizeProfileEmojiInput(it)
                    },
                    singleLine = true,
                    label = { Text("Emoji do perfil") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = normalizeProfileNameInput(name)
                if (clean.isNotBlank()) onSave(clean, normalizeProfileEmojiInput(emoji).ifBlank { "🙂" })
            }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

internal fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
