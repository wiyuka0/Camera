package com.methyleneblue.camera.imagepack.aftereffect

import com.methyleneblue.camera.imagepack.blur.DualBlur
import com.methyleneblue.camera.imagepack.util.AdaptabilityUtil
import com.methyleneblue.camera.imagepack.util.ColorUtil.calcBrightness
import org.bukkit.boss.BossBar
import java.awt.image.BufferedImage
import kotlin.math.*

object Bloom {
    fun applyEffect(
        image: BufferedImage,
        fov: Double,
        progressBar: BossBar? = null,
        bloomRadius: Float = 50f,
        threshold: Float = 0.6f,
        softness: Float = 0.3f,
        intensity: Float = 1f
    ): BufferedImage {
        val bloomOnly = extractBrightParts(image, threshold, softness, progressBar)
        val radius = AdaptabilityUtil.calculateRadius(image.width, fov, bloomRadius).roundToInt().coerceAtLeast(1)
        progressBar?.setTitle("后处理 - 泛光 - 模糊亮部")
        progressBar?.progress = 1.0
        val bloomed = DualBlur.blur(bloomOnly, radius)
        return blendBloomEffect(image, bloomed, intensity, progressBar)
    }

    private fun extractBrightParts(
        source: BufferedImage,
        threshold: Float,
        softness: Float,
        progressBar: BossBar? = null
    ): BufferedImage {
        progressBar?.setTitle("后处理 - 泛光 - 提取亮部")
        progressBar?.progress = 0.0

        val width = source.width
        val height = source.height

        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val softThreshold = threshold + softness

        var currentCount = 0
        val totalCount = width * height

        for (y in 0 until height) {
            for (x in 0 until width) {
                currentCount++
                if (currentCount % 10000 == 0) progressBar?.progress = currentCount.toDouble() / totalCount

                val pixel = source.getRGB(x, y)
                val a = pixel shr 24 and 0xff
                val r = pixel shr 16 and 0xFF
                val g = pixel shr 8 and 0xFF
                val b = pixel and 0xFF

                val brightness = calcBrightness(a, r, g, b)

                val bloomAlpha = when {
                    brightness >= softThreshold -> 1.0f
                    brightness <= threshold -> 0.0f
                    else -> smooth(threshold, softThreshold, brightness)
                }

                val bloomIntensity = (brightness - threshold).coerceAtLeast(0f) / softness
                val curvedAlpha = easeOutQuad(bloomAlpha) * bloomIntensity

                val newAlpha = (curvedAlpha * 255).toInt().coerceIn(0, 255)
                result.setRGB(x, y, (newAlpha shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return result
    }

    private fun blendBloomEffect(
        original: BufferedImage,
        bloom: BufferedImage,
        intensity: Float,
        progressBar: BossBar? = null
    ): BufferedImage {
        progressBar?.setTitle("后处理 - 泛光 - 混合")
        progressBar?.progress = 1.0

        val width = original.width
        val height = original.height

        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        var currentCount = 0
        val totalCount = width * height

        for (y in 0 until height) {
            for (x in 0 until width) {
                currentCount++
                if (currentCount % 10000 == 0) progressBar?.progress = currentCount.toDouble() / totalCount

                val originalPixel = original.getRGB(x, y)
                val bloomPixel = bloom.getRGB(x, y)

                val oA = originalPixel shr 24 and 0xff
                val oR = originalPixel shr 16 and 0xff
                val oG = originalPixel shr 8 and 0xff
                val oB = originalPixel and 0xff

                val bA = bloomPixel shr 24 and 0xff
                val bR = bloomPixel shr 16 and 0xff
                val bG = bloomPixel shr 8 and 0xff
                val bB = bloomPixel and 0xff

                val bloomAlphaFactor = bA / 255f * intensity
                val combinedAlpha = oA + (255 - oA) * bloomAlphaFactor
                val newAlpha = combinedAlpha.toInt().coerceIn(0, 255)

                val bloomWeight = min(1f, intensity * (bA / 255f))
                val screenBlend = { base: Int, top: Int ->
                    255 - (255 - base) * (255 - top) / 255
                }

                val blend = { base: Int, top: Int ->
                    (base * (1 - bloomWeight) + screenBlend(base, top) * bloomWeight).toInt().coerceIn(0, 255)
                }

                val r = blend(oR, bR)
                val g = blend(oG, bG)
                val b = blend(oB, bB)

                result.setRGB(x, y, (newAlpha shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return result
    }

    private fun smooth(start: Float, end: Float, x: Float): Float {
        val t = ((x - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun easeOutQuad(x: Float): Float {
        return 1 - (1 - x).pow(2)
    }
}