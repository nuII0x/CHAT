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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.null0x.chat.viewmodel.ChatViewModel
import com.null0x.chat.util.normalizeProfileEmojiInput
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
    onProfileEmojiChange: (String) -> Unit,
    onProfileMapColorChange: (Int) -> Unit,
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
            onProfileEmojiChange = onProfileEmojiChange,
            onProfileMapColorChange = onProfileMapColorChange,
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
private fun MapGateContent(
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
private fun LocationSharingDialog(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartilhar localização?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Para mostrar distâncias reais, sua localização será compartilhada apenas com quem você permitir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onShareAll()
                }
            ) {
                Text("Com todos")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Não compartilhar")
                }
                TextButton(onClick = { selecting = true }) {
                    Text("Selecionar")
                }
            }
        }
    )
}

@Composable
private fun OrbitMapContent(
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
    onProfileEmojiChange: (String) -> Unit,
    onProfileMapColorChange: (Int) -> Unit,
    onShareLocationWithSelected: (Set<String>) -> Unit,
    onDisableLocationSharing: () -> Unit,
    onMapTitleChange: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onRecordMedia: (String) -> Unit
) {
    val landData = rememberOfflineLandData()
    val cityPoints = rememberOfflineCityPoints()
    val countryLabels = rememberOfflineCountryLabels()
    val adminRegions = rememberOfflineAdminRegions()
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
    fun focusUserOnMap(user: OrbitUser) {
        val focusMetersPerPixel = 10f / 72f
        zoom = baseMetersPerPixel / focusMetersPerPixel
        pan = Offset(
            x = -user.xMeters / focusMetersPerPixel,
            y = user.yMeters / focusMetersPerPixel
            )
        selectedUser = user
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
            val maxMetersPerPixelByWidth = worldCircumferenceMeters / widthPx.coerceAtLeast(1f)
            val maxMetersPerPixelByHeight = worldHeightMeters / heightPx.coerceAtLeast(1f)
            val maxMetersPerPixel = minOf(maxMetersPerPixelByWidth, maxMetersPerPixelByHeight)
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
            val dynamicMapTitle = remember(centerGeoPoint, viewportGeoBounds, metersPerPixel, countryLabels, cityPoints, adminRegions) {
                titleForMapCenter(
                    center = centerGeoPoint,
                    viewport = viewportGeoBounds,
                    metersPerPixel = metersPerPixel,
                    countryLabels = countryLabels,
                    cityPoints = cityPoints,
                    adminRegions = adminRegions
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
            val cityColor = mapPalette.cityLabel
            val cityDotColor = mapPalette.cityDot
            val locationMapCards = remember(mapUsers) {
                mapUsers.filter { it.hasLocation && !it.isSelf }
            }
            val locatedMapUsers = mapUsers.filter { it.hasLocation }
            val visibleUsers = locatedMapUsers.filter { user ->
                user.screenPoint(center, renderPan, metersPerPixel, worldWidthPx).isInside(widthPx, heightPx, padding = 46f)
            }
            val positionedUsers = remember(visibleUsers, center, renderPan, metersPerPixel, worldWidthPx, widthPx, heightPx) {
                separateNearbyUsers(
                    users = visibleUsers,
                    center = center,
                    pan = renderPan,
                    metersPerPixel = metersPerPixel,
                    worldWidthPx = worldWidthPx,
                    width = widthPx,
                    height = heightPx
                )
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
                drawRect(color = mapBackground)
                horizontalCopies.forEach { copyOffset ->
                    drawOfflineWorldLand(
                        landRings = landData.landRingsFor(metersPerPixel),
                        origin = origin,
                        center = center,
                        pan = renderPan.copy(x = renderPan.x + copyOffset),
                        metersPerPixel = metersPerPixel,
                        color = landColor,
                        outlineColor = gridColor.copy(alpha = if (useDarkMapColors) 0.22f else 0.30f)
                    )
                }
                val gridStep = 72.dp.toPx()
                var x = (center.x + renderPan.x) % gridStep
                while (x < size.width) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    x += gridStep
                }
                var y = (center.y + renderPan.y) % gridStep
                while (y < size.height) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    y += gridStep
                }
                horizontalCopies.forEach { copyOffset ->
                    drawOfflineMapLabels(
                        countryLabels = countryLabels,
                        cityPoints = cityPoints,
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
                            selectedUser = null
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
                        onClick = { selectedUser = positionedUser.user },
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
                    onEmojiChange = onProfileEmojiChange,
                    onColorChange = onProfileMapColorChange,
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
private fun CenterOrbitAvatar(
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
private fun OrbitUserAvatar(
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
private fun OrbitUserDistanceCard(
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
private fun MapUserBottomCard(
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
private fun SelfMapQuickSettingsSheet(
    name: String,
    emoji: String,
    profileMapColor: Color,
    contacts: List<ChatViewModel.ContactPreview>,
    locationSharingMode: ChatViewModel.LocationSharingMode,
    locationSharingAllowedRoutes: Set<String>,
    onEmojiChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    onDisableLocationSharing: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var emojiDraft by remember { mutableStateOf(emoji) }
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
    LaunchedEffect(emoji) {
        emojiDraft = emoji
    }

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
                    color = profileMapColor,
                    modifier = Modifier.size(58.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = emoji.ifBlank { "🙂" },
                            style = MaterialTheme.typography.titleLarge,
                            color = readableOnColor(profileMapColor)
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
            OutlinedTextField(
                value = emojiDraft,
                onValueChange = { value ->
                    val clean = normalizeProfileEmojiInput(value).ifBlank { "🙂" }
                    emojiDraft = clean
                    onEmojiChange(clean)
                },
                singleLine = true,
                label = { Text("Emoji") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Cor do fundo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    mapProfileColorOptions.forEach { option ->
                        val selected = option.toArgb() == profileMapColor.toArgb()
                        Surface(
                            modifier = Modifier
                                .size(if (selected) 38.dp else 34.dp)
                                .clickable { onColorChange(option.toArgb()) },
                            shape = CircleShape,
                            color = option,
                            border = if (selected) {
                                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                            } else {
                                null
                            }
                        ) {}
                    }
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

@Composable
private fun rememberOfflineLandData(): OfflineLandData {
    val context = LocalContext.current
    return remember(context) {
        OfflineLandData(
            land50m = loadOfflineLandRings(context, "map/ne_50m_land.min.geojson"),
            land110m = loadOfflineLandRings(context, "map/ne_110m_land.min.geojson")
        )
    }
}

@Composable
private fun rememberOfflineCityPoints(): List<CityPoint> {
    val context = LocalContext.current
    return remember(context) {
        loadOfflineCityPoints(context)
    }
}

@Composable
private fun rememberOfflineCountryLabels(): List<CountryLabel> {
    val context = LocalContext.current
    return remember(context) {
        loadOfflineCountryLabels(context)
    }
}

@Composable
private fun rememberOfflineAdminRegions(): List<AdminRegion> {
    val context = LocalContext.current
    return remember(context) {
        loadOfflineAdminRegions(context)
    }
}

private fun loadOfflineLandRings(context: Context, assetPath: String): List<LandRing> {
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

private fun loadOfflineCityPoints(context: Context): List<CityPoint> {
    val raw = runCatching {
        context.assets.open("map/ne_populated_places.json").bufferedReader().use { it.readText() }
    }.getOrNull() ?: return emptyList()
    val cities = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until cities.length()) {
            val city = cities.optJSONObject(index) ?: continue
            val name = city.optString("name").trim()
            if (name.isBlank()) continue
            add(
                CityPoint(
                    name = name,
                    longitude = city.optDouble("lon"),
                    latitude = city.optDouble("lat"),
                    importanceRank = cityImportanceRank(name)
                )
            )
        }
    }
}

private fun loadOfflineCountryLabels(context: Context): List<CountryLabel> {
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

private fun loadOfflineAdminRegions(context: Context): List<AdminRegion> {
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

private data class OfflineLandData(
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

private fun MutableList<LandRing>.addPolygonRings(polygon: JSONArray?) {
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

private fun createLandRing(points: List<GeoPoint>): LandRing {
    val continuousPoints = points.withContinuousLongitudes()
    return LandRing(
        detailedPoints = continuousPoints,
        balancedPoints = continuousPoints.simplifiedByStride(stride = 3),
        overviewPoints = continuousPoints.simplifiedByStride(stride = 8)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOfflineMapLabels(
    countryLabels: List<CountryLabel>,
    cityPoints: List<CityPoint>,
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    labelColor: Color,
    dotColor: Color,
    occupiedBoxes: List<LabelBox>
) {
    if (countryLabels.isEmpty() && cityPoints.isEmpty()) return
    val labelZoom = mapLabelZoom(metersPerPixel)
    val crowdedCountryLimit = ((size.width * size.height) / 32_000f).roundToInt().coerceIn(8, 42)
    val crowdedCityLimit = ((size.width * size.height) / 18_000f).roundToInt().coerceIn(14, 96)
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
    val cityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor.toArgb()
        textSize = 11.sp.toPx()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    }
    val latScale = 111_320.0
    val lonScale = (111_320.0 * cos(Math.toRadians(origin.latitude))).coerceAtLeast(1e-6)
    val placedLabels = mutableListOf<LabelBox>()
    val labelRenderPadding = 220f

    if (metersPerPixel > continentOnlyMetersPerPixel) {
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

    if (metersPerPixel > firstCityLabelMetersPerPixel) return
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
            if (point.isNearViewport(size.width, size.height, labelRenderPadding)) city to point else null
        }
        .toList()
    val cityStep = if (visibleCityLabels.size <= crowdedCityLimit * 2) {
        1
    } else {
        citySampleStep(metersPerPixel)
    }
    val cityLimit = if (visibleCityLabels.size <= crowdedCityLimit * 2 || metersPerPixel <= 240f) {
        Int.MAX_VALUE
    } else {
        cityLimitForZoom(metersPerPixel, crowdedCityLimit)
    }
    visibleCityLabels
        .asSequence()
        .filter { (city, _) -> cityStep == 1 || city.importanceRank <= 1 || city.stableSampleIndex() % cityStep == 0 }
        .sortedWith(
            compareBy<Pair<CityPoint, Offset>> { it.first.importanceRank }
                .thenBy { it.first.stableSampleIndex() }
        )
        .forEach { (city, point) ->
            if (placedLabels.count { it.kind == LabelKind.City } >= cityLimit) return@forEach
            val placement = cityLabelPlacement(
                text = city.name,
                point = point,
                paint = cityPaint,
                placedLabels = placedLabels,
                occupiedBoxes = occupiedBoxes,
                width = size.width,
                height = size.height,
                renderPadding = labelRenderPadding,
                normalXOffset = 5.dp.toPx(),
                normalYOffset = -5.dp.toPx()
            )
            if (placement != null) {
                drawCircle(color = dotColor, radius = 2.5.dp.toPx(), center = point)
                if (placement.pinned) {
                    drawLine(
                        color = dotColor.copy(alpha = 0.72f),
                        start = point,
                        end = placement.box.connectorPointToward(point),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawContext.canvas.nativeCanvas.drawText(city.name, placement.box.textX, placement.box.textY, cityPaint)
                placedLabels += placement.box
            }
        }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOfflineWorldLand(
    landRings: List<LandRing>,
    origin: Location,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    color: Color,
    outlineColor: Color
) {
    if (landRings.isEmpty()) return
    val latScale = 111_320.0
    val lonScale = (111_320.0 * cos(Math.toRadians(origin.latitude))).coerceAtLeast(1e-6)
    val renderPadding = 320.dp.toPx()
    landRings.forEach { ring ->
        val points = ring.pointsFor(metersPerPixel)
        if (points.size < 3) return@forEach
        val path = Path()
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        points.forEachIndexed { index, geoPoint ->
            val xMeters = (geoPoint.longitude - origin.longitude) * lonScale
            val yMeters = (geoPoint.latitude - origin.latitude) * latScale
            val x = center.x + pan.x + (xMeters / metersPerPixel).toFloat()
            val y = center.y + pan.y - (yMeters / metersPerPixel).toFloat()
            if (!x.isFinite() || !y.isFinite()) return@forEach
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        val intersectsViewport = maxX >= -renderPadding &&
            minX <= size.width + renderPadding &&
            maxY >= -renderPadding &&
            minY <= size.height + renderPadding
        if (!intersectsViewport) return@forEach

        path.close()
        drawPath(path = path, color = color)
        drawPath(path = path, color = outlineColor, style = Stroke(width = 1.dp.toPx()))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawContinentLabels(
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

private fun LandRing.pointsFor(metersPerPixel: Float): List<GeoPoint> {
    return when {
        metersPerPixel > 110f -> overviewPoints
        metersPerPixel > 50f -> balancedPoints
        else -> detailedPoints
    }
}

private fun List<GeoPoint>.simplifiedByStride(stride: Int): List<GeoPoint> {
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

private fun List<GeoPoint>.withContinuousLongitudes(): List<GeoPoint> {
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

private fun CityPoint.isVisibleAt(metersPerPixel: Float): Boolean {
    return when (importanceRank) {
        0 -> metersPerPixel <= firstCityLabelMetersPerPixel
        1 -> metersPerPixel <= secondCityLabelMetersPerPixel
        else -> metersPerPixel <= localCityLabelMetersPerPixel
    }
}

private fun CityPoint.preProjectionSampledAt(metersPerPixel: Float): Boolean {
    val step = preProjectionCitySampleStep(metersPerPixel)
    return step == 1 || importanceRank <= 1 || stableSampleIndex() % step == 0
}

private fun cityLimitForZoom(metersPerPixel: Float, crowdedCityLimit: Int): Int {
    return when {
        metersPerPixel > 7_200f -> 8
        metersPerPixel > 4_800f -> 14
        metersPerPixel > 2_400f -> 24
        metersPerPixel > 1_200f -> crowdedCityLimit.coerceAtMost(42)
        else -> crowdedCityLimit
    }
}

private fun mapLabelZoom(metersPerPixel: Float): Float {
    return log2((60_000f / metersPerPixel).coerceAtLeast(1f)) + 1f
}

private fun citySampleStep(metersPerPixel: Float): Int {
    return when {
        metersPerPixel > 2_400f -> 14
        metersPerPixel > 1_200f -> 8
        metersPerPixel > 600f -> 4
        metersPerPixel > 240f -> 2
        else -> 1
    }
}

private fun preProjectionCitySampleStep(metersPerPixel: Float): Int {
    return when {
        metersPerPixel > 2_400f -> 8
        metersPerPixel > 1_200f -> 5
        metersPerPixel > 600f -> 3
        metersPerPixel > 240f -> 2
        else -> 1
    }
}

private fun cityImportanceRank(name: String): Int {
    return when (name.trim().lowercase(Locale.ROOT)) {
        "new york", "los angeles", "chicago", "washington, d.c.", "washington", "toronto",
        "mexico city", "sao paulo", "são paulo", "rio de janeiro", "buenos aires", "lima", "bogota", "bogotá",
        "santiago", "london", "paris", "berlin", "madrid", "rome", "moscow", "istanbul",
        "cairo", "lagos", "nairobi", "johannesburg", "dubai", "riyadh", "tehran",
        "mumbai", "delhi", "new delhi", "karachi", "dhaka", "bangkok", "singapore",
        "jakarta", "beijing", "shanghai", "hong kong", "seoul", "tokyo", "osaka",
        "sydney", "melbourne" -> 0
        "san francisco", "miami", "houston", "atlanta", "boston", "montreal", "vancouver",
        "caracas", "quito", "la paz", "montevideo", "brasilia", "brasília", "recife", "salvador",
        "fortaleza", "manaus", "curitiba", "porto alegre", "lisbon", "amsterdam",
        "brussels", "vienna", "prague", "warsaw", "stockholm", "oslo", "copenhagen",
        "athens", "zurich", "dublin", "budapest", "bucharest", "kiev", "kyiv",
        "casablanca", "addis ababa", "accra", "dakar", "cape town", "doha", "kuwait",
        "jerusalem", "tel aviv", "baghdad", "tashkent", "kabul", "kolkata", "chennai",
        "bengaluru", "bangalore", "hyderabad", "lahore", "yangon", "hanoi", "ho chi minh city",
        "manila", "taipei", "guangzhou", "shenzhen", "tianjin", "wuhan", "chengdu",
        "busan", "kyoto", "auckland" -> 1
        else -> 2
    }
}

private fun CityPoint.stableSampleIndex(): Int {
    val coordinateHash = ((latitude * 10_000).roundToInt() * 31) xor (longitude * 10_000).roundToInt()
    return (name.hashCode() xor coordinateHash).absoluteValue
}

private fun mapLabelPoint(
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

private fun mapGeoPointAtScreen(
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

private fun mapGeoBoundsForViewport(
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

private fun titleForMapCenter(
    center: GeoPoint,
    viewport: GeoBounds,
    metersPerPixel: Float,
    countryLabels: List<CountryLabel>,
    cityPoints: List<CityPoint>,
    adminRegions: List<AdminRegion>
): String {
    if (isWholeEarthViewport(viewport, metersPerPixel)) {
        return "Terra"
    }
    val visibleCities = cityPoints.any { city ->
        viewport.contains(city.longitude, city.latitude, paddingDegrees = 0.35)
    }
    val dominantRegion = dominantAdminRegionName(
        center = center,
        viewport = viewport,
        regions = adminRegions
    )
    if (metersPerPixel > continentOnlyMetersPerPixel) {
        return dominantContinentName(viewport, center) ?: nearestContinentName(center) ?: "Terra"
    }
    if (metersPerPixel <= localCityLabelMetersPerPixel) {
        nearestCityName(center, cityPoints, maxDegrees = 2.8)?.let { return it }
    }
    if (metersPerPixel <= secondCityLabelMetersPerPixel) {
        nearestCityName(
            center = center,
            cityPoints = cityPoints.filter { it.importanceRank <= 1 },
            maxDegrees = 6.0
        )?.let { return it }
    }
    if (!visibleCities) {
        nearestAdminRegionNameFromNearestCity(center, cityPoints, adminRegions)?.let { return it }
    }
    dominantRegion?.let { return it }
    dominantAdminCountryName(
        center = center,
        viewport = viewport,
        regions = adminRegions
    )?.let { return it }
    return nearestAdminCountryName(center, adminRegions)
        ?: nearestCountryName(center, countryLabels)
        ?: nearestContinentName(center)
        ?: "Terra"
}

private fun dominantAdminRegionName(
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

private fun dominantAdminCountryName(
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

private fun nearestAdminCountryName(center: GeoPoint, regions: List<AdminRegion>): String? {
    val containingRegion = regions
        .filter { it.contains(center) && it.country.isNotBlank() }
        .minWithOrNull(compareBy<AdminRegion> { it.rank }.thenBy {
            approximateGeoDistanceScore(center.longitude, center.latitude, it.longitude, it.latitude)
        })
    if (containingRegion != null) return containingRegion.country
    return regions
        .filter { it.country.isNotBlank() }
        .minByOrNull {
            approximateGeoDistanceScore(center.longitude, center.latitude, it.longitude, it.latitude) *
                it.rank.coerceAtLeast(1)
        }
        ?.country
}

private fun nearestAdminRegionNameFromNearestCity(
    center: GeoPoint,
    cityPoints: List<CityPoint>,
    regions: List<AdminRegion>
): String? {
    val nearestCity = cityPoints.minByOrNull { city ->
        approximateGeoDistanceScore(center.longitude, center.latitude, city.longitude, city.latitude) *
            (city.importanceRank + 1)
    } ?: return null
    return regions
        .filter { it.contains(GeoPoint(nearestCity.longitude, nearestCity.latitude)) }
        .minWithOrNull(compareBy<AdminRegion> { it.rank }.thenBy {
            approximateGeoDistanceScore(nearestCity.longitude, nearestCity.latitude, it.longitude, it.latitude)
        })
        ?.name
}

private fun dominantContinentName(viewport: GeoBounds, center: GeoPoint): String? {
    val visibleContinents = continentLabels
        .filter { viewport.contains(it.longitude, it.latitude, paddingDegrees = 12.0) }
    if (visibleContinents.isEmpty()) return null
    return visibleContinents.minByOrNull { continent ->
        approximateGeoDistanceScore(center.longitude, center.latitude, continent.longitude, continent.latitude)
    }?.name
}

private fun nearestContinentName(center: GeoPoint): String? {
    return continentLabels.minByOrNull { label ->
        approximateGeoDistanceScore(
            center.longitude,
            center.latitude,
            label.longitude,
            label.latitude
        )
    }?.name
}

private fun nearestCountryName(center: GeoPoint, countryLabels: List<CountryLabel>): String? {
    return countryLabels.minByOrNull { country ->
        approximateGeoDistanceScore(
            center.longitude,
            center.latitude,
            country.longitude,
            country.latitude
        ) * country.rank.coerceAtLeast(1)
    }?.name
}

private fun nearestCityName(
    center: GeoPoint,
    cityPoints: List<CityPoint>,
    maxDegrees: Double
): String? {
    return cityPoints
        .asSequence()
        .map { city ->
            city to approximateGeoDistanceScore(center.longitude, center.latitude, city.longitude, city.latitude)
        }
        .filter { (_, score) -> score <= maxDegrees * maxDegrees }
        .minWithOrNull(
            compareBy<Pair<CityPoint, Double>> { it.first.importanceRank }
                .thenBy { it.second }
        )
        ?.first
        ?.name
}

private fun approximateGeoDistanceScore(
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

private fun isWholeEarthViewport(viewport: GeoBounds, metersPerPixel: Float): Boolean {
    return metersPerPixel >= 22_000f ||
        (viewport.longitudeSpan() >= 260.0 && viewport.latitudeSpan() >= 120.0)
}

private fun normalizeLongitude(longitude: Double): Double {
    var normalized = longitude
    while (normalized > 180.0) normalized -= 360.0
    while (normalized < -180.0) normalized += 360.0
    return normalized
}

private fun labelBox(
    text: String,
    point: Offset,
    paint: Paint,
    kind: LabelKind,
    xOffset: Float,
    yOffset: Float
): LabelBox {
    val width = paint.measureText(text)
    val height = paint.textSize
        val textX = point.x + xOffset - if (kind == LabelKind.Country || kind == LabelKind.Continent) width / 2f else 0f
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

private fun cityLabelPlacement(
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

private fun cityPinCandidateOffsets(textWidth: Float, textHeight: Float): List<Offset> {
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

private fun centeredCityLabelBox(
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

private fun LabelBox.connectorPointToward(point: Offset): Offset {
    return Offset(
        x = point.x.coerceIn(left, right),
        y = point.y.coerceIn(top, bottom)
    )
}

private fun Offset.isNearViewport(width: Float, height: Float, padding: Float): Boolean {
    return x >= -padding && x <= width + padding && y >= -padding && y <= height + padding
}

private fun LabelBox.isInsideRenderBand(width: Float, height: Float, padding: Float): Boolean {
    return right >= -padding && left <= width + padding && bottom >= -padding && top <= height + padding
}

private fun isLabelPlaceable(
    candidate: LabelBox,
    placedLabels: List<LabelBox>,
    occupiedBoxes: List<LabelBox>,
    padding: Float
): Boolean {
    if (placedLabels.any { it.intersects(candidate, padding = padding) }) return false
    if (occupiedBoxes.any { it.intersects(candidate, padding = padding) }) return false
    return true
}

private fun LabelBox.intersects(other: LabelBox, padding: Float): Boolean {
    return left - padding < other.right &&
        right + padding > other.left &&
        top - padding < other.bottom &&
        bottom + padding > other.top
}

private fun buildUserOccupationBoxes(
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

private suspend fun PointerInputScope.detectStableMapTransformGestures(
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
        var lockedToPinchUntilRelease = false
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
                if (lockedToPinchUntilRelease) {
                    previousSinglePointer = null
                    previousCentroid = null
                    previousSpan = null
                    tapStartPosition = null
                    latestSinglePointer = null
                    hadMultiplePointers = true
                    pressedChanges.forEach { it.consume() }
                    continue
                }
                val pointer = pressedChanges.first()
                val currentPosition = pointer.position
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

            lockedToPinchUntilRelease = true
            hadMultiplePointers = true
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

private fun wrapHorizontalMapPan(panX: Float, worldWidthPx: Float): Float {
    if (worldWidthPx <= 0f) return panX
    var wrapped = panX % worldWidthPx
    val halfWorld = worldWidthPx / 2f
    if (wrapped > halfWorld) wrapped -= worldWidthPx
    if (wrapped < -halfWorld) wrapped += worldWidthPx
    return wrapped
}

private fun horizontalWorldCopyOffsets(width: Float, worldWidthPx: Float, panX: Float): List<Float> {
    if (worldWidthPx <= 0f) return listOf(0f)
    val halfWorld = worldWidthPx / 2f
    val halfViewport = width / 2f
    return buildList {
        if (-halfViewport - panX < -halfWorld) add(-worldWidthPx)
        add(0f)
        if (halfViewport - panX > halfWorld) add(worldWidthPx)
    }
}

private fun clampVerticalMapPan(
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

private fun wrappedLongitudeDelta(originLongitude: Double, targetLongitude: Double): Double {
    var delta = targetLongitude - originLongitude
    while (delta > 180.0) delta -= 360.0
    while (delta < -180.0) delta += 360.0
    return delta
}

private fun offlineMapPalette(useDarkMapColors: Boolean): OfflineMapPalette {
    return if (useDarkMapColors) {
        OfflineMapPalette(
            ocean = Color(0xFF071B33),
            land = Color(0xFF153D2A).copy(alpha = 0.84f),
            grid = Color.White.copy(alpha = 0.10f),
            cityLabel = Color(0xFFD8EBF8),
            cityDot = Color(0xFF8FCBFF),
            pointer = Color(0xFF9FD3FF)
        )
    } else {
        OfflineMapPalette(
            ocean = Color(0xFFD8F0FF),
            land = Color(0xFFE7F3D4).copy(alpha = 0.92f),
            grid = Color(0xFF5A91B8).copy(alpha = 0.16f),
            cityLabel = Color(0xFF17425F),
            cityDot = Color(0xFF1976A8),
            pointer = Color(0xFF1976A8).copy(alpha = 0.74f)
        )
    }
}

private fun separateNearbyUsers(
    users: List<OrbitUser>,
    center: Offset,
    pan: Offset,
    metersPerPixel: Float,
    worldWidthPx: Float,
    width: Float,
    height: Float
): List<PositionedOrbitUser> {
    if (users.isEmpty()) return emptyList()
    val clusteredAnchorDistance = 118f
    val anchoredUsers = users
        .sortedBy { it.distanceMeters }
        .map { user ->
            AnchoredOrbitUser(
                user = user,
                anchor = user.screenPoint(center, pan, metersPerPixel, worldWidthPx)
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

private fun stablePinSeed(value: String): Int {
    return value.hashCode().absoluteValue
}

private fun List<AnchoredOrbitUser>.maxAnchorDistance(): Float {
    var maxDistance = 0f
    forEachIndexed { index, user ->
        for (otherIndex in index + 1 until size) {
            val distance = user.anchor.distanceTo(this[otherIndex].anchor)
            if (distance > maxDistance) maxDistance = distance
        }
    }
    return maxDistance
}

private fun bestSeparatedPoint(
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

private fun buildOrbitUserClusters(
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

private fun List<Offset>.averageOffset(): Offset {
    if (isEmpty()) return Offset.Zero
    return Offset(
        x = sumOf { it.x.toDouble() }.toFloat() / size,
        y = sumOf { it.y.toDouble() }.toFloat() / size
    )
}

private fun Offset.coerceInside(width: Float, height: Float, radius: Float): Offset {
    return Offset(
        x = x.coerceIn(radius, (width - radius).coerceAtLeast(radius)),
        y = y.coerceIn(radius, (height - radius).coerceAtLeast(radius))
    )
}

private fun Offset.distanceTo(other: Offset): Float {
    return hypot(x - other.x, y - other.y)
}

private data class AnchoredOrbitUser(
    val user: OrbitUser,
    val anchor: Offset
)

private data class PositionedOrbitUser(
    val user: OrbitUser,
    val anchor: Offset,
    val display: Offset,
    val needsPointer: Boolean,
    val label: String?
)

private data class OfflineMapPalette(
    val ocean: Color,
    val land: Color,
    val grid: Color,
    val cityLabel: Color,
    val cityDot: Color,
    val pointer: Color
)

private val mapProfileColorOptions = listOf(
    Color(0xFF6750A4),
    Color(0xFF006A6A),
    Color(0xFFB3261E),
    Color(0xFF386A20),
    Color(0xFF7D5260),
    Color(0xFF005FAF)
)

private fun readableOnColor(color: Color): Color {
    return if (color.luminance() > 0.5f) Color.Black else Color.White
}

private const val worldCircumferenceMeters = 40_075_000f
private const val worldHeightMeters = 20_037_500f
private const val continentOnlyMetersPerPixel = 12_500f
private const val firstCityLabelMetersPerPixel = 10_500f
private const val secondCityLabelMetersPerPixel = 4_800f
private const val localCityLabelMetersPerPixel = 1_150f

private val continentLabels = listOf(
    ContinentLabel("América do Norte", -101.0, 52.0),
    ContinentLabel("América do Sul", -60.0, -17.0),
    ContinentLabel("Europa", 15.0, 52.0),
    ContinentLabel("África", 20.0, 3.0),
    ContinentLabel("Ásia", 88.0, 39.0),
    ContinentLabel("Oceania", 135.0, -25.0),
    ContinentLabel("Antártida", 20.0, -78.0)
)

private data class LandRing(
    val detailedPoints: List<GeoPoint>,
    val balancedPoints: List<GeoPoint>,
    val overviewPoints: List<GeoPoint>
)

private data class CountryLabel(
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val rank: Int,
    val population: Long,
    val minZoom: Float,
    val maxZoom: Float
)

private data class CityPoint(
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val importanceRank: Int
)

private data class AdminRegion(
    val name: String,
    val country: String,
    val longitude: Double,
    val latitude: Double,
    val minLongitude: Double,
    val minLatitude: Double,
    val maxLongitude: Double,
    val maxLatitude: Double,
    val rank: Int
) {
    fun contains(point: GeoPoint): Boolean {
        return point.longitude in minLongitude..maxLongitude &&
            point.latitude in minLatitude..maxLatitude
    }

    fun intersectionArea(viewport: GeoBounds): Double {
        val left = maxOf(minLongitude, viewport.minLongitude)
        val right = minOf(maxLongitude, viewport.maxLongitude)
        val bottom = maxOf(minLatitude, viewport.minLatitude)
        val top = minOf(maxLatitude, viewport.maxLatitude)
        if (right <= left || top <= bottom) return 0.0
        return (right - left) * (top - bottom)
    }
}

private data class GeoBounds(
    val minLongitude: Double,
    val minLatitude: Double,
    val maxLongitude: Double,
    val maxLatitude: Double
) {
    fun area(): Double {
        val width = (maxLongitude - minLongitude).coerceAtLeast(0.0)
        val height = (maxLatitude - minLatitude).coerceAtLeast(0.0)
        return width * height
    }

    fun longitudeSpan(): Double {
        return (maxLongitude - minLongitude).coerceAtLeast(0.0)
    }

    fun latitudeSpan(): Double {
        return (maxLatitude - minLatitude).coerceAtLeast(0.0)
    }

    fun contains(longitude: Double, latitude: Double, paddingDegrees: Double = 0.0): Boolean {
        return longitude >= minLongitude - paddingDegrees &&
            longitude <= maxLongitude + paddingDegrees &&
            latitude >= minLatitude - paddingDegrees &&
            latitude <= maxLatitude + paddingDegrees
    }
}

private enum class LabelKind {
    Continent,
    Country,
    City
}

private data class LabelBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val textX: Float,
    val textY: Float,
    val kind: LabelKind
)

private data class CityLabelPlacement(
    val box: LabelBox,
    val pinned: Boolean
)

private data class ContinentLabel(
    val name: String,
    val longitude: Double,
    val latitude: Double
)

private data class GeoPoint(
    val longitude: Double,
    val latitude: Double
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

private fun Offset.isInside(width: Float, height: Float, padding: Float): Boolean {
    return x >= padding && x <= width - padding && y >= padding && y <= height - padding
}

private fun buildOwnPublicMapUser(
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

private fun String.normalizedRouteKey(): String {
    return trim().lowercase(Locale.ROOT)
}

private fun mapOffsetMeters(origin: Location, target: Location): Offset {
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

private fun formatElapsedAgo(timestamp: Long): String {
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

private fun genericMapOrigin(): Location {
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
