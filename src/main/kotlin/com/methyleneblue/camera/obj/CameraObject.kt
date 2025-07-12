package com.methyleneblue.camera.obj

import org.bukkit.Location
import org.bukkit.entity.Player
import java.awt.Color
import java.awt.image.BufferedImage

abstract class CameraObject(
    val location: Location,
    val size: Pair<Int, Int>,
    val fov: Double,
    val distance: Double,
    var bufferedImage: BufferedImage,
) {

    abstract fun updateCamera(player: Player?, mixinTimes: Int = 1, maxDepth: Float = 20.0f): Pair<BufferedImage, BufferedImage>
}