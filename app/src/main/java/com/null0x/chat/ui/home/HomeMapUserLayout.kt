package com.null0x.chat.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Paint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.null0x.chat.ui.common.PrimalisAlertDialog
import com.null0x.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun buildUserOccupationBoxes(
    positionedUsers: List<PositionedOrbitUser>,
    width: Float,
    height: Float
): List<LabelBox> {
    if (positionedUsers.isEmpty()) return emptyList()
    return positionedUsers.mapNotNull { user ->
        if (!user.display.isNearViewport(width, height, 120f)) return@mapNotNull null
        val headRadius = if (user.user.isSelf) 32f else 28f
        val headBox = LabelBox(
            left = user.display.x - headRadius,
            top = user.display.y - headRadius,
            right = user.display.x + headRadius,
            bottom = user.display.y + headRadius,
            textX = user.display.x,
            textY = user.display.y,
            kind = LabelKind.City
        )
        val label = user.label
        if (label == null) {
            listOf(headBox)
        } else {
            val nameWidth = label.length * 7.5f
            val nameBox = LabelBox(
                left = user.display.x - nameWidth / 2f - 8f,
                top = user.display.y + headRadius + 4f,
                right = user.display.x + nameWidth / 2f + 8f,
                bottom = user.display.y + headRadius + 26f,
                textX = user.display.x,
                textY = user.display.y,
                kind = LabelKind.City
            )
            val distanceBox = LabelBox(
                left = user.display.x - 64f,
                top = user.display.y + headRadius + 22f,
                right = user.display.x + 64f,
                bottom = user.display.y + headRadius + 42f,
                textX = user.display.x,
                textY = user.display.y,
                kind = LabelKind.City
            )
            if (label == "Eu") listOf(headBox, nameBox) else listOf(headBox, nameBox, distanceBox)
        }
    }.flatten()
}

internal suspend fun PointerInputScope.detectStableMapTransformGestures(
    onGesture: (centroid: Offset, panChange: Offset, zoomChange: Float) -> Unit,
    onDoubleTap: (position: Offset) -> Unit
) {
    var lastTapPosition: Offset? = null
    var lastTapUptimeMillis = 0L
    val tapSlop = 10.dp.toPx()
    val doubleTapDistance = 48.dp.toPx()
    val doubleTapTimeoutMillis = 320L
    awaitEachGesture {
        var previousSinglePointer: Offset? = null
        var previousCentroid: Offset? = null
        var previousSpan: Float? = null
        var resumedSinglePointerAfterPinch = false
        var tapStartPosition: Offset? = null
        var latestSinglePointer: Offset? = null
        var maxTapMove = 0f
        var hadMultiplePointers = false
        while (true) {
            val event = awaitPointerEvent()
            val pressedChanges = event.changes.filter { it.pressed }
            if (pressedChanges.isEmpty()) {
                val tapPosition = latestSinglePointer ?: tapStartPosition
                val eventUptimeMillis = event.changes.maxOfOrNull { it.uptimeMillis } ?: lastTapUptimeMillis
                if (!hadMultiplePointers && tapPosition != null && maxTapMove <= tapSlop) {
                    val previousTapPosition = lastTapPosition
                    val isDoubleTap = previousTapPosition != null &&
                        eventUptimeMillis - lastTapUptimeMillis in 1..doubleTapTimeoutMillis &&
                        tapPosition.distanceTo(previousTapPosition) <= doubleTapDistance
                    if (isDoubleTap) {
                        onDoubleTap(tapPosition)
                        lastTapPosition = null
                        lastTapUptimeMillis = 0L
                    } else {
                        lastTapPosition = tapPosition
                        lastTapUptimeMillis = eventUptimeMillis
                    }
                }
                break
            }

            if (pressedChanges.size == 1) {
                val pointer = pressedChanges.first()
                val currentPosition = pointer.position
                if (resumedSinglePointerAfterPinch) {
                    previousSinglePointer = currentPosition
                    previousCentroid = null
                    previousSpan = null
                    latestSinglePointer = null
                    tapStartPosition = null
                    resumedSinglePointerAfterPinch = false
                    pressedChanges.forEach { it.consume() }
                    continue
                }
                if (tapStartPosition == null) {
                    tapStartPosition = currentPosition
                }
                latestSinglePointer = currentPosition
                maxTapMove = maxOf(
                    maxTapMove,
                    tapStartPosition?.distanceTo(currentPosition) ?: 0f
                )
                val oldPosition = previousSinglePointer
                if (oldPosition != null && maxTapMove > tapSlop) {
                    onGesture(
                        currentPosition,
                        currentPosition - oldPosition,
                        1f
                    )
                }
                previousSinglePointer = currentPosition
                previousCentroid = null
                previousSpan = null
                pressedChanges.forEach { it.consume() }
                continue
            }

            hadMultiplePointers = true
            resumedSinglePointerAfterPinch = true
            tapStartPosition = null
            latestSinglePointer = null
            previousSinglePointer = null
            val centroid = pressedChanges
                .map { it.position }
                .averageOffset()
            val span = pressedChanges
                .map { it.position.distanceTo(centroid) }
                .average()
                .toFloat()
                .coerceAtLeast(1f)
            val oldCentroid = previousCentroid
            val oldSpan = previousSpan
            if (oldCentroid != null && oldSpan != null && oldSpan > 0f) {
                val zoomChange = (span / oldSpan).coerceIn(0.86f, 1.16f)
                onGesture(
                    centroid,
                    centroid - oldCentroid,
                    zoomChange
                )
            }
            previousCentroid = centroid
            previousSpan = span
            pressedChanges.forEach { it.consume() }
        }
    }
}

