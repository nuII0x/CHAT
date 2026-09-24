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

@Composable
internal fun rememberOfflineLandData(): OfflineLandData {
    val context = LocalContext.current
    return remember(context) {
        OfflineLandData(
            land50m = loadOfflineLandRings(context, "map/ne_50m_land.min.geojson"),
            land110m = loadOfflineLandRings(context, "map/ne_110m_land.min.geojson")
        )
    }
}

@Composable
internal fun rememberOfflineCityPoints(): CityPlacesData {
    val context = LocalContext.current
    return produceState(initialValue = CityPlacesData(emptyList()), key1 = context) {
        value = withContext(Dispatchers.IO) { CityPlacesData(loadOfflineCityPoints(context)) }
    }.value
}

@Composable
internal fun rememberOfflineCountryLabels(): List<CountryLabel> {
    val context = LocalContext.current
    return remember(context) {
        loadOfflineCountryLabels(context)
    }
}

@Composable
internal fun rememberOfflineAdminRegions(): List<AdminRegion> {
    val context = LocalContext.current
    return remember(context) {
        loadOfflineAdminRegions(context)
    }
}

@Composable
internal fun rememberHighDetailPlaces(): HighDetailPlacesData {
    val context = LocalContext.current
    return remember(context) {
        loadHighDetailPlaces(context)
    }
}

@Composable
internal fun rememberHighDetailRegionBoundaries(): List<BoundaryRing> {
    val context = LocalContext.current
    return remember(context) {
        highDetailRegionConfigs.flatMap { config ->
            loadBoundaryRings(context, config.boundariesAssetPath)
        }
    }
}

@Composable
internal fun rememberCountryBoundaries(): List<BoundaryRing> {
    val context = LocalContext.current
    return remember(context) {
        loadBoundaryRings(context, "map/ne_country_boundaries_50m.json")
    }
}

internal fun loadOfflineLandRings(context: Context, assetPath: String): List<LandRing> {
    val raw = runCatching {
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }.getOrNull() ?: return emptyList()
    val features = runCatching { JSONObject(raw).optJSONArray("features") }.getOrNull() ?: return emptyList()
    return buildList {
        for (featureIndex in 0 until features.length()) {
            val geometry = features.optJSONObject(featureIndex)?.optJSONObject("geometry") ?: continue
            when (geometry.optString("type")) {
                "Polygon" -> addPolygonRings(geometry.optJSONArray("coordinates"))
                "MultiPolygon" -> {
                    val polygons = geometry.optJSONArray("coordinates") ?: continue
                    for (polygonIndex in 0 until polygons.length()) {
                        addPolygonRings(polygons.optJSONArray(polygonIndex))
                    }
                }
            }
        }
    }
}

internal fun loadOfflineCityPoints(context: Context): List<CityPoint> {
    val raw = runCatching {
        context.assets.open("map/ne_populated_places.json").bufferedReader().use { it.readText() }
    }.getOrNull() ?: return emptyList()
    val cities = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until cities.length()) {
            val city = cities.optJSONObject(index) ?: continue
            val name = city.optString("n", city.optString("name")).trim()
            if (name.isBlank()) continue
            val longitude = city.optDouble("lon")
            val latitude = city.optDouble("lat")
            if (isInsideHighDetailRegion(longitude, latitude)) continue
            add(
                CityPoint(
                    name = name,
                    longitude = longitude,
                    latitude = latitude,
                    importanceRank = cityImportanceRank(
                        scaleRank = city.optInt("rank", 9),
                        population = city.optLong("pop", 0L),
                        isCountryCapital = city.optInt("cap", 0) == 1
                    ),
                    population = city.optLong("pop", 0L),
                    isCountryCapital = city.optInt("cap", 0) == 1,
                    minZoom = city.optDouble("min", 9.0).toFloat()
                )
            )
        }
    }
}

internal fun isInsideHighDetailRegion(longitude: Double, latitude: Double): Boolean {
    return highDetailRegionConfigs.any { it.bounds.contains(longitude, latitude) }
}

