package com.null0x.chat.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.scale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.null0x.chat.R
import com.null0x.chat.ui.common.navigationBarsBottomPadding
import com.null0x.chat.ui.theme.AppBluePrimary
import com.null0x.chat.ui.theme.readableContentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import android.widget.VideoView
import android.widget.ImageView

private val MediaButtonSize = 54.dp
private val RecordingMediaButtonSize = 66.dp
private val AudioTrashBottomPadding = 76.dp
private const val AudioLongPressMillis = 100L
private const val VideoQuickReleaseMillis = 300L
private const val AudioMinBytes = 800L
private const val VideoMinBytes = 1_200L
private const val AudioMaxAmplitude = 32_767f
private val CameraFrameAspectRatio = 9f / 16f
private val CameraFrameShape = RoundedCornerShape(28.dp)

@Composable
internal fun FloatingMediaButtonOverlay(
    modifier: Modifier = Modifier,
    buttonOffsetX: Dp = 0.dp,
    buttonAlignment: Alignment = Alignment.BottomCenter,
    isAudioRecording: Boolean,
    onAudioRecordingStateChange: (Boolean) -> Unit,
    onMediaClick: () -> Unit,
    onAudioHoldStart: () -> Boolean,
    onAudioRecorded: () -> Unit,
    onAudioPermissionNeeded: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var audioRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var audioLevel by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }
    var audioButtonOffset by remember { mutableStateOf(Offset.Zero) }
    var audioButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var audioTrashBounds by remember { mutableStateOf<Rect?>(null) }
    var audioIsOverTrash by remember { mutableStateOf(false) }
    val mediaButtonColor by animateColorAsState(
        targetValue = AppBluePrimary,
        label = "mediaButtonColor"
    )
    val mediaButtonContentColor = readableContentColor(mediaButtonColor)
    val audioVisualLevel by animateFloatAsState(
        targetValue = audioLevel,
        label = "audioVisualLevel"
    )
    val mediaButtonScale by animateFloatAsState(
        targetValue = when {
            isAudioRecording -> 1.12f + (audioVisualLevel * 0.34f)
            isPressed -> 0.94f
            else -> 1f
        },
        label = "mediaButtonScale"
    )
    val mediaButtonGlow by animateFloatAsState(
        targetValue = when {
            isAudioRecording -> 0.42f + (audioVisualLevel * 0.52f)
            else -> 0f
        },
        label = "mediaButtonGlow"
    )
    val audioPulseTransition = rememberInfiniteTransition(label = "audioPulseTransition")
    val audioPulse by audioPulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "audioPulse"
    )

    LaunchedEffect(isAudioRecording, audioRecorder) {
        if (!isAudioRecording) {
            audioLevel = 0f
            return@LaunchedEffect
        }
        while (isAudioRecording) {
            val amplitude = runCatching { audioRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
            val level = (amplitude / AudioMaxAmplitude).coerceIn(0f, 1f)
            audioLevel = (audioLevel * 0.72f) + (level * 0.28f)
            delay(90L)
        }
    }
    LaunchedEffect(isAudioRecording) {
        if (!isAudioRecording) {
            audioTrashBounds = null
            audioIsOverTrash = false
        }
    }

    fun translatedAudioButtonBounds(): Rect? {
        val bounds = audioButtonBounds ?: return null
        return Rect(
            left = bounds.left + audioButtonOffset.x,
            top = bounds.top + audioButtonOffset.y,
            right = bounds.right + audioButtonOffset.x,
            bottom = bounds.bottom + audioButtonOffset.y
        )
    }

    fun isAudioOverTrash(): Boolean {
        val buttonBounds = translatedAudioButtonBounds() ?: return false
        val trashBounds = audioTrashBounds ?: return false
        return buttonBounds.overlaps(trashBounds)
    }

    fun resetAudioInteraction() {
        audioLevel = 0f
        isPressed = false
        audioButtonOffset = Offset.Zero
        audioIsOverTrash = false
    }

    fun finishAudioRecording(cancel: Boolean) {
        val recorder = audioRecorder
        audioRecorder = null
        onAudioRecordingStateChange(false)
        resetAudioInteraction()
        runCatching { recorder?.stop() }
        recorder?.release()

        val recorded = audioFile
        audioFile = null
        if (!cancel && recorded?.exists() == true && recorded.length() > AudioMinBytes) {
            onAudioRecorded()
        } else {
            recorded?.delete()
        }
    }

    Box(modifier = modifier) {
        if (isAudioRecording) {
            val trashColor = if (audioIsOverTrash) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
            val trashTint = readableContentColor(trashColor)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AudioTrashBottomPadding)
                    .scale(if (audioIsOverTrash) 1.16f else 1f)
                    .onGloballyPositioned { coordinates ->
                        audioTrashBounds = coordinates.boundsInRoot()
                    },
                shape = CircleShape,
                color = trashColor,
                tonalElevation = 4.dp
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Cancelar áudio",
                    tint = trashTint,
                    modifier = Modifier.padding(14.dp).size(22.dp)
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = when {
                isAudioRecording && audioIsOverTrash -> MaterialTheme.colorScheme.errorContainer
                else -> mediaButtonColor
            },
            modifier = Modifier
                .align(buttonAlignment)
                .offset(x = buttonOffsetX)
                .graphicsLayer(
                    translationX = audioButtonOffset.x,
                    translationY = audioButtonOffset.y
                )
                .size(if (isAudioRecording) RecordingMediaButtonSize else MediaButtonSize)
                .scale(mediaButtonScale)
                .zIndex(if (isAudioRecording) 1f else 0f)
                .onGloballyPositioned { coordinates ->
                    audioButtonBounds = coordinates.boundsInRoot()
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                .border(
                        width = if (mediaButtonGlow > 0f) 2.dp else 0.dp,
                        color = mediaButtonColor.copy(alpha = 0.24f + (mediaButtonGlow * 0.38f)),
                        shape = CircleShape
                    )
                    .pointerInput(context) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPressed = true
                            val tapReleasedEarly = withTimeoutOrNull(AudioLongPressMillis) {
                                waitForUpOrCancellation()
                            }
                            if (tapReleasedEarly != null) {
                                isPressed = false
                                onMediaClick()
                                return@awaitEachGesture
                            }

                            if (!onAudioHoldStart()) {
                                isPressed = false
                                onAudioPermissionNeeded()
                                waitForUpOrCancellation()
                                return@awaitEachGesture
                            }

                            val targetFile = ephemeralMediaFile(context, "audio", "m4a")
                            audioFile = targetFile
                            onAudioRecordingStateChange(true)
                            audioRecorder = createAudioRecorder(context, targetFile).also { recorder ->
                                runCatching {
                                    recorder.prepare()
                                    recorder.start()
                                }.onFailure {
                                    recorder.release()
                                    audioRecorder = null
                                    audioFile = null
                                    onAudioRecordingStateChange(false)
                                    resetAudioInteraction()
                                    targetFile.delete()
                                }
                            }

                            audioButtonOffset = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    audioButtonOffset += delta
                                }
                                audioIsOverTrash = isAudioOverTrash()
                                if (!change.pressed) break
                            }
                            finishAudioRecording(cancel = isAudioOverTrash())
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isAudioRecording) {
                    val haloColor = colorScheme.primary.copy(alpha = 0.16f + (audioVisualLevel * 0.34f))
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.06f + (audioPulse * 0.20f) + (audioVisualLevel * 0.18f))
                            .border(
                                width = 2.dp,
                                color = haloColor,
                                shape = CircleShape
                            )
                    )
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val barBase = 6.dp
                        val barOne = barBase + ((audioVisualLevel * 10f) + (audioPulse * 3f)).dp
                        val barTwo = barBase + ((audioVisualLevel * 14f) + (audioPulse * 5f)).dp
                        val barThree = barBase + ((audioVisualLevel * 8f) + (audioPulse * 4f)).dp
                        listOf(barOne, barTwo, barThree).forEachIndexed { index, barHeight ->
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(barHeight.coerceAtMost(18.dp))
                                    .background(
                                        color = mediaButtonContentColor.copy(
                                            alpha = when (index) {
                                                1 -> 0.88f
                                                else -> 0.72f
                                            }
                                        ),
                                        shape = RoundedCornerShape(999.dp)
                                    )
                            )
                        }
                    }
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_stat_nochat),
                    contentDescription = "Criar mídia",
                    tint = mediaButtonContentColor
                )
            }
        }
    }
}

