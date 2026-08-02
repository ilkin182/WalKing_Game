package com.example.ui.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's bottom navigation bar.
 *
 * Coloured against the map rather than against Material's default surface: the bar sits directly on
 * top of daylight cartography, so it has to read as a solid edge to the map instead of a pale panel
 * the map's own greys bleed into. The gesture-navigation inset is left to [NavigationBar]'s own
 * default, which already reserves it.
 */
@Composable
fun AppBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.testTag("app_bottom_bar"),
        containerColor = Color(0xFF0C1E1B),
        contentColor = Color(0xFF98BCB6),
        tonalElevation = 0.dp
    ) {
        MainTab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF0A1F1C),
                    selectedTextColor = Color(0xFF5DF2D6),
                    indicatorColor = Color(0xFF5DF2D6),
                    unselectedIconColor = Color(0xFF6E8F8A),
                    unselectedTextColor = Color(0xFF6E8F8A)
                ),
                modifier = Modifier.testTag(tab.testTag)
            )
        }
    }
}