internal fun loadOfflineCountryLabels(context: Context): List<CountryLabel> {
    val raw = runCatching {
        context.assets.open("map/ne_countries_labels.json").bufferedReader().use { it.readText() }
    }.getOrNull() ?: return emptyList()
    val labels = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until labels.length()) {
            val label = labels.optJSONObject(index) ?: continue
            val name = label.optString("name").trim()
            if (name.isBlank()) continue
            add(
                CountryLabel(
                    name = name,
                    longitude = label.optDouble("lon"),
                    latitude = label.optDouble("lat"),
                    rank = label.optInt("rank", 99),
                    population = label.optLong("pop", 0L),
                    minZoom = label.optDouble("min", 0.0).toFloat(),
                    maxZoom = label.optDouble("max", 99.0).toFloat()
                )
            )
        }
    }
}

internal fun loadOfflineAdminRegions(context: Context): List<AdminRegion> {
    val raw = runCatching {
        context.assets.open("map/ne_admin1_regions.json").bufferedReader().use { it.readText() }
    }.getOrNull() ?: return emptyList()
    val regions = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until regions.length()) {
            val region = regions.optJSONObject(index) ?: continue
            val name = region.optString("name").trim()
            val bbox = region.optJSONArray("bbox") ?: continue
            if (name.isBlank() || bbox.length() < 4) continue
            add(
                AdminRegion(
                    name = name,
                    country = region.optString("country").trim(),
                    longitude = region.optDouble("lon"),
                    latitude = region.optDouble("lat"),
                    minLongitude = bbox.optDouble(0),
                    minLatitude = bbox.optDouble(1),
                    maxLongitude = bbox.optDouble(2),
                    maxLatitude = bbox.optDouble(3),
                    rank = region.optInt("rank", 9)
                )
            )
        }
    }
}

internal fun loadHighDetailPlaces(context: Context): HighDetailPlacesData {
    val points = highDetailRegionConfigs.flatMap { config ->
        loadHighDetailPlacesForRegion(context, config)
    }
    return HighDetailPlacesData(points)
}

internal fun loadHighDetailPlacesForRegion(
    context: Context,
    config: HighDetailRegionConfig
): List<HighDetailPlacePoint> {
    val raw = runCatching {
        context.assets.open(config.placesAssetPath).bufferedReader().use { it.readText() }
    }.getOrNull() ?: return emptyList()
    val places = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until places.length()) {
            val place = places.optJSONObject(index) ?: continue
            val name = place.optString("n").trim()
            if (name.isBlank()) continue
            add(
                HighDetailPlacePoint(
                    regionId = config.id,
                    name = name,
                    longitude = place.optDouble("lon"),
                    latitude = place.optDouble("lat"),
                    kind = when (place.optString("k")) {
                        "d" -> HighDetailPlaceKind.District
                        "s" -> HighDetailPlaceKind.Subdistrict
                        else -> HighDetailPlaceKind.Municipality
                    },
                    rank = place.optInt("rank", 3)
                )
            )
        }
    }
}

internal fun loadBoundaryRings(context: Context, assetPath: String): List<BoundaryRing> {
    val raw = runCatching {
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }.getOrNull() ?: return emptyList()
    val features = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    return buildList {
        for (featureIndex in 0 until features.length()) {
            val feature = features.optJSONObject(featureIndex) ?: continue
            val rings = feature.optJSONArray("rings") ?: continue
            for (ringIndex in 0 until rings.length()) {
                boundaryRingFromJson(rings.optJSONArray(ringIndex))?.let { add(it) }
            }
        }
    }
}

