package com.methyleneblue.camera.obj


import com.methyleneblue.camera.util.VectorUtil
import com.methyleneblue.camera.obj.raytrace.LightMaterial
import com.methyleneblue.camera.obj.raytrace.ReflectionMaterial
import com.methyleneblue.camera.obj.raytrace.getLight
import com.methyleneblue.camera.obj.raytrace.getReflectionMaterialData
import com.methyleneblue.camera.obj.raytrace.isLight
import com.methyleneblue.camera.raytracepack.bvh.BVHTree
import com.methyleneblue.camera.raytracepack.bvh.BVHTree.Companion.getBVHTree
import com.methyleneblue.camera.raytracepack.bvh.FlatBVHNode
import com.methyleneblue.camera.raytracepack.bvh.HitResult
import com.methyleneblue.camera.raytracepack.bvh.getBlockAtOnSurface
import com.methyleneblue.camera.raytracepack.bvh.jocl.JoclInterface
import com.methyleneblue.camera.raytracepack.bvh.jocl.async.AsyncFuture
import com.methyleneblue.camera.texture.TextureManager
import com.methyleneblue.camera.texture.TextureManager.skyColor
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.joml.Vector3f
import org.joml.Vector3i
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.tan

class RayTraceCamera(
    location: Location,
    size: Pair<Int, Int>,
    fov: Double, // 基于横向计算
    distance: Double,
    bufferedImage: BufferedImage,
    depthImage: BufferedImage
): BVHCamera(
    location = location,
    size,
    fov,
    distance,
    bufferedImage,
    depthImage
) {
    companion object {
        private const val REFLECTION_FACTOR = 1.0
        private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    }

    private val maxReflectionTimes = 3

//    override fun updateCamera(player: Player?, mixinTimes: Int): BufferedImage {
//        val bvhTree = getBVHTree(location, distance.toInt())
//        val flatBVHNode = bvhTree.root!!.flatten()
//        val arrayFlatBVHNode = flatBVHNode.toTypedArray()
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
//        JoclInterface.processResults(arrayFlatBVHNode, bvhTree)
//
//        val images = mutableListOf<BufferedImage>()
//
//        var threads = AtomicInteger(12)
//        val threadPerPixel = future1.size / threads.get()
//        var globalCountDownLatch = CountDownLatch(threads.get())
//        val globalThreadEndLatch = CountDownLatch(threads.get())
//
//        for (t in 0 until threads.get()) {
//            Thread {
//                val start = t * threadPerPixel
//                val end = if (t == threads.get() - 1) future1.size else (t + 1) * threadPerPixel
//
//                for (i in start until end) {
//                    val (x, y, pack) = future1[i]
//                    val result = pack.get()
//                    if(result == null){
//                        globalCountDownLatch.countDown()
//                        continue
//                    }
//                    if(result.direction == null) {
//                        globalCountDownLatch.countDown()
//                        continue
//                    }
//                    val color = getColorInWorld(result, result.direction, arrayFlatBVHNode, bvhTree)
//                    image.setRGB(x, y, color.rgb)
//                }
//                globalCountDownLatch.countDown()
//                threads.set(threads.get() - 1)
//                globalThreadEndLatch.countDown()
//            }.start()
//        }
//
////        var flag = true
////        Thread {
////            globalThreadEndLatch.await()
////            flag = false
////        }
//
////        while (flag) {
////            globalCountDownLatch.await()
////            globalCountDownLatch = CountDownLatch(threads.get())
////            JoclInterface.processResults(arrayFlatBVHNode, bvhTree)
////        }
//
//        progressBar?.let { bar ->
//            Bukkit.getOnlinePlayers().filter { it.isOp }.forEach { it.hideBossBar(bar) }
//        }
//
//        bufferedImage = image
//        return image
//    }


    val EPSILON = 0.001f


    fun getNextReflectionRayDirections(idealReflectionVector: Vector3f, planeNormal: Vector3f, material: ReflectionMaterial, reflectionTimes: Int): List<Pair<Vector3f, Float>>{
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

    fun getColorInWorldA(
        rayTraceResult: HitResult?,
        startDir: Vector3f,
        flatBVHNode: Array<FlatBVHNode>,
        bvhTree: BVHTree
    ): Color {
        return Color.BLACK
    }
    override fun getColorInWorld(
        rayTraceResult: HitResult?,
        startDirection: Vector3f,
        flattenBVHNode: Array<FlatBVHNode>,
        bvhTree: BVHTree,
//        globalPixelCountDownLatch: CountDownLatch
    ): Color {
        val currentTime = location.world.time

        if (rayTraceResult == null) return skyColor(currentTime)

        val colorList = mutableListOf<ColorData>()

        val startRayTraceData = RayTraceData(
            Vector3f(1f, 1f, 1f),
            1f,
            startDirection
        )

        val lastRequestPacks = rayTracing(rayTraceResult, 0, startRayTraceData, colorList)
        val newRequests = mutableListOf<RequestPack>()
            JoclInterface.processResults(flattenBVHNode, bvhTree = bvhTree)
        for (i in 1 until maxReflectionTimes) {
            for (pack in lastRequestPacks) {
                val result = pack.asyncFuture.get()
                if (result?.distance == -1f) continue
                newRequests.addAll(rayTracing(result, i, pack.rayTraceData, colorList))
            }
            JoclInterface.processResults(flattenBVHNode, bvhTree = bvhTree)
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
    ): MutableList<RequestPack> {

        val result = mutableListOf<RequestPack>()
        // 命中天空
        if (rayTraceResult == null ||
            rayTraceResult.hitPosition?.let { rayTraceResult.face?.let { face -> location.world.getBlockAtOnSurface(it, face) } } == null ||
            rayTraceResult.face == null
        ) {
            val light = LightMaterial.SKY_LIGHT
            val mul = light.lightColor.toOneRange()
            globalColorList.add(
                ColorData(
                    parentRayTraceData.wc.mul(mul),
                    light.brightness,
                    parentRayTraceData.wx
                )
            )
            return result
        }

        val hitBlock = rayTraceResult.hitPosition.let { location.world.getBlockAtOnSurface(it, rayTraceResult.face) }
        val threshold = 0.001f

        if (reflectionTimes > maxReflectionTimes || parentRayTraceData.wx < threshold) {
            return result
        }

        // 命中光源方块
        if (hitBlock.type.isLight()) {
            // TODO 混合光源材质与颜色
            val light = hitBlock.type.getLight() ?: return result
            val mul = light.lightColor.toOneRange()
            globalColorList.add(
                ColorData(
                    parentRayTraceData.wc.mul(mul),
                    light.brightness,
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
        val hitMaterial = hitBlock.type.getReflectionMaterialData()
        val normal = VectorUtil.faceToNormalMap[rayTraceResult.face] ?: return result
        if(rayTraceResult.distance == 0.0f || rayTraceResult.distance == -1.0f || (normal.x == 0.0f && normal.z == 0.0f && normal.y == 0.0f)) return result
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
            val perturbedVector = Vector3f().apply {
                VectorUtil.perturbDirection(idealReflectionVector, hitMaterial.spread, this)
                normalize()
            }

            if (perturbedVector.dot(normal) < 0.0 && true) return@repeat

            val finalWeight = getMaterialWeightOnTwoVector(
                hitMaterial,
                perturbedVector,
                idealReflectionVector
            ) *  1 - (reflectionTimes / maxReflectionTimes)

            if (finalWeight <= EPSILON) return@repeat

            val offsetHitPos = hitPos.toVector3f().add(
                Vector3f(normal).mul(EPSILON)
            )

//
//           val traceResult = bvhTree.rayTrace(offsetHitPos, perturbedVector) ?: return emptyList()

//            val asyncFuture1 = JoclInterface.traceRay(offsetHitPos, perturbedVector)

            val traceBukkit = location.world.rayTraceBlocks(Location(location.world,
                offsetHitPos.x.toDouble(), offsetHitPos.y.toDouble(),
                offsetHitPos.z.toDouble()), perturbedVector.toVector() , 40.0
            )

            val hitResult = HitResult(
                hitPosition = traceBukkit?.hitPosition?.toVector3f()?: return@repeat,
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
        hitMaterial: ReflectionMaterial,
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
        hitMaterial: ReflectionMaterial,
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
private fun Color.toOneRange(): Vector3f {
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
private fun Vector3i.toOneRange(): Vector3f {
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
private fun Vector3f.toVector3f(): Vector3f {
    return Vector3f(this)
}