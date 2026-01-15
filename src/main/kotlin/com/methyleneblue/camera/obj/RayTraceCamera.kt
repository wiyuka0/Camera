package com.methyleneblue.camera.obj


import com.methyleneblue.camera.util.VectorUtil
import com.methyleneblue.camera.obj.raytrace.RayTraceMaterial
import com.methyleneblue.camera.obj.raytrace.getReflectionMaterialData
import com.methyleneblue.camera.raytracepack.bvh.BVHTree
import com.methyleneblue.camera.raytracepack.bvh.BVHTree.Companion.getBVHTree
import com.methyleneblue.camera.raytracepack.bvh.Block
import com.methyleneblue.camera.raytracepack.bvh.FlatBVHNode
import com.methyleneblue.camera.raytracepack.bvh.HitResult
import com.methyleneblue.camera.raytracepack.bvh.getBlockAtOnSurface
import com.methyleneblue.camera.raytracepack.bvh.jocl.async.AsyncFuture
import com.methyleneblue.camera.texture.TextureManager
import com.methyleneblue.camera.texture.TextureManager.skyColor
import com.methyleneblue.camera.util.nextFloat
import com.methyleneblue.camera.util.toVector3f
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.type.Light
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.joml.Vector3f
import org.joml.Vector3i
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.math.ln
import kotlin.math.tan
import kotlin.random.Random

