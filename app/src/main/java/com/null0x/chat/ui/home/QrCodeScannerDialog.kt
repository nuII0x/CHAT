package com.null0x.chat.ui.home

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.android.gms.tasks.Tasks
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.null0x.chat.ui.common.WindowDispositionScaffold
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrCodeScannerDialog(
    onDismiss: () -> Unit,
    isValidQrCode: (String) -> Boolean,
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    var warningText by remember { mutableStateOf("") }
    LaunchedEffect(warningText) {
        if (warningText.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(1_600)
        warningText = ""
    }
    WindowDispositionScaffold(
        title = "Ler QR",
        subtitle = "Aponte para o código da rota",
        onBack = onDismiss,
        windowColor = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Transparent)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { },
                contentAlignment = Alignment.Center
            ) {
                QrCameraPreview(onQrCodeRead = { code ->
                    if (isValidQrCode(code)) {
                        mainExecutor.execute { onQrCodeScanned(code) }
                        true
                    } else {
                        mainExecutor.execute { warningText = "QR sem token NoChat" }
                        false
                    }
                })
            }
        }
        if (warningText.isNotBlank()) {
            Text(
                text = warningText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 18.dp)
            )
        }
    }
}

@Composable
private fun QrCameraPreview(onQrCodeRead: (String) -> Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onQrCodeReadState by rememberUpdatedState(onQrCodeRead)
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                previewView = this
            }
        }
    )

    DisposableEffect(previewView, lifecycleOwner) {
        val activePreviewView = previewView ?: return@DisposableEffect onDispose {}
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraExecutor = Executors.newSingleThreadExecutor()
        var cameraProvider: ProcessCameraProvider? = null
        var analyzer: QrCodeAnalyzer? = null
        val disposed = AtomicBoolean(false)
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(720, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        cameraProviderFuture.addListener(
            {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                if (disposed.get()) {
                    provider.unbindAll()
                } else {
                    val rotation = activePreviewView.display?.rotation ?: 0
                    val preview = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setTargetRotation(rotation)
                        .build()
                        .also {
                            it.setSurfaceProvider(activePreviewView.surfaceProvider)
                        }
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageRotationEnabled(true)
                        .setTargetRotation(rotation)
                        .build()
                    val qrAnalyzer = QrCodeAnalyzer { code -> onQrCodeReadState(code) }
                    analyzer = qrAnalyzer
                    analysis.setAnalyzer(cameraExecutor, qrAnalyzer)

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }
            },
            ContextCompat.getMainExecutor(context)
        )

        onDispose {
            disposed.set(true)
            cameraProvider?.unbindAll()
            analyzer?.close()
            cameraExecutor.shutdown()
        }
    }
}

private class QrCodeAnalyzer(
    private val onQrCodeRead: (String) -> Boolean
) : ImageAnalysis.Analyzer {
    private val finished = AtomicBoolean(false)
    private var lastRejectedCode: String? = null
    private val qrReader = QRCodeReader()
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    override fun analyze(image: ImageProxy) {
        if (finished.get()) {
            image.close()
            return
        }

        try {
            val code = decodeWithZxing(image).ifBlank {
                decodeWithMlKit(image)
            }
            if (code.isNotBlank()) {
                if (code == lastRejectedCode) {
                    return
                }
                if (onQrCodeRead(code)) {
                    finished.compareAndSet(false, true)
                } else {
                    lastRejectedCode = code
                }
            } else {
                lastRejectedCode = null
            }
        } finally {
            image.close()
        }
    }

    private fun decodeWithZxing(image: ImageProxy): String {
        return runCatching {
            val source = yPlaneToLuminanceSource(image)
            val hints = mapOf(DecodeHintType.TRY_HARDER to true)
            val result = qrReader.decode(BinaryBitmap(HybridBinarizer(source)), hints)
            result.text.trim()
        }.also {
            qrReader.reset()
        }.getOrElse { "" }
    }

    private fun decodeWithMlKit(image: ImageProxy): String {
        val mediaImage = image.image ?: return ""
        return runCatching {
            val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
            Tasks.await(scanner.process(inputImage), 350, TimeUnit.MILLISECONDS)
                .firstOrNull()
                ?.rawValue
                ?.trim()
                .orEmpty()
        }.getOrElse { "" }
    }

    fun close() {
        scanner.close()
    }

    private fun yPlaneToLuminanceSource(image: ImageProxy): RGBLuminanceSource {
        val width = image.width
        val height = image.height
        val plane = image.planes.firstOrNull() ?: error("Frame YUV indisponivel")
        val buffer = plane.buffer.duplicate()
        val pixels = IntArray(width * height)
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride.takeIf { it > 0 } ?: 1

        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (col in 0 until width) {
                val offset = rowStart + col * pixelStride
                if (offset >= buffer.limit()) continue
                val luminance = buffer.get(offset).toInt() and 0xFF
                pixels[row * width + col] = (luminance shl 16) or (luminance shl 8) or luminance
            }
        }
        return RGBLuminanceSource(width, height, pixels)
    }
}