internal fun wrapHorizontalMapPan(panX: Float, worldWidthPx: Float): Float {
    if (worldWidthPx <= 0f) return panX
    var wrapped = panX % worldWidthPx
    val halfWorld = worldWidthPx / 2f
    if (wrapped > halfWorld) wrapped -= worldWidthPx
    if (wrapped < -halfWorld) wrapped += worldWidthPx
    return wrapped
}

internal fun horizontalWorldCopyOffsets(width: Float, worldWidthPx: Float, panX: Float): List<Float> {
    if (worldWidthPx <= 0f) return listOf(0f)
    val halfWorld = worldWidthPx / 2f
    val halfViewport = width / 2f
    return buildList {
        if (-halfViewport - panX < -halfWorld) add(-worldWidthPx)
        add(0f)
        if (halfViewport - panX > halfWorld) add(worldWidthPx)
    }
}

internal fun clampVerticalMapPan(
    origin: Location,
    centerY: Float,
    height: Float,
    metersPerPixel: Float,
    panY: Float
): Float {
    val latScale = 111_320.0
    val northMeters = (90.0 - origin.latitude) * latScale
    val southMeters = (-90.0 - origin.latitude) * latScale
    val minPanY = height - centerY + (southMeters / metersPerPixel).toFloat()
    val maxPanY = (northMeters / metersPerPixel).toFloat() - centerY
    if (minPanY > maxPanY) return (minPanY + maxPanY) / 2f
    return panY.coerceIn(minPanY, maxPanY)
}

internal fun wrappedLongitudeDelta(originLongitude: Double, targetLongitude: Double): Double {
    var delta = targetLongitude - originLongitude
    while (delta > 180.0) delta -= 360.0
    while (delta < -180.0) delta += 360.0
    return delta
}

internal fun offlineMapPalette(useDarkMapColors: Boolean): OfflineMapPalette {
    return if (useDarkMapColors) {
        OfflineMapPalette(
            ocean = Color(0xFF071B33),
            land = Color(0xFF153D2A).copy(alpha = 0.84f),
            grid = Color.White.copy(alpha = 0.10f),
            boundary = Color.White.copy(alpha = 0.28f),
            stateBoundary = Color(0xFFC9F2D1).copy(alpha = 0.36f),
            cityLabel = Color(0xFFD8EBF8),
            cityDot = Color(0xFF8FCBFF),
            pointer = Color(0xFF9FD3FF)
        )
    } else {
        OfflineMapPalette(
            ocean = Color(0xFFD8F0FF),
            land = Color(0xFFE7F3D4).copy(alpha = 0.92f),
            grid = Color(0xFF5A91B8).copy(alpha = 0.16f),
            boundary = Color(0xFF2F6F93).copy(alpha = 0.34f),
            stateBoundary = Color(0xFF3D7D3C).copy(alpha = 0.42f),
            cityLabel = Color(0xFF17425F),
            cityDot = Color(0xFF1976A8),
            pointer = Color(0xFF1976A8).copy(alpha = 0.74f)
        )
    }
}