internal fun boundaryRingFromJson(ring: JSONArray?): BoundaryRing? {
    if (ring == null || ring.length() < 2) return null
    var minLongitude = Double.POSITIVE_INFINITY
    var minLatitude = Double.POSITIVE_INFINITY
    var maxLongitude = Double.NEGATIVE_INFINITY
    var maxLatitude = Double.NEGATIVE_INFINITY
    val points = buildList {
        for (pointIndex in 0 until ring.length()) {
            val point = ring.optJSONArray(pointIndex) ?: continue
            val longitude = point.optDouble(0)
            val latitude = point.optDouble(1)
            if (!longitude.isFinite() || !latitude.isFinite()) continue
            minLongitude = minOf(minLongitude, longitude)
            minLatitude = minOf(minLatitude, latitude)
            maxLongitude = maxOf(maxLongitude, longitude)
            maxLatitude = maxOf(maxLatitude, latitude)
            add(GeoPoint(longitude = longitude, latitude = latitude))
        }
    }
    if (points.size < 2) return null
    return BoundaryRing(
        points = points.withContinuousLongitudes(),
        minLongitude = minLongitude,
        minLatitude = minLatitude,
        maxLongitude = maxLongitude,
        maxLatitude = maxLatitude
    )
}

internal data class OfflineLandData(
    val land50m: List<LandRing>,
    val land110m: List<LandRing>
) {
    fun landRingsFor(metersPerPixel: Float): List<LandRing> {
        return when {
            metersPerPixel <= 50f -> land50m
            metersPerPixel <= 110f -> land110m
            else -> land110m
        }
    }
}

internal enum class HighDetailPlaceKind {
    Municipality,
    District,
    Subdistrict
}

internal data class HighDetailPlacePoint(
    val regionId: String,
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val kind: HighDetailPlaceKind,
    val rank: Int
)

internal data class HighDetailPlacesData(
    val all: List<HighDetailPlacePoint>
) {
    private val byCell: Map<Long, List<HighDetailPlacePoint>> = all.groupBy { place ->
        highDetailPlaceCellKey(place.longitude, place.latitude)
    }

    fun near(viewport: GeoBounds, paddingDegrees: Double): List<HighDetailPlacePoint> {
        if (all.isEmpty()) return emptyList()
        val minLon = kotlin.math.floor(viewport.minLongitude - paddingDegrees).toInt()
        val maxLon = kotlin.math.ceil(viewport.maxLongitude + paddingDegrees).toInt()
        val minLat = kotlin.math.floor(viewport.minLatitude - paddingDegrees).toInt()
        val maxLat = kotlin.math.ceil(viewport.maxLatitude + paddingDegrees).toInt()
        return buildList {
            for (lon in minLon..maxLon) {
                for (lat in minLat..maxLat) {
                    byCell[highDetailPlaceCellKey(lon.toDouble(), lat.toDouble())]?.let { addAll(it) }
                }
            }
        }
    }
}

internal data class CityPlacesData(
    val all: List<CityPoint>
) {
    private val byCell: Map<Long, List<CityPoint>> = all.groupBy { place ->
        mapPlaceCellKey(place.longitude, place.latitude)
    }

    fun near(viewport: GeoBounds, paddingDegrees: Double): List<CityPoint> {
        if (all.isEmpty()) return emptyList()
        val minLat = kotlin.math.floor(viewport.minLatitude - paddingDegrees).toInt().coerceAtLeast(-90)
        val maxLat = kotlin.math.ceil(viewport.maxLatitude + paddingDegrees).toInt().coerceAtMost(90)
        val longitudeRanges = if (viewport.longitudeSpan() <= 180.0) {
            listOf(
                kotlin.math.floor(viewport.minLongitude - paddingDegrees).toInt().coerceAtLeast(-180)..
                    kotlin.math.ceil(viewport.maxLongitude + paddingDegrees).toInt().coerceAtMost(180)
            )
        } else {
            listOf(
                -180..kotlin.math.ceil(viewport.minLongitude + paddingDegrees).toInt().coerceAtMost(180),
                kotlin.math.floor(viewport.maxLongitude - paddingDegrees).toInt().coerceAtLeast(-180)..180
            )
        }
        return buildList {
            for (longitudeRange in longitudeRanges) {
                for (lon in longitudeRange) {
                    for (lat in minLat..maxLat) {
                        byCell[mapPlaceCellKey(lon.toDouble(), lat.toDouble())]?.let(::addAll)
                    }
                }
            }
        }
    }
}

internal data class LocalityDensityCell(
    val longitude: Double,
    val latitude: Double,
    val count: Int
)

