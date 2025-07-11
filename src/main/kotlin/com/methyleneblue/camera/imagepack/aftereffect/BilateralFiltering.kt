package com.methyleneblue.camera.imagepack.aftereffect

import java.awt.image.BufferedImage

import kotlin.math.sqrt
import kotlin.math.exp
import kotlin.math.pow

object BilateralFiltering {
    fun applyEffect(
        image: BufferedImage,
        radius: Int = 3,
        sigmaSpace: Double = 3.0,
        sigmaColor: Double = 0.1
    ): BufferedImage {
        val width = image.width
        val height = image.height

        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val spaceWeights = Array(2 * radius + 1) { DoubleArray(2 * radius + 1) }
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val dist = sqrt((dx * dx + dy * dy).toDouble())
                spaceWeights[dy + radius][dx + radius] = exp(-(dist * dist) / (2 * sigmaSpace * sigmaSpace))
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val centerPixel = image.getRGB(x, y)
                val centerR = centerPixel shr 16 and 0xFF
                val centerG = centerPixel shr 8 and 0xFF
                val centerB = centerPixel and 0xFF

                var sumA = 0.0
                var sumR = 0.0
                var sumG = 0.0
                var sumB = 0.0
                var totalWeight = 0.0

                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue

                    for (dx in -radius..radius) {
                        val nx = x + dx
                        if (nx < 0 || nx >= width) continue

                        val neighborPixel = image.getRGB(nx, ny)
                        val neighborA = neighborPixel shr 24 and 0xFF
                        val neighborR = neighborPixel shr 16 and 0xFF
                        val neighborG = neighborPixel shr 8 and 0xFF
                        val neighborB = neighborPixel and 0xFF

                        val colorDiff = sqrt(
                            (centerR - neighborR).toDouble().pow(2) +
                            (centerG - neighborG).toDouble().pow(2) +
                            (centerB - neighborB).toDouble().pow(2)
                        ) / 255.0

                        val colorWeight = exp(-(colorDiff * colorDiff) / (2 * sigmaColor * sigmaColor))

                        val weight = spaceWeights[dy + radius][dx + radius] * colorWeight

                        sumR += neighborR * weight
                        sumG += neighborG * weight
                        sumB += neighborB * weight
                        sumA += neighborA * weight
                        totalWeight += weight
                    }
                }

                val r = (sumR / totalWeight).toInt().coerceIn(0, 255)
                val g = (sumG / totalWeight).toInt().coerceIn(0, 255)
                val b = (sumB / totalWeight).toInt().coerceIn(0, 255)
                val a = (sumA / totalWeight).toInt().coerceIn(0, 255)

                output.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return output
    }
}
