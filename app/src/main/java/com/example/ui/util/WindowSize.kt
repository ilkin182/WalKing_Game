
package com.example.ui.util

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Set once in MainActivity from `calculateWindowSizeClass(activity)` and read by any screen that
 * needs to adapt phone vs. tablet layout, instead of threading a parameter through every
 * composable call site.
 */
val LocalWindowWidthSizeClass = staticCompositionLocalOf { WindowWidthSizeClass.Compact }
