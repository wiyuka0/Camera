package com.methyleneblue.camera.obj

import com.methyleneblue.camera.obj.raytrace.RayTraceMaterial
import com.methyleneblue.camera.raytracepack.bvh.BVHTree
import com.methyleneblue.camera.raytracepack.bvh.BVHTree.Companion.getBVHTree
import com.methyleneblue.camera.raytracepack.bvh.FlatBVHNode
import com.methyleneblue.camera.raytracepack.bvh.HitResult
import com.methyleneblue.camera.raytracepack.bvh.getBlockAtOnSurface
import com.methyleneblue.camera.raytracepack.bvh.toVector
import com.methyleneblue.camera.texture.TextureManager
import com.methyleneblue.camera.util.VectorUtil
import com.methyleneblue.camera.util.toVector3f
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Vector
import org.joml.Vector3f
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.ln
import kotlin.math.tan

class SingleRayTraceCamera(location: Location, size: Pair<Int, Int>, fov: Double, distance: Double,
                           progressBar: BossBar?, bufferedImage: BufferedImage, depthImage: Array<FloatArray>
) : BVHCamera(location, size, fov,
    distance, progressBar, bufferedImage, depthImage
) {

    val maxReflectionTimes = 10

    override fun updateCamera(
        player: Player?,
        mixinTimes: Int,
        maxDepth: Float
    ): Pair<BufferedImage, Array<FloatArray>> {
        val depthImage = Array(bufferedImage.width) { FloatArray(bufferedImage.height) { 0f } }

        val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

        progressBar?.setTitle("渲染 - 构建 BVH 树")
        progressBar?.progress = 1.0

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
        val totalRayTraceCountDouble = totalRayTraceCount.toDouble()

        progressBar?.setTitle("渲染 - 射线追踪")
        progressBar?.progress = 0.0

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
                        if (count % 1000 == 0) progressBar?.progress = count.toDouble() / totalRayTraceCountDouble

                        val dir = forward.clone()
                            .add(right.clone().multiply(u * halfWidth))
                            .add(up.clone().multiply(v * halfHeight))
                            .normalize()

//                        val result = bvhTree.rayTrace(location.toVector().toVector3f(), dir.toVector3f())
                        val hitResult = location.world.rayTraceBlocks(location, dir, distance)

//                        val distance = hitResult?.hitPosition?.distance(location.toVector())?.toFloat() ?: 0.0f
//                        val result = HitResult(
//                            hitPosition = hitResult?.hitPosition?.toVector3f(),
//                            distance = distance,
//                            face = hitResult?.hitBlockFace,
//                            startPos = location.toVector3f(),
//                            direction = dir.toVector3f(),
//                        )

//                        val distance = result?.distance

                        if (distance != null) {
                            val logDepth = ln(distance + 1.0)

                            depthImage[i][j] = logDepth.toFloat()
                        }

                        var rSum = 0
                        var gSum = 0
                        var bSum = 0

                        val dirVec3f = Vector3f(dir.toVector3f())

                        val results = mutableListOf<Pair<Color, Float>>()

//                        val hitResultStart = location.world.rayTraceBlocks(location, dirVec3f.toVector(), distance.toDouble())

                        repeat(mixinTimes) {
                            val colorData = getColorInWorldA(hitResult, dirVec3f)
                            results += colorData
                        }

// 累加总权重
                        val totalWeight = results.sumOf { it.second.toDouble() }.toFloat()

// 防止除以 0
                        val finalColor = if (totalWeight == 0f) {
                            Color(0, 0, 0)
                        } else {
                            val (r, g, b) = results.fold(Triple(0f, 0f, 0f)) { acc, (color, weight) ->
                                val rf = color.red / 255f
                                val gf = color.green / 255f
                                val bf = color.blue / 255f
                                Triple(
                                    acc.first + rf * weight,
                                    acc.second + gf * weight,
                                    acc.third + bf * weight
                                )
                            }

                            // 归一化后转换为 Color
                            Color(
                                (r / totalWeight * 255).toInt().coerceIn(0, 255),
                                (g / totalWeight * 255).toInt().coerceIn(0, 255),
                                (b / totalWeight * 255).toInt().coerceIn(0, 255)
                            )
                        }
//                        repeat(mixinTimes) {
//                            val color = getColorInWorldA(
//                                result, Vector3f().set(dirVec3f),
//                                flatBVHNode,
//                                bvhTree
//                            )
//                            rSum += color.red
//                            gSum += color.green
//                            bSum += color.blue
//                        }
//
//                        val rAvg = rSum / mixinTimes
//                        val gAvg = gSum / mixinTimes
//                        val bAvg = bSum / mixinTimes

                        threadImage.setRGB(i, j - startRow, finalColor.rgb)
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

        bufferedImage = finalImage
        this.depthImage = depthImage

        var minLog = Float.MAX_VALUE
        var maxLog = Float.MIN_VALUE

        for (row in depthImage) {
            for (depth in row) {
                minLog = minOf(minLog, depth)
                maxLog = maxOf(maxLog, depth)
            }
        }

        val logImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val depth = depthImage[x][y]

                if (depth < 0.0f) {
                    logImage.setRGB(x, y, 0xFFFF0000.toInt())
                } else if (depth == 0.0f) {
                    logImage.setRGB(x, y, 0xFF0000FF.toInt())
                } else {
                    val normalized = (depth - minLog) / (maxLog - minLog)
                    val gray = (normalized.coerceIn(0f, 1f) * 255.0f).toInt()

                    val rgb = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                    logImage.setRGB(x, y, rgb)
                }
            }
        }

        ImageIO.write(logImage, "png", File("C:\\image\\depth_output.png"))

        return finalImage to depthImage
    }

    fun getColorInWorldA(
        rayTraceResult: RayTraceResult?,
        startDir: Vector3f
    ): Pair<Color, Float> {
        val defaultColor = Color(0, 0, 0) to 0.0f
        val baseColor = getColorInWorldTexture(rayTraceResult?.hitPosition?.toVector3f() ?: return defaultColor, rayTraceResult.hitBlockFace ?: return defaultColor)
        if(baseColor == null) return defaultColor
        val normalizedColor = baseColor.toOneRange()
        var currentReflectionTimes = 0

        var startHitResult = rayTraceResult
        var currentWeight = 1.0f
        var currentColorWeight = normalizedColor
        var lastDirection = startDir
        while (currentReflectionTimes < maxReflectionTimes) {
            val hitFace = startHitResult?.hitBlockFace
            val hitFaceNormal = VectorUtil.faceToNormalMap[hitFace]
            if(hitFaceNormal == null || startHitResult?.hitBlockFace == null)
                return Color((currentColorWeight.x * 255).toInt(), (currentColorWeight.y * 255).toInt(), (currentColorWeight.z * 255).toInt()) to currentWeight
            val hitBlock = location.world.getBlockAtOnSurface(startHitResult.hitPosition.toVector3f(),
                startHitResult.hitBlockFace!!
            )
            val hitMaterial = RayTraceMaterial.getMaterialReflectionData(hitBlock.type)
            if(hitMaterial.isLight) {
                val weightedLightColor = hitMaterial.lightColor.toOneRange().toVector3f().mul(currentColorWeight)
                return weightedLightColor.toColor() to currentWeight
            }


            val direction = lastDirection
            val idealReflectionDirection = Vector3f()
            VectorUtil.getReflectedVector(
                incident = direction,
                planeNormal = hitFaceNormal,
                output = idealReflectionDirection
            )

            val reflectionDirection = Vector3f()

            VectorUtil.perturbDirection(
                idealReflectionDirection,
                spread = hitMaterial.spread,
                output = reflectionDirection,
            )
//            val offsetStartPos = reflectionDirection.add(hitFaceNormal.toVector3f().mul(0.01f))

            val newHitResult = location.world.rayTraceBlocks(location, reflectionDirection.toVector(), distance)

            if(newHitResult == null || newHitResult.hitBlockFace == null || newHitResult.hitBlock == null) {

                return defaultColor // TODO sky color
            }
            val newHitColor = getColorInWorldTexture(newHitResult.hitPosition.toVector3f(), newHitResult.hitBlockFace!!)
            if(newHitColor == null) return defaultColor
            val weight = hitMaterial.weight(idealReflectionDirection.toVector3f().dot(reflectionDirection))
            currentColorWeight = currentColorWeight.mul(newHitColor.toOneRange())
            currentWeight *= weight.toFloat()
            if (currentWeight < 0.01f) break

            startHitResult = newHitResult
            lastDirection = reflectionDirection
            currentReflectionTimes++
        }
        return currentColorWeight.toColor() to currentWeight
    }

    override fun getColorInWorld(
        rayTraceResult: HitResult?,
        startDir: Vector3f,
        flatBVHNode: Array<FlatBVHNode>,
        bvhTree: BVHTree
    ): Color {
        // Placeholder
        return Color(0, 0, 0)
    }
    fun getColorInWorldTexture(rayTraceResult: HitResult?, hitPosition: Vector3f): Color? {
        if (rayTraceResult == null || rayTraceResult.hitPosition == null || rayTraceResult.face == null) return null

        val hitBlock = location.world.getBlockAtOnSurface(rayTraceResult.hitPosition, rayTraceResult.face)
        val image = TextureManager.getTexture(hitBlock.type, rayTraceResult.face) ?: return null
        val relativeHit = hitPosition.sub(hitBlock.location.toVector().toVector3f())

        val (texX, texY) = TextureManager.getTextureCoords(rayTraceResult.face, relativeHit.x, relativeHit.y, relativeHit.z, image.width, image.height)
        return Color(image.getRGB(texX, texY))
    }


    fun getColorInWorldTexture(hitPosition: Vector3f, face: BlockFace): Color? {
        val hitBlock = location.world.getBlockAtOnSurface(hitPosition, face)
        val image = TextureManager.getTexture(hitBlock.type, face) ?: return null
        val relativeHit = hitPosition.sub(hitBlock.location.toVector().toVector3f())

        val (texX, texY) = TextureManager.getTextureCoords(face, relativeHit.x, relativeHit.y, relativeHit.z, image.width, image.height)
        return Color(image.getRGB(texX, texY))
    }
}

fun Vector3f.toColor(): Color {
    return Color((this.x * 255.0f).toInt(), (this.y * 255.0f).toInt(), (this.z * 255.0f).toInt())
}