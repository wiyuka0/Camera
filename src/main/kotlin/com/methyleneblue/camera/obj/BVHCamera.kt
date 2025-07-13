package com.methyleneblue.camera.obj

import com.methyleneblue.camera.raytracepack.bvh.BVHTree
import com.methyleneblue.camera.raytracepack.bvh.BVHTree.Companion.getBVHTree
import com.methyleneblue.camera.raytracepack.bvh.FlatBVHNode
import com.methyleneblue.camera.raytracepack.bvh.HitResult
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.joml.Vector3f
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ln
import kotlin.math.tan

abstract class BVHCamera(
    location: Location,
    size: Pair<Int, Int>,
    fov: Double,
    distance: Double,
    bufferedImage: BufferedImage,
    depthImage: BufferedImage
    ): CameraObject(
    location = location,
    size,
    fov,
    distance,
    bufferedImage,
    depthImage,
) {

    abstract fun getColorInWorld(rayTraceResult: HitResult?, startDir: Vector3f, flatBVHNode: Array<FlatBVHNode>, bvhTree: BVHTree): Color

    /* Single Thread */
//    override fun updateCamera(player: Player?, mixinTimes: Int): BufferedImage {
//        val bvhTree = getBVHTree(location, distance.toInt())
//        val flatBVHNode = bvhTree.root!!.flatten()
//
//        val width = size.first
//        val height = size.second
//        val aspectRatio = width.toFloat() / height.toFloat()
//        val fovRad = Math.toRadians(fov)
//
//        val forward = location.direction.normalize()
//        val upVector = Vector(0.0, 1.0, 0.0)
//        val right = forward.clone().crossProduct(upVector).normalize()
//        val up = right.clone().crossProduct(forward).normalize()
//
//        val halfWidth = tan(fovRad / 2.0)
//        val halfHeight = halfWidth / aspectRatio
//
//        val totalRayTraceCount = width * height
//        val totalRayTraceCountFloat = totalRayTraceCount.toFloat()
//
//        var progressBar: BossBar? = null
//        val useBossBar = player != null
//
//        if (useBossBar) {
//            progressBar = BossBar.bossBar(Component.text("渲染进度"), 0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS)
//            Bukkit.getOnlinePlayers().filter { it.isOp }.forEach { it.showBossBar(progressBar) }
//        }
//
//        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
//        var count = 0
//
//        val updatePerTimes = max(1, totalRayTraceCount / 1000)
//
//        data class Pack(val i: Int, val j: Int, val result: AsyncFuture<HitResult?>)
//        val future1: MutableList<Pack> = mutableListOf()
//
//        for (j in 0 until height) {
//            val v = (1.0 - (j + 0.5) / height) * 2 - 1
//            for (i in 0 until width) {
//                val u = ((i + 0.5) / width) * 2 - 1
//                count++
//
//                if (useBossBar && count % updatePerTimes == 0) {
//                    progressBar?.progress((count.toFloat() / totalRayTraceCountFloat).coerceIn(0f, 1f))
//                }
//
//                val dir = forward.clone()
//                    .add(right.clone().multiply(u * halfWidth))
//                    .add(up.clone().multiply(v * halfHeight))
//                    .normalize()
//
//                // val target = location.clone().add(dir.clone().multiply(5))
//                // location.world.spawnParticle(Particle.ELECTRIC_SPARK, target.x, target.y, target.z, 1, 0.0, 0.0, 0.0, 0.0, null, true)
//
////                val hit = bvhTree.rayTrace(location.toVector().toVector3f(), dir.toVector3f())
//
//                val hit = JoclInterface.traceRay(location.toVector().toVector3f(), dir.toVector3f())
//
//                future1.add(Pack(
//                    i, j, hit
//                ))
//            }
//        }
//        val arrayFlatBVHNode = flatBVHNode.toTypedArray()
//
//        JoclInterface.processResults(arrayFlatBVHNode, bvhTree)
//
//        for ((i, j, pack) in future1) {
//            val hit = pack.get()
//
//            if(hit == null) {
//                val skyColor = TextureManager.skyColor(time = location.world.time)
//                image.setRGB(i, j, skyColor.rgb)
//                continue
//            }
//            val dir = hit.direction
//            val rayDir = Vector3f(dir)
//            var rSum = 0
//            var gSum = 0
//            var bSum = 0
//
//            repeat(mixinTimes) {
//                val color = getColorInWorld(hit, Vector3f(rayDir), arrayFlatBVHNode, bvhTree)
//                rSum += color.red
//                gSum += color.green
//                bSum += color.blue
//            }
//
//            val rAvg = rSum / mixinTimes
//            val gAvg = gSum / mixinTimes
//            val bAvg = bSum / mixinTimes
//
//            image.setRGB(i, j, Color(rAvg, gAvg, bAvg).rgb)
//        }
//
//        progressBar?.let { bar ->
//            Bukkit.getOnlinePlayers().filter { it.isOp }.forEach { it.hideBossBar(bar) }
//        }
//
//        bufferedImage = image
//        return image
//    }

    /* Multi Thread*/
    override fun updateCamera(player: Player?, mixinTimes: Int, maxDepth: Float): Pair<BufferedImage, BufferedImage> {

        val depthImage = BufferedImage(this.bufferedImage.width, this.bufferedImage.height, BufferedImage.TYPE_INT_RGB)

        val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

        val bvhTree = getBVHTree(location, distance.toInt())
        val flatBVHNode = bvhTree.root!!.flatten().toTypedArray()
        val width = size.first
        val height = size.second
        val aspectRatio = width.toFloat() / height.toFloat()
        val fovRad = Math.toRadians(fov.toDouble())

        val forward = location.direction.normalize()
        val upVector = Vector(0.0, 1.0, 0.0)
        val right = forward.clone().crossProduct(upVector).normalize()
        val up = right.clone().crossProduct(forward).normalize()

        val halfWidth = tan(fovRad / 2.0)
        val halfHeight = halfWidth / aspectRatio

        val numThreads = Runtime.getRuntime().availableProcessors()
        val rowsPerThread = height / numThreads


        val totalRayTraceCount = width * height
        val totalRayTraceCountFloat = totalRayTraceCount.toFloat()

        val useBossBar = player != null

        var progressBar: BossBar? = null
        if (useBossBar) {
            progressBar = BossBar.bossBar(Component.text("渲染进度"), 0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS)
            for (player in Bukkit.getOnlinePlayers()) {
                if (player.isOp) {
                    player.showBossBar(progressBar)
                }
            }
        }

        var currentRayTraceCount = AtomicInteger(0)
        val results = Array<BufferedImage?>(numThreads) { null }
        val futures = mutableListOf<CompletableFuture<Void>>()
        for (t in 0 until numThreads) {
            val startRow = t * rowsPerThread
            val endRow = if (t == numThreads - 1) height else (t + 1) * rowsPerThread

            futures.add(CompletableFuture.runAsync({
                val threadImage = BufferedImage(width, endRow - startRow, BufferedImage.TYPE_INT_RGB)
                for (j in startRow until endRow) {
                    val v = (1.0 - (j + 0.5) / height) * 2 - 1
                    for (i in 0 until width) {
                        val u = ((i + 0.5) / width) * 2 - 1
                        val count = currentRayTraceCount.incrementAndGet()
                        if (count % 10000 == 0 && useBossBar) progressBar?.progress((count.toFloat() / totalRayTraceCountFloat).toFloat())

                        val dir = forward.clone()
                            .add(right.clone().multiply(u * halfWidth))
                            .add(up.clone().multiply(v * halfHeight))
                            .normalize()

                        val result = bvhTree.rayTrace(location.toVector().toVector3f(), dir.toVector3f())

                        val distance = result?.distance

                        if (distance != null) {
                            val logDepth = ln(distance + 1e-6) / ln(1.0 + 1e-6 + 1.0)
                            val depthColor = logDepth.toInt().coerceAtLeast(0)

                            val rgb = depthColor and 0xFFFFFFFF.toInt()
                            depthImage.setRGB(i, j - startRow, rgb)
                        }

                        var rSum = 0
                        var gSum = 0
                        var bSum = 0

                        val dirVec3f = Vector3f(dir.toVector3f())

                        repeat(mixinTimes) {
                            val color = getColorInWorld(
                                result, Vector3f().set(dirVec3f),
                                flatBVHNode,
                                bvhTree
                            )
                            rSum += color.red
                            gSum += color.green
                            bSum += color.blue
                        }

                        val rAvg = rSum / mixinTimes
                        val gAvg = gSum / mixinTimes
                        val bAvg = bSum / mixinTimes

                        threadImage.setRGB(i, j - startRow, Color(rAvg, gAvg, bAvg).rgb)
                    }
                }
                results[t] = threadImage
            }, executor))
        }

        futures.forEach { it.join() }

        var finalImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = finalImage.graphics
        for (t in 0 until numThreads) {
            val startRow = t * rowsPerThread
            g.drawImage(results[t], 0, startRow, null)
        }
        g.dispose()

        progressBar?.let {
            for (player in Bukkit.getOnlinePlayers()) {
                if (player.isOp) {
                    player.hideBossBar(it)
                }
            }
        }
        bufferedImage = finalImage
        this.depthImage = depthImage

        return finalImage to depthImage
    }
}