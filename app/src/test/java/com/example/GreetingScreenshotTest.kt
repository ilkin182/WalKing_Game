package com.example

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.components.StatCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  // DirectionsWalk is flagged deprecated in favor of an AutoMirrored variant that doesn't
  // actually exist in this version of material-icons-extended.
  @Suppress("DEPRECATION")
  @Test
  fun stat_card_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        StatCard(
          title = "Gəzilən Məsafə",
          value = "450 metr",
          icon = Icons.Default.DirectionsWalk,
          iconColor = Color(0xFF5DF2D6)
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/stat_card.png")
  }
}
