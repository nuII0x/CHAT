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

internal fun LandRing.pointsFor(metersPerPixel: Float): List<GeoPoint> {
    return when {
        metersPerPixel > 110f -> overviewPoints
        metersPerPixel > 50f -> balancedPoints
        else -> detailedPoints
    }
}

internal fun List<GeoPoint>.simplifiedByStride(stride: Int): List<GeoPoint> {
    if (stride <= 1 || size <= 10) return this
    return buildList {
        forEachIndexed { index, point ->
            if (index == 0 || index == lastIndex || index % stride == 0) {
                add(point)
            }
        }
        if (size < 3) {
            clear()
            addAll(this@simplifiedByStride)
        }
    }
}

internal fun List<GeoPoint>.withContinuousLongitudes(): List<GeoPoint> {
    val source = this
    if (source.isEmpty()) return emptyList()
    return buildList(source.size) {
        val firstPoint = source.first()
        var previousLongitude = firstPoint.longitude
        add(firstPoint)
        source.drop(1).forEach { point ->
            var longitude = point.longitude
            while (longitude - previousLongitude > 180.0) longitude -= 360.0
            while (longitude - previousLongitude < -180.0) longitude += 360.0
            add(point.copy(longitude = longitude))
            previousLongitude = longitude
        }
    }
}

internal fun CityPoint.isVisibleAt(metersPerPixel: Float): Boolean {
    val currentZoom = mapLabelZoom(metersPerPixel)
    if (currentZoom + 0.45f < minZoom) return false
    return when (importanceRank) {
        0 -> metersPerPixel <= majorCityLabelMetersPerPixel
        1 -> metersPerPixel <= cityLabelMetersPerPixel
        else -> metersPerPixel <= cityTitleMetersPerPixel * 1.5f
    }
}

internal fun CityPoint.preProjectionSampledAt(metersPerPixel: Float): Boolean {
    val step = preProjectionCitySampleStep(metersPerPixel)
    return step == 1 || importanceRank <= 1 || stableSampleIndex() % step == 0
}

internal fun HighDetailPlacePoint.isVisibleAtHighDetailZoom(metersPerPixel: Float): Boolean {
    return when (kind) {
        HighDetailPlaceKind.Municipality -> metersPerPixel <= highDetailMunicipalityMetersPerPixel
        HighDetailPlaceKind.District -> metersPerPixel <= highDetailDistrictMetersPerPixel
        HighDetailPlaceKind.Subdistrict -> metersPerPixel <= highDetailSubdistrictMetersPerPixel
    }
}

internal fun HighDetailPlacePoint.dotAlpha(): Float {
    return when (kind) {
        HighDetailPlaceKind.Municipality -> 0.90f
        HighDetailPlaceKind.District -> 0.68f
        HighDetailPlaceKind.Subdistrict -> 0.48f
    }
}

internal fun highDetailLabelLimit(metersPerPixel: Float, width: Float, height: Float): Int {
    val base = ((width * height) / 14_000f).roundToInt().coerceIn(16, 126)
    return when {
        metersPerPixel > 460f -> base.coerceAtMost(34)
        metersPerPixel > 260f -> base.coerceAtMost(52)
        metersPerPixel > 120f -> base.coerceAtMost(82)
        else -> base
    }.coerceAtMost(maxNamedHighDetailLocalities)
}

internal fun cityLimitForZoom(metersPerPixel: Float, crowdedCityLimit: Int): Int {
    return when {
        metersPerPixel > 600f -> 18
        metersPerPixel > 420f -> 28
        metersPerPixel > 260f -> 44
        metersPerPixel > 160f -> crowdedCityLimit.coerceAtMost(64)
        else -> crowdedCityLimit
    }
}

internal fun crowdedCityLimitForViewport(width: Float, height: Float): Int {
    return ((width * height) / 14_000f).roundToInt().coerceIn(18, 130)
}

internal fun GeoBounds.offsetFor(longitude: Double, latitude: Double, width: Float, height: Float): Offset {
    val safeLongitudeSpan = longitudeSpan().coerceAtLeast(1e-6)
    val safeLatitudeSpan = latitudeSpan().coerceAtLeast(1e-6)
    return Offset(
        x = (((longitude - minLongitude) / safeLongitudeSpan) * width).toFloat(),
        y = (((maxLatitude - latitude) / safeLatitudeSpan) * height).toFloat()
    )
}

