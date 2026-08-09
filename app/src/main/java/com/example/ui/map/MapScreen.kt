package com.example.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.example.domain.model.GeoBounds
import com.example.ui.map.fog.FogOverlay
import com.example.ui.map.fog.FogTileProvider
import com.example.ui.map.grid.GridLod
import com.example.ui.map.grid.HexGridGeometry
import com.example.ui.map.grid.HexGridOverlay
import com.example.ui.map.grid.HexGridStyle
import com.example.ui.map.theme.MapTheme
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.io.File
import java.util.Locale

/**
 * The map tab.
 *
 * @param bottomInset how much of the bottom of the screen is covered by chrome the map does not own
 * (the app's bottom bar). The map itself still draws edge to edge underneath it; this only lifts the
 * floating controls clear so they stay tappable.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: GameViewModel,
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    // Məkan icazələrini istəmək və yoxlamaq üçün MultiplePermissionsState istifadə edirik.
    // ACTIVITY_RECOGNITION (addım sayğacı üçün, API 29+) və POST_NOTIFICATIONS (arxa plan
    // xidməti bildirişi üçün, API 33+) eyni sorğu ilə birlikdə istənilir.
    val permissionState = rememberMultiplePermissionsState(
        permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    )

    // Əgər hər hansı bir məkan icazəsi verilibsə (Fine və ya Coarse), bu dəyişən true olacaq
    val anyLocationGranted = permissionState.permissions.any {
        it.permission in setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) &&
            it.status.isGranted
    }

    // Addım sayğacı icazəsi (pre-Q cihazlarda runtime icazəsi tələb olunmur)
    val stepPermissionGranted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
        permissionState.permissions.any { it.permission == Manifest.permission.ACTIVITY_RECOGNITION && it.status.isGranted }

    // Addım sayğacı icazəsi verildikdə arxa planda işləyən foreground xidməti başladırıq.
    // Bu, Activity-nin lifecycle-ından asılı deyil - tətbiq arxa plana keçəndə belə davam edir.
    LaunchedEffect(stepPermissionGranted) {
        if (stepPermissionGranted) {
            viewModel.onStepPermissionGranted()
        }
    }

    if (anyLocationGranted) {
        // Məkan izlənməsi LocationTrackingService (foreground service) üzərindən aparılır, ona
        // görə tətbiq arxa plana keçəndə və ya ekran kilidlənəndə belə davam edir - bu səbəbdən
        // burada Activity-nin ON_PAUSE/ON_RESUME hadisələrinə görə dayandırıb-başlatmırıq.
        // Yalnız bu ekran kompozisiyadan tamamilə silinəndə (məs. çıxış zamanı) izləməni dayandırırıq.
        DisposableEffect(anyLocationGranted) {
            if (anyLocationGranted) {
                viewModel.startTracking()
            }

            onDispose {
                viewModel.stopTracking()
            }
        }

        GameMapContent(
            viewModel = viewModel,
            bottomInset = bottomInset,
            modifier = modifier
        )
    } else {
        PermissionOnboardingScreen(
            onRequestPermission = {
                permissionState.launchMultiplePermissionRequest()
            }
        )
    }
}

@Composable
fun PermissionOnboardingScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1A1B),
                        Color(0xFF142D2D),
                        Color(0xFF0C1414)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Elegant Icon Wrapper
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color(0xFF1C3D3A), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PinDrop,
                    contentDescription = "Map Pin",
                    tint = Color(0xFF5DF2D6),
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "STOMPED",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.testTag("app_title")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Stomp your territory. Map your footsteps.",
                color = Color(0xFF98BCB6),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF162B28)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Stomped uses real-time high-accuracy GPS to map out a hexagonal grid around you. As you walk, the app fills the hexagons to mark them as claimed.",
                        color = Color(0xFFE2EFEA),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5DF2D6),
                    contentColor = Color(0xFF0A1F1C)
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .height(56.dp)
                    .fillMaxWidth(0.8f)
                    .testTag("grant_permission_button")
            ) {
                Text(
                    text = "GRANT LOCATION ACCESS",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun GameMapContent(
    viewModel: GameViewModel,
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val activeNeighborhood by viewModel.activeNeighborhood.collectAsStateWithLifecycle()
    val stompPercentage by viewModel.stompPercentage.collectAsStateWithLifecycle()
    val errorMsg by viewModel.errorMessage.collectAsStateWithLifecycle()
    val travelingByVehicle by viewModel.travelingByVehicle.collectAsStateWithLifecycle()
    val stompedHexes by viewModel.stompedHexes.collectAsStateWithLifecycle()
    val exploredIndex by viewModel.exploredIndex.collectAsStateWithLifecycle()

    val mapTheme = MapTheme.DEFAULT

    // How far the floating controls sit above the bottom of the screen. Whichever is larger of the
    // app's bottom bar and the gesture-navigation inset - they overlap rather than stack, so adding
    // them would leave the controls floating in the middle of the map when a bar is present.
    val controlsLift = maxOf(
        bottomInset,
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )

    // Map control state
    var mapHasCentered by remember { mutableStateOf(false) }
    var triggerRecenter by remember { mutableStateOf(false) }
    val mapCameraState = remember { mutableStateOf<MapCamera?>(null) }

    // The grid's geometry, rebuilt off the main thread (see the LaunchedEffect below) rather than
    // inside AndroidView's update callback, so panning never blocks a frame on grid math.
    var gridGeometry by remember { mutableStateOf(HexGridGeometry.EMPTY) }

    // Remember the osmdroid MapView to avoid re-creation
    val mapView = remember {
        MapView(context).apply {
            // Theme first, before the zoom below or any camera move: applyTo paints the dark
            // background immediately, so the map is never briefly white while the first tiles load.
            // This is osmdroid's equivalent of styling inside onMapReady.
            mapTheme.applyTo(this)

            // Touch handling: drag to pan, pinch to zoom, double-tap to zoom in. All of it comes
            // from osmdroid's own multi-touch controller, so the map responds to fingers directly
            // rather than only to the on-screen buttons - those stay as a one-handed fallback.
            setMultiTouchControls(true)
            isFlingEnabled = true
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)

            // Bounds on what a pinch can reach. Without them a two-finger flick lands on the whole
            // globe, where the game has nothing to show (the grid only covers 100 km around the
            // player), or past the tile source's deepest level, where the map is just blur.
            setMinZoomLevel(MIN_ZOOM)
            setMaxZoomLevel(MAX_ZOOM)
            controller.setZoom(RECENTER_ZOOM)

            // Set User Agent as required by OSM terms of service
            Configuration.getInstance().userAgentValue = context.packageName
            Configuration.getInstance().osmdroidTileCache = File(context.cacheDir, "osmdroid")
        }
    }

    // Fog of war. The provider owns the tile cache and bitmap pool; the overlay only draws.
    val fogProvider = remember(mapTheme) {
        FogTileProvider(fogArgb = mapTheme.palette.fogArgb)
    }
    val fogOverlay = remember(fogProvider) { FogOverlay(fogProvider) }

    // The hex grid. One overlay for the whole grid rather than one per cell - see HexGridOverlay for
    // why that is what makes claimed ground read as a single territory instead of a honeycomb.
    val hexGridOverlay = remember(mapTheme, density) {
        HexGridOverlay(style = HexGridStyle.forTheme(mapTheme), density = density)
    }

    // Rebuild the fog whenever the explored set changes. Bumping the version invalidates the tile
    // cache, so only the tiles that are actually redrawn cost anything.
    LaunchedEffect(exploredIndex) {
        fogProvider.setExploredCells(exploredIndex)
        mapView.invalidate()
    }

    // Newly revealed cells fade in over 300 ms. osmdroid has no animation loop of its own, so the
    // map is invalidated once per frame for as long as a reveal is running - and not a frame longer.
    LaunchedEffect(fogOverlay) {
        viewModel.newlyExplored.collect { revealed ->
            fogOverlay.revealCells(revealed)
            while (fogOverlay.isAnimating) {
                withFrameNanos { }
                mapView.invalidate()
            }
        }
    }

    // User location marker: created once and repositioned in place on each GPS update, instead
    // of being torn down and rebuilt (including a fresh bitmap) every few seconds.
    val userMarker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            infoWindow = null
            icon = buildUserLocationIcon(context)
        }
    }

    // Connect lifecycle to osmdroid map to prevent memory leaks and handle tile loading
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Every way the camera can move funnels into this one signal - dragging, pinching, flinging,
    // twisting, and the on-screen buttons. osmdroid fires those many times per second during a
    // gesture; coalescing bursts into a single signal ~120 ms after movement settles (rather than
    // recomputing the grid on every event) is what keeps a gesture smooth.
    val movementSignal = remember {
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    // Two-finger twist to rotate. The rotate buttons do the same thing in 45 degree steps, for when
    // the player only has one hand free.
    //
    // Rotation has to feed the movement signal like any other camera move: osmdroid reports scroll
    // and zoom through MapListener but says nothing about orientation, and a turned viewport covers
    // ground the un-rotated bounding box never included - without this the grid would stop short in
    // the corners as the map turns.
    val rotationOverlay = remember(mapView) {
        RotationGestures(mapView) { movementSignal.tryEmit(Unit) }.apply { isEnabled = true }
    }

    LaunchedEffect(mapView) {
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                movementSignal.tryEmit(Unit)
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                movementSignal.tryEmit(Unit)
                return true
            }
        })

        movementSignal.debounce(120).collect {
            mapCameraState.value = mapView.currentCamera()
        }
    }

    // Recenter map logic
    LaunchedEffect(currentLocation, triggerRecenter, mapHasCentered) {
        val loc = currentLocation
        if (loc != null) {
            val geoPoint = GeoPoint(loc.latitude, loc.longitude)
            if (!mapHasCentered || triggerRecenter) {
                mapView.controller.animateTo(geoPoint)
                mapView.controller.setZoom(18.0)
                // The camera the animation is heading for, not the one it is leaving: animateTo is
                // asynchronous, so reading the map back here would rebuild the grid for the old view.
                mapCameraState.value = MapCamera(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    zoom = RECENTER_ZOOM,
                    orientation = mapView.mapOrientation
                )
                mapHasCentered = true
                triggerRecenter = false
            }
        }
    }

    // Rebuild the grid whenever the (debounced) camera moves or the explored set changes. Both the
    // grid-engine query and the edge reduction are CPU-bound, so they run on Dispatchers.Default -
    // only the finished geometry gets handed back to Compose/UI.
    LaunchedEffect(mapCameraState.value, exploredIndex) {
        val camera = mapCameraState.value ?: return@LaunchedEffect
        val lod = GridLod.forZoom(camera.zoom)

        val visibleBounds = mapView.boundingBox
        val bounds = GeoBounds(
            north = visibleBounds.latNorth,
            south = visibleBounds.latSouth,
            east = visibleBounds.lonEast,
            west = visibleBounds.lonWest
        )

        gridGeometry = withContext(Dispatchers.Default) {
            // Everything is built for a margin around the viewport rather than the viewport itself:
            // a cell whose neighbour is just off-screen owns the edge between them, so clipping to
            // the screen would draw the territory's border along the edge of the display. It also
            // means a short pan lands on grid that has already been built.
            val margin = bounds.viewportMargin()
            val area = bounds.expand(margin.first, margin.second)

            // Claimed ground comes from the fog layer's index, which already holds every explored
            // cell's corners - so territory costs no grid-engine work at all.
            val explored = exploredIndex.query(area)

            // The honeycomb over unwalked ground is only fetched when it will actually be drawn.
            // Below GridLod.MIN_EMPTY_GRID_ZOOM a viewport holds tens of thousands of ~40 m cells,
            // and neither computing nor drawing them tells the player anything they can see.
            val emptyCells = if (lod.drawsEmptyCells) {
                viewModel.getGridCellsInBounds(area.toCorners())
            } else {
                emptyList()
            }

            HexGridGeometry.forViewport(explored = explored, emptyCells = emptyCells, lod = lod)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Real-time Map Renderer
        AndroidView(
            factory = { mapView },
            // The map holds osmdroid's own tile caches plus the fog layer's bitmap pool - up to a
            // few tens of megabytes. Detaching on release hands all of it back instead of leaving
            // it to be collected some time after the next configuration change.
            onRelease = { view -> view.onDetach() },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Only cheap per-recomposition work here: hand the grid overlay its latest (already
                // reduced) geometry and reposition the marker. No grid math, and no overlay churn -
                // the same three overlay objects live for the whole screen.
                //
                // The order of this list *is* the z-index in osmdroid. Fog goes on first, directly
                // above the base tiles; the hex grid and the user marker are both added after it so
                // neither is ever dimmed by the fog they sit over.
                hexGridOverlay.geometry = gridGeometry

                view.overlays.clear()
                view.overlays.add(fogOverlay)
                view.overlays.add(hexGridOverlay)

                currentLocation?.let { loc ->
                    userMarker.position = GeoPoint(loc.latitude, loc.longitude)
                    view.overlays.add(userMarker)
                }

                // Last, so it sees touch events first: osmdroid offers them to overlays in reverse
                // order, and a twist starting on top of the marker still has to rotate the map.
                view.overlays.add(rotationOverlay)

                view.invalidate() // refresh map layout
            }
        )

        // Overlay: waiting for the first GPS fix
        AnimatedVisibility(
            visible = currentLocation == null && errorMsg == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .testTag("waiting_for_location_indicator")
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xE60A1F1C)),
                border = BorderStroke(1.dp, Color(0xFF26524D))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color(0xFF5DF2D6))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "GPS siqnalı axtarılır...",
                        color = Color(0xFFE2EFEA),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Overlay: Top Pill Neighborhood Card
        AnimatedVisibility(
            visible = activeNeighborhood != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 20.dp, end = 20.dp)
                .testTag("neighborhood_pill_card")
        ) {
            activeNeighborhood?.let { active ->
                Card(
                    shape = RoundedCornerShape(100),
                    colors = CardDefaults.cardColors(containerColor = Color(0xE60A1F1C)),
                    border = BorderStroke(1.dp, Color(0xFF26524D)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .widthIn(max = 450.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Soft pulsing glowing green indicator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF5DF2D6), CircleShape)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "${active.name}  ·  ",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )

                        Text(
                            text = String.format(Locale.US, "%.2f%%", stompPercentage),
                            color = Color(0xFF5DF2D6),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif
                        )

                        Text(
                            text = " stomped",
                            color = Color(0xFF98BCB6),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }

        // Overlay: "you are riding, not walking" notice. Claiming stops above walking pace, and a
        // player watching the map stay grey through a whole bus ride deserves to be told why rather
        // than left wondering whether the app has stopped working.
        AnimatedVisibility(
            visible = travelingByVehicle && errorMsg == null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = controlsLift + 96.dp, start = 24.dp, end = 24.dp)
                .testTag("vehicle_mode_notice")
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xE60A1F1C)),
                border = BorderStroke(1.dp, Color(0xFF26524D)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color(0xFF5DF2D6)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Nəqliyyatdasınız - xanalar yalnız piyada gedəndə tutulur",
                        color = Color(0xFFE2EFEA),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Overlay: Error message Toast/Bar
        AnimatedVisibility(
            visible = errorMsg != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = controlsLift + 96.dp, start = 24.dp, end = 24.dp)
        ) {
            errorMsg?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF8C1D1D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = msg, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        // Floating Control Panel at Bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = controlsLift + 16.dp, start = 24.dp, end = 24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Map Rotation Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(
                        onClick = {
                            mapView.mapOrientation = mapView.mapOrientation - 45f
                            mapView.invalidate()
                            movementSignal.tryEmit(Unit)
                        },
                        containerColor = Color(0xCC0C1E1B),
                        contentColor = Color(0xFF5DF2D6),
                        shape = CircleShape,
                        modifier = Modifier.testTag("rotate_left_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateLeft,
                            contentDescription = "Xəritəni sola çevir",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            mapView.mapOrientation = mapView.mapOrientation + 45f
                            mapView.invalidate()
                            movementSignal.tryEmit(Unit)
                        },
                        containerColor = Color(0xCC0C1E1B),
                        contentColor = Color(0xFF5DF2D6),
                        shape = CircleShape,
                        modifier = Modifier.testTag("rotate_right_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Xəritəni sağa çevir",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Recenter Live Location Button (Main FAB)
                FloatingActionButton(
                    onClick = { triggerRecenter = true },
                    containerColor = Color(0xFF5DF2D6),
                    contentColor = Color(0xFF0A1F1C),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("recenter_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Recenter Map",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Floating Zoom Controls (Yaxınlaşdırmaq / Uzaqlaşdırmaq) on the right
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom In (+)
            FloatingActionButton(
                onClick = {
                    // zoomIn animates, where setZoom jumps - so the button lands the map the same
                    // way a pinch does. The camera state follows from the zoom event it fires.
                    mapView.controller.zoomIn()
                },
                containerColor = Color(0xE60A1F1C),
                contentColor = Color(0xFF5DF2D6),
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("zoom_in_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Yaxınlaşdır",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Zoom Out (-)
            FloatingActionButton(
                onClick = { mapView.controller.zoomOut() },
                containerColor = Color(0xE60A1F1C),
                contentColor = Color(0xFF5DF2D6),
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("zoom_out_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Uzaqlaşdır",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

    }
}

/**
 * Where the map is looking: what the grid has to be rebuilt for when any of it changes.
 *
 * Orientation counts as a camera move even though it leaves the centre alone - a turned viewport
 * reaches ground that the upright bounding box did not cover.
 */
