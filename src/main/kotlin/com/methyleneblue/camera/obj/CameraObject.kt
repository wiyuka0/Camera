package com.methyleneblue.camera.obj

import org.bukkit.Location
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import java.awt.image.BufferedImage

abstract class CameraObject(
    var location: Location,
    val size: Pair<Int, Int>,
    val fov: Double,
    val distance: Double,
    var progressBar: BossBar?,
    var bufferedImage: BufferedImage,
    var depthImage: Array<FloatArray>,
) {

    abstract fun updateCamera(player: Player?, mixinTimes: Int = 1, maxDepth: Float = 20.0f): Pair<BufferedImage, Array<FloatArray>>
}