internal fun Offset.viewportGridIndex(width: Float, height: Float, columns: Int = 4, rows: Int = 4): Int {
    val safeWidth = width.coerceAtLeast(1f)
    val safeHeight = height.coerceAtLeast(1f)
    val column = ((x / safeWidth) * columns).toInt().coerceIn(0, columns - 1)
    val row = ((y / safeHeight) * rows).toInt().coerceIn(0, rows - 1)
    return row * columns + column
}

internal fun <T> List<Pair<T, Offset>>.distributedAcrossViewport(
    width: Float,
    height: Float,
    comparator: Comparator<Pair<T, Offset>>,
    columns: Int = 4,
    rows: Int = 4
): List<Pair<T, Offset>> {
    if (isEmpty()) return emptyList()
    val cellCount = (columns * rows).coerceAtLeast(1)
    val cells = List(cellCount) { mutableListOf<Pair<T, Offset>>() }
    forEach { item ->
        cells[item.second.viewportGridIndex(width, height, columns, rows)] += item
    }
    cells.forEach { it.sortWith(comparator) }
    return buildList(size) {
        var depth = 0
        while (size < this@distributedAcrossViewport.size) {
            var addedAtThisDepth = false
            cells.forEach { cell ->
                if (depth < cell.size) {
                    add(cell[depth])
                    addedAtThisDepth = true
                }
            }
            if (!addedAtThisDepth) break
            depth++
        }
    }
}

internal fun String.mapLabelKey(): String {
    return trim()
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }
}

internal fun mapLabelZoom(metersPerPixel: Float): Float {
    return log2((60_000f / metersPerPixel).coerceAtLeast(1f)) + 1f
}

internal fun citySampleStep(metersPerPixel: Float): Int {
    return when {
        metersPerPixel > 420f -> 6
        metersPerPixel > 260f -> 5
        metersPerPixel > 160f -> 3
        else -> 1
    }
}

internal fun preProjectionCitySampleStep(metersPerPixel: Float): Int {
    return when {
        metersPerPixel > 420f -> 4
        metersPerPixel > 260f -> 3
        metersPerPixel > 160f -> 2
        else -> 1
    }
}

internal fun cityImportanceRank(scaleRank: Int, population: Long, isCountryCapital: Boolean): Int {
    return when {
        isCountryCapital || scaleRank <= 2 || population >= 5_000_000L -> 0
        scaleRank <= 5 || population >= 500_000L -> 1
        else -> 2
    }
}

internal fun CityPoint.stableSampleIndex(): Int {
    val coordinateHash = ((latitude * 10_000).roundToInt() * 31) xor (longitude * 10_000).roundToInt()
    return (name.hashCode() xor coordinateHash).absoluteValue
}

internal fun mapLabelPoint(
    longitude: Double,
    latitude: Double,
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    lonScale: Double,
    latScale: Double
): Offset {
    val deltaLongitude = wrappedLongitudeDelta(origin.longitude, longitude)
    val xMeters = deltaLongitude * lonScale
    val yMeters = (latitude - origin.latitude) * latScale
    return Offset(
        x = center.x + pan.x + (xMeters / metersPerPixel).toFloat(),
        y = center.y + pan.y - (yMeters / metersPerPixel).toFloat()
    )
}

internal fun mapGeoPointAtScreen(
    screenPoint: Offset,
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float
): GeoPoint {
    val latScale = 111_320.0
    val lonScale = (111_320.0 * cos(Math.toRadians(origin.latitude))).coerceAtLeast(1e-6)
    val xMeters = (screenPoint.x - center.x - pan.x) * metersPerPixel
    val yMeters = (center.y + pan.y - screenPoint.y) * metersPerPixel
    return GeoPoint(
        longitude = normalizeLongitude(origin.longitude + xMeters / lonScale),
        latitude = (origin.latitude + yMeters / latScale).coerceIn(-90.0, 90.0)
    )
}

internal fun mapGeoBoundsForViewport(
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    width: Float,
    height: Float
): GeoBounds {
    val topLeft = mapGeoPointAtScreen(
        screenPoint = Offset(0f, 0f),
        origin = origin,
        center = center,
        pan = pan,
        metersPerPixel = metersPerPixel
    )
    val bottomRight = mapGeoPointAtScreen(
        screenPoint = Offset(width, height),
        origin = origin,
        center = center,
        pan = pan,
        metersPerPixel = metersPerPixel
    )
    return GeoBounds(
        minLongitude = minOf(topLeft.longitude, bottomRight.longitude),
        minLatitude = minOf(topLeft.latitude, bottomRight.latitude),
        maxLongitude = maxOf(topLeft.longitude, bottomRight.longitude),
        maxLatitude = maxOf(topLeft.latitude, bottomRight.latitude)
    )
}

