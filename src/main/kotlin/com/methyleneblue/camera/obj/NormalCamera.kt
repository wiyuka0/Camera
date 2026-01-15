package com.methyleneblue.camera.obj

import com.methyleneblue.camera.raytracepack.bvh.BVHTree
import com.methyleneblue.camera.raytracepack.bvh.FlatBVHNode
import com.methyleneblue.camera.raytracepack.bvh.HitResult
import org.bukkit.Location
import org.bukkit.boss.BossBar
import org.bukkit.util.RayTraceResult
import org.joml.Vector3f
import java.awt.Color
import java.awt.image.BufferedImage

class NormalCamera(
    location: Location,
    size: Pair<Int, Int>,
    fov: Double,
    distance: Double,
    progressBar: BossBar?,
    bufferedImage: BufferedImage,
    depthImage: Array<FloatArray>
): BVHCamera(
    location = location,
    size,
    fov,
    distance,
    progressBar,
    bufferedImage,
    depthImage,
) {
    override fun getColorInWorld(
        rayTraceResult: HitResult?,
        startDir: Vector3f,
        flatBVHNode: Array<FlatBVHNode>,
        bvhTree: BVHTree
    ): Color {
        if (rayTraceResult == null) {
            return Color(0, 0, 0)
        }

        val normalVec: Vector3f = rayTraceResult.face?.direction?.toVector3f() ?: Vector3f()

        // 将 [-1,1] 映射到 [0,255]
        val r = ((normalVec.x + 1f) * 0.5f * 255f).toInt().coerceIn(0, 255)
        val g = ((normalVec.y + 1f) * 0.5f * 255f).toInt().coerceIn(0, 255)
        val b = ((normalVec.z + 1f) * 0.5f * 255f).toInt().coerceIn(0, 255)

        return Color(r, g, b)
    }
}