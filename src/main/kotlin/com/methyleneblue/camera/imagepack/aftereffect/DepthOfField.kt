package com.methyleneblue.camera.imagepack.aftereffect

import com.methyleneblue.camera.imagepack.blur.DualBlur
import com.methyleneblue.camera.imagepack.util.AdaptabilityUtil
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.min

object DepthOfField {
    fun applyEffect(
        image: BufferedImage,
        depthImage: BufferedImage,
        fov: Double,
        maxBlurRadius: Float = 50f,
        depthScale: Float = 1f,
        passes: Int = 2,
        customFocusDepth: Float? = null
    ): BufferedImage {
        val width = image.width
        val height = image.height

        val maxBlurRadius1 = AdaptabilityUtil.calculateRadius(width, fov, maxBlurRadius)

        val focusDepth = customFocusDepth ?: getCenterDepth(depthImage)
        val blurredLayers = precomputeBlurLayers(image, (maxBlurRadius1 + 1).toInt(), passes) // Fixed .toInt()

        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val depth = depthImage.getRGB(x, y).toFloat()

                val depthDiff = abs(depth - focusDepth)
                val blurRadius = AdaptabilityUtil.calculateRadius(width, fov, depthDiff * depthScale)
                val finalBlurRadius = min(blurRadius, maxBlurRadius1)

                val color = getInterpolatedPixelColor(blurredLayers, x, y, finalBlurRadius)
                output.setRGB(x, y, color)
            }
        }

        return output
    }

    private fun getCenterDepth(depthImage: BufferedImage): Float {
        val centerX = depthImage.width / 2
        val centerY = depthImage.height / 2
        return depthImage.getRGB(centerX, centerY).toFloat()
    }

    private fun precomputeBlurLayers(
        image: BufferedImage,
        maxBlurRadius: Int,
        passes: Int
    ): Array<BufferedImage> {
        return Array(maxBlurRadius + 1, { radius ->
            when (radius) {
                0 -> image
                else -> DualBlur.blur(image, maxBlurRadius, passes)
            }
        })
    }

    private fun getInterpolatedPixelColor(
        layers: Array<BufferedImage>,
        x: Int, y: Int,
        radius: Float
    ): Int {
        val r0 = radius.toInt()
        val r1 = r0 + 1
        val t = radius - r0

        val c0 = layers[r0].getRGB(x, y)
        val c1 = layers[r1].getRGB(x, y)

//        return (interpolate(c0.alpha, c1.alpha, t).toInt().coerceIn(0, 255) shl 24) or
//                (interpolate(c0.red, c1.red, t).toInt().coerceIn(0, 255) shl 16) or
//                (interpolate(c0.green, c1.green, t).toInt().coerceIn(0, 255) shl 8) or
//                (interpolate(c0.blue, c1.blue, t).toInt().coerceIn(0, 255))
        val a0 = (c0 ushr 24) and 0xFF
        val r0c = (c0 ushr 16) and 0xFF
        val g0 = (c0 ushr 8) and 0xFF
        val b0 = c0 and 0xFF

        val a1 = (c1 ushr 24) and 0xFF
        val r1c = (c1 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF
        val b1 = c1 and 0xFF

        val a = interpolate(a0, a1, t).toInt().coerceIn(0, 255)
        val r = interpolate(r0c, r1c, t).toInt().coerceIn(0, 255)
        val g = interpolate(g0, g1, t).toInt().coerceIn(0, 255)
        val b = interpolate(b0, b1, t).toInt().coerceIn(0, 255)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun interpolate(a: Int, b: Int, t: Float): Float {
        return a * (1f - t) + b * t
    }
}