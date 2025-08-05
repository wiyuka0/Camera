package com.methyleneblue.camera.imagepack.aftereffect

import com.methyleneblue.camera.imagepack.util.ColorUtil
import org.bukkit.boss.BossBar
import java.awt.image.BufferedImage
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

object AutoExposure {
    fun applyEffect(
        image: BufferedImage,
        progressBar: BossBar? = null,
        intensity: Float = 0.4f,
        exposureOffset: Float = 1f,
        samplingMode: Int = 2 // 0: 中心采光 1: 平均采光 2: 中心权重
    ): BufferedImage {
        progressBar?.setTitle("后处理 - 自动曝光 - 测光")
        progressBar?.progress = 1.0

        val avgLuma = when (samplingMode) {
            0 -> calcCenterLuma(image)
            1 -> calcAvgLuma(image)
            2 -> calcWeightedLuma(image)
            else -> throw IllegalArgumentException("SamplingMode must be between 0 and 2")
        }

        val width = image.width
        val height = image.height
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        val exposureFactor = calcExposureFactor(avgLuma, exposureOffset, intensity)

        progressBar?.setTitle("后处理 - 自动曝光 - 曝光")
        progressBar?.progress = 0.0

        var currentCount = 0
        val totalCount = width * height

        for (y in 0 until height) {
            for (x in 0 until width) {
                currentCount++
                if (currentCount % 10000 == 0) progressBar?.progress = currentCount.toDouble() / totalCount

                val color = image.getRGB(x, y)
                var r = color shr 16 and 0xFF
                var g = color shr 8 and 0xFF
                var b = color and 0xFF

                r = (r * exposureFactor).toInt().coerceIn(0, 255)
                g = (g * exposureFactor).toInt().coerceIn(0, 255)
                b = (b * exposureFactor).toInt().coerceIn(0, 255)

                output.setRGB(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return output
    }

    private fun calcExposureFactor(avgLuma: Double, exposureOffset: Float, intensity: Float): Double {
        val targetLuma = 128.0
        val safeLuma = max(avgLuma, 1.0)
        val fullFactor = targetLuma / safeLuma
        val mixedFactor = 1.0 + (fullFactor - 1.0) * intensity
        return mixedFactor * exposureOffset
    }

    private fun calcCenterLuma(image: BufferedImage, ratio: Float = 0.2f): Double {
        return 128.0
    }

    private fun calcAvgLuma(image: BufferedImage): Double {
        return 128.0
    }

    private fun calcWeightedLuma(image: BufferedImage, ratio: Float = 0.4f): Double {
        val width = image.width
        val height = image.height
        val centerX = image.width / 2.0
        val centerY = image.height / 2.0
        val maxDistance = sqrt(centerX * centerX + centerY * centerY)
        val sampleRadius = maxDistance * ratio

        var totalWeight = 0.0
        var weightedSum = 0.0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val distanceX = x - centerX
                val distanceY = y - centerY
                val distance = sqrt(distanceX * distanceX + distanceY * distanceY)
                val weight = exp(-distance / (sampleRadius * 0.5))

                var color = image.getRGB(x, y)
                val luma = ColorUtil.calculateLuma(color)
                weightedSum += luma * weight
                totalWeight += weight
            }
        }
        return if (totalWeight > 0) weightedSum / totalWeight else 128.0
    }
}