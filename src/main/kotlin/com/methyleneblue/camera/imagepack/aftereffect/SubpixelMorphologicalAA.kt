package com.methyleneblue.camera.imagepack.aftereffect

import com.methyleneblue.camera.imagepack.util.ColorUtil
import org.bukkit.boss.BossBar
import java.awt.image.BufferedImage
import kotlin.math.abs

import kotlin.math.hypot

object SubpixelMorphologicalAA {
    fun applyEffect(
        image: BufferedImage,
        progressBar: BossBar? = null
    ): BufferedImage {
        val width = image.width
        val height = image.height

        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val output1 = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        edgeDetection(image, output, progressBar)
        blendingWeight(output, output1, progressBar)
        neighborhoodBlending(image, output1, output, progressBar)

        return output
    }

    private fun edgeDetection(
        input: BufferedImage,
        output: BufferedImage,
        progressBar: BossBar? = null,
        edgeThreshold: Float = 20f
    ) {
        progressBar?.setTitle("后处理 - SMAA - 边缘检测")
        progressBar?.progress = 0.0

        val width = input.width
        val height = input.height

        val luma = Array(height) { FloatArray(width) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                luma[y][x] = ColorUtil.calculateLuma(input.getRGB(x, y))
            }
        }

        fun getLuma(default: Float, y: Int, x: Int): Float {
            if (y < 0 || y >= height) return default
            if (x < 0 || x >= width) return default
            return luma[y][x]
        }

        var currentCount = 0
        val totalCount = width * height