internal fun titleForMapCenter(
    center: GeoPoint,
    viewport: GeoBounds,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    metersPerPixel: Float,
    countryLabels: List<CountryLabel>,
    cityPoints: List<CityPoint>,
    adminRegions: List<AdminRegion>,
    highDetailPlaces: List<HighDetailPlacePoint>
): String {
    if (isWholeEarthViewport(viewport, metersPerPixel)) {
        return "Terra"
    }
    val dominantRegion = dominantAdminRegionName(
        center = center,
        viewport = viewport,
        regions = adminRegions
    )
    if (metersPerPixel > continentOnlyMetersPerPixel) {
        return dominantContinentName(viewport, center) ?: nearestContinentName(center) ?: "Terra"
    }
    if (metersPerPixel <= highDetailSubdistrictTitleMetersPerPixel) {
        nearestHighDetailPlaceTitleName(
            center = center,
            viewport = viewport,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            metersPerPixel = metersPerPixel,
            places = highDetailPlaces,
            maxDegrees = 0.08,
            includeDistricts = true
        )?.let { return it }
    }
    if (metersPerPixel <= cityTitleMetersPerPixel) {
        nearestHighDetailPlaceTitleName(
            center = center,
            viewport = viewport,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            metersPerPixel = metersPerPixel,
            places = highDetailPlaces,
            maxDegrees = 0.32,
            includeDistricts = true
        )?.let { return it }
    }
    if (metersPerPixel <= cityTitleMetersPerPixel) {
        nearestCityTitleName(
            center = center,
            viewport = viewport,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            metersPerPixel = metersPerPixel,
            cityPoints = cityPoints,
            maxDegrees = 2.8
        )?.let { return it }
    }
    if (metersPerPixel <= cityLabelMetersPerPixel) {
        dominantRegion?.let { return it }
        nearestAdminRegionName(center, adminRegions)?.let { return it }
    }
    if (metersPerPixel <= adminLabelMetersPerPixel) {
        dominantAdminCountryName(
            center = center,
            viewport = viewport,
            regions = adminRegions
        )?.let { return it }
        return nearestAdminCountryName(center, adminRegions)
            ?: dominantCountryName(center, viewport, countryLabels)
            ?: nearestCountryName(center, countryLabels)
            ?: nearestContinentName(center)
            ?: "Terra"
    }
    return dominantContinentName(viewport, center)
        ?: nearestContinentName(center)
        ?: "Terra"
}

internal fun nearestHighDetailPlaceTitleName(
    center: GeoPoint,
    viewport: GeoBounds,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    metersPerPixel: Float,
    places: List<HighDetailPlacePoint>,
    maxDegrees: Double,
    includeDistricts: Boolean
): String? {
    if (places.isEmpty() || viewportWidthPx <= 0f || viewportHeightPx <= 0f) return null
    val allowedKinds = if (includeDistricts) {
        setOf(HighDetailPlaceKind.Municipality, HighDetailPlaceKind.District, HighDetailPlaceKind.Subdistrict)
    } else {
        setOf(HighDetailPlaceKind.Municipality)
    }
    val nearestPlace = places
        .asSequence()
        .filter { it.kind in allowedKinds }
        .filter { viewport.contains(it.longitude, it.latitude) }
        .map { place ->
            place to approximateGeoDistanceScore(center.longitude, center.latitude, place.longitude, place.latitude)
        }
        .filter { (_, score) -> score <= maxDegrees * maxDegrees }
        .minWithOrNull(
            compareBy<Pair<HighDetailPlacePoint, Double>> { it.second }
                .thenBy { it.first.rank }
                .thenBy { it.first.kind.ordinal }
        )
        ?.first
        ?: return null
    return if (
        isHighDetailPlaceNameVisibleOnMap(
            place = nearestPlace,
            places = places,
            viewport = viewport,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            metersPerPixel = metersPerPixel
        )
    ) {
        null
    } else {
        nearestPlace.name
    }
}

