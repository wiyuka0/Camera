package com.methyleneblue.camera.imagepack.blur

import java.awt.image.BufferedImage

object DualBlur {
    fun blur(
        image: BufferedImage,
        radius: Int,
        passes: Int = 2
    ): BufferedImage {
        val current = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        current.graphics.drawImage(image, 0, 0, null)
        current.graphics.dispose()
        val result = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)

        repeat(passes) {
            blurHorizontal(current, result, radius)
            blurVertical(result, current, radius)
        }
        return current
    }

    private fun blurHorizontal(
        source: BufferedImage,
        dest: BufferedImage,
        radius: Int
    ) {
        val width = source.width
        val height = source.height
        val halfRadius = radius / 2

        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0
                var g = 0
                var b = 0
                var a = 0
                var cnt = 0

                for (dx in -halfRadius..halfRadius) {
                    val nx = (x + dx).coerceIn(0, width - 1)
                    val pixel = source.getRGB(nx, y)
                    a += pixel shr 24 and 0xFF
                    r += pixel shr 16 and 0xFF
                    g += pixel shr 8 and 0xFF
                    b += pixel and 0xFF
                    cnt++
                }

                a /= cnt
                r /= cnt
                g /= cnt
                b /= cnt
                dest.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
    }

    private fun blurVertical(
        source: BufferedImage,
        dest: BufferedImage,
        radius: Int
    ) {
        val width = source.width
        val height = source.height
        val halfRadius = radius / 2

        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0
                var g = 0
                var b = 0
                var a = 0
                var cnt = 0

                for (dy in -halfRadius..halfRadius) {
                    val ny = (y + dy).coerceIn(0, height - 1)
                    val pixel = source.getRGB(x, ny)
                    a += pixel shr 24 and 0xFF
                    r += pixel shr 16 and 0xFF
                    g += pixel shr 8 and 0xFF
                    b += pixel and 0xFF
                    cnt++
                }

                a /= cnt
                r /= cnt
                g /= cnt
                b /= cnt
                dest.setRGB(x, y, (a shr 24) or (r shl 16) or (g shl 8) or b)
            }
        }
    }
}