package com.methyleneblue.camera.raytracepack.bvh

import org.bukkit.block.BlockFace
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import org.joml.Vector3f
import kotlin.math.*

class BVHNode(blocks: List<Block>) {

    val bounds: AABB
    val left: BVHNode?
    val right: BVHNode?
    val block: Block?

    init {
        if (blocks.size == 1) {
            block = blocks[0]
            bounds = block.aabb
            left = null
            right = null
        } else {
            val axis = (0..2).maxByOrNull { dim ->
                val positions = blocks.map { it.position[dim] }
                 positions.maxOrNull()!! - positions.minOrNull()!!
            }!!
//            val axis = (0..2).random()
            val sorted = blocks.sortedBy { it.position[axis] }
            val mid = sorted.size / 2
            left = BVHNode(sorted.subList(0, mid))
            right = BVHNode(sorted.subList(mid, sorted.size))
            block = null
            bounds = mergeBounds(left.bounds, right.bounds)
        }
    }

    private fun mergeBounds(a: AABB, b: AABB): AABB {
        val min = Vector3f(
            min(a.min.x, b.min.x),
            min(a.min.y, b.min.y),
            min(a.min.z, b.min.z)
        )
        val max = Vector3f(
            max(a.max.x, b.max.x),
            max(a.max.y, b.max.y),
            max(a.max.z, b.max.z)
        )
        return AABB(min, max)
    }

//    fun intersect(origin: Vector3f, dir: Vector3f): BlockHit? {
//        bounds.rayIntersect(origin, dir, 0f, Float.MAX_VALUE) ?: return null
//
//
//
////        if (block != null) {
////            val hit = block.aabb.rayIntersect(origin, dir, 0f, Float.MAX_VALUE)
////            // println("block: hit: " + hit?.t)
////            return hit?.let { BlockHit(block, it.t, it.face) } // 嗯...
////        } else {
////            // println("block == null")
////        }
//        if (block != null) {
//            // 如果射线起点就在 block 内部，跳过这个 block
//            if (!block.aabb.contains(origin)) {
//                val hit = block.aabb.rayIntersect(origin, dir, 0f, Float.MAX_VALUE)
//                return hit?.let { BlockHit(block, it.t, it.face) }
//            }
//        }
//
//        val hitL = left?.intersect(origin, dir)
//        val hitR = right?.intersect(origin, dir)
//        // println("hitL: ${hitL?.t}  ${hitR?.t} ") // 为什么这里没有输出
//        return when {
//            hitL != null && hitR != null -> if (hitL.t < hitR.t) hitL else hitR
//            hitL != null -> hitL
//            else -> hitR
//        }
//    }

    fun intersect(origin: Vector3f, dir: Vector3f): BlockHit? {

        val skipBlock = findContainingBlock(origin)
        // 先判断当前包围盒是否和射线相交
        bounds.rayIntersect(origin, dir, 0f, Float.MAX_VALUE) ?: return null
//        val boundBox = BoundingBox(
//            bounds.min.x.toDouble(),
//            bounds.min.y.toDouble(),
//            bounds.min.z.toDouble(),
//            bounds.max.x.toDouble(),
//            bounds.max.y.toDouble(),
//            bounds.max.z.toDouble()
//        ) b
//        if(boundBox.rayTrace(origin.toVector(), dir.toVector(), 100.0) == null) {
//            return null
//        }

        // 如果是叶节点且不是要跳过的 block
        if (block != null && block != skipBlock) { //  && block != skipBlock
            val hit = block.aabb.rayIntersect(origin, dir, 0f, Float.MAX_VALUE)
//            val boundBox1 = BoundingBox(
//                block.aabb.min.x.toDouble(),
//                block.aabb.min.y.toDouble(),
//                block.aabb.min.z.toDouble(),
//                block.aabb.max.x.toDouble(),
//                block.aabb.max.y.toDouble(),
//                block.aabb.max.z.toDouble()
//            )
//            val rrResult = boundBox1.rayTrace(origin.toVector(), dir.toVector(), 100.0) ?: return null
//            return rrResult.hitBlockFace?.let { BlockHit(block, rrResult.hitPosition.distance(origin.toVector()).toFloat(), it) }
            return hit?.let { BlockHit(block, it.t, it.face) }
        }

        // 递归左右子树
        val hitL = left?.intersect(origin, dir)
        val hitR = right?.intersect(origin, dir)

        return when {
            hitL != null && hitR != null -> if (hitL.t < hitR.t) hitL else hitR
            hitL != null -> hitL
            else -> hitR
        }
    }

    fun flatten(): List<FlatBVHNode> {
        val list = mutableListOf<FlatBVHNode>()
        fun dfs(node: BVHNode?): Int {
            if (node == null) return -1
            val index = list.size
            list.add(FlatBVHNode(
                node.bounds.min,
                node.bounds.max,
                -1, -1, // placeholder
                if (node.block != null) 1 else 0,
                node.block?.position ?: Vector3f(0f)
            ))
            val leftIndex = dfs(node.left)
            val rightIndex = dfs(node.right)
            list[index] = list[index].copy(leftIndex = leftIndex, rightIndex = rightIndex)
            return index
        }
        dfs(this)

        // println(list.toTypedArray().contentToString())
        return list
    }

    data class BlockHit(val block: Block, val t: Float, val face: BlockFace)
}

fun BVHNode.findContainingBlock(point: Vector3f): Block? {
    if (!bounds.contains(point)) return null

    if (block != null && block.aabb.contains(point)) {
        return block
    }

    return left?.findContainingBlock(point)
        ?: right?.findContainingBlock(point)
}

fun Vector3f.toVector(): Vector {
    return Vector(x, y, z)
}
