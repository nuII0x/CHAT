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

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOfflineMapLabels(
    countryLabels: List<CountryLabel>,
    adminRegions: List<AdminRegion>,
    cityPoints: List<CityPoint>,
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    labelColor: Color,
    dotColor: Color,
    occupiedBoxes: List<LabelBox>,
    showContinentOverview: Boolean
) {
    if (countryLabels.isEmpty() && adminRegions.isEmpty() && cityPoints.isEmpty()) return
    val labelZoom = mapLabelZoom(metersPerPixel)
    val crowdedCountryLimit = ((size.width * size.height) / 32_000f).roundToInt().coerceIn(8, 42)
    val crowdedAdminLimit = ((size.width * size.height) / 34_000f).roundToInt().coerceIn(8, 38)
    val crowdedCityLimit = crowdedCityLimitForViewport(size.width, size.height)
    val continentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor.copy(alpha = 0.72f).toArgb()
        textSize = 15.sp.toPx()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val countryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor.copy(alpha = 0.90f).toArgb()
        textSize = 13.sp.toPx()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val adminPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor.copy(alpha = 0.86f).toArgb()
        textSize = 12.sp.toPx()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val cityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor.toArgb()
        textSize = 11.sp.toPx()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    }
    val latScale = 111_320.0
    val lonScale = (111_320.0 * cos(Math.toRadians(origin.latitude))).coerceAtLeast(1e-6)
    val placedLabels = mutableListOf<LabelBox>()
    val labelRenderPadding = 220f

    if (showContinentOverview || metersPerPixel > continentOnlyMetersPerPixel) {
        drawContinentLabels(
            origin = origin,
            center = center,
            pan = pan,
            metersPerPixel = metersPerPixel,
            lonScale = lonScale,
            latScale = latScale,
            paint = continentPaint,
            renderPadding = labelRenderPadding
        )
        return
    }

    if (metersPerPixel <= adminLabelMetersPerPixel && metersPerPixel > majorCityLabelMetersPerPixel) {
        val placedAdminLabels = drawAdminRegionLabels(
            adminRegions = adminRegions,
            origin = origin,
            center = center,
            pan = pan,
            metersPerPixel = metersPerPixel,
            lonScale = lonScale,
            latScale = latScale,
            paint = adminPaint,
            occupiedBoxes = occupiedBoxes,
            renderPadding = labelRenderPadding,
            labelLimit = crowdedAdminLimit
        )
        drawCountryLabelsWithoutAdminRegions(
            countryLabels = countryLabels,
            administeredCountryKeys = adminRegions.mapNotNull { it.country.mapLabelKey().takeIf { key -> key.isNotBlank() } }.toSet(),
            origin = origin,
            center = center,
            pan = pan,
            metersPerPixel = metersPerPixel,
            lonScale = lonScale,
            latScale = latScale,
            paint = countryPaint,
            occupiedBoxes = occupiedBoxes,
            existingLabels = placedAdminLabels,
            renderPadding = labelRenderPadding,
            labelZoom = labelZoom,
            labelLimit = crowdedCountryLimit
        )
        return
    }

    if (metersPerPixel <= majorCityLabelMetersPerPixel) {
        drawCityLabels(
            cityPoints = cityPoints,
            origin = origin,
            center = center,
            pan = pan,
            metersPerPixel = metersPerPixel,
            lonScale = lonScale,
            latScale = latScale,
            paint = cityPaint,
            dotColor = dotColor,
            occupiedBoxes = occupiedBoxes,
            renderPadding = labelRenderPadding,
            crowdedCityLimit = crowdedCityLimit
        )
        return
    }

    val visibleCountryLabels = countryLabels
        .asSequence()
        .filter { labelZoom >= it.minZoom - 0.35f && labelZoom <= it.maxZoom + 1.25f }
        .mapNotNull { country ->
            val point = mapLabelPoint(
                longitude = country.longitude,
                latitude = country.latitude,
                origin = origin,
                center = center,
                pan = pan,
                metersPerPixel = metersPerPixel,
                lonScale = lonScale,
                latScale = latScale
            )
            if (point.isNearViewport(size.width, size.height, labelRenderPadding)) country to point else null
        }
        .sortedWith(
            compareBy<Pair<CountryLabel, Offset>> { it.first.rank }
                .thenByDescending { it.first.population }
        )
        .toList()
    val countryLimit = if (visibleCountryLabels.size <= crowdedCountryLimit * 2) {
        Int.MAX_VALUE
    } else {
        crowdedCountryLimit
    }
    visibleCountryLabels
        .forEach { (country, point) ->
            if (placedLabels.count { it.kind == LabelKind.Country } >= countryLimit) return@forEach
            val box = labelBox(country.name, point, countryPaint, LabelKind.Country, xOffset = 0f, yOffset = -7.dp.toPx())
            if (
                box.isInsideRenderBand(size.width, size.height, labelRenderPadding) &&
                isLabelPlaceable(box, placedLabels, occupiedBoxes, padding = 8f)
            ) {
                drawContext.canvas.nativeCanvas.drawText(country.name, box.textX, box.textY, countryPaint)
                placedLabels += box
            }
        }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAdminRegionLabels(
    adminRegions: List<AdminRegion>,
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    lonScale: Double,
    latScale: Double,
    paint: Paint,
    occupiedBoxes: List<LabelBox>,
    renderPadding: Float,
    labelLimit: Int
): List<LabelBox> {
    if (adminRegions.isEmpty()) return emptyList()
    val placedLabels = mutableListOf<LabelBox>()
    adminRegions
        .asSequence()
        .mapNotNull { region ->
            val point = mapLabelPoint(
                longitude = region.longitude,
                latitude = region.latitude,
                origin = origin,
                center = center,
                pan = pan,
                metersPerPixel = metersPerPixel,
                lonScale = lonScale,
                latScale = latScale
            )
            if (point.isNearViewport(size.width, size.height, renderPadding)) region to point else null
        }
        .sortedWith(
            compareBy<Pair<AdminRegion, Offset>> { it.first.rank }
                .thenBy { it.first.name }
        )
        .forEach { (region, point) ->
            if (placedLabels.size >= labelLimit) return@forEach
            val box = labelBox(region.name, point, paint, LabelKind.Admin, xOffset = 0f, yOffset = -6.dp.toPx())
            if (
                box.isInsideRenderBand(size.width, size.height, renderPadding) &&
                isLabelPlaceable(box, placedLabels, occupiedBoxes, padding = 8f)
            ) {
                drawContext.canvas.nativeCanvas.drawText(region.name, box.textX, box.textY, paint)
                placedLabels += box
            }
        }
    return placedLabels
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCountryLabelsWithoutAdminRegions(
    countryLabels: List<CountryLabel>,
    administeredCountryKeys: Set<String>,
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    lonScale: Double,
    latScale: Double,
    paint: Paint,
    occupiedBoxes: List<LabelBox>,
    existingLabels: List<LabelBox>,
    renderPadding: Float,
    labelZoom: Float,
    labelLimit: Int
) {
    if (countryLabels.isEmpty()) return
    val placedLabels = existingLabels.toMutableList()
    countryLabels
        .asSequence()
        .filter { country -> country.name.mapLabelKey() !in administeredCountryKeys }
        .filter { country -> labelZoom >= country.minZoom - 0.35f }
        .mapNotNull { country ->
            val point = mapLabelPoint(
                longitude = country.longitude,
                latitude = country.latitude,
                origin = origin,
                center = center,
                pan = pan,
                metersPerPixel = metersPerPixel,
                lonScale = lonScale,
                latScale = latScale
            )
            if (point.isNearViewport(size.width, size.height, renderPadding)) country to point else null
        }
        .sortedWith(
            compareBy<Pair<CountryLabel, Offset>> { it.second.viewportGridIndex(size.width, size.height) }
                .thenBy { it.first.rank }
                .thenByDescending { it.first.population }
        )
        .forEach { (country, point) ->
            if (placedLabels.count { it.kind == LabelKind.Country } >= labelLimit) return@forEach
            val box = labelBox(country.name, point, paint, LabelKind.Country, xOffset = 0f, yOffset = -7.dp.toPx())
            if (
                box.isInsideRenderBand(size.width, size.height, renderPadding) &&
                isLabelPlaceable(box, placedLabels, occupiedBoxes, padding = 8f)
            ) {
                drawContext.canvas.nativeCanvas.drawText(country.name, box.textX, box.textY, paint)
                placedLabels += box
            }
        }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCityLabels(
    cityPoints: List<CityPoint>,
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    lonScale: Double,
    latScale: Double,
    paint: Paint,
    dotColor: Color,
    occupiedBoxes: List<LabelBox>,
    renderPadding: Float,
    crowdedCityLimit: Int
) {
    val placedLabels = mutableListOf<LabelBox>()
    val visibleCityLabels = cityPoints
        .asSequence()
        .filter { city -> city.isVisibleAt(metersPerPixel) }
        .filter { city -> city.preProjectionSampledAt(metersPerPixel) }
        .mapNotNull { city ->
            val point = mapLabelPoint(
                longitude = city.longitude,
                latitude = city.latitude,
                origin = origin,
                center = center,
                pan = pan,
                metersPerPixel = metersPerPixel,
                lonScale = lonScale,
                latScale = latScale
            )
            if (point.isNearViewport(size.width, size.height, renderPadding)) city to point else null
        }
        .toList()
    val cityStep = if (visibleCityLabels.size <= crowdedCityLimit * 2) {
        1
    } else {
        citySampleStep(metersPerPixel)
    }
    val cityLimit = (if (visibleCityLabels.size <= crowdedCityLimit * 2 || metersPerPixel <= 240f) {
        Int.MAX_VALUE
    } else {
        cityLimitForZoom(metersPerPixel, crowdedCityLimit)
    }).coerceAtMost(maxNamedWorldLocalities)
    visibleCityLabels
        .asSequence()
        .filter { (city, _) -> cityStep == 1 || city.importanceRank <= 1 || city.stableSampleIndex() % cityStep == 0 }
        .toList()
        .distributedAcrossViewport(
            width = size.width,
            height = size.height,
            comparator = compareBy<Pair<CityPoint, Offset>> { it.first.importanceRank }
                .thenByDescending { it.first.population }
                .thenBy { it.first.stableSampleIndex() }
        )
        .forEach { (city, point) ->
            if (placedLabels.count { it.kind == LabelKind.City } >= cityLimit) return@forEach
            val placement = cityLabelPlacement(
                text = city.name,
                point = point,
                paint = paint,
                placedLabels = placedLabels,
                occupiedBoxes = occupiedBoxes,
                width = size.width,
                height = size.height,
                renderPadding = renderPadding,
                normalXOffset = 5.dp.toPx(),
                normalYOffset = -5.dp.toPx()
            )
            if (placement != null) {
                if (placement.pinned) {
                    drawLine(
                        color = dotColor.copy(alpha = 0.72f),
                        start = point,
                        end = placement.box.connectorPointToward(point),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawContext.canvas.nativeCanvas.drawText(city.name, placement.box.textX, placement.box.textY, paint)
                placedLabels += placement.box
            }
        }

    val hasLabelInsideViewport = placedLabels.any { label ->
        label.isInsideRenderBand(size.width, size.height, padding = 0f)
    }
    if (!hasLabelInsideViewport) {
        val fallback = cityPoints
            .asSequence()
            .mapNotNull { city ->
                val point = mapLabelPoint(
                    longitude = city.longitude,
                    latitude = city.latitude,
                    origin = origin,
                    center = center,
                    pan = pan,
                    metersPerPixel = metersPerPixel,
                    lonScale = lonScale,
                    latScale = latScale
                )
                if (point.isInside(size.width, size.height, padding = 8f)) city to point else null
            }
            .minByOrNull { (city, point) ->
                val centerDistance = (point - center).getDistanceSquared()
                centerDistance + city.importanceRank * size.width * size.width * 0.04f
            }
        fallback?.let { (city, point) ->
            val placement = cityLabelPlacement(
                text = city.name,
                point = point,
                paint = paint,
                placedLabels = placedLabels,
                occupiedBoxes = occupiedBoxes,
                width = size.width,
                height = size.height,
                renderPadding = 0f,
                normalXOffset = 5.dp.toPx(),
                normalYOffset = -5.dp.toPx()
            ) ?: cityLabelPlacement(
                text = city.name,
                point = point,
                paint = paint,
                placedLabels = emptyList(),
                occupiedBoxes = emptyList(),
                width = size.width,
                height = size.height,
                renderPadding = 0f,
                normalXOffset = 5.dp.toPx(),
                normalYOffset = -5.dp.toPx()
            )
            if (placement != null) {
                drawContext.canvas.nativeCanvas.drawText(city.name, placement.box.textX, placement.box.textY, paint)
            }
        }
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWorldLocalityDots(
    cityPoints: List<CityPoint>,
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    color: Color
) {
    if (cityPoints.isEmpty()) return
    val latScale = 111_320.0
    val lonScale = (111_320.0 * cos(Math.toRadians(origin.latitude))).coerceAtLeast(1e-6)
    val projected = cityPoints.mapNotNull { city ->
        val point = mapLabelPoint(
            longitude = city.longitude,
            latitude = city.latitude,
            origin = origin,
            center = center,
            pan = pan,
            metersPerPixel = metersPerPixel,
            lonScale = lonScale,
            latScale = latScale
        )
        if (point.isInside(size.width, size.height, padding = 0f)) city to point else null
    }
    val step = kotlin.math.ceil(cityPoints.size / maxWorldLocalityDots.toDouble()).toInt().coerceAtLeast(1)
    val radius = when {
        metersPerPixel <= 90f -> 0.95.dp.toPx()
        metersPerPixel <= 320f -> 1.10.dp.toPx()
        else -> 1.25.dp.toPx()
    }
    projected.forEach { (city, point) ->
        if (step == 1 || city.importanceRank <= 1 || city.stableSampleIndex() % step == 0) {
            drawCircle(color = color.copy(alpha = 0.62f), radius = radius, center = point)
        }
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLocalityDensity(
    cells: List<LocalityDensityCell>,
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    color: Color
) {
    if (cells.isEmpty()) return
    val latScale = 111_320.0
    val lonScale = (111_320.0 * cos(Math.toRadians(origin.latitude))).coerceAtLeast(1e-6)
    val maximumCount = cells.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: return
    val maximumLog = kotlin.math.ln(1.0 + maximumCount)
    val baseRadius = (78_000f / metersPerPixel).coerceIn(1.6f, 18f)
    cells.forEach { cell ->
        val point = mapLabelPoint(
            longitude = cell.longitude,
            latitude = cell.latitude,
            origin = origin,
            center = center,
            pan = pan,
            metersPerPixel = metersPerPixel,
            lonScale = lonScale,
            latScale = latScale
        )
        if (!point.isInside(size.width, size.height, padding = 0f)) return@forEach
        val intensity = (kotlin.math.ln(1.0 + cell.count) / maximumLog).toFloat().coerceIn(0f, 1f)
        drawCircle(
            color = color.copy(alpha = 0.08f + intensity * 0.46f),
            radius = baseRadius * (0.72f + intensity * 0.55f),
            center = point
        )
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlobeLocalityDensity(
    cells: List<LocalityDensityCell>,
    centerLongitude: Double,
    centerLatitude: Double,
    radius: Float,
    color: Color
) {
    if (cells.isEmpty() || radius <= 0f) return
    val globeCenter = Offset(size.width / 2f, size.height / 2f)
    val maximumCount = cells.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: return
    val maximumLog = kotlin.math.ln(1.0 + maximumCount)
    cells.forEach { cell ->
        val projected = projectOnGlobe(
            longitude = cell.longitude,
            latitude = cell.latitude,
            centerLongitude = centerLongitude,
            centerLatitude = centerLatitude,
            radius = radius
        )
        if (!projected.visible) return@forEach
        val intensity = (kotlin.math.ln(1.0 + cell.count) / maximumLog).toFloat().coerceIn(0f, 1f)
        val depthFade = projected.depth.toFloat().coerceIn(0f, 1f)
        drawCircle(
            color = color.copy(alpha = (0.10f + intensity * 0.58f) * (0.35f + depthFade * 0.65f)),
            radius = (1.1.dp.toPx() + intensity * 2.8.dp.toPx()) * (0.72f + depthFade * 0.28f),
            center = globeCenter + Offset(projected.x, projected.y)
        )
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOfflineGlobe(
    landRings: List<LandRing>,
    boundaryRings: List<BoundaryRing>,
    centerLongitude: Double,
    centerLatitude: Double,
    metersPerPixel: Float,
    color: Color,
    oceanColor: Color,
    outlineColor: Color,
    boundaryColor: Color,
    useDarkColors: Boolean
) {
    val globeRadius = earthRadiusMeters / metersPerPixel
    if (globeRadius <= 0f) return
    val globeCenter = Offset(size.width / 2f, size.height / 2f)
    val globeClip = Path().apply {
        addOval(Rect(globeCenter, globeRadius))
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = if (useDarkColors) {
                listOf(
                    oceanColor.copy(alpha = 0.96f),
                    oceanColor,
                    Color(0xFF06111B)
                )
            } else {
                listOf(
                    oceanColor.copy(alpha = 0.90f),
                    oceanColor,
                    Color(0xFF8BB9D1)
                )
            },
            center = globeCenter - Offset(globeRadius * 0.28f, globeRadius * 0.32f),
            radius = globeRadius * 1.45f
        ),
        radius = globeRadius,
        center = globeCenter
    )

    clipPath(globeClip) {
        landRings.forEach { ring ->
            val points = ring.pointsFor(metersPerPixel)
            val projected = points.map { point ->
                projectOnGlobe(
                    longitude = point.longitude,
                    latitude = point.latitude,
                    centerLongitude = centerLongitude,
                    centerLatitude = centerLatitude,
                    radius = globeRadius
                )
            }
            clipGlobeRingToHorizon(projected, globeRadius).forEach { polygon ->
                val path = Path()
                polygon.forEachIndexed { index, point ->
                    val screenPoint = globeCenter + Offset(point.x, point.y)
                    if (index == 0) path.moveTo(screenPoint.x, screenPoint.y)
                    else path.lineTo(screenPoint.x, screenPoint.y)
                }
                path.close()
                drawPath(path, color)
                drawPath(
                    path,
                    outlineColor.copy(alpha = 0.32f),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }
        }
        boundaryRings.forEach { ring ->
            drawVisibleGlobeSegments(
                points = ring.points.map { point ->
                    projectOnGlobe(
                        longitude = point.longitude,
                        latitude = point.latitude,
                        centerLongitude = centerLongitude,
                        centerLatitude = centerLatitude,
                        radius = globeRadius
                    )
                },
                center = globeCenter,
                color = Color.Transparent,
                strokeColor = boundaryColor,
                fillClosedSegments = false
            )
        }
    }
    drawCircle(
        color = outlineColor.copy(alpha = if (useDarkColors) 0.40f else 0.52f),
        radius = globeRadius,
        center = globeCenter,
        style = Stroke(width = 1.2.dp.toPx())
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.White.copy(alpha = 0.12f)
            ),
            center = globeCenter,
            radius = globeRadius * 1.04f
        ),
        radius = globeRadius,
        center = globeCenter,
        style = Stroke(width = 2.5.dp.toPx())
    )
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpaceBackground() {
    drawRect(Color(0xFF020307))
    repeat(spaceStarCount) { index ->
        val x = stableStarUnit(index * 2 + 17)
        val y = stableStarUnit(index * 2 + 91)
        val brightness = stableStarUnit(index * 3 + 211)
        drawCircle(
            color = Color.White.copy(alpha = 0.22f + brightness * 0.68f),
            radius = if (index % 13 == 0) 1.35.dp.toPx() else 0.65.dp.toPx(),
            center = Offset(x * size.width, y * size.height)
        )
    }
}

internal fun stableStarUnit(seed: Int): Float {
    var value = seed * 0x45d9f3b
    value = value xor (value ushr 16)
    value *= 0x45d9f3b
    value = value xor (value ushr 16)
    return (value and 0x00FFFFFF) / 16_777_215f
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVisibleGlobeSegments(
    points: List<GlobePoint>,
    center: Offset,
    color: Color,
    strokeColor: Color,
    fillClosedSegments: Boolean
) {
    if (points.size < 2) return
    var path: Path? = null
    var pointCount = 0
    fun flush() {
        val current = path ?: return
        if (pointCount >= if (fillClosedSegments) 3 else 2) {
            if (fillClosedSegments) {
                current.close()
                drawPath(current, color)
            }
            drawPath(current, strokeColor, style = Stroke(width = 0.8.dp.toPx()))
        }
        path = null
        pointCount = 0
    }
    points.forEach { point ->
        if (!point.visible) {
            flush()
        } else {
            val screenPoint = center + Offset(point.x, point.y)
            val current = path ?: Path().also {
                path = it
                it.moveTo(screenPoint.x, screenPoint.y)
            }
            if (pointCount > 0) current.lineTo(screenPoint.x, screenPoint.y)
            pointCount++
        }
    }
    flush()
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMapBoundaries(
    boundaryRings: List<BoundaryRing>,
    viewport: GeoBounds,
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    color: Color,
    strokeWidth: Float,
    visibleWhen: Boolean
) {
    if (!visibleWhen || boundaryRings.isEmpty()) return
    val latScale = 111_320.0
    val lonScale = (111_320.0 * cos(Math.toRadians(origin.latitude))).coerceAtLeast(1e-6)
    val renderPadding = 260.dp.toPx()
    boundaryRings
        .asSequence()
        .filter { it.intersects(viewport, paddingDegrees = 2.5) }
        .forEach { ring ->
            val path = Path()
            var hasPoint = false
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            ring.points.forEachIndexed { index, geoPoint ->
                val point = mapLabelPoint(
                    longitude = geoPoint.longitude,
                    latitude = geoPoint.latitude,
                    origin = origin,
                    center = center,
                    pan = pan,
                    metersPerPixel = metersPerPixel,
                    lonScale = lonScale,
                    latScale = latScale
                )
                if (!point.x.isFinite() || !point.y.isFinite()) return@forEachIndexed
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                hasPoint = true
                minX = minOf(minX, point.x)
                maxX = maxOf(maxX, point.x)
                minY = minOf(minY, point.y)
                maxY = maxOf(maxY, point.y)
            }
            if (!hasPoint) return@forEach
            val intersectsViewport = maxX >= -renderPadding &&
                minX <= size.width + renderPadding &&
                maxY >= -renderPadding &&
                minY <= size.height + renderPadding
            if (intersectsViewport) {
                drawPath(path = path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }
        }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHighDetailPlaceLabels(
    places: HighDetailPlacesData,
    viewport: GeoBounds,
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    labelColor: Color,
    dotColor: Color,
    occupiedBoxes: List<LabelBox>
) {
    if (places.all.isEmpty() || metersPerPixel > highDetailMunicipalityMetersPerPixel) return
    val latScale = 111_320.0
    val lonScale = (111_320.0 * cos(Math.toRadians(origin.latitude))).coerceAtLeast(1e-6)
    val renderPadding = 140f
    val placePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor.toArgb()
        textSize = when {
            metersPerPixel <= highDetailSubdistrictMetersPerPixel -> 10.sp.toPx()
            metersPerPixel <= highDetailDistrictMetersPerPixel -> 10.5.sp.toPx()
            else -> 11.sp.toPx()
        }
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    }
    val visible = places.near(viewport, paddingDegrees = 0.9)
        .asSequence()
        .filter { place -> place.isVisibleAtHighDetailZoom(metersPerPixel) }
        .filter { place -> viewport.contains(place.longitude, place.latitude, paddingDegrees = 0.8) }
        .mapNotNull { place ->
            val point = mapLabelPoint(
                longitude = place.longitude,
                latitude = place.latitude,
                origin = origin,
                center = center,
                pan = pan,
                metersPerPixel = metersPerPixel,
                lonScale = lonScale,
                latScale = latScale
            )
            if (point.isNearViewport(size.width, size.height, renderPadding)) place to point else null
        }
        .toList()
    if (visible.isEmpty()) return

    visible.forEach { (place, point) ->
        if (place.kind == HighDetailPlaceKind.Municipality || metersPerPixel <= highDetailDistrictMetersPerPixel) {
            val radius = if (place.kind == HighDetailPlaceKind.Municipality) 2.25.dp.toPx() else 1.45.dp.toPx()
            drawCircle(color = dotColor.copy(alpha = place.dotAlpha()), radius = radius, center = point)
        }
    }

    val labelLimit = highDetailLabelLimit(metersPerPixel, size.width, size.height)
    val placedLabels = mutableListOf<LabelBox>()
    visible
        .distributedAcrossViewport(
            width = size.width,
            height = size.height,
            comparator = compareBy<Pair<HighDetailPlacePoint, Offset>> { it.first.rank }
                .thenBy { it.first.kind.ordinal }
                .thenBy { it.first.name }
        )
        .forEach { (place, point) ->
            if (placedLabels.size >= labelLimit) return@forEach
            val placement = cityLabelPlacement(
                text = place.name,
                point = point,
                paint = placePaint,
                placedLabels = placedLabels,
                occupiedBoxes = occupiedBoxes,
                width = size.width,
                height = size.height,
                renderPadding = renderPadding,
                normalXOffset = 5.dp.toPx(),
                normalYOffset = -5.dp.toPx()
            ) ?: return@forEach
            if (placement.pinned) {
                drawLine(
                    color = dotColor.copy(alpha = 0.50f),
                    start = point,
                    end = placement.box.connectorPointToward(point),
                    strokeWidth = 0.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawContext.canvas.nativeCanvas.drawText(place.name, placement.box.textX, placement.box.textY, placePaint)
            placedLabels += placement.box
        }
    if (placedLabels.none { it.isInsideRenderBand(size.width, size.height, padding = 0f) }) {
        visible
            .filter { (_, point) -> point.isInside(size.width, size.height, padding = 8f) }
            .minByOrNull { (_, point) -> (point - center).getDistanceSquared() }
            ?.let { (place, point) ->
                cityLabelPlacement(
                    text = place.name,
                    point = point,
                    paint = placePaint,
                    placedLabels = emptyList(),
                    occupiedBoxes = emptyList(),
                    width = size.width,
                    height = size.height,
                    renderPadding = 0f,
                    normalXOffset = 5.dp.toPx(),
                    normalYOffset = -5.dp.toPx()
                )?.let { placement ->
                    drawContext.canvas.nativeCanvas.drawText(
                        place.name,
                        placement.box.textX,
                        placement.box.textY,
                        placePaint
                    )
                }
            }
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawContinentLabels(
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    lonScale: Double,
    latScale: Double,
    paint: Paint,
    renderPadding: Float
) {
    continentLabels.forEach { continent ->
        val point = mapLabelPoint(
            longitude = continent.longitude,
            latitude = continent.latitude,
            origin = origin,
            center = center,
            pan = pan,
            metersPerPixel = metersPerPixel,
            lonScale = lonScale,
            latScale = latScale
        )
        if (!point.isNearViewport(size.width, size.height, renderPadding)) return@forEach
        val box = labelBox(continent.name, point, paint, LabelKind.Continent, xOffset = 0f, yOffset = 0f)
        if (box.isInsideRenderBand(size.width, size.height, renderPadding)) {
            drawContext.canvas.nativeCanvas.drawText(continent.name, box.textX, box.textY, paint)
        }
    }
}
