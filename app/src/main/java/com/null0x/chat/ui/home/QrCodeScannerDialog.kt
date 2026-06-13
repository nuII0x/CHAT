package com.null0x.chat.ui.home

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrCodeScannerDialog(
    onDismiss: () -> Unit,
    onQrCodeScanned: (String) -> Unit
) {
    val previewSize = 240.dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ler QR da rota",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth(0.78f)
                        .sizeIn(maxWidth = previewSize, maxHeight = previewSize)
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        QrCameraPreview(onQrCodeScanned = onQrCodeScanned)
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCameraPreview(onQrCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onQrCodeScannedState by rememberUpdatedState(onQrCodeScanned)
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
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

        cameraProviderFuture.addListener(
            {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                val rotation = activePreviewView.display?.rotation ?: 0
                val preview = Preview.Builder()
                    .setTargetResolution(QR_ANALYSIS_RESOLUTION)
                    .setTargetRotation(rotation)
                    .build()
                    .also {
                        it.setSurfaceProvider(activePreviewView.surfaceProvider)
                    }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setOutputImageRotationEnabled(true)
                    .setTargetResolution(QR_ANALYSIS_RESOLUTION)
                    .setTargetRotation(rotation)
                    .build()
                analysis.setAnalyzer(
                    cameraExecutor,
                    QrCodeAnalyzer { code -> onQrCodeScannedState(code) }
                )

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            },
            ContextCompat.getMainExecutor(context)
        )

        onDispose {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        }
    }
}

private class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val finished = AtomicBoolean(false)
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
            if (code.isNotBlank() && finished.compareAndSet(false, true)) {
                onQrCodeScanned(code)
            }
        } finally {
            image.close()
        }
    }

    private fun decodeWithZxing(image: ImageProxy): String {
        return runCatching {
            val source = rgbaImageToLuminanceSource(image)
            val hints = mapOf(DecodeHintType.TRY_HARDER to true)
            val result = qrReader.decode(BinaryBitmap(HybridBinarizer(source)), hints)
            result.text.trim()
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

    private fun rgbaImageToLuminanceSource(image: ImageProxy): RGBLuminanceSource {
        val width = image.width
        val height = image.height
        val plane = image.planes.firstOrNull() ?: error("Frame RGBA indisponivel")
        val buffer = plane.buffer.duplicate()
        val pixels = IntArray(width * height)
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride.takeIf { it > 0 } ?: 4

        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (col in 0 until width) {
                val offset = rowStart + col * pixelStride
                if (offset + 2 >= buffer.limit()) continue
                val red = buffer.get(offset).toInt() and 0xFF
                val green = buffer.get(offset + 1).toInt() and 0xFF
                val blue = buffer.get(offset + 2).toInt() and 0xFF
                pixels[row * width + col] = (red shl 16) or (green shl 8) or blue
            }
        }
        return RGBLuminanceSource(width, height, pixels)
    }
}

private val QR_ANALYSIS_RESOLUTION = Size(720, 720)
