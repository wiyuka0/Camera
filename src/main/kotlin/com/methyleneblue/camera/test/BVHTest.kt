package com.methyleneblue.camera.test


import com.methyleneblue.camera.raytracepack.bvh.BVHTree
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.joml.Vector3f
import kotlin.math.*


class BVHTest {
    fun testAll() {
        testHitFromWest()
        testHitFromAbove()
        testMiss()
    }

    fun testHitFromWest() {
        println("== testHitFromWest ==")

        val tree = BVHTree()
//        tree.addBlock(Location(null, 0.0, 0.0, 0.0))

        val start = Vector3f(-5f, 0.5f, 0.5f)
        val end = Vector3f(5f, 0.4f, 0.5f)

        val result = tree.rayTrace(start, end)
        assert(result != null) { "Ray should hit block from WEST" }

        val hit = result!!
        assert(hit.face == BlockFace.WEST) { "Expected to hit WEST face, got ${hit.face}" }

        val hitPoint = Vector3f(start).add(Vector3f(end).sub(start).normalize().mul(hit.distance))
        assert(abs(hitPoint.x - 1f) < 0.001f) { "Hit X = ${hitPoint.x}, expected ~1.0 for WEST face" }
        assert(hitPoint.y in 0f..1f) { "Y in range" }
        assert(hitPoint.z in 0f..1f) { "Z in range" }

        println("PASSED testHitFromWest")
    }

    fun testHitFromAbove() {
        println("== testHitFromAbove ==")

        val tree = BVHTree()
//        tree.addBlock(Location(null, 0.0, 0.0, 0.0))

        val start = Vector3f(0.5f, 5f, 0.5f)
        val end = Vector3f(0.5f, -1f, 0.5f)

        val result = tree.rayTrace(start, end)
        assert(result != null) { "Ray should hit block from UP" }

        val hit = result!!
        assert(hit.face == BlockFace.UP) { "Expected UP face, got ${hit.face}" }

        val hitPoint = Vector3f(start).add(Vector3f(end).sub(start).normalize().mul(hit.distance))
        assert(abs(hitPoint.y - 1f) < 0.001f) { "Hit Y = ${hitPoint.y}, expected ~1.0 for UP face" }

        println("PASSED testHitFromAbove")
    }

    fun testMiss() {
        println("== testMiss ==")

        val tree = BVHTree()
//        tree.addBlock(Location(null, 10.0, 10.0, 10.0))

        val start = Vector3f(0f, 0f, 0f)
        val end = Vector3f(1f, 1f, 1f)

        val result = tree.rayTrace(start, end)
        assert(result == null) { "Expected miss, but got hit: $result" }

        println("PASSED testMiss")
    }
}