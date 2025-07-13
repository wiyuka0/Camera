package com.methyleneblue.camera.imagepack.util

import kotlin.math.pow

object ColorUtil {
    const val K = 0.547373141f

    fun calcBrightness(
        a: Int,
        r: Int,
        g: Int,
        b: Int
    ): Float {
        val rx = (1f * r / 255f).pow(2.2f)
        val gx = (1.5f * g / 255f).pow(2.2f)
        val bx = (0.6f * b / 255f).pow(2.2f)
        val x = rx + gx + bx
        return a / 255 * x.pow(1f / 2.2f) * K
    }

    fun calculateLuma(rgb: Int): Float {
        val r = rgb shr 16 and 0xFF
        val g = rgb shr 8 and 0xFF
        val b = rgb and 0xFF
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}