@Composable
internal fun EphemeralCameraOverlay(
    onDismiss: () -> Unit,
    onLockApp: () -> Unit,
    onReviewSend: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val colorScheme = MaterialTheme.colorScheme
    val swipeDismissThreshold = with(LocalDensity.current) { 92.dp.toPx() }
    val controlsBottomPadding = navigationBarsBottomPadding(extraTouchSpace = 22.dp)
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var activeVideoFile by remember { mutableStateOf<File?>(null) }
    var reviewMediaFile by remember { mutableStateOf<File?>(null) }
    var reviewMediaIsVideo by remember { mutableStateOf(false) }
    var reviewVideoView by remember { mutableStateOf<VideoView?>(null) }
    var reviewVideoPlaying by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    val recordingVideo = activeRecording != null
    val reviewMode = reviewMediaFile != null
    val cameraSelector = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }
    val captureButtonColor by animateColorAsState(
        targetValue = if (recordingVideo) {
            colorScheme.error
        } else {
            colorScheme.primary
        },
        label = "captureButtonColor"
    )
    val captureButtonScale by animateFloatAsState(
        targetValue = if (recordingVideo) 1.12f else 1f,
        label = "captureButtonScale"
    )
    val captureIconColor = readableContentColor(if (recordingVideo) colorScheme.error else captureButtonColor)
    val captureRingAlpha by animateFloatAsState(
        targetValue = if (recordingVideo) 0.72f else 0.14f,
        label = "captureRingAlpha"
    )
    val videoPulseTransition = rememberInfiniteTransition(label = "videoPulseTransition")
    val videoPulse by videoPulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "videoPulse"
    )

    LaunchedEffect(previewView, lensFacing) {
        val view = previewView ?: return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val targetRotation = view.display?.rotation ?: Surface.ROTATION_0
            val frameRatio = Rational(9, 16)
            view.scaleX = 1f
            val preview = Preview.Builder()
                .setTargetRotation(targetRotation)
                .build()
                .also {
                it.setSurfaceProvider(view.surfaceProvider)
            }
            val image = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(targetRotation)
                .build()
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HD,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                    )
                )
                .build()
            val video = VideoCapture.withOutput(recorder)
            val viewPort = ViewPort.Builder(frameRatio, targetRotation)
                .setScaleType(ViewPort.FILL_CENTER)
                .build()
            val useCaseGroup = UseCaseGroup.Builder()
                .setViewPort(viewPort)
                .addUseCase(preview)
                .addUseCase(image)
                .addUseCase(video)
                .build()
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    useCaseGroup
                )
                imageCapture = image
                videoCapture = video
            }
        }, executor)
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            deleteEphemeralMedia(context)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(swipeDismissThreshold) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragX = 0f
                        var dragY = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            val delta = change.positionChange()
                            dragX += delta.x
                            dragY += delta.y
                            if (
                                kotlin.math.abs(dragX) > swipeDismissThreshold &&
                                kotlin.math.abs(dragX) > kotlin.math.abs(dragY) * 1.25f
                            ) {
                                onDismiss()
                                break
                            }
                            if (change.changedToUpIgnoreConsumed()) {
                                break
                            }
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(CameraFrameAspectRatio)
                    .clip(CameraFrameShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (reviewMode) {
                    if (reviewMediaIsVideo) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).also { view ->
                                    reviewVideoView = view
                                }
                            },
                            update = { view ->
                                val file = reviewMediaFile ?: return@AndroidView
                                val fileTag = file.absolutePath
                                if (view.tag != fileTag) {
                                    view.stopPlayback()
                                    view.setVideoURI(Uri.fromFile(file))
                                    view.setOnPreparedListener { mediaPlayer ->
                                        mediaPlayer.isLooping = false
                                        view.seekTo(1)
                                        reviewVideoPlaying = false
                                    }
                                    view.setOnCompletionListener {
                                        reviewVideoPlaying = false
                                        view.seekTo(1)
                                    }
                                    view.tag = fileTag
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.surface.copy(alpha = 0.45f),
                                modifier = Modifier.size(88.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val view = reviewVideoView ?: return@IconButton
                                        if (reviewVideoPlaying) {
                                            view.pause()
                                            reviewVideoPlaying = false
                                        } else {
                                            view.start()
                                            reviewVideoPlaying = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = if (reviewVideoPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                        contentDescription = if (reviewVideoPlaying) "Pausar vídeo" else "Reproduzir vídeo",
                                        tint = readableContentColor(colorScheme.surface)
                                    )
                                }
                            }
                        }
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                ImageView(ctx).also { view ->
                                    view.scaleType = ImageView.ScaleType.CENTER_CROP
                                }
                            },
                            update = { view ->
                                val file = reviewMediaFile ?: return@AndroidView
                                val fileTag = file.absolutePath
                                if (view.tag != fileTag) {
                                    view.setImageURI(Uri.fromFile(file))
                                    view.tag = fileTag
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { view ->
                                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                                previewView = view
                            }
                        },
                        update = { view ->
                            view.scaleX = 1f
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = colorScheme.surface.copy(alpha = 0.66f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp)
                    .size(44.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = readableContentColor(colorScheme.surface)
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = colorScheme.surface.copy(alpha = 0.66f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .size(44.dp)
            ) {
                IconButton(
                    onClick = onLockApp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.VpnKey,
                        contentDescription = "Trancar app",
                        tint = readableContentColor(colorScheme.surface)
                    )
                }
            }
            if (reviewMode) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = controlsBottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surface.copy(alpha = 0.66f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        IconButton(
                            onClick = {
                                reviewVideoView?.stopPlayback()
                                reviewVideoPlaying = false
                                reviewMediaFile?.delete()
                                reviewMediaFile = null
                                reviewMediaIsVideo = false
                                reviewVideoView = null
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refazer",
                                tint = readableContentColor(colorScheme.surface)
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    ) {
                        IconButton(
                            onClick = {
                                reviewVideoView?.stopPlayback()
                                reviewVideoPlaying = false
                                onReviewSend(reviewMediaIsVideo)
                                reviewMediaFile = null
                                reviewMediaIsVideo = false
                                reviewVideoView = null
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = "Enviar",
                                tint = readableContentColor(colorScheme.primary)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = controlsBottomPadding)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        shape = CircleShape,
                        color = captureButtonColor,
                        modifier = Modifier
                            .size(if (recordingVideo) 84.dp else 76.dp)
                            .scale(captureButtonScale * if (recordingVideo) videoPulse else 1f)
                            .pointerInput(imageCapture, videoCapture, activeRecording) {
                                awaitEachGesture {
                                    awaitFirstDown()
                                    val recordingInProgress = activeRecording
                                    if (recordingInProgress != null) {
                                        val file = activeVideoFile
                                        activeRecording = null
                                        activeVideoFile = null
                                        runCatching { recordingInProgress.stop() }
                                            .onFailure { file?.delete() }
                                        return@awaitEachGesture
                                    }
                                    val quickRelease = withTimeoutOrNull(VideoQuickReleaseMillis) {
                                        waitForUpOrCancellation()
                                    }
                                    if (quickRelease != null) {
                                        imageCapture?.let { capture ->
                                            val file = ephemeralMediaFile(context, "photo", "jpg")
                                            val output = ImageCapture.OutputFileOptions.Builder(file).build()
                                            capture.takePicture(
                                                output,
                                                ContextCompat.getMainExecutor(context),
                                                object : ImageCapture.OnImageSavedCallback {
                                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                        if (file.exists() && file.length() > 0L) {
                                                            reviewMediaFile = file
                                                            reviewMediaIsVideo = false
                                                            reviewVideoPlaying = false
                                                        } else {
                                                            file.delete()
                                                        }
                                                    }

                                                    override fun onError(exception: ImageCaptureException) {
                                                        file.delete()
                                                    }
                                                }
                                            )
                                        }
                                        return@awaitEachGesture
                                    }
                                    val capture = videoCapture ?: return@awaitEachGesture
                                    val file = ephemeralMediaFile(context, "video", "mp4")
                                    val output = FileOutputOptions.Builder(file).build()
                                    var recording: Recording? = null
                                    activeVideoFile = file
                                    recording = capture.output
                                        .prepareRecording(context, output)
                                        .apply {
                                            if (
                                                ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED
                                            ) {
                                                withAudioEnabled()
                                            }
                                        }
                                        .asPersistentRecording()
                                        .start(ContextCompat.getMainExecutor(context)) { event ->
                                            if (event is VideoRecordEvent.Finalize) {
                                                activeRecording = null
                                                activeVideoFile = null
                                                if (!event.hasError() && file.exists() && file.length() > VideoMinBytes) {
                                                    reviewMediaFile = file
                                                    reviewMediaIsVideo = true
                                                    reviewVideoPlaying = false
                                                } else {
                                                    file.delete()
                                                }
                                            }
                                        }
                                    activeRecording = recording
                                    return@awaitEachGesture
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        width = if (recordingVideo) 3.dp else 2.dp,
                                        color = if (recordingVideo) {
                                            colorScheme.onSurface.copy(alpha = captureRingAlpha)
                                        } else {
                                            colorScheme.primary.copy(alpha = captureRingAlpha)
                                        },
                                        shape = CircleShape
                                    )
                            )
                            if (recordingVideo) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(1.04f)
                                        .border(
                                            width = 2.dp,
                                            color = colorScheme.error.copy(alpha = 0.22f + (videoPulse * 0.24f)),
                                            shape = CircleShape
                                        )
                                )
                            }
                            Icon(
                                imageVector = if (recordingVideo) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                                contentDescription = "Capturar",
                                tint = captureIconColor,
                                modifier = Modifier.size(if (recordingVideo) 40.dp else 34.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surface.copy(alpha = 0.66f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        IconButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cameraswitch,
                                contentDescription = "Alternar câmera",
                                tint = readableContentColor(colorScheme.surface)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun ephemeralMediaFile(context: Context, prefix: String, extension: String): File {
    val dir = File(context.cacheDir, "ephemeral_media").apply { mkdirs() }
    return File(dir, "$prefix-${System.currentTimeMillis()}.$extension")
}

internal fun deleteEphemeralMedia(context: Context) {
    val dir = File(context.cacheDir, "ephemeral_media")
    if (!dir.exists()) return
    dir.deleteRecursively()
}

private fun createAudioRecorder(context: Context, targetFile: File): MediaRecorder {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(64_000)
        setAudioSamplingRate(44_100)
        setOutputFile(targetFile.absolutePath)
    }
}
