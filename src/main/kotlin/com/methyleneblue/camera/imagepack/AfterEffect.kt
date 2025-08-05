package com.methyleneblue.camera.imagepack

import com.methyleneblue.camera.imagepack.aftereffect.AutoExposure
import com.methyleneblue.camera.imagepack.aftereffect.BilateralFiltering
import com.methyleneblue.camera.imagepack.aftereffect.Bloom
import com.methyleneblue.camera.imagepack.aftereffect.DepthOfField
import com.methyleneblue.camera.imagepack.aftereffect.SubpixelMorphologicalAA
import com.methyleneblue.camera.imagepack.aftereffect.Vignette
import org.bukkit.boss.BossBar
import java.awt.image.BufferedImage

object AfterEffect {
    fun apply(bufferedImage: BufferedImage, depthImage: Array<FloatArray>, fov: Double, progressBar: BossBar? = null): BufferedImage {
        var result: BufferedImage? = null

        result = SubpixelMorphologicalAA      .applyEffect(bufferedImage, progressBar)
//        result = SubpixelMorphologicalAA .applyEffect(result, progressBar)
        result = AutoExposure            .applyEffect(result, progressBar)
//        result = DepthOfField            .applyEffect(result, depthImage, fov, progressBar)
        result = Bloom                   .applyEffect(result, fov, progressBar)
        result = Vignette                .applyEffect(result, progressBar)
        return result
    }

    fun apply(bufferedImage: BufferedImage, fov: Double): BufferedImage {
        var result: BufferedImage? = null

        result = SubpixelMorphologicalAA      .applyEffect(bufferedImage)
//        result = SubpixelMorphologicalAA .applyEffect(result)
        result = AutoExposure            .applyEffect(result)
        result = Bloom                   .applyEffect(result, fov)
        result = Vignette                .applyEffect(result)
        return result
    }
}