private data class MapCamera(
    val lat: Double,
    val lng: Double,
    val zoom: Double,
    val orientation: Float
)

/** The zoom a recenter returns to - close enough that the individual grid cells read as cells. */
private const val RECENTER_ZOOM = 18.0

/** How far out a pinch may go: the grid only covers 100 km, so there is nothing beyond this. */
private const val MIN_ZOOM = 5.0

/** How far in: the tile source has no detail past this, so deeper is blur with no new information. */
private const val MAX_ZOOM = 19.0

private fun MapView.currentCamera(): MapCamera =
    MapCamera(mapCenter.latitude, mapCenter.longitude, zoomLevelDouble, mapOrientation)

/**
 * osmdroid's two-finger rotation, with a hook for reporting it.
 *
 * The stock overlay turns the map and tells nobody; [onRotated] is what lets the grid follow the
 * viewport round. Called continuously through a twist, so the callback must be cheap - it feeds the
 * same debounced signal every other camera move goes through.
 */
private class RotationGestures(
    mapView: MapView,
    private val onRotated: () -> Unit
) : RotationGestureOverlay(mapView) {
    override fun onRotate(deltaAngle: Float) {
        super.onRotate(deltaAngle)
        onRotated()
    }
}

/**
 * How far past the viewport the explored set is queried, as a fraction of the viewport's own size.
 *
 * Without it the region's outline would be drawn along the edge of the screen, because a cell whose
 * neighbour is just off-screen looks like a boundary cell to [com.example.ui.map.grid.HexEdges].
 */
