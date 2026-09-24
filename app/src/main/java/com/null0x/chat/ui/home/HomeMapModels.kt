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

internal data class HighDetailRegionConfig(
    val id: String,
    val bounds: GeoBounds,
    val placesAssetPath: String,
    val boundariesAssetPath: String
)

internal val highDetailRegionConfigs = listOf(
    HighDetailRegionConfig(
        id = "primary-high-detail-region",
        bounds = GeoBounds(
            minLongitude = -74.5,
            minLatitude = -34.2,
            maxLongitude = -33.0,
            maxLatitude = 6.0
        ),
        placesAssetPath = "map/br_places.json",
        boundariesAssetPath = "map/br_state_boundaries.json"
    )
)

internal const val worldCircumferenceMeters = 40_075_000f
internal const val worldHeightMeters = 20_037_500f
internal const val focusedProfilePrecisionMeters = 20f
internal const val focusedProfilePrecisionPixels = 72f
internal const val continentOnlyMetersPerPixel = 12_500f
internal const val earthRadiusMeters = 6_371_008.8f
internal const val sphericalDetailMetersPerPixel = 3_000f
internal const val spaceStarCount = 96
internal const val countryBoundaryMetersPerPixel = 16_000f
internal const val stateBoundaryMetersPerPixel = 3_600f
internal const val adminLabelMetersPerPixel = 2_400f
internal const val highDetailMunicipalityMetersPerPixel = 1_600f
internal const val highDetailDistrictMetersPerPixel = 220f
internal const val highDetailSubdistrictMetersPerPixel = 110f
internal const val highDetailSubdistrictTitleMetersPerPixel = 90f
internal const val cityLabelMetersPerPixel = 720f
internal const val majorCityLabelMetersPerPixel = 1_600f
internal const val localityDensityMetersPerPixel = 1_600f
internal const val cityTitleMetersPerPixel = 180f
internal const val maxWorldLocalityDots = 1_400
internal const val maxNamedWorldLocalities = 36
internal const val maxNamedHighDetailLocalities = 48
internal const val citySafeAreaCellDegrees = 3.0

internal val continentLabels = listOf(
    ContinentLabel("América do Norte", -101.0, 52.0),
    ContinentLabel("América do Sul", -60.0, -17.0),
    ContinentLabel("Europa", 15.0, 52.0),
    ContinentLabel("África", 20.0, 3.0),
    ContinentLabel("Ásia", 88.0, 39.0),
    ContinentLabel("Oceania", 135.0, -25.0),
    ContinentLabel("Antártida", 20.0, -78.0)
)


internal enum class LabelKind {
    Continent,
    Country,
    Admin,
    City
}

internal data class LabelBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val textX: Float,
    val textY: Float,
    val kind: LabelKind
)

internal data class CityLabelPlacement(
    val box: LabelBox,
    val pinned: Boolean
)

internal data class OrbitUser(
    val username: String,
    val displayName: String,
    val emoji: String,
    val mapColorArgb: Int,
    val distanceMeters: Float,
    val xMeters: Float,
    val yMeters: Float,
    val hasLocation: Boolean,
    val locationUpdatedAt: Long,
    val isActive: Boolean,
    val isSelf: Boolean = false,
    val lastActivityLabel: String
) {
    fun screenPoint(center: Offset, pan: Offset, metersPerPixel: Float): Offset {
        return Offset(
            x = center.x + pan.x + xMeters / metersPerPixel,
            y = center.y + pan.y - yMeters / metersPerPixel
        )
    }

    fun screenPoint(center: Offset, pan: Offset, metersPerPixel: Float, worldWidthPx: Float): Offset {
        return Offset(
            x = center.x + wrapHorizontalMapPan(pan.x + xMeters / metersPerPixel, worldWidthPx),
            y = center.y + pan.y - yMeters / metersPerPixel
        )
    }

    fun distanceLabel(): String {
        if (!hasLocation) return "sem localização"
        return "${formatDistance(distanceMeters)} (${formatElapsedAgo(locationUpdatedAt)})"
    }

    fun distanceOnlyLabel(): String {
        if (!hasLocation) return "sem localização"
        return formatDistance(distanceMeters)
    }

    fun mapStatusLabel(): String {
        return when {
            !hasLocation -> "sem localização"
            isActive -> "Disponível"
            else -> "Visto ${formatElapsedTime(locationUpdatedAt)}"
        }
    }
}

internal fun Offset.isInside(width: Float, height: Float, padding: Float): Boolean {
    return x >= padding && x <= width - padding && y >= padding && y <= height - padding
}

internal fun buildOwnPublicMapUser(
    origin: Location,
    distanceOrigin: Location?,
    publicRoute: String,
    myName: String,
    myEmoji: String,
    profileMapColor: Color,
    sharedLocation: ChatViewModel.SharedRouteLocation?
): OrbitUser? {
    if (publicRoute.isBlank() || sharedLocation == null) return null
    val target = Location(LocationManager.GPS_PROVIDER).apply {
        latitude = sharedLocation.latitude
        longitude = sharedLocation.longitude
        sharedLocation.accuracyMeters?.let { accuracy = it }
        time = sharedLocation.updatedAt
    }
    val distance = (distanceOrigin ?: origin).distanceTo(target).coerceAtLeast(1f)
    val mapPosition = mapOffsetMeters(origin, target)
    return OrbitUser(
        username = publicRoute,
        displayName = myName,
        emoji = myEmoji,
        mapColorArgb = profileMapColor.toArgb(),
        distanceMeters = distance,
        xMeters = mapPosition.x,
        yMeters = mapPosition.y,
        hasLocation = true,
        locationUpdatedAt = sharedLocation.updatedAt,
        isActive = true,
        isSelf = true,
        lastActivityLabel = "Sua localização"
    )
}

