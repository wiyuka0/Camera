package com.methyleneblue.camera.imagepack.aftereffect

import com.methyleneblue.camera.imagepack.blur.DualBlur
import com.methyleneblue.camera.imagepack.util.AdaptabilityUtil
import org.bukkit.boss.BossBar
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

object DepthOfField {
    fun applyEffect(
        image: BufferedImage,
        depthImage: Array<FloatArray>,
        fov: Double,
        progressBar: BossBar? = null,
        maxBlurRadius: Float = 50f,
        depthScale: Float = 1f,
        passes: Int = 2,
        customFocusDepth: Float? = null
    ): BufferedImage {
        val width = image.width
        val height = image.height

        val maxBlurRadius1 = AdaptabilityUtil.calculateRadius(width, fov, maxBlurRadius)

        val focusDepth = customFocusDepth ?: getCenterDepth(depthImage)

        progressBar?.setTitle("后处理 - 景深 - 预处理")
        progressBar?.progress = 0.0
        val blurredLayers = precomputeBlurLayers(image, ceil(maxBlurRadius1 + 1.0f).toInt(), passes, progressBar)

        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        progressBar?.setTitle("后处理 - 景深 - 景深")
        progressBar?.progress = 0.0

        var currentCount = 0
        val totalCount = width * height

        for (y in 0 until height) {
            for (x in 0 until width) {
                currentCount++
                if (currentCount % 10000 == 0) progressBar?.progress = currentCount.toDouble() / totalCount

                val depth = depthImage[x][y]

                val depthDiff = abs(depth - focusDepth)
                val blurRadius = AdaptabilityUtil.calculateRadius(width, fov, depthDiff * depthScale)
                val finalBlurRadius = min(blurRadius, maxBlurRadius1)

                val color = getInterpolatedPixelColor(blurredLayers, x, y, finalBlurRadius)
                output.setRGB(x, y, color)
            }
        }

        return output
    }

    private fun getCenterDepth(depthImage: Array<FloatArray>): Float {
        val centerX = depthImage.size / 2
        val centerY = depthImage[0].size / 2
        return depthImage[centerX][centerY]
    }

    private fun precomputeBlurLayers(
        image: BufferedImage,
        maxBlurRadius: Int,
        passes: Int,
        progressBar: BossBar? = null
    ): Array<BufferedImage> {
        return Array(maxBlurRadius + 1, { radius ->
            progressBar?.progress = radius.toDouble() / maxBlurRadius
            when (radius) {
                0 -> image
                else -> DualBlur.blur(image, radius, passes)
            }
        })
    }

    private fun getInterpolatedPixelColor(
        layers: Array<BufferedImage>,
        x: Int, y: Int,
        radius: Float
    ): Int {
        val ra0 = radius.toInt()
        val ra1 = ra0 + 1
        val t = radius - ra0

        val c0 = layers[ra0].getRGB(x, y)
        val c1 = layers[ra1].getRGB(x, y)

        val a0 = (c0 shr 24) and 0xFF
        val r0c = (c0 shr 16) and 0xFF
        val g0 = (c0 shr 8) and 0xFF
        val b0 = c0 and 0xFF

        val a1 = (c1 shr 24) and 0xFF
        val r1c = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
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