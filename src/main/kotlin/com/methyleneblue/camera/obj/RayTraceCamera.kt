package com.methyleneblue.camera.obj

import com.methyleneblue.camera.VectorUtil
import com.methyleneblue.camera.obj.raytrace.LightMaterial
import com.methyleneblue.camera.obj.raytrace.getLight
import com.methyleneblue.camera.obj.raytrace.getReflectionMaterialData
import com.methyleneblue.camera.obj.raytrace.isLight
import com.methyleneblue.camera.obj.texture.TextureManager
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Vector
import org.joml.Vector3f
import org.joml.Vector3i
import java.awt.Color
import java.awt.image.BufferedImage
import java.lang.reflect.Array.set
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.tan

class RayTraceCamera(
    location: Location,
    size: Pair<Int, Int>,
    fov: Double, // 基于横向计算
    distance: Double,
    bufferedImage: BufferedImage,
): CameraObject(
    location = location,
    size,
    fov,
    distance,
    bufferedImage,
) {
    companion object {
        private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        private val vectorPool = VectorPool()

        class VectorPool {
            private val pool = ConcurrentLinkedQueue<Vector>()

            fun alloc(): Vector = pool.poll() ?: Vector()

            fun free(vector: Vector) {
                vector.zero()
                pool.offer(vector)
            }
        }
    }

    override fun updateCamera(player: Player?, mixinTimes: Int): BufferedImage {
        val width = size.first
        val height = size.second
        val aspectRatio = width.toFloat() / height.toFloat()
        val fovRad = Math.toRadians(fov.toDouble())

        var image = bufferedImage
        val forward = location.direction.normalize()
        val upVector = Vector(0.0, 1.0, 0.0)
        val right = forward.clone().crossProduct(upVector).normalize()
        val up = right.clone().crossProduct(forward).normalize()

        val halfWidth = tan(fovRad / 2.0)
        val halfHeight = halfWidth / aspectRatio

        val numThreads = Runtime.getRuntime().availableProcessors()
        val rowsPerThread = height / numThreads
        val threads = mutableListOf<Thread>()

        val totalRayTraceCount = width * height
        var currentRayTraceCount = 0

        val useBossBar = player != null

        var progressBar: BossBar? = null
        if(useBossBar) {
            progressBar = BossBar.bossBar(Component.text("渲染进度"), 0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS)
            player.showBossBar(progressBar)
        }
        val futures = mutableListOf<CompletableFuture<Void>>()
        for (t in 0 until numThreads) {
            val startRow = t * rowsPerThread
            val endRow = if (t == numThreads - 1) height else (t + 1) * rowsPerThread
            
            futures.add(CompletableFuture.runAsync({
                for (j in startRow until endRow) {
                    val v = (1.0 - (j + 0.5) / height) * 2 - 1
                    for (i in 0 until width) {
                        val u = ((i + 0.5) / width) * 2 - 1
                        currentRayTraceCount += 1
                        if(currentRayTraceCount % 10000 == 0) progressBar?.progress((currentRayTraceCount.toFloat() / totalRayTraceCount.toFloat()).toFloat())

                        val dir = vectorPool.alloc().apply {
//                            set(forward)

                            setX(forward.x)
                            setY(forward.y)
                            setZ(forward.z)

                            val rightOffset = vectorPool.alloc().apply {
//                                set(right)
                                setX(right.x)
                                setY(right.y)
                                setZ(right.z)

                                multiply(u * halfWidth)
                            }
                            add(rightOffset)
                            vectorPool.free(rightOffset)

                            val upOffset = vectorPool.alloc().apply {
//                                set(up)
                                setX(up.x)
                                setY(up.y)
                                setZ(up.z)
                                multiply(v * halfHeight)
                            }

                            add(upOffset)
                            vectorPool.free(upOffset)

                            normalize()
                        }

                        val result = location.world.rayTraceBlocks(location, dir, distance)
                        var rSum = 0
                        var gSum = 0
                        var bSum = 0

                        repeat (mixinTimes){
                            val color = getColorInWorld(result, dir.toVector3f())
                            rSum += color.red
                            gSum += color.green
                            bSum += color.blue
                        }

                        vectorPool.free(dir)

                        val rAvg = rSum / mixinTimes
                        val gAvg = gSum / mixinTimes
                        val bAvg = bSum / mixinTimes
                        try{
                            image.setRGB(i, j, Color(rAvg, gAvg, bAvg).rgb)
                        }catch (e: Exception){
                            image.setRGB(i, j, Color.BLACK.rgb)
                        }
                    }
                }
            }, executor))
            //喵？可以直接用executors.submit的喵
            // futures.add(future)
            // threads.add(thread)
        }

//        CompletableFuture.allOf(arrayOf(futures))
//        CompletableFuture.allOf(futures.toArray<CompletableFuture<Void>>()).join()

        // 等待所有线程完成
        // threads.forEach { it.join() }

        progressBar?.let { player?.hideBossBar(it) }

        image = applyBilateralFilter(image)
        bufferedImage = image

        return image
    }

    private val textureCache = ConcurrentHashMap<String, BufferedImage>()
    private val sideShadow = 0.6

    private val maxReflectionTimes = 2
    private val raytraceDistance = 100.0


    fun getColorInWorld(rayTraceResult: RayTraceResult?, vector: Vector3f): Color {
        val currentTime = location.world.time

        if(rayTraceResult == null) return skyColor(currentTime)

        val colorList = mutableListOf<ColorData>()

        val startRayTraceData = RayTraceData(
            Vector3f(1f, 1f, 1f),
            1f,
            vector
        )

        rayTracing(rayTraceResult, 0, startRayTraceData, colorList)

        val finalColor = Vector3f(0f, 0f, 0f)
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
        return Color(clamp01To255(finalColor.x), clamp01To255(finalColor.y), clamp01To255(finalColor.z))
    }

    fun clamp01To255(value: Float): Int {
        return (value.coerceIn(0f, 1f) * 255).toInt()
    }

    class RayTraceData(
        val wc: Vector3f,
        val wx: Float,
        val vector: Vector3f
    )
    data class ColorData(
        val color: Vector3f,
        val brightness: Float,
        val wx: Float,
    )

    fun applyBoxBlur(input: BufferedImage, passes: Int = 1): BufferedImage {
        var image = input

        repeat(passes) {
            val width = image.width
            val height = image.height
            val blurred = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0
                    var count = 0

                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = (x + dx).coerceIn(0, width - 1)
                            val ny = (y + dy).coerceIn(0, height - 1)
                            val rgb = image.getRGB(nx, ny)
                            val color = Color(rgb)
                            rSum += color.red
                            gSum += color.green
                            bSum += color.blue
                            count++
                        }
                    }

                    val r = (rSum / count).coerceIn(0, 255)
                    val g = (gSum / count).coerceIn(0, 255)
                    val b = (bSum / count).coerceIn(0, 255)
                    blurred.setRGB(x, y, Color(r, g, b).rgb)
                }
            }

            image = blurred
        }

        return image
    }

    private fun processBilateralChunk(
        input: BufferedImage,
        output: BufferedImage,
        chunkStartY: Int,
        chunkEndY: Int,
        outputStartY: Int,
        outputEndY: Int,
        width: Int,
        radius: Int,
        sigmaColor2: Float,
        sigmaSpace2: Float
    ) {
        val pixels = Array(chunkEndY - chunkStartY + 1) { offset ->
            IntArray(width) { x ->
                input.getRGB(x, offset + chunkStartY)
            }
        }

        for (y in outputStartY until outputEndY) {
            for (x in 0 until width) {
                val centerColor = Color(pixels[y - chunkStartY][x])

                var rSum = 0f
                var gSum = 0f
                var bSum = 0f
                var weightSum = 0f

                for (dy in ((-radius).coerceAtLeast(chunkEndY - y))..(radius.coerceAtMost(chunkStartY - y))) {
                    val ny = y + dy
                    for (dx in -(radius.coerceAtLeast(width - x))..(radius.coerceAtMost(-x))) {
                        val nx = x + dx
                        val neighborColor = Color(pixels[ny - chunkStartY][nx])

                        // 颜色差
                        val dr = neighborColor.red - centerColor.red
                        val dg = neighborColor.green - centerColor.green
                        val db = neighborColor.blue - centerColor.blue
                        val colorDist2 = (dr * dr + dg * dg + db * db).toFloat()

                        // 空间差
                        val spaceDist2 = (dx * dx + dy * dy).toFloat()

                        // 计算权重
                        val weight = exp(-(colorDist2 / sigmaColor2 + spaceDist2 / sigmaSpace2)).toFloat()

                        rSum += neighborColor.red * weight
                        gSum += neighborColor.green * weight
                        bSum += neighborColor.blue * weight
                        weightSum += weight
                    }
                }

                if (weightSum > 0.001f) {
                    val r = (rSum / weightSum).toInt().coerceIn(0, 255)
                    val g = (gSum / weightSum).toInt().coerceIn(0, 255)
                    val b = (bSum / weightSum).toInt().coerceIn(0, 255)

                    output.setRGB(x, y, Color(r, g, b).rgb)
                } else {
                    output.setRGB(x, y, centerColor.rgb)
                }
            }
        }
    }

    fun applyBilateralFilter(input: BufferedImage, diameter: Int = 3, sigmaColor: Float = 25f, sigmaSpace: Float = 3f): BufferedImage {
        val width = input.width
        val height = input.height
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        val radius = diameter / 2
        val sigmaColor2 = 2 * sigmaColor * sigmaColor
        val sigmaSpace2 = 2 * sigmaSpace * sigmaSpace

        val chunks = (Runtime.getRuntime().availableProcessors() * 2).coerceIn(4, 16)
        val chunkHeight = (height / chunks).coerceAtLeast(1)
        val futures = mutableListOf<CompletableFuture<Void>>()

        for (i in 0 until chunks) {
            val startY = i * chunkHeight
            var endY = (i + 1) * chunkHeight

            if (i == chunks - 1) {
                endY = height
            }

            val chunkStartY = (startY - radius).coerceAtLeast(0)
            val chunkEndY = (endY + radius).coerceAtMost(height - 1)

            futures.add(CompletableFuture.runAsync({
                processBilateralChunk(
                    input, output,
                    chunkStartY, chunkEndY,
                    startY, endY,
                    width, radius,
                    sigmaColor2, sigmaSpace2
                )
            }, executor))
        }

        return output
    }
    fun applyMedianFilter(input: BufferedImage, passes: Int = 1): BufferedImage {
        var image = input
        repeat(passes) {
            val width = image.width
            val height = image.height
            val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val reds = mutableListOf<Int>()
                    val greens = mutableListOf<Int>()
                    val blues = mutableListOf<Int>()

                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = (x + dx).coerceIn(0, width - 1)
                            val ny = (y + dy).coerceIn(0, height - 1)
                            val color = Color(image.getRGB(nx, ny))
                            reds += color.red
                            greens += color.green
                            blues += color.blue
                        }
                    }

                    val medianColor = Color(
                        reds.sorted()[4],
                        greens.sorted()[4],
                        blues.sorted()[4]
                    )
                    result.setRGB(x, y, medianColor.rgb)
                }
            }
            image = result
        }

        return image
    }


    fun rayTracing(rayTraceResult: RayTraceResult?, reflectionTimes: Int = 0, parentRayTraceData: RayTraceData, globalColorList: MutableList<ColorData>): Boolean{
        if(rayTraceResult == null || rayTraceResult.hitBlock == null){
            //Hit to sky
            val light = LightMaterial.SKY_LIGHT
            val colorData = ColorData(
                parentRayTraceData.wc.mul(light.lightColor.toOneRange()),
                light.brightness,
                parentRayTraceData.wx
            )
            globalColorList.add(colorData)
            return true
        }
        if(reflectionTimes > maxReflectionTimes || parentRayTraceData.wx < 0.001f) {
            return false
        }

        if(rayTraceResult.hitBlock!!.type.isLight()){
            val light = rayTraceResult.hitBlock!!.type.getLight()
//            val lightColor = getColorInWorldTexture(rayTraceResult)
            val lightColor = light!!.lightColor
//            LightMaterial.colorBleaching(lightColor!!, light.brightness.toDouble())
            val colorData = ColorData(
                parentRayTraceData.wc.mul(lightColor.toOneRange()),
                light.brightness,
                parentRayTraceData.wx
            )
            globalColorList.add(colorData)
            return true
        }

        val hitColor = getColorInWorldTexture(rayTraceResult)?.toOneRange()?.mul(parentRayTraceData.wc) ?: return false
        val hitMaterial = rayTraceResult.hitBlock!!.type.getReflectionMaterialData()
        val hitPos = rayTraceResult.hitPosition
        val idealReflectionVector = VectorUtil.getReflectedVector(parentRayTraceData.vector, VectorUtil.normalFromBlockFace(rayTraceResult.hitBlockFace!!))

        val world = rayTraceResult.hitBlock!!.world

        val k = 1.0
        val reflectionTimesWeight = (maxReflectionTimes.toFloat() - reflectionTimes.toFloat()) * k

        val totals = hitMaterial.reflectionTimes
        var misses = 0

        repeat(hitMaterial.reflectionTimes) {
            val perturbedVector = VectorUtil.perturbDirection(idealReflectionVector, hitMaterial.spread)

            val angleWeight = hitMaterial.weight(perturbedVector.dot(idealReflectionVector).toDouble())

            val finalWeight = angleWeight * reflectionTimesWeight
            if(finalWeight == 0.001) return@repeat
            val rayTraceResult = world.rayTraceBlocks(
                Location(world, hitPos.x, hitPos.y, hitPos.z),
                perturbedVector.toVector(),
                raytraceDistance
            )
            if (!rayTracing(
                    rayTraceResult,
                    reflectionTimes + 1,
                    RayTraceData(
                        wc = hitColor,
                        finalWeight.toFloat(),
                        perturbedVector,
                    ),
                    globalColorList,
                )
            ) {
                misses += 1
            }
        }


//        val missRate = misses.toFloat() / totals.toFloat()
////        val darkColor = Color(22, 22, 22)
//        val darkColor = parentRayTraceData.wc.mul(0.2f)
////            LightMaterial.colorBleaching(lightColor!!, light.brightness.toDouble())
//        val colorData = ColorData(
//            parentRayTraceData.wc.mul(darkColor),
//            0.1f,
//            parentRayTraceData.wx * missRate
//        )
//        globalColorList.add(colorData)

        return true
    }

    fun getColorInWorldTexture(rayTraceResult: RayTraceResult?): Color? {
        if (rayTraceResult == null) return null

        val hitBlock = rayTraceResult.hitBlock ?: return null
        val hitFace = rayTraceResult.hitBlockFace ?: return null
        val material = hitBlock.type

        val image = TextureManager.getTexture(material, hitFace) ?: return null

        val relativeHit = rayTraceResult.hitPosition.subtract(hitBlock.location.toVector())
        val x = relativeHit.x.toFloat()
        val y = relativeHit.y.toFloat()
        val z = relativeHit.z.toFloat()

        val (texX, texY) = TextureManager.getTextureCoords(
            hitFace, x, y, z, image.width, image.height
        )

        return Color(image.getRGB(texX, texY))
    }

    /** TODO:
     *   全局信息: 最大反射次数s
     *             每条射线均有一个Wc(Wr, Wg, Wb) 和一个 Wx
     *             k在每一步中均为不定值而非变量
     *             每个像素点维护一个ColorList
     *     1. 由 摄像机 -> 像素 作为方向，发射一条主射线，发射点维护一个Color
     *     2. 主射线的碰撞点p，获取点p的material(spread, weightFunction, raytracePrecision),
     *     3. 如果射线撞到
     *       - 方块
     *         1. 获取撞击点所对应的像素颜色，点p的Wc就是对点p所对应射线的Wc进行分量相乘
     *         2. 如果当前反射次数大于最大反射次数，直接忽略这条射线
     *         3. 由入射射线得到的反射射线为基础, 发射raytracePrecision条射线
     *           - 每条射线的Wc等于点p的颜色
     *           - 每条射线基于反射射线进行spread * k的偏移
     *           - 每条射线的 Wx 等于 点p对应的入射射线的Wx * k * (s - 当前反射次数) * material.weightFunction(理想反射射线.dot(偏移后射线))
     *       - 光源
     *         1. 以Wx的明度贡献，对光源颜色泛化后（指由光源强度决定由白（高能）到光源本色（低能）的过程）与Wc分量相乘添加到ColorList
     *     4. 如果所有射线都返回，对ColorList进行比例*k混色，明度则是明度贡献中最大的Wx，最终就是像素颜色
     *   补充：
     *     每个射线加一条路径长度/经过低能见度区域距离，和一个空气能见度系数，
     *     路径长度乘以空气能见度系数得到x，用环境颜色（附近3*3区域内随机撒点，
     *     得到的像素颜色均值）乘以x，与上述计算所得的此color进行混色，得到finalColor
     */
}
private fun Color.toOneRange(): Vector3f {
    return Vector3f(this.red.toFloat() / 255, this.green.toFloat() / 255, this.blue.toFloat() / 255)
}

private fun Color.toVector3i(): Vector3i{
    return Vector3i(this.red, this.green, this.blue)
}
private fun Vector3i.toOneRange(): Vector3f {
    return Vector3f(this.x.toFloat() / 255, this.y.toFloat() / 255, this.z.toFloat() / 255)
}
private fun Vector3f.toVector(): Vector {
    return Vector(this.x, this.y, this.z)
}


