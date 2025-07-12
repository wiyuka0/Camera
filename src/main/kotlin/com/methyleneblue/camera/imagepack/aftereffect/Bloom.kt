package com.methyleneblue.camera.imagepack.aftereffect

import com.methyleneblue.camera.imagepack.blur.DualBlur
import java.awt.image.BufferedImage
import kotlin.math.*


object Bloom {
    const val K = 0.547373141f
    const val REFERENCE_FOV = 90.0
    const val REFERENCE_WIDTH = 1920
    val TAN_REFERENCE_FOV = tan(Math.toRadians(REFERENCE_FOV / 2))

    fun applyEffect(
        image: BufferedImage,
        fov: Double,
        bloomRadius: Float = 50f,
        threshold: Float = 0.6f,
        softness: Float = 0.3f,
        intensity: Float = 1f
    ): BufferedImage {
        val bloomOnly = extractBrightParts(image, threshold, softness)
        val radius = calculateRadius(image.width, fov, bloomRadius)
        val bloomed = DualBlur.blur(bloomOnly, radius)
        return blendBloomEffect(image, bloomed, intensity)
    }

    /* 参考值 */
    private fun calculateRadius(
        width: Int,
        fov: Double,
        baseRadius: Float
    ): Int {
        val fovRatio = TAN_REFERENCE_FOV / tan(Math.toRadians(fov / 2))
        val resRatio = width.toDouble() / REFERENCE_WIDTH

        val radius = (baseRadius * fovRatio * resRatio).roundToInt()
        return radius.coerceAtLeast(1)
    }

    private fun extractBrightParts(
        source: BufferedImage,
        threshold: Float,
        softness: Float
    ): BufferedImage {
        val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val softThreshold = threshold + softness

        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
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
        intensity: Float
    ): BufferedImage {
        val result = BufferedImage(original.width, original.height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until original.height) {
            for (x in 0 until original.width) {
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

    private fun smooth(start: Float, end: Float, x: Float): Float {
        val t = ((x - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun easeOutQuad(x: Float): Float {
        return 1 - (1 - x).pow(2)
    }
}