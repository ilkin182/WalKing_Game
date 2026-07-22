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
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.example.ui.profile.ProfileScreen
import com.example.ui.util.LocalWindowWidthSizeClass
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: GameViewModel,
    onLogout: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {},
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
        val lifecycleOwner = LocalLifecycleOwner.current

        // Tətbiqin həyat dövrünü (Lifecycle) izləyirik ki, arxa plana keçəndə izləmə dayansın
        DisposableEffect(lifecycleOwner, anyLocationGranted) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        // Tətbiq ön plana keçdikdə izlənməni başladırıq
                        if (anyLocationGranted) {
                            viewModel.startTracking()
                        }
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        // Tətbiq arxa plana keçdikdə izlənməni dayandırırıq (batareya üçün)
                        viewModel.stopTracking()
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            // Əgər ekran artıq aktivdirsə (RESUMED), dərhal izləməyə başlayırıq
            if (anyLocationGranted && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                viewModel.startTracking()
            }

            onDispose {
                // Komponent silinəndə observer-i və izləməni təmizləyirik
                lifecycleOwner.lifecycle.removeObserver(observer)
                viewModel.stopTracking()
            }
        }

        GameMapContent(
            viewModel = viewModel,
            onLogout = onLogout,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
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
@Composable
fun GameMapContent(
    viewModel: GameViewModel,
    onLogout: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val activeNeighborhood by viewModel.activeNeighborhood.collectAsStateWithLifecycle()
    val stompPercentage by viewModel.stompPercentage.collectAsStateWithLifecycle()
    val errorMsg by viewModel.errorMessage.collectAsStateWithLifecycle()

    // Map control state
    var mapHasCentered by remember { mutableStateOf(false) }
    var triggerRecenter by remember { mutableStateOf(false) }
    val mapCenterState = remember { mutableStateOf<GeoPoint?>(null) }
    var showProfileScreen by remember { mutableStateOf(false) }

    // Remember the osmdroid MapView to avoid re-creation
    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(18.0)

            // Set User Agent as required by OSM terms of service
            Configuration.getInstance().userAgentValue = context.packageName
            Configuration.getInstance().osmdroidTileCache = File(context.cacheDir, "osmdroid")
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

    // Set up Map Panning / Zoom Listener to dynamically load the grid cells centered where the map is looking
    LaunchedEffect(mapView) {
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                val center = mapView.mapCenter
                mapCenterState.value = GeoPoint(center.latitude, center.longitude)
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                val center = mapView.mapCenter
                mapCenterState.value = GeoPoint(center.latitude, center.longitude)
                return true
            }
        })
    }

    // Recenter map logic
    LaunchedEffect(currentLocation, triggerRecenter, mapHasCentered) {
        val loc = currentLocation
        if (loc != null) {
            val geoPoint = GeoPoint(loc.latitude, loc.longitude)
            if (!mapHasCentered || triggerRecenter) {
                mapView.controller.animateTo(geoPoint)
                mapView.controller.setZoom(18.0)
                mapCenterState.value = geoPoint
                mapHasCentered = true
                triggerRecenter = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Real-time Map Renderer
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val center = mapCenterState.value ?: currentLocation?.let { GeoPoint(it.latitude, it.longitude) }
                if (center != null) {
                    // 1. Fetch grid cells dynamically surrounding the map center
                    val gridCells = viewModel.getGridCellsAround(center.latitude, center.longitude, radiusSteps = 8)

                    // Clear old overlays completely to avoid duplicates/stretching
                    view.overlays.clear()

                    // 2. Draw each Hexagon polygon
                    for (cell in gridCells) {
                        val osmPolygon = Polygon(view).apply {
                            val pts = cell.corners.map { GeoPoint(it.lat, it.lng) }.toMutableList()
                            if (pts.isNotEmpty()) {
                                pts.add(pts.first()) // Close the polygon
                            }
                            points = pts

                            if (cell.isStomped) {
                                // Semi-transparent neon teal color and glowing solid neon teal border
                                fillPaint.color = android.graphics.Color.parseColor("#4D5DF2D6") // ~30% opacity glowing teal
                                fillPaint.style = Paint.Style.FILL
                                outlinePaint.color = android.graphics.Color.parseColor("#FF159980") // Darker teal border
                                outlinePaint.strokeWidth = 4.0f
                                outlinePaint.style = Paint.Style.STROKE
                            } else {
                                // Unstomped Honeycomb Hexagon: elegant clean grid lines
                                fillPaint.color = android.graphics.Color.TRANSPARENT
                                outlinePaint.color = android.graphics.Color.parseColor("#555A7B74") // Darker grid outline
                                outlinePaint.strokeWidth = 2.0f
                                outlinePaint.style = Paint.Style.STROKE
                            }

                            // Prevent intercepting gestures so the user can easily pan the map
                            val emptyListener = Polygon.OnClickListener { _, _, _ -> false }
                            setOnClickListener(emptyListener)
                        }
                        view.overlays.add(osmPolygon)
                    }

                    // 3. Draw User's GPS Location Marker
                    currentLocation?.let { loc ->
                        val locationPoint = GeoPoint(loc.latitude, loc.longitude)
                        val userMarker = Marker(view).apply {
                            position = locationPoint
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                            // High DPI programmatic canvas blue dot with white border
                            val size = 48
                            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            val paint = Paint().apply { isAntiAlias = true }

                            // White outline border
                            paint.color = android.graphics.Color.WHITE
                            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

                            // Vivid Blue dot
                            paint.color = android.graphics.Color.parseColor("#007AFF")
                            canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 5f, paint)

                            icon = BitmapDrawable(context.resources, bitmap)
                            infoWindow = null
                        }
                        view.overlays.add(userMarker)
                    }

                    view.invalidate() // refresh map layout
                }
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

        // Overlay: Error message Toast/Bar
        AnimatedVisibility(
            visible = errorMsg != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp, start = 24.dp, end = 24.dp)
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
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
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
                        },
                        containerColor = Color(0xCC0C1E1B),
                        contentColor = Color(0xFF5DF2D6),
                        shape = CircleShape,
                        modifier = Modifier.testTag("rotate_left_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Rotate Left",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            mapView.mapOrientation = mapView.mapOrientation + 45f
                            mapView.invalidate()
                        },
                        containerColor = Color(0xCC0C1E1B),
                        contentColor = Color(0xFF5DF2D6),
                        shape = CircleShape,
                        modifier = Modifier.testTag("rotate_right_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Rotate Right",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Profile Screen Action (Gamer Trophy / Stats Button)
                FloatingActionButton(
                    onClick = { showProfileScreen = true },
                    containerColor = Color(0xCC112926),
                    contentColor = Color(0xFF5DF2D6),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profil",
                        modifier = Modifier.size(24.dp)
                    )
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
                    val currentZoom = mapView.zoomLevelDouble
                    mapView.controller.setZoom(currentZoom + 1.0)
                    // Trigger map re-render with new grid coordinates
                    val center = mapView.mapCenter
                    mapCenterState.value = GeoPoint(center.latitude, center.longitude)
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
                onClick = {
                    val currentZoom = mapView.zoomLevelDouble
                    mapView.controller.setZoom(currentZoom - 1.0)
                    // Trigger map re-render with new grid coordinates
                    val center = mapView.mapCenter
                    mapCenterState.value = GeoPoint(center.latitude, center.longitude)
                },
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

        // Profile Overlay: a full-screen sheet on phones, a fixed-width side panel that leaves
        // the map visible on tablets (expanded width), so the map's own state/position isn't lost.
        val isExpandedWidth = LocalWindowWidthSizeClass.current == WindowWidthSizeClass.Expanded
        if (isExpandedWidth) {
            AnimatedVisibility(
                visible = showProfileScreen,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(420.dp)
                    .testTag("profile_side_panel")
            ) {
                ProfileScreen(
                    viewModel = viewModel,
                    onClose = { showProfileScreen = false },
                    onLogout = onLogout,
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy
                )
            }
        } else {
            AnimatedVisibility(
                visible = showProfileScreen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize().testTag("profile_fullscreen_overlay")
            ) {
                ProfileScreen(
                    viewModel = viewModel,
                    onClose = { showProfileScreen = false },
                    onLogout = onLogout,
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy
                )
            }
        }
    }
}
