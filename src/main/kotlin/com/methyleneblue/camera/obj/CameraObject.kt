package com.methyleneblue.camera.obj

import org.bukkit.Location
import org.bukkit.entity.Player
import java.awt.Color
import java.awt.image.BufferedImage

abstract class CameraObject(
    val location: Location,
    val size: Pair<Int, Int>,
    val fov: Double,
    val distance: Double,
    var bufferedImage: BufferedImage,
) {

    abstract fun updateCamera(player: Player?, mixinTimes: Int = 1): BufferedImage

    fun skyColor(time: Long): Color {
        val times = longArrayOf(
            0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000, 11000, 12000,
            13000, 14000, 15000, 16000, 17000, 18000, 19000, 20000, 21000, 22000, 23000, 24000
        )
        val rValues = intArrayOf(
            111, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 111,
            45, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 45
        )
        val gValues = intArrayOf(
            155, 167, 167, 167, 167, 167, 167, 167, 167, 167, 167, 167, 155,
            63, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 63
        )
        val bValues = intArrayOf(
            237, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 237,
            96, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 96
        )
        fun cubicSpline(x: Long, xs: LongArray, ys: IntArray): Int {
            val n = xs.size
            if (x <= xs.first()) return ys.first()
            if (x >= xs.last()) return ys.last()

            // Locate interval
            var i = 0
            while (i < n - 1 && x > xs[i + 1]) i++

            val x0 = xs[i]
            val x1 = xs[i + 1]
            val y0 = ys[i]
            val y1 = ys[i + 1]

            val t = (x - x0).toDouble() / (x1 - x0)
            val t2 = t * t
            val t3 = t2 * t

            return ((2 * t3 - 3 * t2 + 1) * y0 +
                    (t3 - 2 * t2 + t) * (y1 - y0) +
                    (-2 * t3 + 3 * t2) * y1).toInt().coerceIn(0, 255)
        }
        val t = time % 24000
        val r = cubicSpline(t, times, rValues)
        val g = cubicSpline(t, times, gValues)
        val b = cubicSpline(t, times, bValues)
        return Color(r, g, b)
    }
}