internal fun separateNearbyUsers(
    users: List<OrbitUser>,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    worldWidthPx: Float,
    width: Float,
    height: Float
): List<PositionedOrbitUser> {
    return separateUsersAtAnchors(
        users = users,
        width = width,
        height = height,
        anchorFor = { user -> user.screenPoint(center, pan, metersPerPixel, worldWidthPx) }
    )
}

internal fun separateGlobeUsers(
    users: List<OrbitUser>,
    origin: Location,
    centerLongitude: Double,
    centerLatitude: Double,
    globeCenter: Offset,
    globeRadius: Float,
    width: Float,
    height: Float
): List<PositionedOrbitUser> {
    val lonScale = (111_320.0 * cos(Math.toRadians(origin.latitude))).coerceAtLeast(1e-6)
    val visibleAnchors = users.mapNotNull { user ->
        val longitude = origin.longitude + user.xMeters / lonScale
        val latitude = origin.latitude + user.yMeters / 111_320.0
        val projected = projectOnGlobe(
            longitude = longitude,
            latitude = latitude,
            centerLongitude = centerLongitude,
            centerLatitude = centerLatitude,
            radius = globeRadius
        )
        if (projected.visible) user to (globeCenter + Offset(projected.x, projected.y)) else null
    }.toMap()
    return separateUsersAtAnchors(
        users = users.filter { it in visibleAnchors },
        width = width,
        height = height,
        anchorFor = { user -> visibleAnchors.getValue(user) }
    )
}

internal fun separateUsersAtAnchors(
    users: List<OrbitUser>,
    width: Float,
    height: Float,
    anchorFor: (OrbitUser) -> Offset
): List<PositionedOrbitUser> {
    if (users.isEmpty()) return emptyList()
    val clusteredAnchorDistance = 118f
    val anchoredUsers = users
        .sortedBy { it.distanceMeters }
        .map { user ->
            AnchoredOrbitUser(
                user = user,
                anchor = anchorFor(user)
            )
        }
    val clusters = buildOrbitUserClusters(anchoredUsers, clusteredAnchorDistance)
    val placed = mutableListOf<PositionedOrbitUser>()
    val avatarRadius = 48f
    clusters.forEach { cluster ->
        val clusterCenter = cluster
            .map { it.anchor }
            .averageOffset()
        val isDenseCluster = cluster.size >= 4 ||
            (cluster.size >= 3 && cluster.maxAnchorDistance() < 54f)
        val forcePins = cluster.size > 1
        cluster.sortedBy { it.user.username }.forEachIndexed { memberIndex, anchored ->
            val hasNearbyDisplay = placed.any { positioned ->
                anchored.anchor.distanceTo(positioned.display) < clusteredAnchorDistance
            }
            val display = if (isDenseCluster) {
                anchored.anchor
            } else if (!forcePins && !hasNearbyDisplay) {
                anchored.anchor
            } else {
                bestSeparatedPoint(
                    anchor = anchored.anchor,
                    clusterCenter = clusterCenter,
                    placed = placed.map { it.display },
                    seed = stablePinSeed(anchored.user.username),
                    memberIndex = memberIndex,
                    memberCount = cluster.size,
                    width = width,
                    height = height,
                    radius = avatarRadius
                )
            }
            placed += PositionedOrbitUser(
                user = anchored.user,
                anchor = anchored.anchor,
                display = display,
                needsPointer = !isDenseCluster && (forcePins || display.distanceTo(anchored.anchor) > 8f),
                label = when {
                    isDenseCluster && anchored.user.isSelf -> "Eu"
                    isDenseCluster -> null
                    else -> anchored.user.displayName
                }
            )
        }
    }
    return placed
}

internal fun stablePinSeed(value: String): Int {
    return value.hashCode().absoluteValue
}

internal fun List<AnchoredOrbitUser>.maxAnchorDistance(): Float {
    var maxDistance = 0f
    forEachIndexed { index, user ->
        for (otherIndex in index + 1 until size) {
            val distance = user.anchor.distanceTo(this[otherIndex].anchor)
            if (distance > maxDistance) maxDistance = distance
        }
    }
    return maxDistance
}