internal fun isHighDetailPlaceNameVisibleOnMap(
    place: HighDetailPlacePoint,
    places: List<HighDetailPlacePoint>,
    viewport: GeoBounds,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    metersPerPixel: Float
): Boolean {
    if (!place.isVisibleAtHighDetailZoom(metersPerPixel)) return false
    val labelLimit = highDetailLabelLimit(metersPerPixel, viewportWidthPx, viewportHeightPx)
    return places
        .asSequence()
        .filter { it.isVisibleAtHighDetailZoom(metersPerPixel) }
        .filter { viewport.contains(it.longitude, it.latitude, paddingDegrees = 0.8) }
        .map { it to viewport.offsetFor(it.longitude, it.latitude, viewportWidthPx, viewportHeightPx) }
        .toList()
        .distributedAcrossViewport(
            width = viewportWidthPx,
            height = viewportHeightPx,
            comparator = compareBy<Pair<HighDetailPlacePoint, Offset>> { it.first.rank }
                .thenBy { it.first.kind.ordinal }
                .thenBy { it.first.name }
        )
        .take(labelLimit)
        .any { it.first == place }
}

internal fun dominantAdminRegionName(
    center: GeoPoint,
    viewport: GeoBounds,
    regions: List<AdminRegion>
): String? {
    val viewportArea = viewport.area()
    if (viewportArea <= 0.0) return null
    var bestRegion: AdminRegion? = null
    var bestCoverage = 0.0
    for (region in regions) {
        val coverage = region.intersectionArea(viewport) / viewportArea
        val currentBestRank = bestRegion?.rank ?: Int.MAX_VALUE
        val containsCenterBonus = if (region.contains(center)) 0.06 else 0.0
        val adjustedCoverage = coverage + containsCenterBonus
        if (adjustedCoverage >= 0.70 && (adjustedCoverage > bestCoverage || region.rank < currentBestRank)) {
            bestRegion = region
            bestCoverage = adjustedCoverage
        }
    }
    return bestRegion?.name
}

internal fun dominantAdminCountryName(
    center: GeoPoint,
    viewport: GeoBounds,
    regions: List<AdminRegion>
): String? {
    val viewportArea = viewport.area()
    if (viewportArea <= 0.0) return null
    val coverageByCountry = linkedMapOf<String, Double>()
    regions.forEach { region ->
        val country = region.country
        if (country.isBlank()) return@forEach
        val coverage = region.intersectionArea(viewport) / viewportArea
        if (coverage > 0.0) {
            val centerBonus = if (region.contains(center)) 0.18 else 0.0
            coverageByCountry[country] = (coverageByCountry[country] ?: 0.0) + coverage + centerBonus
        }
    }
    val ranked = coverageByCountry.entries.sortedByDescending { it.value }
    val best = ranked.firstOrNull() ?: return null
    val second = ranked.getOrNull(1)?.value ?: 0.0
    return if (best.value >= 0.24 || best.value >= second * 1.35) best.key else null
}

internal fun nearestAdminCountryName(center: GeoPoint, regions: List<AdminRegion>): String? {
    return regions
        .filter { it.contains(center) && it.country.isNotBlank() }
        .minWithOrNull(compareBy<AdminRegion> { it.rank }.thenBy {
            approximateGeoDistanceScore(center.longitude, center.latitude, it.longitude, it.latitude)
        })
        ?.country
}

internal fun nearestAdminRegionName(center: GeoPoint, regions: List<AdminRegion>): String? {
    return regions
        .filter { it.contains(center) }
        .minWithOrNull(compareBy<AdminRegion> { it.rank }.thenBy {
            approximateGeoDistanceScore(center.longitude, center.latitude, it.longitude, it.latitude)
        })
        ?.name
}

internal fun dominantContinentName(viewport: GeoBounds, center: GeoPoint): String? {
    val visibleContinents = continentLabels
        .filter { viewport.contains(it.longitude, it.latitude, paddingDegrees = 12.0) }
    if (visibleContinents.isEmpty()) return null
    return visibleContinents.minByOrNull { continent ->
        approximateGeoDistanceScore(center.longitude, center.latitude, continent.longitude, continent.latitude)
    }?.name
}

internal fun nearestContinentName(center: GeoPoint): String? {
    return continentLabels.minByOrNull { label ->
        approximateGeoDistanceScore(
            center.longitude,
            center.latitude,
            label.longitude,
            label.latitude
        )
    }?.name
}

