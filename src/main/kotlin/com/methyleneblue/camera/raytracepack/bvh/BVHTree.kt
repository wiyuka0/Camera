package com.methyleneblue.camera.raytracepack.bvh

import org.bukkit.Location
import org.bukkit.Material
import org.joml.Vector3f
import org.joml.Vector3i
import java.util.LinkedList
import java.util.Queue

class BVHTree {
    private val blocks = mutableListOf<Block>()
    var root: BVHNode? = null

    fun addBlock(location: Vector3i, scale: Vector3f = Vector3f(1f, 1f, 1f), material: Material) {
        val pos = Vector3f(location.x.toFloat(), location.y.toFloat(), location.z.toFloat())
        blocks.add(Block(pos, scale, material.getNewId()))
    }

    fun buildTree() {
        root = BVHNode(blocks)
    }

    fun rayTrace(start: Vector3f, dir: Vector3f): HitResult? {
        val hit = root?.intersect(start, dir) ?: return null
        val hitPosition = Vector3f(start).add(Vector3f(dir.normalize()).mul(hit.t))
        return HitResult(hitPosition, hit.t, hit.face, start, dir)
    }

    companion object {
        val materialsIdMap = HashMap<Material, Int>()
        init {
            var index = 0
            for (material in Material.entries) {
                materialsIdMap.put(material, index++)
            }
        }

        fun getBVHTree(location: Location, distance: Int): BVHTree {
            val origin = location.block

            val bvhTree = BVHTree()
            val visited = mutableSetOf<org.bukkit.block.Block>()
            val queue: Queue<org.bukkit.block.Block> = LinkedList()

            queue.add(origin)
            visited.add(origin)

            val directions = listOf(
                intArrayOf(1, 0, 0),
                intArrayOf(-1, 0, 0),
                intArrayOf(0, 1, 0),
                intArrayOf(0, -1, 0),
                intArrayOf(0, 0, 1),
                intArrayOf(0, 0, -1)
            )

            while (queue.isNotEmpty()) {
                val current = queue.poll()
                val curLoc = current.location

                // 添加非空气方块
                if (current.type != Material.AIR) {
                    bvhTree.addBlock( // run test main  //我看不到
                        location = Vector3i(curLoc.x.toInt(), curLoc.y.toInt(), curLoc.z.toInt()),
                        material = current.type,
                        scale = Vector3f(1f, 1f, 1f)
                    )
                }

                for ((dx, dy, dz) in directions) {
                    val neighbor = curLoc.clone().add(dx.toDouble(), dy.toDouble(), dz.toDouble()).block

                    if (neighbor !in visited &&
                        curLoc.distance(origin.location) <= distance
                    ) {
                        queue.add(neighbor)
                        visited.add(neighbor)
                    }
                }
            }
            bvhTree.buildTree()
            return bvhTree
        }
    }
}

fun Material.getNewId(): Int {
    return BVHTree.materialsIdMap[this]!!
}