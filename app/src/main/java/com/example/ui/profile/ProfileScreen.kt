package com.example.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.HexGridConfig
import com.example.ui.components.StatCard
import com.example.ui.map.GameViewModel
import java.text.SimpleDateFormat
import java.util.*

// DirectionsWalk/DirectionsRun are flagged deprecated in favor of an AutoMirrored variant that
// doesn't actually exist in this version of material-icons-extended; suppressing rather than
// chasing a non-existent replacement.
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: GameViewModel,
    onClose: () -> Unit,
    onLogout: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalDistance by viewModel.totalDistanceWalked.collectAsState()
    val stepCount by viewModel.stepCount.collectAsState()
    val stompedHexes by viewModel.stompedHexes.collectAsState()
    val stompPercentage by viewModel.stompPercentage.collectAsState()
    val activeZone by viewModel.activeNeighborhood.collectAsState()
    val nickname by viewModel.nickname.collectAsState()
    val startTime by viewModel.statsStartTimestamp.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val weeklyStats by viewModel.weeklyStats.collectAsState()

    // Ask for the weather when the screen appears, and again if the player has since been located.
    // The repository caches, so reopening the profile does not mean another request.
    LaunchedEffect(currentLocation != null) {
        viewModel.refreshWeather()
    }

    val hexCount = stompedHexes.size
    val totalArea = hexCount * HexGridConfig.CELL_AREA_SQUARE_METERS

    // Edit Name Dialog State
    var showEditDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(nickname) }

    // Which headline number the player has drilled into, if any. Saveable so a rotation does not
    // throw them back out to the profile.
    var openedMetric by rememberSaveable { mutableStateOf<StatMetric?>(null) }

    // Derive Level & Title based on stomped hex count
    val (level, playerTitle, titleColor) = when {
        hexCount >= 50 -> Triple(5, "Məhəllə Əfsanəsi", Color(0xFFFFD700)) // Golden Yellow
        hexCount >= 30 -> Triple(4, "Fəth Ustası", Color(0xFFC0C0C0)) // Silver
        hexCount >= 15 -> Triple(3, "Ərazi Kəşfiyyatçısı", Color(0xFFE27D60)) // Bronze/Coral
        hexCount >= 5 -> Triple(2, "Küçə Səyyahı", Color(0xFF41B3A3)) // Teal
        else -> Triple(1, "Yeni Başlayan", Color(0xFF85DCB0)) // Mint green
    }

    // Format Start Date
    val dateString = remember(startTime) {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.Builder().setLanguage("az").build())
        sdf.format(Date(startTime))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1A1B)) // Deep dark obsidian
            .testTag("profile_screen_container")
    ) {
        // Decorative background glowing gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x1F5DF2D6), Color.Transparent),
                        radius = 800f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Top Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "OYUNCU PROFİLİ",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 8.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier
                            .background(Color(0x33FFFFFF), CircleShape)
                            .size(48.dp)
                            .testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Hesabdan çıx",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .background(Color(0x33FFFFFF), CircleShape)
                            .size(48.dp)
                            .testTag("close_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Profil pəncərəsini bağla",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Character Avatar & Level Section
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(110.dp)
                ) {
                    // Pulsing/Glowing Ring
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .border(2.dp, titleColor, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(94.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0F1A1B), Color(0xFF142B28))
                                ),
                                CircleShape
                            )
                    )
                    Icon(
                        imageVector = if (level >= 4) Icons.Default.Shield else Icons.Default.Person,
                        contentDescription = "Avatar",
                        tint = titleColor,
                        modifier = Modifier.size(48.dp)
                    )

                    // Level Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-4).dp, y = (-4).dp)
                            .background(titleColor, CircleShape)
                            .size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = level.toString(),
                            color = Color(0xFF0F1A1B),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Nickname & Edit Action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            tempName = nickname
                            showEditDialog = true
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("edit_nickname_trigger")
                ) {
                    Text(
                        text = nickname,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Redaktə et",
                        tint = Color(0xFF5DF2D6),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Player Title
                Text(
                    text = playerTitle,
                    color = titleColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Member Since Info
                Text(
                    text = "Qeydiyyat: $dateString",
                    color = Color(0xFF98BCB6),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Weather where the player is right now
                Text(
                    text = "HAVA DURUMU",
                    color = Color(0xFF98BCB6),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textAlign = TextAlign.Start
                )

                WeatherCard(
                    state = weather,
                    onRetry = { viewModel.refreshWeather() }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Stats Dashboard Grid Header
                Text(
                    text = "ƏSAS STATİSTİKALAR",
                    color = Color(0xFF98BCB6),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textAlign = TextAlign.Start
                )

                // Symmetrical 2-column stats grid: every row shares the same column widths and,
                // via IntrinsicSize.Max, the same card height - including the last row, where the
                // step count card is paired with an equally-sized invisible spacer instead of
                // stretching to full width on its own.
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max)
                    ) {
                        // Stat 1: Total Distance Walked
                        StatCard(
                            title = StatMetric.DISTANCE.title,
                            value = formatDistance(totalDistance),
                            icon = Icons.Default.DirectionsWalk,
                            iconColor = StatMetric.DISTANCE.accent,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("distance_stat_card"),
                            onClick = { openedMetric = StatMetric.DISTANCE }
                        )
                        // Stat 2: Total Area Discovered
                        StatCard(
                            title = StatMetric.AREA.title,
                            value = formatArea(totalArea),
                            icon = Icons.Default.Category,
                            iconColor = StatMetric.AREA.accent,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("area_stat_card"),
                            onClick = { openedMetric = StatMetric.AREA }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max)
                    ) {
                        // Stat 3: Claimed Hexes
                        StatCard(
                            title = StatMetric.CELLS.title,
                            value = "$hexCount hüceyrə",
                            icon = Icons.Default.Layers,
                            iconColor = StatMetric.CELLS.accent,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("cells_stat_card"),
                            onClick = { openedMetric = StatMetric.CELLS }
                        )
                        // Stat 4: Active Zone Rate
                        StatCard(
                            title = "Aktiv Zone Fəthi",
                            value = String.format(Locale.US, "%.1f%%", stompPercentage),
                            icon = Icons.Default.LocationOn,
                            iconColor = Color(0xFF41B3A3),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            subtitle = activeZone?.name ?: "Zonadan kənar"
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Stat 5: Live Step Count (background foreground-service pedometer)
                        StatCard(
                            title = "Addım Sayı",
                            value = "$stepCount addım",
                            icon = Icons.Default.DirectionsRun,
                            iconColor = Color(0xFFBB86FC),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                // Achievements and badges live in their own bottom-bar tab; this screen is the
                // player's identity and their headline numbers.

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(
                    onClick = onOpenPrivacyPolicy,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("open_privacy_policy_button")
                ) {
                    Text("Gizlilik Siyasəti", color = Color(0xFF5A7C77), fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // The breakdown behind whichever headline number was tapped, slid in over the profile
        // rather than pushed onto the navigator: it is a detail of this screen, and keeping it here
        // means the profile underneath does not have to be torn down and rebuilt to come back to.
        AnimatedVisibility(
            visible = openedMetric != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            val metric = rememberLastOpened(openedMetric)
            if (metric != null) {
                WeeklyStatsScreen(
                    metric = metric,
                    days = weeklyStats,
                    onClose = { openedMetric = null }
                )
            }
        }
    }

    BackHandler(enabled = openedMetric != null) { openedMetric = null }

    // Edit Name Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = Color(0xFF142021),
            title = {
                Text(
                    text = "Adı Dəyişdir",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Profiliniz üçün yeni ləqəb daxil edin:",
                        color = Color(0xFF98BCB6),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { if (it.length <= 20) tempName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF5DF2D6),
                            unfocusedBorderColor = Color(0xFF26524D),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nickname_input_field")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateNickname(tempName)
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF5DF2D6)),
                    modifier = Modifier.testTag("save_nickname_button")
                ) {
                    Text("YADDA SAXLA")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    modifier = Modifier.testTag("cancel_nickname_button")
                ) {
                    Text("LƏĞV ET")
                }
            }
        )
    }
}

/**
 * The last metric that was open, which stays put after [metric] is cleared.
 *
 * Only exists so the breakdown has something to draw while it slides back out: reading the cleared
 * state directly would blank the overlay on the first frame of the exit animation and the player
 * would see an empty panel slide away.
 */
@Composable
private fun rememberLastOpened(metric: StatMetric?): StatMetric? {
    val holder = remember { mutableStateOf(metric) }
    if (metric != null) holder.value = metric
    return holder.value
}