class RayTraceCamera(
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
    companion object {
        private const val REFLECTION_FACTOR = 1.0
        private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

        private const val MAX_THREADS = 100

        fun findSeaLanternsUsingThreads(
//            camera: RayTraceCamera,
            world: World,
            a: Location,
            b: Location,
            callback: (List<Location>) -> Unit
        ) {
            val x1 = minOf(a.blockX, b.blockX)
            val x2 = maxOf(a.blockX, b.blockX)
            val y1 = minOf(a.blockY, b.blockY)
            val y2 = maxOf(a.blockY, b.blockY)
            val z1 = minOf(a.blockZ, b.blockZ)
            val z2 = maxOf(a.blockZ, b.blockZ)

            val chunks = HashSet<Chunk>()
            for (x in x1..x2) {
                for (z in z1..z2) {
                    chunks.add(world.getChunkAt(x shr 4, z shr 4))
                }
            }

            val snapshots = HashMap<Chunk, ChunkSnapshot>()
            for (c in chunks) {
                snapshots[c] = c.chunkSnapshot
            }

            Thread {
                val result = ArrayList<Location>()

                for ((chunk, snap) in snapshots) {
                    val chunkX = chunk.x shl 4
                    val chunkZ = chunk.z shl 4

                    for (x in x1..x2) {
                        for (y in y1..y2) {
                            for (z in z1..z2) {
                                if ((x shr 4) == chunk.x && (z shr 4) == chunk.z) {
                                    val bx = x and 15
                                    val bz = z and 15

                                    val type = snap.getBlockType(bx, y, bz)
                                    if (type == Material.SEA_LANTERN) {
                                        result.add(Location(world, x.toDouble(), y.toDouble(), z.toDouble()))
                                    }
                                }
                            }
                        }
                    }
                }
                callback.invoke(result)
            }.start()
        }
    }

    /*

//    override fun updateCamera(player: Player?, mixinTimes: Int, maxDepth: Float): Pair<BufferedImage, Array<FloatArray>> {
//        val depthImage = Array(bufferedImage.width) { FloatArray(bufferedImage.height) { 0f } }
//
//        progressBar?.setTitle("渲染 - 构建 BVH 树")
//        progressBar?.progress = 1.0
//
//        val bvhTree = getBVHTree(location, distance.toInt())
//        val flatBVHNode = bvhTree.root!!.flatten().toTypedArray()
//
//        val width = size.first
//        val height = size.second
//
//        val aspectRatio = width.toFloat() / height.toFloat()
//        val fovRad = Math.toRadians(fov.toDouble())
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
//        val totalRayTraceCountDouble = totalRayTraceCount.toDouble()
//
//        progressBar?.setTitle("渲染 - 射线追踪 - 添加至队列")
//        progressBar?.progress = 0.0
//
//        val directions = Array<Vector3f>(totalRayTraceCount) { Vector3f() }
//
//        data class ResponsePack(
//            val x: Int,
//            val y: Int,
//            val asyncFuture: AsyncFuture<Vector3f>
//        )
//        val responseList = mutableListOf<ResponsePack>()
//        var currentResponseListCount = 0
//        var currentCount = 0
//        for (y in 0 until height) {
//            val v = (1.0 - (y + 0.5) / height) * 2 - 1
//            for (x in 0 until width) {
//                val u = ((x + 0.5) / width) * 2 - 1
//                currentCount++
//                if (currentCount % 10000 == 0) progressBar?.progress = currentCount.toDouble() / totalRayTraceCountDouble
//
//                val dir = forward.clone()
//                    .add(right.clone().multiply(u * halfWidth))
//                    .add(up.clone().multiply(v * halfHeight))
//                    .normalize()
//
//                val id = y * width + x
//
//                directions[id] = dir.toVector3f()
////                val result = JoclInterface.postWorldColorRequest(cameraOrigin = location.toVector3f(), direction = dir.toVector3f())
////                responseList += ResponsePack(
////                    x, y, result
////                )
////                if(currentResponseListCount > MAX_THREADS) {
////                    JoclInterface.processColors(bvhTree, flatBVHNode = flatBVHNode)
////                    currentResponseListCount = 0
////                }
////                currentResponseListCount++
//
//                val result = bvhTree.rayTrace(location.toVector().toVector3f(), dir.toVector3f())
////
//                getColorInWorld(
//                    rayTraceResult = result,
//                    startDirection = location.toVector3f(),
//                    flattenBVHNode = flatBVHNode,
//                    bvhTree = bvhTree
//                )
//                val distance = result?.distance
//
//                if (distance != null) {
//                    val logDepth = ln(distance + 1.0)
//
//                    depthImage[x][y] = logDepth.toFloat()
////                    depthImage[x][y] = 0.0f
//                }
//            }
//        }
//        JoclInterface.processColors(bvhTree, flatBVHNode = flatBVHNode)
//        progressBar?.setTitle("渲染 - 射线追踪 - 射线追踪")
//        progressBar?.progress = 1.0
//
//        var finalImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
//
//        val rayOrigins = Array<Vector3f>(totalRayTraceCount) { location.toVector().toVector3f() }
//        val resultList = JoclInterface.getWorldColors(bvhTree, flattenRoot = bvhTree.root!!.flatten().toTypedArray())
//
////        for (y in 0 until height) {
////            for (x in 0 until width) {
////                val id = y * width + x
////                finalImage.setRGB(x, y, resultList[id].toColorInteger())
////            }
////        }
////        for (pack in responseList) {
////            val rgb = pack.asyncFuture.get()
////            finalImage.setRGB(pack.x, pack.y, Color((rgb.x * 255.0f).toInt(), (rgb.x * 255.0f).toInt(), (rgb.x * 255.0f).toInt()).rgb)
////        }
//
//        bufferedImage = finalImage
//        this.depthImage = depthImage
//
//        var minLog = Float.MAX_VALUE
//        var maxLog = Float.MIN_VALUE
//
//        for (row in depthImage) {
//            for (depth in row) {
//                minLog = minOf(minLog, depth)
//                maxLog = maxOf(maxLog, depth)
//            }
//        }
//
//        val logImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
//
//        for (y in 0 until height) {
//            for (x in 0 until width) {
//                val depth = depthImage[x][y]
//
//                if (depth < 0.0f) {
//                    logImage.setRGB(x, y, 0xFFFF0000.toInt())
//                } else if (depth == 0.0f) {
//                    logImage.setRGB(x, y, 0xFF0000FF.toInt())
//                } else {
//                    val normalized = (depth - minLog) / (maxLog - minLog)
//                    val gray = (normalized.coerceIn(0f, 1f) * 255.0f).toInt()
//
//                    val rgb = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
//                    logImage.setRGB(x, y, rgb)
//                }
//            }
//        }
//
//        ImageIO.write(logImage, "png", File("C:\\image\\depth_output.png"))
//
//        return finalImage to depthImage
//    }*/

    private val maxReflectionTimes = 4

    var lights1: ArrayList<Location>? = null
    val EPSILON = 0.001

    fun getNextReflectionRayDirections(idealReflectionVector: Vector3f, planeNormal: Vector3f, material: RayTraceMaterial, reflectionTimes: Int): List<Pair<Vector3f, Float>>{
        val samples = getMaterialSamples(material, reflectionTimes)

        val result = mutableListOf<Pair<Vector3f, Float>>()

        repeat(samples) {
            val perturbedVector = Vector3f().apply {
                VectorUtil.perturbDirection(idealReflectionVector, material.spread, this)
                normalize()
            }
            if (perturbedVector.dot(planeNormal) < 0.0 && true) return@repeat
            val weight = getMaterialWeightOnTwoVector(
                material,
                perturbedVector,
                idealReflectionVector
            )
            result.add(perturbedVector to weight.toFloat())
        }

        return result
    }

    override fun getColorInWorld(
        rayTraceResult: HitResult?,
        startDirection: Vector3f,
        flattenBVHNode: Array<FlatBVHNode>,
        bvhTree: BVHTree,

    ): Color {
        val currentTime = location.world.time

        val clock = CountDownLatch(1)

        if (rayTraceResult == null) return skyColor(currentTime)
        var lights: List<Location>? = null

        if(this.lights1 != null) lights = lights1
        else {
            findSeaLanternsUsingThreads(
                location.world,
                location.clone().add(distance, distance, distance),
                location.clone().subtract(distance, distance, distance)
            ) {
                lights = it
                lights1 = it as ArrayList<Location>?
                clock.countDown()
            }
            clock.await()
        }

        val colorList = mutableListOf<ColorData>()

        val startRayTraceData = RayTraceData(
            Vector3f(1f, 1f, 1f),
            1f,
            startDirection
        )

        val lastRequestPacks = rayTracing(rayTraceResult, 0, startRayTraceData, colorList, lights!!)
        val newRequests = mutableListOf<RequestPack>()
//            JoclInterface.processResults(flattenBVHNode, bvhTree = bvhTree)
        for (i in 1 until maxReflectionTimes) {
            for (pack in lastRequestPacks) {
                val result = pack.asyncFuture.get()
                if (result?.distance == -1f) continue
                newRequests.addAll(rayTracing(result, i, pack.rayTraceData, colorList, lights))
            }
//            JoclInterface.processResults(flattenBVHNode, bvhTree = bvhTree)
            lastRequestPacks.clear()
            lastRequestPacks.addAll(newRequests)
        }

        val finalColor = Vector3f()
        var totalWeight = 0f

        for (data in colorList) {
            val weight = data.wx * data.brightness
            finalColor.x += data.color.x * weight  // 红色分量
            finalColor.y += data.color.y * weight  // 绿色分量
            finalColor.z += data.color.z * weight  // 蓝色分量
            totalWeight += weight
        }

        if (totalWeight > 0f) {
            finalColor.x /= totalWeight
            finalColor.y /= totalWeight
            finalColor.z /= totalWeight
        } else {
            // 避免除以 0，可以设置默认颜色或保持 finalColor 为黑
        }
        val result = Color(clamp01To255(finalColor.x), clamp01To255(finalColor.y), clamp01To255(finalColor.z))
        return result

    }

    fun getColorInWorld1(
        rayTraceResult: HitResult?,
        startDirection: Vector3f,
    ): Color {
        val currentTime = location.world.time

        if (rayTraceResult == null) return skyColor(currentTime)

        val colorList = mutableListOf<ColorData>()

        val startRayTraceData = RayTraceData(
            Vector3f(1f, 1f, 1f),
            1f,
            startDirection
        )
        val clock = CountDownLatch(1)


        var lights: List<Location>? = null

        if(this.lights1 != null) lights = lights1
        else {
            findSeaLanternsUsingThreads(
                location.world,
                location.clone().add(distance, distance, distance),
                location.clone().subtract(distance, distance, distance)
            ) {
                lights = it
                lights1 = it as ArrayList<Location>?
                clock.countDown()
            }
            clock.await()
        }

        val lastRequestPacks = rayTracing(rayTraceResult, 0, startRayTraceData, colorList, lights!!)
        val newRequests = mutableListOf<RequestPack>()
//            JoclInterface.processResults(flattenBVHNode, bvhTree = bvhTree)
        for (i in 1 until maxReflectionTimes) {
            for (pack in lastRequestPacks) {
                val result = pack.asyncFuture.get()
                if (result?.distance == -1f) continue
                newRequests.addAll(rayTracing(result, i, pack.rayTraceData, colorList, lights))
            }
//            JoclInterface.processResults(flattenBVHNode, bvhTree = bvhTree)
            lastRequestPacks.clear()
            lastRequestPacks.addAll(newRequests)
        }

        val finalColor = Vector3f()
        var totalWeight = 0f

        for (data in colorList) {
            val weight = data.wx * data.brightness
            finalColor.x += data.color.x * weight  // 红色分量
            finalColor.y += data.color.y * weight  // 绿色分量
            finalColor.z += data.color.z * weight  // 蓝色分量
            totalWeight += weight
        }

        if (totalWeight > 0f) {
            finalColor.x /= totalWeight
            finalColor.y /= totalWeight
            finalColor.z /= totalWeight
        } else {
            // 避免除以 0，可以设置默认颜色或保持 finalColor 为黑
        }
        val result = Color(clamp01To255(finalColor.x), clamp01To255(finalColor.y), clamp01To255(finalColor.z))
        return result

    }

    fun clamp01To255(value: Float): Int {
        return (value.coerceIn(0f, 1f) * 255).toInt()
    }

    class RayTraceData(
        var wc: Vector3f,
        var wx: Float,
        var vector: Vector3f
    )
    data class ColorData(
        var color: Vector3f,
        val brightness: Float,
        val wx: Float,
    )

    data class RequestPack(
        val reflectionTime: Int,
        val asyncFuture: AsyncFuture<HitResult?>,
        val rayTraceData: RayTraceData,
    )

    fun rayTracing(
        rayTraceResult: HitResult?,
        reflectionTimes: Int = 0,
        parentRayTraceData: RayTraceData,
        globalColorList: MutableList<ColorData>,
        lights: List<Location>
    ): MutableList<RequestPack> {

        val result = mutableListOf<RequestPack>()
        // 命中天空
        if (rayTraceResult == null ||
            rayTraceResult.hitPosition?.let {
                rayTraceResult.face?.let { face ->
                    location.world.getBlockAtOnSurface(
                        it,
                        face
                    )
                }
            } == null ||
            rayTraceResult.face == null
        ) {
//            val light = LightMaterial.SKY_LIGHT
//            val mul = light.lightColor.toOneRange()
//            globalColorList.add(
//                ColorData(
//                    parentRayTraceData.wc.mul(mul),
//                    light.brightness,
//                    parentRayTraceData.wx
//                )
//            )
            return result
        }

        val hitBlock = rayTraceResult.hitPosition.let { location.world.getBlockAtOnSurface(it, rayTraceResult.face) }
        val threshold = 0.001f

        if (reflectionTimes > maxReflectionTimes || parentRayTraceData.wx < threshold) {
            return result
        }

        // 命中光源方块
        val hitBlockReflectionData = hitBlock.type.getReflectionMaterialData()
        if (hitBlockReflectionData.isLight) {
            // TODO 混合光源材质与颜色
            val mul = hitBlockReflectionData.lightColor.toOneRange()
            globalColorList.add(
                ColorData(
                    parentRayTraceData.wc.mul(mul),
                    hitBlockReflectionData.brightness,
                    parentRayTraceData.wx
                )
            )
            return result
        }

        // 命中普通方块
        val hitPos = getHitPosition(rayTraceResult)

        val originColor = getColorInWorldTexture(rayTraceResult, hitPosition = Vector3f(hitPos))?.toOneRange()
            ?: return result
        val currentColor = originColor.mul(parentRayTraceData.wc)
        val hitMaterial = hitBlockReflectionData
        val normal = VectorUtil.faceToNormalArray[rayTraceResult.face.ordinal]
        if (rayTraceResult.distance == 0.0f || rayTraceResult.distance == -1.0f || (normal.x == 0.0f && normal.z == 0.0f && normal.y == 0.0f)) return result
        val idealReflectionVector = Vector3f().apply {
            VectorUtil.getReflectedVector(parentRayTraceData.vector, normal, this)
        }
        val EPSILON = 0.001f

//        val reflectionTimesWeight =
//            (maxReflectionTimes.toFloat() - reflectionTimes.toFloat()) * REFLECTION_FACTOR
//
//
//        val reflectionDirections = getNextReflectionRayDirections(
//            idealReflectionVector = idealReflectionVector,
//            planeNormal = normal,
//            material = hitMaterial,
//            reflectionTimes = reflectionTimes
//        )
//
//        for ((reflectionDirection, weight) in reflectionDirections) {
//            val finalWeight = weight * reflectionTimesWeight
//            if(finalWeight < EPSILON) {
//                continue
//            }
//            val offsetStartPos = hitPos.toVector3f().add(
//                Vector3f(normal).mul(-EPSILON)
//            )
////            val traceResult = JoclInterface.traceRay(offsetStartPos, reflectionDirection)
//
////            RayTraceData(
////                wc = currentColor,
////                wx = finalWeight.toFloat(),
////                vector = reflectionDirection
////            )
//
////                       val traceResult = .rayTrace(offsetHitPos, perturbedVector) ?: return emptyList()
//            val traceBukkit = location.world.rayTraceBlocks(Location(location.world,
//                offsetStartPos.x.toDouble(), offsetStartPos.y.toDouble(),
//                offsetStartPos.z.toDouble()), reflectionDirection.toVector() , 40.0
//            )
//
//            val hitResult = HitResult(
//                hitPosition = traceBukkit?.hitPosition?.toVector3f() ?: continue,
//                distance = traceBukkit.hitPosition.distance(offsetStartPos.toVector()).toFloat(),
//                face = traceBukkit.hitBlockFace,
//                startPos = offsetStartPos,
//                direction = reflectionDirection
//            )
//
//            val asyncFuture1 = AsyncFuture<HitResult?>()
//            asyncFuture1.set(hitResult)
//
//            result.add(
//                RequestPack(
//                    reflectionTimes,
//                    asyncFuture1,
//                    RayTraceData(
//                        wc = currentColor,
//                        wx = finalWeight.toFloat(),
//                        vector = reflectionDirection
//                    ),
//                )
//            )
//        }

        val samples = getMaterialSamples(hitMaterial, reflectionTimes)


        repeat(samples) {

            val perturbedVector = if (Random.nextFloat(0f, 1f) < hitMaterial.maxProbability) {
                val dir = lights.random().clone().add(0.5, 0.5, 0.5).subtract(hitPos.toVector())
                dir.toVector().toVector3f()
            } else
                Vector3f().also {
                    VectorUtil.perturbDirection(idealReflectionVector, hitMaterial.spread, it)
                    it.normalize()
                }

            if (perturbedVector.dot(normal) < 0.0 && true) return@repeat

            val finalWeight = getMaterialWeightOnTwoVector(
                hitMaterial,
                perturbedVector,
                idealReflectionVector
            ) * 1 - (reflectionTimes / maxReflectionTimes)

            if (finalWeight <= EPSILON) return@repeat

            val offsetHitPos = hitPos.toVector3f().add(
                Vector3f(normal).mul(EPSILON)
            )

//
//           val traceResult = bvhTree.rayTrace(offsetHitPos, perturbedVector) ?: return emptyList()

//            val asyncFuture1 = JoclInterface.traceRay(offsetHitPos, perturbedVector)

            val traceBukkit = location.world.rayTraceBlocks(
                Location(
                    location.world,
                    offsetHitPos.x.toDouble(), offsetHitPos.y.toDouble(),
                    offsetHitPos.z.toDouble()
                ), perturbedVector.toVector(), 40.0
            )

            val hitResult = HitResult(
                hitPosition = traceBukkit?.hitPosition?.toVector3f() ?: return@repeat,
                distance = traceBukkit.hitPosition.distance(offsetHitPos.toVector()).toFloat(),
                face = traceBukkit.hitBlockFace,
                startPos = offsetHitPos,
                direction = perturbedVector,
            )

            val asyncFuture1 = AsyncFuture<HitResult?>()
            asyncFuture1.set(hitResult)



            RayTraceData(
                wc = currentColor,
                wx = finalWeight.toFloat(),
                vector = perturbedVector
            )
            result.add(
                RequestPack(
                    reflectionTimes,
                    asyncFuture1,
                    RayTraceData(
                        wc = currentColor,
                        wx = finalWeight.toFloat(),
                        vector = perturbedVector
                    ),
                )
            )
        }

        return result
    }

    private fun getMaterialWeightOnTwoVector(
        hitMaterial: RayTraceMaterial,
        perturbedVector: Vector3f,
        idealReflectionVector: Vector3f
    ): Double {
        val angleWeight = hitMaterial.weight(perturbedVector.dot(idealReflectionVector))
        return angleWeight
    }

    private fun getHitPosition(rayTraceResult: HitResult): Vector3f {
        val hitPos = Vector3f().apply {
            set(rayTraceResult.startPos)
            add(Vector3f(rayTraceResult.direction).mul(Vector3f(rayTraceResult.distance)))
        }
        return hitPos
    }


    private fun getMaterialSamples(
        hitMaterial: RayTraceMaterial,
        reflectionTimes: Int
    ): Int {
        val baseSamples = hitMaterial.reflectionTimes
        val samples = when {
            reflectionTimes > 2 -> (baseSamples / 4).coerceAtLeast(1)
            reflectionTimes > 1 -> (baseSamples / 2).coerceAtLeast(1)
            else -> baseSamples
        }
        return samples
    }

    fun getColorInWorldTexture(rayTraceResult: HitResult?, hitPosition: Vector3f): Color? {
        if (rayTraceResult == null) return null
        if (rayTraceResult.hitPosition == null || rayTraceResult.face == null) {
            return null
        }

        val hitBlock = location.world.getBlockAtOnSurface(rayTraceResult.hitPosition, rayTraceResult.face)
        val hitFace = rayTraceResult.face
        val material = hitBlock.type

        val image = TextureManager.getTexture(material, hitFace) ?: return null

        val relativeHit = hitPosition.sub(hitBlock.location.toVector().toVector3f())

        val x = relativeHit.x.toFloat()
        val y = relativeHit.y.toFloat()
        val z = relativeHit.z.toFloat()

        val (texX, texY) = TextureManager.getTextureCoords(
            hitFace, x, y, z, image.width, image.height
        )

        return Color(image.getRGB(texX, texY))
    }
}
fun Color.toOneRange(): Vector3f {
    val ths = this
    return Vector3f().apply {
        x = ths.red / 255f
        y = ths.green / 255f
        z = ths.blue / 255f
    }
}
private fun Color.toVector3i(): Vector3i{
    return Vector3i(this.red, this.green, this.blue)
}
fun Vector3i.toOneRange(): Vector3f {
    val ths = this
    return Vector3f().apply {
        x = ths.x / 255f
        y = ths.y / 255f
        z = ths.z / 255f
    }
}
private fun Vector3f.toVector(): Vector {
    val ths = this
    return Vector().apply {
        x = ths.x.toDouble()
        y = ths.y.toDouble()
        z = ths.z.toDouble()
    }
}
private fun Vector.set(other: Vector) {
    this.x = other.x
    this.y = other.y
    this.z = other.z
}
private fun Vector3f.set(other: Vector3f) {
    this.x = other.x
    this.y = other.y
    this.z = other.z
}
private fun Vector3f.set(other: Vector){
    this.x = other.x.toFloat()
    this.y = other.y.toFloat()
    this.z = other.z.toFloat()
}
fun Vector3f.toVector3f(): Vector3f {
    return Vector3f(this)
}

