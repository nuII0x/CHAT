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
internal fun MapTab(
    active: Boolean,
    preheat: Boolean,
    myName: String,
    myEmoji: String,
    publicRoute: String,
    profileMapColor: Color,
    useDarkMapColors: Boolean,
    contacts: List<ChatViewModel.ContactPreview>,
    conversations: List<ChatViewModel.ConversationPreview>,
    isRouteActive: (String) -> Boolean,
    mapColorForRoute: (String) -> Int,
    locationSharingMode: ChatViewModel.LocationSharingMode,
    locationSharingAllowedRoutes: Set<String>,
    sharedLocationForRoute: (String) -> ChatViewModel.SharedRouteLocation?,
    onShareLocationWithAll: (Location?) -> Unit,
    onShareLocationWithSelected: (Set<String>, Location?) -> Unit,
    onDisableLocationSharing: () -> Unit,
    onLocationReady: (Location) -> Unit,
    onMapTitleChange: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onRecordMedia: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasLocationPermission by remember {
        mutableStateOf(context.hasLocationPermission())
    }
    var gpsEnabled by remember {
        mutableStateOf(context.isGpsEnabled())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            context.hasLocationPermission()
        gpsEnabled = context.isGpsEnabled()
    }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        hasLocationPermission = context.hasLocationPermission()
        gpsEnabled = context.isGpsEnabled()
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(lifecycleOwner, active) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && active) {
                hasLocationPermission = context.hasLocationPermission()
                gpsEnabled = context.isGpsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentLocation = rememberGpsLocation(
        context = context,
        enabled = preheat && hasLocationPermission && gpsEnabled
    )
    val genericMapOrigin = remember { genericMapOrigin() }
    val mapOrigin = genericMapOrigin
    LaunchedEffect(active, currentLocation) {
        if (active && currentLocation != null) {
            onLocationReady(currentLocation)
        }
    }
    val acceptedContacts = contacts
        .filter { it.accepted }
        .distinctBy { it.username }

    when {
        active && !hasLocationPermission -> MapGateContent(
            icon = Icons.Filled.LocationOff,
            title = "Permitir localização",
            text = "O Mapa precisa do GPS para calcular distâncias e posicionar os usuários ativos.",
            buttonText = "Permitir GPS",
            onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        )
        active && !gpsEnabled -> MapGateContent(
            icon = Icons.Filled.GpsFixed,
            title = "Ligar GPS",
            text = "Ative a localização do aparelho para usar esta aba.",
            buttonText = "Abrir ajustes de GPS",
            onClick = {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        )
        else -> OrbitMapContent(
            myName = myName.ifBlank { "Você" },
            myEmoji = myEmoji.ifBlank { "🙂" },
            publicRoute = publicRoute,
            profileMapColor = profileMapColor,
            useDarkMapColors = useDarkMapColors,
            origin = mapOrigin,
            currentLocation = currentLocation,
            showLiveLocations = currentLocation != null,
            contacts = acceptedContacts,
            conversations = conversations,
            isRouteActive = isRouteActive,
            mapColorForRoute = mapColorForRoute,
            locationSharingMode = locationSharingMode,
            locationSharingAllowedRoutes = locationSharingAllowedRoutes,
            sharedLocationForRoute = sharedLocationForRoute,
            onShareLocationWithSelected = { routes -> onShareLocationWithSelected(routes, currentLocation) },
            onDisableLocationSharing = onDisableLocationSharing,
            onMapTitleChange = onMapTitleChange,
            onOpenChat = onOpenChat,
            onRecordMedia = onRecordMedia
        )
    }

    if (active && hasLocationPermission && gpsEnabled && currentLocation != null && locationSharingMode == ChatViewModel.LocationSharingMode.UNSET) {
        LocationSharingDialog(
            contacts = contacts.filter { it.accepted },
            initiallySelected = locationSharingAllowedRoutes,
            onShareAll = { onShareLocationWithAll(currentLocation) },
            onShareSelected = { routes -> onShareLocationWithSelected(routes, currentLocation) },
            onDismiss = onDisableLocationSharing
        )
    }
}

@Composable
internal fun MapGateContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
                Icon(imageVector = Icons.Filled.MyLocation, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(buttonText)
            }
        }
    }
}