internal fun buildMapUsers(
    origin: Location,
    distanceOrigin: Location?,
    ownRoute: String,
    contacts: List<ChatViewModel.ContactPreview>,
    sharedLocationForRoute: (String) -> ChatViewModel.SharedRouteLocation?,
    isRouteActive: (String) -> Boolean = { false },
    mapColorForRoute: (String) -> Int = { 0xFF6750A4.toInt() },
    conversationForRoute: (String) -> ChatViewModel.ConversationPreview? = { null }
): List<OrbitUser> {
    val cleanOwnRoute = ownRoute.normalizedRouteKey()
    if (contacts.isEmpty()) return emptyList()
    return contacts
        .map { contact ->
        val sharedLocation = sharedLocationForRoute(contact.username)
        val target = sharedLocation?.let { location ->
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = location.latitude
                longitude = location.longitude
                location.accuracyMeters?.let { accuracy = it }
                time = location.updatedAt
            }
        }
        val distance = target?.let { (distanceOrigin ?: origin).distanceTo(it).coerceAtLeast(1f) } ?: 0f
        val mapPosition = target?.let { mapOffsetMeters(origin, it) } ?: Offset.Zero
        val isSelfRoute = cleanOwnRoute.isNotBlank() && contact.username.normalizedRouteKey() == cleanOwnRoute
        OrbitUser(
            username = contact.username,
            displayName = contact.displayName,
            emoji = contact.emoji,
            mapColorArgb = mapColorForRoute(contact.username),
            distanceMeters = distance,
            xMeters = mapPosition.x,
            yMeters = mapPosition.y,
            hasLocation = target != null,
            locationUpdatedAt = sharedLocation?.updatedAt ?: 0L,
            isActive = isRouteActive(contact.username),
            isSelf = isSelfRoute,
            lastActivityLabel = conversationForRoute(contact.username)?.let { preview ->
                if (preview.lastTimestamp > 0L) {
                    "Última atividade no chat: ${formatElapsedTime(preview.lastTimestamp)}"
                } else {
                    preview.previewLine.ifBlank { "Sem atividade no chat" }
                }
            } ?: "Sem atividade no chat"
        )
    }.sortedWith(compareBy<OrbitUser> { !it.hasLocation }.thenBy { it.distanceMeters }.thenBy { it.displayName })
}

internal fun String.normalizedRouteKey(): String {
    return trim().lowercase(Locale.ROOT)
}

internal fun mapOffsetMeters(origin: Location, target: Location): Offset {
    val latScale = 111_320.0
    val lonScale = (111_320.0 * cos(Math.toRadians(origin.latitude))).coerceAtLeast(1e-6)
    val xMeters = wrappedLongitudeDelta(origin.longitude, target.longitude) * lonScale
    val yMeters = (target.latitude - origin.latitude) * latScale
    return Offset(xMeters.toFloat(), yMeters.toFloat())
}

internal fun formatDistance(distanceMeters: Float): String {
    return if (distanceMeters < 1_000f) {
        "${distanceMeters.roundToInt()} m"
    } else {
        String.format(Locale.US, "%.1f km", distanceMeters / 1_000f)
    }
}

internal fun formatElapsedTime(timestamp: Long): String {
    if (timestamp <= 0L) return "há pouco"
    val elapsedSeconds = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 1_000L).coerceAtLeast(1L)
    return when {
        elapsedSeconds < 60L -> "há pouco"
        elapsedSeconds < 3_600L -> {
            val minutes = elapsedSeconds / 60L
            "$minutes ${if (minutes == 1L) "minuto" else "minutos"}"
        }
        elapsedSeconds < 86_400L -> {
            val hours = elapsedSeconds / 3_600L
            "$hours ${if (hours == 1L) "hora" else "horas"}"
        }
        else -> {
            val days = elapsedSeconds / 86_400L
            "$days ${if (days == 1L) "dia" else "dias"}"
        }
    }
}

internal fun formatElapsedAgo(timestamp: Long): String {
    if (timestamp <= 0L) return "agora"
    val elapsedSeconds = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 1_000L).coerceAtLeast(1L)
    return when {
        elapsedSeconds < 60L -> "agora"
        elapsedSeconds < 3_600L -> {
            val minutes = elapsedSeconds / 60L
            "$minutes ${if (minutes == 1L) "minuto" else "minutos"} atrás"
        }
        elapsedSeconds < 86_400L -> {
            val hours = elapsedSeconds / 3_600L
            "$hours ${if (hours == 1L) "hora" else "horas"} atrás"
        }
        else -> {
            val days = elapsedSeconds / 86_400L
            "$days ${if (days == 1L) "dia" else "dias"} atrás"
        }
    }
}

internal fun genericMapOrigin(): Location {
    return Location(LocationManager.GPS_PROVIDER).apply {
        latitude = 0.0
        longitude = 0.0
        time = 0L
    }
}

internal fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

internal fun Context.isGpsEnabled(): Boolean {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
}

@SuppressLint("MissingPermission")
@Composable
internal fun rememberGpsLocation(context: Context, enabled: Boolean): Location? {
    var location by remember { mutableStateOf<Location?>(null) }
    DisposableEffect(context, enabled) {
        if (!enabled) {
            location = null
            return@DisposableEffect onDispose {}
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@DisposableEffect onDispose {}
        val listener = object : LocationListener {
            override fun onLocationChanged(newLocation: Location) {
                location = newLocation
            }
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { locationManager.isProviderEnabled(it) }
        location = providers
            .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
            .maxByOrNull { it.time }
        providers.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                6_000L,
                250f,
                listener,
                Looper.getMainLooper()
            )
        }
        onDispose {
            locationManager.removeUpdates(listener)
        }
    }
    return location
}
