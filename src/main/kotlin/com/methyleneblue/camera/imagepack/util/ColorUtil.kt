package com.methyleneblue.camera.imagepack.util

object ColorUtil {
    fun calculateLuma(rgb: Int): Float {
        val r = rgb shr 16 and 0xFF
        val g = rgb shr 8 and 0xFF
        val b = rgb and 0xFF
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}