private const val VIEWPORT_MARGIN_FRACTION = 0.35

private fun GeoBounds.viewportMargin(): Pair<Double, Double> =
    ((north - south) * VIEWPORT_MARGIN_FRACTION) to ((east - west) * VIEWPORT_MARGIN_FRACTION)

/**
 * The blue "you are here" dot: a white-rimmed disc inside a soft halo of the same blue.
 *
 * Sized in dp rather than raw pixels - the old fixed 48 px bitmap shrank to a speck on a high
 * density screen, which is exactly where the player most needs to find themselves on the map.
 */
private fun buildUserLocationIcon(context: android.content.Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = Math.round(DOT_TOTAL_DP * density).coerceAtLeast(24)
    val center = size / 2f

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Halo: separates the dot from whatever it is standing on, so it stays findable over both the
    // pale basemap and the teal wash of claimed ground.
    paint.color = 0x33007AFF
    canvas.drawCircle(center, center, center, paint)

    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, DOT_CORE_DP / DOT_TOTAL_DP * center, paint)

    paint.color = 0xFF007AFF.toInt()
    canvas.drawCircle(center, center, (DOT_CORE_DP - DOT_RIM_DP * 2f) / DOT_TOTAL_DP * center, paint)

    return BitmapDrawable(context.resources, bitmap)
}

private const val DOT_TOTAL_DP = 26f
private const val DOT_CORE_DP = 18f
private const val DOT_RIM_DP = 2.5f