//package com.methyleneblue.camera.obj
//
//import com.methyleneblue.camera.obj.raytrace.RayTraceMaterial
//import com.methyleneblue.camera.obj.raytrace.getReflectionMaterialData
//import com.methyleneblue.camera.raytracepack.bvh.BVHTree
//import com.methyleneblue.camera.raytracepack.bvh.BVHTree.Companion.getBVHTree
//import com.methyleneblue.camera.raytracepack.bvh.FlatBVHNode
//import com.methyleneblue.camera.raytracepack.bvh.HitResult
//import com.methyleneblue.camera.raytracepack.bvh.getBlockAtOnSurface
//import com.methyleneblue.camera.texture.TextureManager
//import com.methyleneblue.camera.texture.TextureManager.skyColor
//import com.methyleneblue.camera.util.VectorUtil
//import com.methyleneblue.camera.util.toVector3f
//import org.bukkit.Location
//import org.bukkit.boss.BossBar
//import org.bukkit.entity.Player
//import org.bukkit.util.Vector
//import org.joml.Vector3f
//import org.joml.Vector3i
//import java.awt.Color
//import java.awt.image.BufferedImage
//import java.io.File
//import java.util.concurrent.CompletableFuture
//import java.util.concurrent.Executors
//import java.util.concurrent.atomic.AtomicInteger
//import javax.imageio.ImageIO
//import kotlin.math.ln
//import kotlin.math.tan
//
//class RayTraceCamera(
//    location: Location,
//    size: Pair<Int, Int>,
//    fov: Double,
//    distance: Double,
//    progressBar: BossBar?,
//    bufferedImage: BufferedImage,
//    depthImage: Array<FloatArray>
//) : BVHCamera(
//    location = location,
//    size,
//    fov,
//    distance,
//    progressBar,
//    bufferedImage,
//    depthImage
//) {
//    companion object {
//        private const val REFLECTION_FACTOR = 1.0
//        private const val MAX_THREADS = 100
//    }
//
//    private val maxReflectionTimes = 3
//    private val EPSILON = 0.001f
//    override fun getColorInWorld(
//        rayTraceResult: HitResult?,
//        startDir: Vector3f,
//        flatBVHNode: Array<FlatBVHNode>,
//        bvhTree: BVHTree
//    ): Color {
//        return Color(0, 0, 0)
//    }
//
//    data class RayTraceData(var wc: Vector3f, var wx: Float, var vector: Vector3f)
//    data class ColorData(var color: Vector3f, val brightness: Float, val wx: Float)
//    data class RequestPack(val reflectionTime: Int, val asyncFuture: com.methyleneblue.camera.raytracepack.bvh.jocl.async.AsyncFuture<HitResult?>, val rayTraceData: RayTraceData)
//
//    override fun updateCamera(player: Player?, mixinTimes: Int, maxDepth: Float): Pair<BufferedImage, Array<FloatArray>> {
//        val depthImage = Array(bufferedImage.width) { FloatArray(bufferedImage.height) { 0f } }
//
//        val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
//
//        progressBar?.setTitle("渲染 - 构建 BVH 树")
//        progressBar?.progress = 1.0
//
////        val bvhTree = getBVHTree(location, distance.toInt())
////        val flatBVHNode = bvhTree.root!!.flatten().toTypedArray()
//        val width = size.first
//        val height = size.second
//        val aspectRatio = width.toFloat() / height.toFloat()
//        val fovRad = Math.toRadians(fov.toDouble())
//
//        val forward = location.direction.normalize()
//        val upVector = Vector(0.0, 1.0, 0.0)
//        val right = forward.clone().crossProduct(upVector).normalize()
//        val up = right.clone().crossProduct(forward).normalize()
//
//        val halfWidth = tan(fovRad / 2.0)
//        val halfHeight = halfWidth / aspectRatio
//
//        val numThreads = Runtime.getRuntime().availableProcessors()
//        val rowsPerThread = height / numThreads
//
//        val totalRayTraceCount = width * height
//        val totalRayTraceCountDouble = totalRayTraceCount.toDouble()
//
//        progressBar?.setTitle("渲染 - 射线追踪")
//        progressBar?.progress = 0.0
//
//        var currentRayTraceCount = AtomicInteger(0)
//        val results = Array<BufferedImage?>(numThreads) { null }
//        val futures = mutableListOf<CompletableFuture<Void>>()
//        for (t in 0 until numThreads) {
//            val startRow = t * rowsPerThread
//            val endRow = if (t == numThreads - 1) height else (t + 1) * rowsPerThread
//
//            futures.add(CompletableFuture.runAsync({
//                val threadImage = BufferedImage(width, endRow - startRow, BufferedImage.TYPE_INT_RGB)
//                for (j in startRow until endRow) {
//                    val v = (1.0 - (j + 0.5) / height) * 2 - 1
//                    for (i in 0 until width) {
//                        val u = ((i + 0.5) / width) * 2 - 1
//                        val count = currentRayTraceCount.incrementAndGet()
//                        if (count % 1000 == 0) progressBar?.progress = count.toDouble() / totalRayTraceCountDouble
//
//                        val dir = forward.clone()
//                            .add(right.clone().multiply(u * halfWidth))
//                            .add(up.clone().multiply(v * halfHeight))
//                            .normalize()
//
////                        val result = bvhTree.rayTrace(location.toVector().toVector3f(), dir.toVector3f())
//                        val hitResult = location.world.rayTraceBlocks(location, dir, distance)
//
//                        val distance = hitResult?.hitPosition?.distance(location.toVector())?.toFloat() ?: 0.0f
//                        val result = HitResult(
//                            hitPosition = hitResult?.hitPosition?.toVector3f(),
//                            distance = distance,
//                            face = hitResult?.hitBlockFace,
//                            startPos = location.toVector3f(),
//                            direction = dir.toVector3f(),
//                        )
//
////                        val distance = result?.distance
//
//                        if (true) {
//                            val logDepth = ln(distance + 1.0)
//
//                            depthImage[i][j] = logDepth.toFloat()
//                        }
//
//                        var rSum = 0
//                        var gSum = 0
//                        var bSum = 0
//
//                        val dirVec3f = Vector3f(dir.toVector3f())
//
//                        repeat(mixinTimes) {
//                            val color = getColorInWorldA(
//                                result, Vector3f().set(dirVec3f)
//                            )
//                            rSum += color.red
//                            gSum += color.green
//                            bSum += color.blue
//                        }
//
//                        val rAvg = rSum / mixinTimes
//                        val gAvg = gSum / mixinTimes
//                        val bAvg = bSum / mixinTimes
//
//                        threadImage.setRGB(i, j - startRow, Color(rAvg, gAvg, bAvg).rgb)
//                    }
//                }
//                results[t] = threadImage
//            }, executor))
//        }
//
//        futures.forEach { it.join() }
//
//        var finalImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
//        val g = finalImage.graphics
//        for (t in 0 until numThreads) {
//            val startRow = t * rowsPerThread
//            g.drawImage(results[t], 0, startRow, null)
//        }
//        g.dispose()
//
//        bufferedImage = finalImage
//        this.depthImage = depthImage
//
//        var minLog = Float.MAX_VALUE
//        var maxLog = Float.MIN_VALUE
//
//        for (row in depthImage) {
//            for (depth in row) {
//                minLog = minOf(minLog, depth)
//                maxLog = maxOf(maxLog, depth)
//            }
//        }
//
//        val logImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
//
//        for (y in 0 until height) {
//            for (x in 0 until width) {
//                val depth = depthImage[x][y]
//
//                if (depth < 0.0f) {
//                    logImage.setRGB(x, y, 0xFFFF0000.toInt())
//                } else if (depth == 0.0f) {
//                    logImage.setRGB(x, y, 0xFF0000FF.toInt())
//                } else {
//                    val normalized = (depth - minLog) / (maxLog - minLog)
//                    val gray = (normalized.coerceIn(0f, 1f) * 255.0f).toInt()
//
//                    val rgb = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
//                    logImage.setRGB(x, y, rgb)
//                }
//            }
//        }
//
//        ImageIO.write(logImage, "png", File("C:\\image\\depth_output.png"))
//
//        return finalImage to depthImage
//    }
//    fun getNextReflectionRayDirections(idealReflectionVector: Vector3f, planeNormal: Vector3f, material: RayTraceMaterial, reflectionTimes: Int): List<Pair<Vector3f, Float>> {
//        val samples = getMaterialSamples(material, reflectionTimes)
//        val result = mutableListOf<Pair<Vector3f, Float>>()
//
//        repeat(samples) {
//            val perturbedVector = Vector3f().apply {
//                VectorUtil.perturbDirection(idealReflectionVector, material.spread, this)
//                normalize()
//            }
//            if (perturbedVector.dot(planeNormal) < 0.0) return@repeat
//            val weight = getMaterialWeightOnTwoVector(material, perturbedVector, idealReflectionVector)
//            result.add(perturbedVector to weight.toFloat())
//        }
//        return result
//    }
//
//    fun getColorInWorldA(rayTraceResult: HitResult?, startDirection: Vector3f): Color {
//        val currentTime = location.world.time
//        if (rayTraceResult == null) return skyColor(currentTime)
//
//        val colorList = mutableListOf<ColorData>()
//        val startRayTraceData = RayTraceData(Vector3f(1f, 1f, 1f), 1f, startDirection)
//        val lastRequestPacks = rayTracing(rayTraceResult, 0, startRayTraceData, colorList)
//        val newRequests = mutableListOf<RequestPack>()
//
//        for (i in 1 until maxReflectionTimes) {
//            for (pack in lastRequestPacks) {
//                val result = pack.asyncFuture.get()
//                if (result?.distance == -1f) continue
//                newRequests.addAll(rayTracing(result, i, pack.rayTraceData, colorList))
//            }
//            lastRequestPacks.clear()
//            lastRequestPacks.addAll(newRequests)
//        }
//
//        val finalColor = Vector3f()
//        var totalWeight = 0f
//
//        for (data in colorList) {
//            val weight = data.wx * data.brightness
//            finalColor.x += data.color.x * weight
//            finalColor.y += data.color.y * weight
//            finalColor.z += data.color.z * weight
//            totalWeight += weight
//        }
//
//        if (totalWeight > 0f) {
//            finalColor.x /= totalWeight
//            finalColor.y /= totalWeight
//            finalColor.z /= totalWeight
//        }
//
//        return Color(clamp01To255(finalColor.x), clamp01To255(finalColor.y), clamp01To255(finalColor.z))
//    }
//
//    fun clamp01To255(value: Float): Int = (value.coerceIn(0f, 1f) * 255).toInt()
//
//    fun rayTracing(rayTraceResult: HitResult?, reflectionTimes: Int = 0, parentRayTraceData: RayTraceData, globalColorList: MutableList<ColorData>): MutableList<RequestPack> {
//        val result = mutableListOf<RequestPack>()
//        if (rayTraceResult == null || rayTraceResult.hitPosition == null || rayTraceResult.face == null) return result
//
//        val hitBlock = rayTraceResult.hitPosition.let { location.world.getBlockAtOnSurface(it, rayTraceResult.face) }
//        if (reflectionTimes > maxReflectionTimes || parentRayTraceData.wx < EPSILON) return result
//
//        val hitBlockReflectionData = hitBlock.type.getReflectionMaterialData()
//        if (hitBlockReflectionData.isLight) {
//            val mul = hitBlockReflectionData.lightColor.toOneRange()
//            globalColorList.add(ColorData(parentRayTraceData.wc.mul(mul), hitBlockReflectionData.brightness, parentRayTraceData.wx))
//            return result
//        }
//
//        val hitPos = getHitPosition(rayTraceResult)
//        val originColor = getColorInWorldTexture(rayTraceResult, Vector3f(hitPos))?.toOneRange() ?: return result
//        val currentColor = originColor.mul(parentRayTraceData.wc)
//        val normal = VectorUtil.faceToNormalMap[rayTraceResult.face] ?: return result
//        if (rayTraceResult.distance <= 0f || normal.lengthSquared() == 0f) return result
//
//        val idealReflectionVector = Vector3f().apply {
//            VectorUtil.getReflectedVector(parentRayTraceData.vector, normal, this)
//        }
//
//        val samples = getMaterialSamples(hitBlockReflectionData, reflectionTimes)
//
//        repeat(samples) {
//            val perturbedVector = Vector3f().apply {
//                VectorUtil.perturbDirection(idealReflectionVector, hitBlockReflectionData.spread, this)
//                normalize()
//            }
//            if (perturbedVector.dot(normal) < 0.0) return@repeat
//
//            val finalWeight = getMaterialWeightOnTwoVector(hitBlockReflectionData, perturbedVector, idealReflectionVector) * (1 - reflectionTimes / maxReflectionTimes)
//            if (finalWeight <= EPSILON) return@repeat
//
//            val offsetHitPos = hitPos.toVector3f().add(Vector3f(normal).mul(EPSILON))
//            val traceBukkit = location.world.rayTraceBlocks(Location(location.world, offsetHitPos.x.toDouble(), offsetHitPos.y.toDouble(), offsetHitPos.z.toDouble()), perturbedVector.toVector(), 40.0) ?: return@repeat
//
//            val hitResult = HitResult(traceBukkit.hitPosition.toVector3f(), traceBukkit.hitPosition.distance(offsetHitPos.toVector()).toFloat(), traceBukkit.hitBlockFace, offsetHitPos, perturbedVector)
//
//            val asyncFuture1 = com.methyleneblue.camera.raytracepack.bvh.jocl.async.AsyncFuture<HitResult?>().apply { set(hitResult) }
//
//            result.add(RequestPack(reflectionTimes, asyncFuture1, RayTraceData(currentColor, finalWeight.toFloat(), perturbedVector)))
//        }
//
//        return result
//    }
//
//    private fun getMaterialWeightOnTwoVector(hitMaterial: RayTraceMaterial, perturbedVector: Vector3f, idealReflectionVector: Vector3f): Double =
//        hitMaterial.weight(perturbedVector.dot(idealReflectionVector))
//
//    private fun getHitPosition(rayTraceResult: HitResult): Vector3f = Vector3f(rayTraceResult.startPos).add(Vector3f(rayTraceResult.direction).mul(rayTraceResult.distance))
//
//    private fun getMaterialSamples(hitMaterial: RayTraceMaterial, reflectionTimes: Int): Int {
////        return 1
//        val baseSamples = hitMaterial.reflectionTimes
//        return when {
//            reflectionTimes > 2 -> (baseSamples / 4).coerceAtLeast(1)
//            reflectionTimes > 1 -> (baseSamples / 2).coerceAtLeast(1)
//            else -> baseSamples
//        }
//    }
//
//    fun getColorInWorldTexture(rayTraceResult: HitResult?, hitPosition: Vector3f): Color? {
//        if (rayTraceResult == null || rayTraceResult.hitPosition == null || rayTraceResult.face == null) return null
//
//        val hitBlock = location.world.getBlockAtOnSurface(rayTraceResult.hitPosition, rayTraceResult.face)
//        val image = TextureManager.getTexture(hitBlock.type, rayTraceResult.face) ?: return null
//        val relativeHit = hitPosition.sub(hitBlock.location.toVector().toVector3f())
//
//        val (texX, texY) = TextureManager.getTextureCoords(rayTraceResult.face, relativeHit.x, relativeHit.y, relativeHit.z, image.width, image.height)
//        return Color(image.getRGB(texX, texY))
//    }
//}

//fun Color.toOneRange(): Vector3f = Vector3f(red / 255f, green / 255f, blue / 255f)
//private fun Vector3f.toVector(): Vector = Vector(x.toDouble(), y.toDouble(), z.toDouble())
//fun Vector3f.toVector3f(): Vector3f = Vector3f(x, y, z)
//fun Vector3i.toOneRange(): Vector3f = Vector3f(x.toFloat() / 255f, y.toFloat() / 255f, z.toFloat() / 255f)
