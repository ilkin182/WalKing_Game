package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.achievements.AchievementsScreen
import com.example.ui.map.GameViewModel
import com.example.ui.map.MapScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.util.LocalWindowWidthSizeClass

/**
 * The logged-in app: the map, with a bottom bar switching between it and the two side tabs.
 *
 * ## Why the map is always composed
 *
 * The other two tabs are drawn *over* the map rather than in place of it. [MapScreen] owns an
 * osmdroid `MapView` with its own tile cache, camera and location tracking; swapping it out of the
 * composition on every tab change would tear all of that down and rebuild it - the player would come
 * back from checking a badge to a blank map re-downloading tiles at their old position. Keeping it
 * behind the overlay costs one idle view and makes returning to the map instant.
 *
 * On tablets ([WindowWidthSizeClass.Expanded]) the overlays are side panels instead of full-screen
 * sheets, so the map stays visible next to whatever the player opened.
 */
@Composable
fun MainShell(
    viewModel: GameViewModel,
    onLogout: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.DEFAULT) }
    val isExpandedWidth = LocalWindowWidthSizeClass.current == WindowWidthSizeClass.Expanded

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0F1A1B),
        bottomBar = {
            AppBottomBar(selected = selectedTab, onSelect = { selectedTab = it })
        }
    ) { innerPadding ->
        val barHeight = innerPadding.calculateBottomPadding()

        Box(modifier = Modifier.fillMaxSize()) {
            // The map runs edge to edge *under* the bar rather than being inset by it - a map that
            // stops short of the screen edge looks like a cropped picture. Only its floating
            // controls are lifted clear, which is what bottomInset does.
            MapScreen(
                viewModel = viewModel,
                bottomInset = barHeight,
                modifier = Modifier.fillMaxSize()
            )

            TabOverlay(
                visible = selectedTab == MainTab.ACHIEVEMENTS,
                isExpandedWidth = isExpandedWidth,
                testTag = "achievements_overlay",
                bottomInset = barHeight
            ) {
                AchievementsScreen(
                    viewModel = viewModel,
                    onClose = { selectedTab = MainTab.MAP }
                )
            }

            TabOverlay(
                visible = selectedTab == MainTab.PROFILE,
                isExpandedWidth = isExpandedWidth,
                testTag = "profile_overlay",
                bottomInset = barHeight
            ) {
                ProfileScreen(
                    viewModel = viewModel,
                    onClose = { selectedTab = MainTab.MAP },
                    onLogout = onLogout,
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy
                )
            }
        }
    }
}

/**
 * One non-map tab, presented as a full-screen sheet on phones and a side panel on tablets.
 *
 * Both forms stop above the bottom bar ([bottomInset]) so the bar stays reachable and keeps showing
 * which tab is open.
 */
@Composable
private fun BoxScope.TabOverlay(
    visible: Boolean,
    isExpandedWidth: Boolean,
    testTag: String,
    bottomInset: Dp,
    content: @Composable () -> Unit
) {
    if (isExpandedWidth) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(420.dp)
                .padding(bottom = bottomInset)
                .testTag("${testTag}_side_panel")
        ) {
            content()
        }
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomInset)
                .testTag("${testTag}_fullscreen")
        ) {
            content()
        }
    }
}
