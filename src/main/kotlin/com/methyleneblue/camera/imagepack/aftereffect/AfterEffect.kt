package com.methyleneblue.camera.imagepack.aftereffect

import java.awt.image.BufferedImage

object AfterEffect {
    fun apply(bufferedImage: BufferedImage, fov: Double): BufferedImage {
        var result: BufferedImage? = null

        result = BilateralFiltering      .applyEffect(bufferedImage)
        result = SubpixelMorphologicalAA .applyEffect(result)
        result = Bloom                   .applyEffect(result, fov, 50f)


        return result
    }
}