internal fun bestSeparatedPoint(
    anchor: Offset,
    clusterCenter: Offset,
    placed: List<Offset>,
    seed: Int,
    memberIndex: Int,
    memberCount: Int,
    width: Float,
    height: Float,
    radius: Float
): Offset {
    val isCluster = memberCount > 1
    val origin = if (isCluster) clusterCenter else anchor
    val preferredAngle = if (isCluster) {
        -90.0 + (360.0 / memberCount.coerceAtLeast(1)) * memberIndex + (seed % 17)
    } else {
        null
    }
    val distances = if (isCluster) {
        listOf(96f, 128f, 160f)
    } else {
        listOf(84f, 116f, 148f)
    }
    val angles = preferredAngle?.let { angle ->
        listOf(angle, angle - 34.0, angle + 34.0, angle - 68.0, angle + 68.0, angle + 180.0)
    } ?: listOf(0.0, 60.0, 120.0, 180.0, 240.0, 300.0)
    val candidates = buildList {
        distances.forEach { distance ->
            angles.forEachIndexed { angleIndex, rawAngle ->
                val angle = Math.toRadians(rawAngle + (seed % 3) * 20.0 + angleIndex * 4.0)
                add(
                    Offset(
                        x = origin.x + (cos(angle) * distance).toFloat(),
                        y = origin.y + (sin(angle) * distance).toFloat()
                    ).coerceInside(width, height, radius)
                )
            }
        }
    }
    return candidates.minByOrNull { candidate ->
        val overlapPenalty = placed.sumOf { placedPoint ->
            val distance = candidate.distanceTo(placedPoint)
            if (distance < 86f) ((86f - distance) * 12f).toDouble() else 0.0
        }
        val edgePenalty = when {
            candidate.x <= radius || candidate.x >= width - radius ||
                candidate.y <= radius || candidate.y >= height - radius -> 180.0
            else -> 0.0
        }
        val desiredDistance = if (isCluster) 104f else 0f
        val distancePenalty = kotlin.math.abs(candidate.distanceTo(anchor) - desiredDistance) * 0.25f
        overlapPenalty + edgePenalty + distancePenalty + candidate.distanceTo(anchor) * 0.08f
    } ?: anchor.coerceInside(width, height, radius)
}

internal fun buildOrbitUserClusters(
    users: List<AnchoredOrbitUser>,
    distance: Float
): List<List<AnchoredOrbitUser>> {
    val visited = BooleanArray(users.size)
    val clusters = mutableListOf<List<AnchoredOrbitUser>>()
    users.forEachIndexed { startIndex, _ ->
        if (visited[startIndex]) return@forEachIndexed
        val clusterIndexes = mutableListOf<Int>()
        val pending = ArrayDeque<Int>()
        pending.add(startIndex)
        visited[startIndex] = true
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            clusterIndexes += current
            users.forEachIndexed { candidateIndex, candidate ->
                if (!visited[candidateIndex] && users[current].anchor.distanceTo(candidate.anchor) < distance) {
                    visited[candidateIndex] = true
                    pending.add(candidateIndex)
                }
            }
        }
        clusters += clusterIndexes.map { users[it] }
    }
    return clusters
}

internal fun List<Offset>.averageOffset(): Offset {
    if (isEmpty()) return Offset.Zero
    return Offset(
        x = sumOf { it.x.toDouble() }.toFloat() / size,
        y = sumOf { it.y.toDouble() }.toFloat() / size
    )
}

internal fun Offset.coerceInside(width: Float, height: Float, radius: Float): Offset {
    return Offset(
        x = x.coerceIn(radius, (width - radius).coerceAtLeast(radius)),
        y = y.coerceIn(radius, (height - radius).coerceAtLeast(radius))
    )
}

internal fun Offset.distanceTo(other: Offset): Float {
    return hypot(x - other.x, y - other.y)
}

internal data class AnchoredOrbitUser(
    val user: OrbitUser,
    val anchor: Offset
)

internal data class PositionedOrbitUser(
    val user: OrbitUser,
    val anchor: Offset,
    val display: Offset,
    val needsPointer: Boolean,
    val label: String?
)

internal data class OfflineMapPalette(
    val ocean: Color,
    val land: Color,
    val grid: Color,
    val boundary: Color,
    val stateBoundary: Color,
    val cityLabel: Color,
    val cityDot: Color,
    val pointer: Color
)

internal fun readableOnColor(color: Color): Color {
    return if (color.luminance() > 0.5f) Color.Black else Color.White
}
