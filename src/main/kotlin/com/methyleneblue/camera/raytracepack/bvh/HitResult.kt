package com.methyleneblue.camera.raytracepack.bvh

import com.methyleneblue.camera.util.VectorUtil
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.joml.Vector3f
import kotlin.math.floor

class HitResult(val hitPosition: Vector3f?, val distance: Float, val face: BlockFace?, val startPos: Vector3f? = null, val direction: Vector3f? = null) {}

fun World.getBlockAtOnSurface(v3f: Vector3f, face: BlockFace, epsilon: Float = 0.001f): org.bukkit.block.Block {
    val realPos = Vector3f(v3f).add(Vector3f(VectorUtil.faceToNormalMap[face]).mul(-epsilon))
    return this.getBlockAt(floor(realPos.x).toInt(), floor(realPos.y).toInt(), floor(realPos.z).toInt())
}