internal fun buildLocalityDensityCells(
    cityPoints: List<CityPoint>,
    highDetailPlaces: List<HighDetailPlacePoint>
): List<LocalityDensityCell> {
    val counts = mutableMapOf<Pair<Int, Int>, Int>()
    fun add(longitude: Double, latitude: Double) {
        val key = kotlin.math.floor(longitude).toInt() to kotlin.math.floor(latitude).toInt()
        counts[key] = (counts[key] ?: 0) + 1
    }
    cityPoints.forEach { add(it.longitude, it.latitude) }
    highDetailPlaces.forEach { add(it.longitude, it.latitude) }
    return counts.map { (cell, count) ->
        LocalityDensityCell(
            longitude = cell.first + 0.5,
            latitude = cell.second + 0.5,
            count = count
        )
    }
}

internal fun highDetailPlaceCellKey(longitude: Double, latitude: Double): Long {
    return mapPlaceCellKey(longitude, latitude)
}

internal fun mapPlaceCellKey(longitude: Double, latitude: Double): Long {
    val lonCell = kotlin.math.floor(longitude).toInt() + 180
    val latCell = kotlin.math.floor(latitude).toInt() + 90
    return (lonCell.toLong() shl 32) xor (latCell.toLong() and 0xffffffffL)
}

internal fun snappedCitySafeArea(viewport: GeoBounds): GeoBounds {
    val rawLongitudeSpan = viewport.longitudeSpan()
    val visibleLongitudeSpan = if (rawLongitudeSpan > 180.0) 360.0 - rawLongitudeSpan else rawLongitudeSpan
    val longitudeMargin = maxOf(1.2, visibleLongitudeSpan * 0.12)
    val latitudeMargin = maxOf(1.2, viewport.latitudeSpan() * 0.12)
    val minLongitude: Double
    val maxLongitude: Double
    if (rawLongitudeSpan > 180.0) {
        minLongitude = snapUp(viewport.minLongitude + longitudeMargin, citySafeAreaCellDegrees)
        maxLongitude = snapDown(viewport.maxLongitude - longitudeMargin, citySafeAreaCellDegrees)
    } else {
        minLongitude = snapDown(viewport.minLongitude - longitudeMargin, citySafeAreaCellDegrees)
            .coerceAtLeast(-180.0)
        maxLongitude = snapUp(viewport.maxLongitude + longitudeMargin, citySafeAreaCellDegrees)
            .coerceAtMost(180.0)
    }
    return GeoBounds(
        minLongitude = minLongitude,
        minLatitude = snapDown(viewport.minLatitude - latitudeMargin, citySafeAreaCellDegrees)
            .coerceAtLeast(-90.0),
        maxLongitude = maxLongitude,
        maxLatitude = snapUp(viewport.maxLatitude + latitudeMargin, citySafeAreaCellDegrees)
            .coerceAtMost(90.0)
    )
}

internal fun snapDown(value: Double, step: Double): Double = kotlin.math.floor(value / step) * step

internal fun snapUp(value: Double, step: Double): Double = kotlin.math.ceil(value / step) * step

internal fun MutableList<LandRing>.addPolygonRings(polygon: JSONArray?) {
    if (polygon == null) return
    for (ringIndex in 0 until polygon.length()) {
        val ring = polygon.optJSONArray(ringIndex) ?: continue
        val points = buildList {
            for (pointIndex in 0 until ring.length()) {
                val point = ring.optJSONArray(pointIndex) ?: continue
                add(
                    GeoPoint(
                        longitude = point.optDouble(0),
                        latitude = point.optDouble(1)
                    )
                )
            }
        }
        if (points.size >= 3) {
            add(createLandRing(points))
        }
    }
}

internal fun createLandRing(points: List<GeoPoint>): LandRing {
    val continuousPoints = points.withContinuousLongitudes()
    return LandRing(
        detailedPoints = continuousPoints,
        balancedPoints = continuousPoints.simplifiedByStride(stride = 3),
        overviewPoints = continuousPoints.simplifiedByStride(stride = 8)
    )
}

