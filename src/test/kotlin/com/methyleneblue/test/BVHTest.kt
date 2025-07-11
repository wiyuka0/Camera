package com.methyleneblue.test

import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import com.methyleneblue.camera.raytracepack.bvh.BVHTree
import org.bukkit.Location
import org.bukkit.World
import org.joml.Vector3f
import org.junit.jupiter.api.Test


class BVHTest {
    private val world: World? = null // 测试中可为 null，不影响 BVH 逻辑
    fun testBasicRayHit() {
        val bvh = BVHTree()

        // 添加一个方块 (0, 0, 0)
        bvh.addBlock(Location(world, 0.0, 0.0, 0.0))

        // 从 (-1, 0.5, 0.5) 朝 (2, 0.5, 0.5) 发射射线，应命中
        val start = Vector3f(-1f, 0.5f, 0.5f)
        val end = Vector3f(2f, 0.5f, 0.5f)
        val hit = bvh.rayTrace(start, end)
        assertNotNull(hit, "应命中方块")
        assertEquals(0, hit.blockX)
        assertEquals(0, hit.blockY)
        assertEquals(0, hit.blockY)
        assertEquals(0, hit.blockZ)
    }

    fun testMissRay() {
        val bvh = BVHTree()
        bvh.addBlock(Location(world, 10.0, 10.0, 10.0))

        val start = Vector3f(0f, 0f, 0f)
        val end = Vector3f(1f, 1f, 1f)
        val hit = bvh.rayTrace(start, end)

        assertNull(hit, "应未命中任何方块")
    }

    @Test
    fun runAllTests() {
        println("Running BVH tests...")
        testBasicRayHit()
        testMissRay()
        println("All BVH tests passed.")
    }
}