internal fun dominantCountryName(
    center: GeoPoint,
    viewport: GeoBounds,
    countryLabels: List<CountryLabel>
): String? {
    val viewportPadding = (maxOf(viewport.longitudeSpan(), viewport.latitudeSpan()) * 0.28)
        .coerceIn(0.8, 8.0)
    return countryLabels
        .asSequence()
        .mapNotNull { country ->
            val isInsideViewport = viewport.contains(country.longitude, country.latitude)
            val isNearViewport = viewport.contains(
                longitude = country.longitude,
                latitude = country.latitude,
                paddingDegrees = viewportPadding
            )
            if (!isInsideViewport && !isNearViewport) return@mapNotNull null
            country to if (isInsideViewport) 0 else 1
        }
        .minWithOrNull(
            compareBy<Pair<CountryLabel, Int>> { it.second }
                .thenBy {
                    approximateGeoDistanceScore(
                        center.longitude,
                        center.latitude,
                        it.first.longitude,
                        it.first.latitude
                    )
                }
                .thenBy { it.first.rank }
                .thenByDescending { it.first.population }
        )
        ?.first
        ?.name
}

internal fun nearestCountryName(center: GeoPoint, countryLabels: List<CountryLabel>): String? {
    return countryLabels.minByOrNull { country ->
        approximateGeoDistanceScore(
            center.longitude,
            center.latitude,
            country.longitude,
            country.latitude
        ) * country.rank.coerceAtLeast(1)
    }?.name
}

internal fun nearestCityTitleName(
    center: GeoPoint,
    viewport: GeoBounds,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    metersPerPixel: Float,
    cityPoints: List<CityPoint>,
    maxDegrees: Double
): String? {
    if (cityPoints.isEmpty() || viewportWidthPx <= 0f || viewportHeightPx <= 0f) return null
    val nearestCity = cityPoints
        .asSequence()
        .filter { city -> viewport.contains(city.longitude, city.latitude) }
        .map { city ->
            city to approximateGeoDistanceScore(center.longitude, center.latitude, city.longitude, city.latitude)
        }
        .filter { (_, score) -> score <= maxDegrees * maxDegrees }
        .minWithOrNull(
            compareBy<Pair<CityPoint, Double>> { it.second }
                .thenBy { it.first.importanceRank }
        )
        ?.first
        ?: return null
    return if (
        isCityNameVisibleOnMap(
            city = nearestCity,
            cityPoints = cityPoints,
            viewport = viewport,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            metersPerPixel = metersPerPixel
        )
    ) {
        null
    } else {
        nearestCity.name
    }
}

internal fun isCityNameVisibleOnMap(
    city: CityPoint,
    cityPoints: List<CityPoint>,
    viewport: GeoBounds,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    metersPerPixel: Float
): Boolean {
    if (!city.isVisibleAt(metersPerPixel) || !city.preProjectionSampledAt(metersPerPixel)) return false
    val crowdedCityLimit = crowdedCityLimitForViewport(viewportWidthPx, viewportHeightPx)
    val visibleCityLabels = cityPoints
        .asSequence()
        .filter { it.isVisibleAt(metersPerPixel) }
        .filter { it.preProjectionSampledAt(metersPerPixel) }
        .filter { viewport.contains(it.longitude, it.latitude, paddingDegrees = 0.8) }
        .map { it to viewport.offsetFor(it.longitude, it.latitude, viewportWidthPx, viewportHeightPx) }
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
    return visibleCityLabels
        .asSequence()
        .filter { (visibleCity, _) ->
            cityStep == 1 || visibleCity.importanceRank <= 1 || visibleCity.stableSampleIndex() % cityStep == 0
        }
        .toList()
        .distributedAcrossViewport(
            width = viewportWidthPx,
            height = viewportHeightPx,
            comparator = compareBy<Pair<CityPoint, Offset>> { it.first.importanceRank }
                .thenBy { it.first.stableSampleIndex() }
        )
        .take(cityLimit)
        .any { it.first == city }
}

internal fun approximateGeoDistanceScore(
    longitudeA: Double,
    latitudeA: Double,
    longitudeB: Double,
    latitudeB: Double
): Double {
    val latitudeFactor = cos(Math.toRadians((latitudeA + latitudeB) / 2.0)).coerceAtLeast(0.2)
    val deltaLongitude = wrappedLongitudeDelta(longitudeA, longitudeB) * latitudeFactor
    val deltaLatitude = latitudeB - latitudeA
    return deltaLongitude * deltaLongitude + deltaLatitude * deltaLatitude
}

internal fun isWholeEarthViewport(viewport: GeoBounds, metersPerPixel: Float): Boolean {
    return metersPerPixel >= 22_000f ||
        viewport.latitudeSpan() >= 168.0 ||
        (viewport.longitudeSpan() >= 260.0 && viewport.latitudeSpan() >= 120.0)
}

