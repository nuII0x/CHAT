package com.null0x.chat.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs

class SwipeToCloseState internal constructor() {
    internal var closeRequests by mutableIntStateOf(0)
        private set

    fun requestClose() {
        closeRequests++
    }
}

@Composable
fun rememberSwipeToCloseState(): SwipeToCloseState = remember { SwipeToCloseState() }

@Composable
fun SwipeToCloseContainer(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    state: SwipeToCloseState = rememberSwipeToCloseState(),
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        var dragX by remember { mutableFloatStateOf(0f) }
        val screenWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val animatedOffset = remember(maxWidth) { Animatable(screenWidthPx) }
        val closeThreshold = screenWidthPx * 0.18f
        val flingThreshold = 700f
        var closing by remember { androidx.compose.runtime.mutableStateOf(false) }

        LaunchedEffect(screenWidthPx) {
            animatedOffset.snapTo(screenWidthPx)
            animatedOffset.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            )
        }

        LaunchedEffect(state.closeRequests) {
            if (state.closeRequests == 0 || closing) return@LaunchedEffect
            closing = true
            animatedOffset.animateTo(
                targetValue = screenWidthPx,
                animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
            )
            onClose()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(animatedOffset.value.toInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        dragX += delta
                        scope.launch {
                            val nextValue = (animatedOffset.value + delta).coerceIn(-screenWidthPx, screenWidthPx)
                            animatedOffset.snapTo(nextValue)
                        }
                    },
                    onDragStopped = { velocity ->
                        val shouldClose = abs(dragX) >= closeThreshold || abs(velocity) >= flingThreshold
                        val direction = when {
                            abs(velocity) >= flingThreshold -> velocity
                            else -> dragX
                        }

                        if (shouldClose) {
                            val target = if (direction >= 0f) screenWidthPx else -screenWidthPx
                            scope.launch {
                                if (closing) return@launch
                                closing = true
                                animatedOffset.animateTo(target, animationSpec = tween(durationMillis = 170))
                                onClose()
                            }
                        } else {
                            scope.launch {
                                animatedOffset.animateTo(0f, animationSpec = tween(durationMillis = 220))
                            }
                        }
                        dragX = 0f
                    }
                )
        ) {
            content()
        }
    }
}
