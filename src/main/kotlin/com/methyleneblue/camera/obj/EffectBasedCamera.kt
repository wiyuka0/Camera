package com.methyleneblue.camera.obj

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockFace.*
import org.bukkit.block.data.type.TrapDoor
import org.bukkit.entity.Player
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Vector
import org.joml.Vector3f
import java.awt.Color
import java.awt.Point
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class EffectBasedCamera(
    location: Location,
    size: Pair<Int, Int>,
    fov: Double, // 基于横向计算
    distance: Double,
    bufferedImage: BufferedImage,
    ): CameraObject(
    location = location,
    size,
    fov,
    distance,
        bufferedImage,
) {
//    override fun updateCamera(): BufferedImage {
//        val width = size.first
//        val height = size.second
//        val centerYaw = location.yaw
//        val centerPitch = location.pitch
//        val aspectRatio = width.toFloat() / height.toFloat()
//        val pitchFov = fov / aspectRatio
//        val pitchStart = centerPitch - (pitchFov / 2)
//        val yawStart = centerYaw - (fov / 2)
//        val image = bufferedImage
//        val angleStep = fov / width.toFloat() // X-Y Same Pixel Density, share `step`
//        for (i in 0 until width) {
//            val yaw = yawStart + (i * angleStep)
//            for (j in 0 until height) {
//                val pitch = pitchStart + (j * angleStep)
//                val dir = directionFromYawPitch(yaw.toFloat(), pitch.toFloat()).normalize()
//                val result = location.world.rayTraceBlocks(location, Vector(dir.x, dir.y, dir.z), distance)
//                val color = result?.let { getColorInWorld(it) } ?: Color.WHITE
//                image.setRGB(i, j, color.rgb)
//            }
//        }
//        return image
//    }

    override fun updateCamera(player: Player?): BufferedImage {
        val width = size.first
        val height = size.second
        val aspectRatio = width.toFloat() / height.toFloat()
        val fovRad = Math.toRadians(fov.toDouble())

        val image = bufferedImage
        val forward = location.direction.normalize()
        val upVector = Vector(0.0, 1.0, 0.0)
        val right = forward.clone().crossProduct(upVector).normalize()
        val up = right.clone().crossProduct(forward).normalize()

        val halfWidth = tan(fovRad / 2.0)
        val halfHeight = halfWidth / aspectRatio

        val numThreads = Runtime.getRuntime().availableProcessors()
        val rowsPerThread = height / numThreads
        val threads = mutableListOf<Thread>()

        val totalRayTraceCount = width * height
        var currentRayTraceCount = 0

        val useBossBar = player != null

        var progressBar: BossBar? = null
        if(useBossBar) {
            progressBar = BossBar.bossBar(Component.text("渲染进度"), 0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS)
            player.showBossBar(progressBar)
        }

        for (t in 0 until numThreads) {
            val startRow = t * rowsPerThread
            val endRow = if (t == numThreads - 1) height else (t + 1) * rowsPerThread

            val thread = Thread {
                for (j in startRow until endRow) {
                    val v = (1.0 - (j + 0.5) / height) * 2 - 1
                    for (i in 0 until width) {
                        val u = ((i + 0.5) / width) * 2 - 1
                        currentRayTraceCount += 1
                        if(currentRayTraceCount % 10000 == 0) progressBar?.progress((currentRayTraceCount.toFloat() / totalRayTraceCount.toFloat()).toFloat())

                        val dir = forward.clone()
                            .add(right.clone().multiply(u * halfWidth))
                            .add(up.clone().multiply(v * halfHeight))
                            .normalize()

                        val result = location.world.rayTraceBlocks(location, dir, distance)
                        val color = getColorInWorld(result)
                        image.setRGB(i, j, color.rgb)
                    }
                }
            }
            thread.start()
            threads.add(thread)
        }

        // 等待所有线程完成
        threads.forEach { it.join() }

        progressBar?.let { player?.hideBossBar(it) }

        return image
    }

    private val textureCache = ConcurrentHashMap<String, BufferedImage>()

    private val sideShadow = 0.6

    fun getColorInWorld(rayTraceResult: RayTraceResult?): Color {
        val currentTime = location.world.time
        val texturesPath = File(Bukkit.getPluginsFolder().path + "\\Camera\\textures")
        if (!texturesPath.exists()) texturesPath.mkdirs()

        if(rayTraceResult == null) return skyColor(currentTime)

        val hitBlock = rayTraceResult.hitBlock ?: return skyColor(currentTime)
        val hitFace = rayTraceResult.hitBlockFace ?: return skyColor(currentTime)
        val hitBlockNamespaceKey = hitBlock.type.toString().lowercase(Locale.getDefault())

        var textureFileName = "$hitBlockNamespaceKey.png"
        var textureFile = File(texturesPath, textureFileName)

        if (!textureFile.exists()) {
            val hitFaceKey = when (hitFace) {
                UP -> "top"
                DOWN -> "bottom"
                else -> hitFace.name.lowercase(Locale.getDefault())
            }

            textureFileName = "${hitBlockNamespaceKey}_$hitFaceKey.png"
            textureFile = File(texturesPath, textureFileName)

            if (!textureFile.exists()) {
                textureFileName = "${hitBlockNamespaceKey}_side.png"
                textureFile = File(texturesPath, textureFileName)

                if (!textureFile.exists()) return skyColor(currentTime)
            }
        }

        // 使用缓存，key 是完整文件路径
        val image = textureCache.computeIfAbsent(textureFile.absolutePath) {
            ImageIO.read(textureFile)
        }

        val relativeHit = rayTraceResult.hitPosition.subtract(hitBlock.location.toVector())
        val x = relativeHit.x.toFloat()
        val y = relativeHit.y.toFloat()
        val z = relativeHit.z.toFloat()
        val texX: Float
        val texY: Float
        val width = image.width.toFloat()
        val height = image.height.toFloat()

        when (hitFace) {
            NORTH -> {
                texX = (1f - x) * width
                texY = (1f - y) * height
            }
            SOUTH -> {
                texX = x * width
                texY = (1f - y) * height
            }
            EAST -> {
                texX = (1f - z) * width
                texY = (1f - y) * height
            }
            WEST -> {
                texX = z * width
                texY = (1f - y) * height
            }
            UP -> {
                texX = x * width
                texY = (1f - z) * height
            }
            DOWN -> {
                texX = x * width
                texY = z * height
            }
            else -> return skyColor(currentTime)
        }

        val clampedX = texX.coerceIn(0f, width - 1)
        val clampedY = texY.coerceIn(0f, height - 1)

        val baseColor = Color(image.getRGB(clampedX.toInt(), clampedY.toInt()))
        if(hitFace == BlockFace.UP) {
            return baseColor
        } else {
            return Color((baseColor.red * sideShadow).toInt(), (baseColor.green * sideShadow).toInt(), (baseColor.blue * sideShadow).toInt(), baseColor.alpha)
        }
    }


    fun directionFromYawPitch(yaw: Float, pitch: Float): Vector3f {
        val radYaw = Math.toRadians(yaw.toDouble())
        val radPitch = Math.toRadians(pitch.toDouble())
        val x = -cos(radPitch) * sin(radYaw)
        val y = -sin(radPitch)
        val z = cos(radPitch) * cos(radYaw)
        return Vector3f(x.toFloat(), y.toFloat(), z.toFloat())
    }
}


infix fun Float.step(other: Float): Float = this + other