internal fun normalizeLongitude(longitude: Double): Double {
    var normalized = longitude
    while (normalized > 180.0) normalized -= 360.0
    while (normalized < -180.0) normalized += 360.0
    return normalized
}

internal fun labelBox(
    text: String,
    point: Offset,
    paint: Paint,
    kind: LabelKind,
    xOffset: Float,
    yOffset: Float
): LabelBox {
    val width = paint.measureText(text)
    val height = paint.textSize
    val textX = point.x + xOffset - if (
        kind == LabelKind.Country ||
        kind == LabelKind.Continent ||
        kind == LabelKind.Admin
    ) {
        width / 2f
    } else {
        0f
    }
    val textY = point.y + yOffset
    return LabelBox(
        left = textX,
        top = textY - height,
        right = textX + width,
        bottom = textY + height * 0.25f,
        textX = textX,
        textY = textY,
        kind = kind
    )
}

internal fun cityLabelPlacement(
    text: String,
    point: Offset,
    paint: Paint,
    placedLabels: List<LabelBox>,
    occupiedBoxes: List<LabelBox>,
    width: Float,
    height: Float,
    renderPadding: Float,
    normalXOffset: Float,
    normalYOffset: Float
): CityLabelPlacement? {
    val normalBox = labelBox(text, point, paint, LabelKind.City, normalXOffset, normalYOffset)
    if (
        normalBox.isInsideRenderBand(width, height, renderPadding) &&
        isLabelPlaceable(normalBox, placedLabels, occupiedBoxes, padding = 5f)
    ) {
        return CityLabelPlacement(box = normalBox, pinned = false)
    }

    val candidates = cityPinCandidateOffsets(paint.measureText(text), paint.textSize)
    candidates.forEach { offset ->
        val box = centeredCityLabelBox(text, point + offset, paint)
        if (
            box.isInsideRenderBand(width, height, renderPadding) &&
            isLabelPlaceable(box, placedLabels, occupiedBoxes, padding = 7f)
        ) {
            return CityLabelPlacement(box = box, pinned = true)
        }
    }
    return null
}

internal fun cityPinCandidateOffsets(textWidth: Float, textHeight: Float): List<Offset> {
    val horizontal = textWidth / 2f + 36f
    val nearVertical = textHeight + 24f
    val farVertical = textHeight + 48f
    return listOf(
        Offset(0f, -nearVertical),
        Offset(0f, farVertical),
        Offset(horizontal, -textHeight * 0.45f),
        Offset(-horizontal, -textHeight * 0.45f),
        Offset(horizontal, textHeight * 1.2f),
        Offset(-horizontal, textHeight * 1.2f),
        Offset(0f, -farVertical),
        Offset(0f, farVertical + 24f),
        Offset(horizontal + 36f, 0f),
        Offset(-horizontal - 36f, 0f)
    )
}

internal fun centeredCityLabelBox(
    text: String,
    baselineCenter: Offset,
    paint: Paint
): LabelBox {
    val width = paint.measureText(text)
    val height = paint.textSize
    val textX = baselineCenter.x - width / 2f
    val textY = baselineCenter.y
    return LabelBox(
        left = textX,
        top = textY - height,
        right = textX + width,
        bottom = textY + height * 0.25f,
        textX = textX,
        textY = textY,
        kind = LabelKind.City
    )
}

internal fun LabelBox.connectorPointToward(point: Offset): Offset {
    return Offset(
        x = point.x.coerceIn(left, right),
        y = point.y.coerceIn(top, bottom)
    )
}

internal fun Offset.isNearViewport(width: Float, height: Float, padding: Float): Boolean {
    return x >= -padding && x <= width + padding && y >= -padding && y <= height + padding
}

internal fun LabelBox.isInsideRenderBand(width: Float, height: Float, padding: Float): Boolean {
    return right >= -padding && left <= width + padding && bottom >= -padding && top <= height + padding
}

internal fun isLabelPlaceable(
    candidate: LabelBox,
    placedLabels: List<LabelBox>,
    occupiedBoxes: List<LabelBox>,
    padding: Float
): Boolean {
    if (placedLabels.any { it.intersects(candidate, padding = padding) }) return false
    if (occupiedBoxes.any { it.intersects(candidate, padding = padding) }) return false
    return true
}

internal fun LabelBox.intersects(other: LabelBox, padding: Float): Boolean {
    return left - padding < other.right &&
        right + padding > other.left &&
        top - padding < other.bottom &&
        bottom + padding > other.top
}

