package com.methyleneblue.camera.raytracepack.bvh

import org.joml.Vector3f

data class Block(val position: Vector3f, val bukkitBlock: org.bukkit.block.Block?, val scale: Vector3f = Vector3f(1f, 1f, 1f), val materialId: Int = 0) {
    val aabb: AABB = AABB(position, Vector3f(position).add(scale))
}