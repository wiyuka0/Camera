package com.methyleneblue.camera.imagepack

import com.methyleneblue.camera.imagepack.aftereffect.AutoExposure
import com.methyleneblue.camera.imagepack.aftereffect.BilateralFiltering
import com.methyleneblue.camera.imagepack.aftereffect.Bloom
import com.methyleneblue.camera.imagepack.aftereffect.SubpixelMorphologicalAA
import com.methyleneblue.camera.imagepack.aftereffect.Vignette
import java.awt.image.BufferedImage

object AfterEffect {
    fun apply(bufferedImage: BufferedImage, fov: Double): BufferedImage {
        var result: BufferedImage? = null

        result = BilateralFiltering      .applyEffect(bufferedImage)
        result = SubpixelMorphologicalAA .applyEffect(result)
        result = AutoExposure            .applyEffect(result)
        result = Bloom                   .applyEffect(result, fov)
        result = Vignette                .applyEffect(result)
        return result
    }
}