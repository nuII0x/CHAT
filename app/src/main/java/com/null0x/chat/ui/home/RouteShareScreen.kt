package com.null0x.chat.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.widget.Toast
import com.null0x.chat.security.SensitiveClipboard
import com.null0x.chat.ui.common.WindowDispositionScaffold

@Composable
internal fun RouteShareScreen(
    tokenLabel: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val windowColor = MaterialTheme.colorScheme.background
    val qrForeground = if (windowColor.luminance() > 0.5f) Color.Black else Color.White
    WindowDispositionScaffold(
        title = "Compartilhar",
        subtitle = "Mostra o QR de acesso para outro dispositivo",
        onBack = onBack,
        windowColor = windowColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Compartilhar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Acesso rapido via QR",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        RouteTokenQr(
                            token = tokenLabel,
                            foregroundColor = qrForeground,
                            displaySize = 240.dp
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tokenLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Token",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (tokenLabel.isNotBlank() && tokenLabel != "Aguardando parceiro...") {
                Button(
                    onClick = {
                        SensitiveClipboard.copy(context, "Token de rota", tokenLabel)
                        Toast.makeText(context, "Token copiado por 60 segundos", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    Text("Copiar token")
                }
            }
        }
    }
}

@Composable
internal fun RouteTokenQr(
    token: String,
    foregroundColor: Color,
    displaySize: Dp
) {
    if (token.isBlank()) return
    val foreground = foregroundColor
    val bitmap = remember(token, foreground) {
        generateQrBitmap(
            text = token,
            sizePx = 360,
            foregroundArgb = foreground.toArgb(),
            backgroundArgb = Color.Transparent.toArgb()
        )
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "QR do token",
            modifier = Modifier.size(displaySize)
        )
    }
}

internal fun generateQrBitmap(
    text: String,
    sizePx: Int,
    foregroundArgb: Int,
    backgroundArgb: Int
): Bitmap? = runCatching {
    val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)

    Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                setPixel(x, y, if (bitMatrix[x, y]) foregroundArgb else backgroundArgb)
            }
        }
    }
}.getOrNull()
