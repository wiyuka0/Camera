package com.methyleneblue.camera.imagepack.aftereffect

import org.bukkit.boss.BossBar
import java.awt.image.BufferedImage
import kotlin.math.sqrt

object Vignette {

    private fun f(x: Double): Double {
        return x * x
    }

    fun applyEffect(
        image: BufferedImage,
        progressBar: BossBar? = null,
        intensity: Float = 0.3f
    ): BufferedImage {
        progressBar?.setTitle("后处理 - 光晕")
        progressBar?.progress = 0.0

        val width = image.width
        val height = image.height
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        val centerX = width / 2.0
        val centerY = height / 2.0

        val maxDistance = sqrt(centerX * centerX + centerY * centerY)

        val maxF = f(maxDistance)

        var currentCount = 0
        val totalCount = width * height

        for (y in 0 until height) {
            for (x in 0 until width) {
                currentCount++
                if (currentCount % 10000 == 0) progressBar?.progress = currentCount.toDouble() / totalCount

                val distanceX = x - centerX
                val distanceY = y - centerY
                val distance = sqrt(distanceX * distanceX + distanceY * distanceY)

                val factor = 1.0 - f(distance) / maxF * intensity

                val color = image.getRGB(x, y)
                var r = color shr 16 and 0xFF
                var g = color shr 8 and 0xFF
                var b = color and 0xFF

                r = (r * factor).toInt().coerceIn(0, 255)
                g = (g * factor).toInt().coerceIn(0, 255)
                b = (b * factor).toInt().coerceIn(0, 255)

                output.setRGB(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return output
    }
}