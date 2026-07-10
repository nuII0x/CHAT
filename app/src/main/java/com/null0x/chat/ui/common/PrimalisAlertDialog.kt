package com.null0x.chat.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PrimalisAlertDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Agora não",
    icon: ImageVector = Icons.Filled.Info,
    neutralLabel: String? = null,
    onNeutral: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    neutralEnabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = if (destructive) colorScheme.error else colorScheme.primary
    val accentContentColor = if (destructive) colorScheme.onError else colorScheme.onPrimary

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        containerColor = colorScheme.surface,
        tonalElevation = 0.dp,
        icon = {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.12f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.padding(12.dp)
                )
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                content()
            }
        },
        confirmButton = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (neutralLabel != null && onNeutral != null) {
                    TextButton(
                        enabled = neutralEnabled,
                        onClick = onNeutral
                    ) {
                        OneLineButtonText(neutralLabel)
                    }
                }
                TextButton(
                    enabled = dismissEnabled,
                    onClick = onDismiss
                ) {
                    OneLineButtonText(dismissLabel)
                }
                Button(
                    enabled = confirmEnabled,
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = accentContentColor
                    )
                ) {
                    OneLineButtonText(confirmLabel)
                }
            }
        },
        dismissButton = {
            Spacer(modifier = Modifier.height(0.dp))
        }
    )
}

@Composable
private fun OneLineButtonText(text: String) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip
    )
}