@Composable
internal fun LocationSharingDialog(
    contacts: List<ChatViewModel.ContactPreview>,
    initiallySelected: Set<String>,
    onShareAll: () -> Unit,
    onShareSelected: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selecting by remember { mutableStateOf(false) }
    var selectedRoutes by remember {
        mutableStateOf(initiallySelected.ifEmpty { contacts.map { it.username }.toSet() })
    }
    PrimalisAlertDialog(
        title = if (selecting) "Escolher contatos" else "Compartilhar localização?",
        message = if (selecting) {
            "Marque quem pode ver sua posição para calcular distâncias no mapa."
        } else {
            "Sua localização só será compartilhada com quem você permitir. Você pode pausar isso depois nos ajustes."
        },
        icon = Icons.Filled.MyLocation,
        confirmLabel = if (selecting) "Salvar seleção" else "Com todos",
        dismissLabel = if (selecting) "Cancelar" else "Não compartilhar",
        neutralLabel = if (selecting) "Todos" else "Selecionar",
        confirmEnabled = !selecting || selectedRoutes.isNotEmpty(),
        onNeutral = {
            if (selecting) {
                onShareAll()
            } else {
                selecting = true
            }
        },
        onConfirm = {
            if (selecting) {
                if (selectedRoutes.isNotEmpty()) {
                    onShareSelected(selectedRoutes)
                }
            } else {
                onShareAll()
            }
        },
        onDismiss = onDismiss
    ) {
        if (selecting) {
            LazyColumn(
                modifier = Modifier.height(220.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(contacts, key = { it.username }) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedRoutes = if (selectedRoutes.contains(contact.username)) {
                                    selectedRoutes - contact.username
                                } else {
                                    selectedRoutes + contact.username
                                }
                                if (selectedRoutes.isNotEmpty()) {
                                    onShareSelected(selectedRoutes)
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedRoutes.contains(contact.username),
                            onCheckedChange = { checked ->
                                selectedRoutes = if (checked) {
                                    selectedRoutes + contact.username
                                } else {
                                    selectedRoutes - contact.username
                                }
                                if (selectedRoutes.isNotEmpty()) {
                                    onShareSelected(selectedRoutes)
                                }
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = contact.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun OrbitMapContent(
    myName: String,
    myEmoji: String,
    publicRoute: String,
    profileMapColor: Color,
    useDarkMapColors: Boolean,
    origin: Location,
    currentLocation: Location?,
    showLiveLocations: Boolean,
    contacts: List<ChatViewModel.ContactPreview>,
    conversations: List<ChatViewModel.ConversationPreview>,
    isRouteActive: (String) -> Boolean,
    mapColorForRoute: (String) -> Int,
    locationSharingMode: ChatViewModel.LocationSharingMode,
    locationSharingAllowedRoutes: Set<String>,
    sharedLocationForRoute: (String) -> ChatViewModel.SharedRouteLocation?,
    onShareLocationWithSelected: (Set<String>) -> Unit,
    onDisableLocationSharing: () -> Unit,
    onMapTitleChange: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onRecordMedia: (String) -> Unit
) {
    val landData = rememberOfflineLandData()
    val cityPlaces = rememberOfflineCityPoints()
    val cityPoints = cityPlaces.all
    val countryLabels = rememberOfflineCountryLabels()
    val adminRegions = rememberOfflineAdminRegions()
    val highDetailPlaces = rememberHighDetailPlaces()
    val localityDensityCells = remember(cityPlaces, highDetailPlaces) {
        buildLocalityDensityCells(cityPoints, highDetailPlaces.all)
    }
    val highDetailRegionBoundaries = rememberHighDetailRegionBoundaries()
    val countryBoundaries = rememberCountryBoundaries()
    val mapPalette = remember(useDarkMapColors) { offlineMapPalette(useDarkMapColors) }
    var selectedUser by remember { mutableStateOf<OrbitUser?>(null) }
    var showSelfSheet by remember { mutableStateOf(false) }
    BackHandler(enabled = selectedUser != null || showSelfSheet) {
        selectedUser = null
        showSelfSheet = false
    }
    val contactMapUsers = buildMapUsers(
        origin = origin,
        distanceOrigin = currentLocation,
        ownRoute = publicRoute,
        contacts = contacts,
        sharedLocationForRoute = sharedLocationForRoute,
        isRouteActive = isRouteActive,
        mapColorForRoute = mapColorForRoute,
        conversationForRoute = { route -> conversations.firstOrNull { it.username == route } }
    )
    val selfRouteKey = publicRoute.normalizedRouteKey()
    val selfMapUser = remember(publicRoute, myName, myEmoji, profileMapColor, origin, currentLocation, sharedLocationForRoute(publicRoute)) {
        buildOwnPublicMapUser(
            origin = origin,
            distanceOrigin = currentLocation,
            publicRoute = publicRoute,
            myName = myName,
            myEmoji = myEmoji,
            profileMapColor = profileMapColor,
            sharedLocation = sharedLocationForRoute(publicRoute)
        )
    }
    val mapUsers = remember(contactMapUsers, selfMapUser, selfRouteKey) {
        if (contactMapUsers.any { it.isSelf }) {
            contactMapUsers
        } else {
            selfMapUser?.let { contactMapUsers + it } ?: contactMapUsers
        }
    }
    val baseMetersPerPixel = 120f
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var initializedMapView by remember { mutableStateOf(false) }
    fun focusUserOnMap(user: OrbitUser, selectUser: Boolean = true) {
        val focusMetersPerPixel = focusedProfilePrecisionMeters / focusedProfilePrecisionPixels
        zoom = baseMetersPerPixel / focusMetersPerPixel
        pan = Offset(
            x = -user.xMeters / focusMetersPerPixel,
            y = user.yMeters / focusMetersPerPixel
        )
        selectedUser = if (selectUser) user else null
        if (selectUser) {
            showSelfSheet = false
        }
    }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val center = Offset(widthPx / 2f, heightPx / 2f)
            val minMetersPerPixel = 1f / 72f
            val overviewGlobeRadius = minOf(widthPx, heightPx).coerceAtLeast(1f) * 0.34f
            val maxMetersPerPixel = (earthRadiusMeters / overviewGlobeRadius)
                .coerceAtLeast(minMetersPerPixel)
            val minZoom = (baseMetersPerPixel / maxMetersPerPixel).coerceAtLeast(0.002f)
            val maxZoom = 8_640f
            LaunchedEffect(minZoom) {
                if (!initializedMapView) {
                    zoom = minZoom
                    initializedMapView = true
                } else if (zoom < minZoom) {
                    zoom = minZoom
                }
            }
            val effectiveZoom = zoom
            val metersPerPixel = (baseMetersPerPixel / effectiveZoom).coerceIn(minMetersPerPixel, maxMetersPerPixel)
            val worldWidthPx = worldCircumferenceMeters / metersPerPixel
            val worldHeightPx = worldHeightMeters / metersPerPixel
            val wholeMapHeightVisible = worldHeightPx <= heightPx + 1f
            val showFlatDetails = metersPerPixel <= sphericalDetailMetersPerPixel
            val globeRadius = earthRadiusMeters / metersPerPixel
            val renderPan = Offset(
                x = wrapHorizontalMapPan(pan.x, worldWidthPx),
                y = clampVerticalMapPan(
                    origin = origin,
                    centerY = center.y,
                    height = heightPx,
                    metersPerPixel = metersPerPixel,
                    panY = pan.y
                )
            )
            val centerGeoPoint = mapGeoPointAtScreen(
                screenPoint = center,
                origin = origin,
                center = center,
                pan = renderPan,
                metersPerPixel = metersPerPixel
            )
            val viewportGeoBounds = mapGeoBoundsForViewport(
                origin = origin,
                center = center,
                pan = renderPan,
                metersPerPixel = metersPerPixel,
                width = widthPx,
                height = heightPx
            )
            val citySafeArea = remember(viewportGeoBounds) {
                snappedCitySafeArea(viewportGeoBounds)
            }
            val nearbyCityPoints = remember(cityPlaces, citySafeArea) {
                cityPlaces.near(
                    viewport = citySafeArea,
                    paddingDegrees = 0.0
                )
            }
            val dynamicMapTitle = remember(
                centerGeoPoint,
                viewportGeoBounds,
                widthPx,
                heightPx,
                metersPerPixel,
                countryLabels,
                nearbyCityPoints,
                adminRegions,
                highDetailPlaces
            ) {
                titleForMapCenter(
                    center = centerGeoPoint,
                    viewport = viewportGeoBounds,
                    viewportWidthPx = widthPx,
                    viewportHeightPx = heightPx,
                    metersPerPixel = metersPerPixel,
                    countryLabels = countryLabels,
                    cityPoints = nearbyCityPoints,
                    adminRegions = adminRegions,
                    highDetailPlaces = highDetailPlaces.all
                )
            }
            LaunchedEffect(dynamicMapTitle) {
                onMapTitleChange(dynamicMapTitle)
            }
            val horizontalCopies = horizontalWorldCopyOffsets(
                width = widthPx,
                worldWidthPx = worldWidthPx,
                panX = renderPan.x
            )
            val gridColor = mapPalette.grid
            val mapBackground = mapPalette.ocean
            val landColor = mapPalette.land
            val boundaryColor = mapPalette.boundary
            val stateBoundaryColor = mapPalette.stateBoundary
            val cityColor = mapPalette.cityLabel
            val cityDotColor = mapPalette.cityDot
            val locationMapCards = remember(mapUsers) {
                mapUsers.filter { it.hasLocation && !it.isSelf }
            }
            val locatedMapUsers = mapUsers.filter { it.hasLocation }
            val positionedUsers = remember(
                locatedMapUsers,
                showFlatDetails,
                center,
                renderPan,
                metersPerPixel,
                worldWidthPx,
                widthPx,
                heightPx,
                centerGeoPoint,
                globeRadius,
                origin
            ) {
                if (showFlatDetails) {
                    val visibleUsers = locatedMapUsers.filter { user ->
                        user.screenPoint(center, renderPan, metersPerPixel, worldWidthPx)
                            .isInside(widthPx, heightPx, padding = 46f)
                    }
                    separateNearbyUsers(
                        users = visibleUsers,
                        center = center,
                        pan = renderPan,
                        metersPerPixel = metersPerPixel,
                        worldWidthPx = worldWidthPx,
                        width = widthPx,
                        height = heightPx
                    )
                } else {
                    separateGlobeUsers(
                        users = locatedMapUsers,
                        origin = origin,
                        centerLongitude = centerGeoPoint.longitude,
                        centerLatitude = centerGeoPoint.latitude,
                        globeCenter = center,
                        globeRadius = globeRadius,
                        width = widthPx,
                        height = heightPx
                    )
                }
            }
            val occupiedUserLabelBoxes = remember(positionedUsers, widthPx, heightPx) {
                buildUserOccupationBoxes(positionedUsers, widthPx, heightPx)
            }
            val latestCenter by rememberUpdatedState(center)
            val latestPan by rememberUpdatedState(renderPan)
            val latestMinZoom by rememberUpdatedState(minZoom)
            val latestMaxZoom by rememberUpdatedState(maxZoom)
            val latestMaxMetersPerPixel by rememberUpdatedState(maxMetersPerPixel)
            val latestHeightPx by rememberUpdatedState(heightPx)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        fun zoomAround(point: Offset, panChange: Offset, zoomChange: Float) {
                            val previousZoom = zoom
                            val nextZoom = (zoom * zoomChange).coerceIn(latestMinZoom, latestMaxZoom)
                            val nextMetersPerPixel = (baseMetersPerPixel / nextZoom)
                                .coerceIn(minMetersPerPixel, latestMaxMetersPerPixel)
                            val nextWorldWidthPx = worldCircumferenceMeters / nextMetersPerPixel
                            val targetPoint = point + panChange
                            val scaleChange = nextZoom / previousZoom
                            val nextPan = Offset(
                                x = targetPoint.x - latestCenter.x - ((point.x - latestCenter.x - latestPan.x) * scaleChange),
                                y = targetPoint.y - latestCenter.y - ((point.y - latestCenter.y - latestPan.y) * scaleChange)
                            )
                            zoom = nextZoom
                            pan = Offset(
                                x = wrapHorizontalMapPan(nextPan.x, nextWorldWidthPx),
                                y = clampVerticalMapPan(
                                    origin = origin,
                                    centerY = latestCenter.y,
                                    height = latestHeightPx,
                                    metersPerPixel = nextMetersPerPixel,
                                    panY = nextPan.y
                                )
                            )
                        }
                        detectStableMapTransformGestures(
                            onGesture = { centroid, panChange, zoomChange ->
                                zoomAround(centroid, panChange, zoomChange)
                            },
                            onDoubleTap = { tapPosition ->
                                zoomAround(tapPosition, Offset.Zero, 2f)
                            }
                        )
                    }
            ) {
                drawSpaceBackground()
                drawOfflineGlobe(
                    landRings = landData.landRingsFor(metersPerPixel),
                    boundaryRings = if (showFlatDetails) countryBoundaries else emptyList(),
                    centerLongitude = centerGeoPoint.longitude,
                    centerLatitude = centerGeoPoint.latitude,
                    metersPerPixel = metersPerPixel,
                    color = landColor,
                    oceanColor = mapBackground,
                    outlineColor = gridColor,
                    boundaryColor = boundaryColor,
                    useDarkColors = useDarkMapColors
                )
                if (!showFlatDetails) {
                    drawGlobeLocalityDensity(
                        cells = localityDensityCells,
                        centerLongitude = centerGeoPoint.longitude,
                        centerLatitude = centerGeoPoint.latitude,
                        radius = globeRadius,
                        color = cityDotColor
                    )
                }
                if (showFlatDetails) horizontalCopies.forEach { copyOffset ->
                    if (metersPerPixel >= localityDensityMetersPerPixel) {
                        drawLocalityDensity(
                            cells = localityDensityCells,
                            origin = origin,
                            center = center,
                            pan = renderPan.copy(x = renderPan.x + copyOffset),
                            metersPerPixel = metersPerPixel,
                            color = cityDotColor
                        )
                    } else {
                        drawWorldLocalityDots(
                            cityPoints = nearbyCityPoints,
                            origin = origin,
                            center = center,
                            pan = renderPan.copy(x = renderPan.x + copyOffset),
                            metersPerPixel = metersPerPixel,
                            color = cityDotColor
                        )
                    }
                }
                if (showFlatDetails) horizontalCopies.forEach { copyOffset ->
                    drawMapBoundaries(
                        boundaryRings = highDetailRegionBoundaries,
                        viewport = viewportGeoBounds,
                        origin = origin,
                        center = center,
                        pan = renderPan.copy(x = renderPan.x + copyOffset),
                        metersPerPixel = metersPerPixel,
                        color = stateBoundaryColor,
                        strokeWidth = 1.15.dp.toPx(),
                        visibleWhen = metersPerPixel <= stateBoundaryMetersPerPixel
                    )
                }
                if (showFlatDetails) horizontalCopies.forEach { copyOffset ->
                    drawOfflineMapLabels(
                        countryLabels = countryLabels,
                        adminRegions = adminRegions,
                        cityPoints = nearbyCityPoints,
                        origin = origin,
                        center = center,
                        pan = renderPan.copy(x = renderPan.x + copyOffset),
                        metersPerPixel = metersPerPixel,
                        labelColor = cityColor,
                        dotColor = cityDotColor,
                        occupiedBoxes = occupiedUserLabelBoxes,
                        showContinentOverview = wholeMapHeightVisible
                    )
                    drawHighDetailPlaceLabels(
                        places = highDetailPlaces,
                        viewport = viewportGeoBounds,
                        origin = origin,
                        center = center,
                        pan = renderPan.copy(x = renderPan.x + copyOffset),
                        metersPerPixel = metersPerPixel,
                        labelColor = cityColor,
                        dotColor = cityDotColor,
                        occupiedBoxes = occupiedUserLabelBoxes
                    )
                }
                positionedUsers.forEach { positionedUser ->
                    if (positionedUser.needsPointer) {
                        drawLine(
                            color = mapPalette.pointer,
                            start = positionedUser.anchor,
                            end = positionedUser.display,
                            strokeWidth = 1.4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = mapPalette.ocean,
                            radius = 7.dp.toPx(),
                            center = positionedUser.anchor
                        )
                        drawCircle(
                            color = mapPalette.pointer,
                            radius = 5.dp.toPx(),
                            center = positionedUser.anchor
                        )
                        drawCircle(
                            color = mapPalette.ocean,
                            radius = 2.dp.toPx(),
                            center = positionedUser.anchor
                        )
                    }
                }
            }

            positionedUsers.forEach { positionedUser ->
                if (positionedUser.user.isSelf) {
                    CenterOrbitAvatar(
                        name = positionedUser.label ?: positionedUser.user.displayName,
                        emoji = positionedUser.user.emoji.ifBlank { myEmoji },
                        backgroundColor = Color(positionedUser.user.mapColorArgb),
                        showLabel = positionedUser.label != null,
                        onClick = {
                            focusUserOnMap(positionedUser.user, selectUser = false)
                            showSelfSheet = true
                        },
                        modifier = Modifier.offset {
                            IntOffset(
                                x = (positionedUser.display.x - 38.dp.toPx()).roundToInt(),
                                y = (positionedUser.display.y - 44.dp.toPx()).roundToInt()
                            )
                        }
                    )
                } else {
                    OrbitUserAvatar(
                        user = positionedUser.user,
                        label = positionedUser.label,
                        onClick = { focusUserOnMap(positionedUser.user) },
                        modifier = Modifier.offset {
                            IntOffset(
                                x = (positionedUser.display.x - 38.dp.toPx()).roundToInt(),
                                y = (positionedUser.display.y - 42.dp.toPx()).roundToInt()
                            )
                        }
                    )
                }
            }
            if (locationMapCards.isNotEmpty() && selectedUser == null) {
                LazyRow(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(92.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 2.dp, top = 8.dp, end = 2.dp, bottom = 0.dp)
                ) {
                    items(locationMapCards, key = { it.username }) { user ->
                        OrbitUserDistanceCard(
                            user = user,
                            onClick = { focusUserOnMap(user) }
                        )
                    }
                }
            }
            selectedUser?.let { user ->
                MapUserBottomCard(
                    user = user,
                    onClose = { selectedUser = null },
                    onOpenChat = { onOpenChat(user.username) },
                    onRecordMedia = { onRecordMedia(user.username) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            if (showSelfSheet) {
                SelfMapQuickSettingsSheet(
                    name = myName,
                    emoji = myEmoji,
                    profileMapColor = profileMapColor,
                    contacts = contacts.filter { it.username.normalizedRouteKey() != selfRouteKey },
                    locationSharingMode = locationSharingMode,
                    locationSharingAllowedRoutes = locationSharingAllowedRoutes,
                    onSelectionChange = onShareLocationWithSelected,
                    onDisableLocationSharing = onDisableLocationSharing,
                    onDismiss = { showSelfSheet = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 104.dp)
                )
            }
        }
    }
}

@Composable
internal fun CenterOrbitAvatar(
    name: String,
    emoji: String,
    backgroundColor: Color,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(76.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = backgroundColor,
            modifier = Modifier.size(58.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.titleLarge,
                    color = readableOnColor(backgroundColor)
                )
            }
        }
        if (showLabel) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun OrbitUserAvatar(
    user: OrbitUser,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(76.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(user.mapColorArgb),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = user.emoji.ifBlank { "🙂" },
                    style = MaterialTheme.typography.titleMedium,
                    color = readableOnColor(Color(user.mapColorArgb))
                )
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = user.distanceOnlyLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun OrbitUserDistanceCard(
    user: OrbitUser,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(218.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clickable(onClick = onClick),
                shape = CircleShape,
                color = Color(user.mapColorArgb)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = user.emoji.ifBlank { "🙂" },
                        style = MaterialTheme.typography.titleMedium,
                        color = readableOnColor(Color(user.mapColorArgb))
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = user.mapStatusLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = user.distanceOnlyLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun MapUserBottomCard(
    user: OrbitUser,
    onClose: () -> Unit,
    onOpenChat: () -> Unit,
    onRecordMedia: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(user.mapColorArgb),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user.emoji.ifBlank { "🙂" },
                            style = MaterialTheme.typography.titleLarge,
                            color = readableOnColor(Color(user.mapColorArgb))
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.mapStatusLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Fechar")
                }
            }
            if (user.hasLocation) {
                Text(
                    text = "Distância ${user.distanceLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            if (!user.isActive) {
                Text(
                    text = user.lastActivityLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRecordMedia,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Videocam, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Gravar mídia")
                }
                Button(
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Filled.ChatBubble, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Chat")
                }
            }
        }
    }
}

@Composable
internal fun SelfMapQuickSettingsSheet(
    name: String,
    emoji: String,
    profileMapColor: Color,
    contacts: List<ChatViewModel.ContactPreview>,
    locationSharingMode: ChatViewModel.LocationSharingMode,
    locationSharingAllowedRoutes: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDisableLocationSharing: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedContacts by remember { mutableStateOf(false) }
    val cleanAllowedRoutes = locationSharingAllowedRoutes.map { it.normalizedRouteKey() }.toSet()
    val contactRoutes = contacts.map { it.username.normalizedRouteKey() }.filter { it.isNotBlank() }.toSet()
    val selectedRoutes = when (locationSharingMode) {
        ChatViewModel.LocationSharingMode.ALL -> contactRoutes
        ChatViewModel.LocationSharingMode.SELECTED -> cleanAllowedRoutes
        ChatViewModel.LocationSharingMode.EMERGENCY -> cleanAllowedRoutes
        ChatViewModel.LocationSharingMode.NONE,
        ChatViewModel.LocationSharingMode.UNSET -> emptySet()
    }
    val visibleContacts = if (expandedContacts) contacts else contacts.take(5)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 42.dp, height = 4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            ) {}
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(58.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Perfil no mapa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Fechar")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Compartilhar com",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (contacts.isEmpty()) {
                    Text(
                        text = "Nenhum contato aceito.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(if (expandedContacts) 238.dp else 184.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(visibleContacts, key = { it.username }) { contact ->
                            val routeKey = contact.username.normalizedRouteKey()
                            val checked = selectedRoutes.contains(routeKey)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val next = if (checked) {
                                            selectedRoutes - routeKey
                                        } else {
                                            selectedRoutes + routeKey
                                        }
                                        if (next.isEmpty()) {
                                            onDisableLocationSharing()
                                        } else {
                                            onSelectionChange(next)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        val next = if (isChecked) {
                                            selectedRoutes + routeKey
                                        } else {
                                            selectedRoutes - routeKey
                                        }
                                        if (next.isEmpty()) {
                                            onDisableLocationSharing()
                                        } else {
                                            onSelectionChange(next)
                                        }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = contact.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (!expandedContacts && contacts.size > visibleContacts.size) {
                            item {
                                TextButton(
                                    onClick = { expandedContacts = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Ver mais")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