        for (y in 0 until height) {
            for (x in 0 until width) {
                currentCount++
                if (currentCount % 10000 == 0) progressBar?.progress = currentCount.toDouble() / totalCount

                val current = luma[y][x]

                val gx =
                    -1f * getLuma(current, y - 1, x - 1) + 1f * getLuma(current, y - 1, x + 1) +
                    -2f * getLuma(current, y, x - 1) + 2f * getLuma(current, y, x + 1) +
                    -1f * getLuma(current, y + 1, x - 1) + 1f * getLuma(current, y + 1, x + 1)

                val gy =
                        -1f * getLuma(current, y - 1, x - 1) - 2f * getLuma(current, y - 1, x) -1f * getLuma(current, y - 1, x + 1) +
                        1f * getLuma(current, y + 1, x - 1) + 2f * getLuma(current, y + 1, x) + 1f * getLuma(current, y + 1, x + 1)

                val magnitude = hypot(gx, gy)

                val edgeValue = when {
                    magnitude < edgeThreshold -> 0x00000000
                    abs(gx) > abs(gy) -> 0xFFFF0000
                    else -> 0xFF00FF00
                }.toInt()

                // if (magnitude > edgeThreshold) Int.MAX_VALUE else 0
                output.setRGB(x, y, edgeValue)
            }
        }
    }

    private fun blendingWeight(
        input: BufferedImage,
        output: BufferedImage,
        progressBar: BossBar? = null
    ) {
        progressBar?.setTitle("后处理 - SMAA - 混合权重计算")
        progressBar?.progress = 0.0

        val width = input.width
        val height = input.height

        var currentCount = 0
        val totalCount = width * height

        for (y in 0 until height) {
            for (x in 0 until width) {
                currentCount++
                if (currentCount % 10000 == 0) progressBar?.progress = currentCount.toDouble() / totalCount

                val edgeColor = input.getRGB(x, y)
                if (edgeColor and 0x00FFFFFF == 0) {
                    output.setRGB(x, y, 0)
                    continue
                }

                val isHorizontal = (edgeColor and 0x00FF0000) != 0

                // 0: horizontal
                // 1: vertical
                // -1: other
                val pattern = detectPattern(input, isHorizontal, x, y)

                val weight = calculateBlendWeight(pattern, isHorizontal)
                output.setRGB(x, y, weight)
            }
        }
    }

    private fun detectPattern(
        input: BufferedImage,
        isHorizontal: Boolean,
        x: Int,
        y: Int
    ): Int {
        return if (isHorizontal) {
            val left = if (x > 0) input.getRGB(x - 1, y) else 0
            val right = if (x < input.width - 1) input.getRGB(x + 1, y) else 0
            if (left != 0 && right != 0) 0 else -1
        } else {
            val top = if (y > 0) input.getRGB(x, y - 1) else 0
            val bottom = if (y < input.height - 1) input.getRGB(x, y + 1) else 0

            if (top != 0 && bottom != 0) 1 else -1
        }
    }

    private fun calculateBlendWeight(pattern: Int, isHorizontal: Boolean): Int {
        val hWeight: Float
        val vWeight: Float

        when (pattern) {
            0 -> {
                if (isHorizontal) {
                    hWeight = 0.0f
                    vWeight = 0.8f
                } else {
                    hWeight = 0.8f
                    vWeight = 0.0f
                }
            }
            1 -> {
                if (!isHorizontal) {
                    hWeight = 0.8f
                    vWeight = 0.0f
                } else {
                    hWeight = 0.0f
                    vWeight = 0.8f
                }
            }
            else -> {
                hWeight = 0.5f
                vWeight = 0.5f
            }
        }

        val r = (hWeight * 255).toInt() and 0xFF
        val g = (vWeight * 255).toInt() and 0xFF
        return r shl 16 or (g shl 8)
    }

    private fun neighborhoodBlending(
        image: BufferedImage,
        input: BufferedImage,
        output: BufferedImage,
        progressBar: BossBar? = null
    ) {
        progressBar?.setTitle("后处理 - SMAA - 邻域混合")
        progressBar?.progress = 0.0

        val width = input.width
        val height = input.height

        var currentCount = 0
        val totalCount = width * height

        for (y in 0 until height) {
            for (x in 0 until width) {
                currentCount++
                if (currentCount % 10000 == 0) progressBar?.progress = currentCount.toDouble() / totalCount

                val blend = input.getRGB(x, y)

                val current = image.getRGB(x, y)

                if (blend and 0x00FFFFFF == 0) {
                    output.setRGB(x, y, current)
                    continue
                }

                val hWeight = (blend shr 16 and 0xFF) / 255f
                val vWeight = (blend shr 8 and 0xFF) / 255f

                val left = if (x == 0) current else image.getRGB(x - 1, y)
                val right = if (x == width - 1) current else image.getRGB(x + 1, y)
                val top = if (y == 0) current else image.getRGB(x, y - 1)
                val bottom = if (y == height - 1) current else image.getRGB(x, y + 1)

                val blended = blendColors(
                    current,
                    left, right, top, bottom,
                    hWeight, vWeight
                )
                output.setRGB(x, y, blended)
            }
        }
    }

    private fun blendColors(
        current: Int,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        hWeight: Float,
        vWeight: Float
    ): Int {
        val a1 = current shr 24 and 0xFF
        val r1 = current shr 16 and 0xFF
        val g1 = current shr 8 and 0xFF
        val b1 = current and 0xFF

        val a2 = left shr 24 and 0xFF
        val r2 = left shr 16 and 0xFF
        val g2 = left shr 8 and 0xFF
        val b2 = left and 0xFF

        val a3 = right shr 24 and 0xFF
        val r3 = right shr 16 and 0xFF
        val g3 = right shr 8 and 0xFF
        val b3 = right and 0xFF

        val a4 = top shr 24 and 0xFF
        val r4 = top shr 16 and 0xFF
        val g4 = top shr 8 and 0xFF
        val b4 = top and 0xFF

        val a5 = bottom shr 24 and 0xFF
        val r5 = bottom shr 16 and 0xFF
        val g5 = bottom shr 8 and 0xFF
        val b5 = bottom and 0xFF

        val totalWeight = 1f + hWeight + vWeight

        val a = ((a1 + (a2 + a3) * hWeight * 0.5f + (a4 + a5) * vWeight * 0.5f) / totalWeight).toInt().coerceIn(0, 255)
        val r = ((r1 + (r2 + r3) * hWeight * 0.5f + (r4 + r5) * vWeight * 0.5f) / totalWeight).toInt().coerceIn(0, 255)
        val g = ((g1 + (g2 + g3) * hWeight * 0.5f + (g4 + g5) * vWeight * 0.5f) / totalWeight).toInt().coerceIn(0, 255)
        val b = ((b1 + (b2 + b3) * hWeight * 0.5f + (b4 + b5) * vWeight * 0.5f) / totalWeight).toInt().coerceIn(0, 255)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}