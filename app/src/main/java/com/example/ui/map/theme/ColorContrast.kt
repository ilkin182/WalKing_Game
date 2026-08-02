package com.example.ui.map.theme

/**
 * WCAG 2.1 relative-luminance and contrast-ratio math, used to keep [MapTheme]'s label colours
 * legible against the base fill rather than trusting hand-picked hex values to be readable.
 *
 * Colours are plain 0xRRGGBB ints (alpha is ignored - map labels are drawn opaque).
 */
object ColorContrast {

    /** WCAG relative luminance of an sRGB colour, 0.0 (black) to 1.0 (white). */
    fun relativeLuminance(rgb: Int): Double {
        val r = channelLuminance((rgb shr 16) and 0xFF)
        val g = channelLuminance((rgb shr 8) and 0xFF)
        val b = channelLuminance(rgb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * Contrast ratio between two colours, from 1.0 (identical) to 21.0 (black on white). The order
     * of the arguments does not matter.
     */
    fun ratio(foreground: Int, background: Int): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun channelLuminance(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
}
