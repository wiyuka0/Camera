package com.methyleneblue.camera.obj

import com.methyleneblue.camera.raytracepack.bvh.BVHTree
import com.methyleneblue.camera.raytracepack.bvh.FlatBVHNode
import com.methyleneblue.camera.raytracepack.bvh.HitResult
import com.methyleneblue.camera.raytracepack.bvh.getBlockAtOnSurface
import com.methyleneblue.camera.texture.TextureManager
import com.methyleneblue.camera.texture.TextureManager.skyColor
import com.methyleneblue.camera.util.VectorUtil
import org.bukkit.Bukkit
import org.bukkit.Location
import org.joml.Vector3f
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

class EffectBasedCamera(
    location: Location,
    size: Pair<Int, Int>,
    fov: Double, // 基于横向计算
    distance: Double,
    bufferedImage: BufferedImage
    ): BVHCamera(
    location = location,
    size,
    fov,
    distance,
    bufferedImage
) {

    private val textureCache = ConcurrentHashMap<String, BufferedImage>()

    private val sideShadow = 0.6

    override fun getColorInWorld(rayTraceResult: HitResult?, startDir: Vector3f, flatBVHNode: Array<FlatBVHNode>, bvhTree: BVHTree): Color {
        val currentTime = location.world.time
        val texturesPath = File(Bukkit.getPluginsFolder().path + "\\Camera\\textures")
        if (!texturesPath.exists()) texturesPath.mkdirs()

        if (rayTraceResult == null || rayTraceResult.face == null) return skyColor(currentTime)

        val stepLength = 0.2f

        val hitPosition = Vector3f(rayTraceResult.startPos).add(Vector3f(rayTraceResult.direction).mul(rayTraceResult.distance)).add(
            Vector3f(VectorUtil.faceToNormalMap[rayTraceResult.face]).mul(-stepLength))
        // val hitPosition = rayTraceResult.hitPosition
//        if(rayTraceResult.hitPosition == null) return skyColor(currentTime)

        val hitBlock = location.world.getBlockAtOnSurface(hitPosition, rayTraceResult.face)
        val hitFace1 = rayTraceResult.face
        val hitBlockType = hitBlock.type
        
        val hitBlockLocation1 = hitBlock.location
        // return Color(0xFF000000.toInt())

        return TextureManager.getWorldColorTexture(hitBlockType, hitPosition, hitFace1, currentTime)!!
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