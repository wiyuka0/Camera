package com.methyleneblue.camera.imagepack.aftereffect

import com.methyleneblue.camera.imagepack.aftereffect.Bloom.blendBloomEffect
import com.methyleneblue.camera.imagepack.aftereffect.Bloom.calculateRadius
import com.methyleneblue.camera.imagepack.aftereffect.Bloom.extractBrightParts
import com.methyleneblue.camera.imagepack.blur.DualBlur
import java.awt.image.BufferedImage

object DepthOfField {
    fun applyEffect(
        image: BufferedImage,
        depthImage: BufferedImage,
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
}