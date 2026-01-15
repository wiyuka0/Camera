package com.methyleneblue.camera.obj

import com.methyleneblue.camera.obj.RayTraceCamera.Companion.findSeaLanternsUsingThreads
import com.methyleneblue.camera.obj.raytrace.RayTraceMaterial
import com.methyleneblue.camera.obj.raytrace.getReflectionMaterialData
import com.methyleneblue.camera.raytracepack.bvh.BVHTree
import com.methyleneblue.camera.raytracepack.bvh.FlatBVHNode
import com.methyleneblue.camera.raytracepack.bvh.HitResult
import com.methyleneblue.camera.texture.TextureManager
import com.methyleneblue.camera.texture.TextureManager.skyColor
import com.methyleneblue.camera.util.VectorUtil
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.boss.BossBar
import org.bukkit.util.Vector
import org.joml.Vector3f
import java.awt.image.BufferedImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.random.Random

class BetterRTCamera(
    location: Location,
    size: Pair<Int, Int>,
    fov: Double,
    distance: Double,
    progressBar: BossBar?,
    bufferedImage: BufferedImage,
    depthImage: Array<FloatArray>
) : BVHCamera(
    location,
    size,
    fov,
    distance,
    progressBar,
    bufferedImage,
    depthImage,
) {

    private val maxBounces = 4
    // 调大一点点防止浮点数精度误差导致的自阴影
    private val EPSILON = 0.005f
    private var cachedLights: List<Location>? = null

    companion object {
        // 如果你的服务端支持异步调用Bukkit API，这个线程池就没问题
        private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    }

    override fun getColorInWorld(
        rayTraceResult: HitResult?,
        startDirection: Vector3f,
        flatBVHNode: Array<FlatBVHNode>, // 忽略
        bvhTree: BVHTree // 忽略
    ): java.awt.Color {

        ensureLightsCached()

        val accumulatedColor = Vector3f(0f, 0f, 0f)
        val throughput = Vector3f(1f, 1f, 1f)

        var currentRayOrigin: Vector3f
        var currentRayDirection = startDirection
        var currentHit: HitResult?

        // 1. 初始化第一帧
        if (rayTraceResult != null) {
            currentHit = rayTraceResult
            // 如果传入了 result，起点就是命中点
            currentRayOrigin = getHitPosition(rayTraceResult)
        } else {
            currentRayOrigin = location.toVector().toVector3f()
            currentHit = traceBukkit(currentRayOrigin, currentRayDirection)
        }

        // 打空直接返回天空
        if (currentHit == null) {
            return vecToColor(skyColorVector(location.world.time))
        }

        // --- 路径追踪循环 ---
        for (bounce in 0 until maxBounces) {
            // 确保有法线，如果没有（在方块内部等情况），默认向上
            val face = currentHit!!.face ?: BlockFace.UP
            val normalVector = face.direction
            val normal = Vector3f(normalVector.x.toFloat(), normalVector.y.toFloat(), normalVector.z.toFloat())

            // 获取命中点坐标
            val hitPos = currentHit!!.hitPosition ?: getHitPosition(currentHit!!)

            // 修正：获取方块必须向法线反方向偏移，进入方块内部，否则 int 取整会出错
            val hitBlock = getHitBlock(hitPos, normal)

            // 如果取不到方块（空气），则跳过（理论上 rayTrace 不会打中空气，但为了健壮性）
            if (hitBlock.type.isAir) {
                break
            }

            // 获取材质和颜色
            val materialData = hitBlock.type.getReflectionMaterialData()
            val albedoColor = getTextureColor(hitBlock, currentHit!!, hitPos) ?: Vector3f(1f, 1f, 1f)

            // 2. 自发光 (Emission)
            // 直接加上自发光贡献。如果是第一次弹射或者是镜面反射过来的，才加？
            // 这里的逻辑是只要打中光源就算。
            if (materialData.isLight) {
                val emission = materialData.lightColor.toOneRange().mul(materialData.brightness)
                accumulatedColor.add(emission.mul(throughput))
                // 打中光源通常不再继续追踪（除非是半透明光源，这里简化处理停止）
                break
            }

            // 3. NEE (显式光源采样)
            // 只有当材质不是绝对光滑时才做 NEE（镜面反射靠 bounce 撞到光源）
            if (cachedLights != null && cachedLights!!.isNotEmpty() && materialData.spread > 0.01f) {
                val lightLoc = cachedLights!!.random()
                // 光源采样点：中心 + 少量随机偏移减少条纹，这里简化取中心
                val lightPos = Vector3f(lightLoc.blockX + 0.5f, lightLoc.blockY + 0.5f, lightLoc.blockZ + 0.5f)

                val toLight = Vector3f(lightPos).sub(hitPos)
                val distToLight = toLight.length()
                val toLightDir = Vector3f(toLight).normalize()

                val nDotL = normal.dot(toLightDir)

                if (nDotL > 0) {
                    // 阴影射线起点：向法线外推，防止打中自己
                    val shadowOrigin = Vector3f(hitPos).add(Vector3f(normal).mul(EPSILON))

                    // 发射阴影射线，最大距离限制为光源距离
                    // 稍微减去一点距离防止打中光源本身造成的遮挡误判
                    val shadowHit = traceBukkit(shadowOrigin, toLightDir, (distToLight - 0.1).toDouble())

                    var visible = false
                    if (shadowHit == null) {
                        // 没打中任何东西，说明通向光源的路是通的
                        visible = true
                    } else {
                        // 打中了东西，检查打中的是不是透光物体（可选），或者距离已经超过光源
                        if (shadowHit.distance >= distToLight - 0.1) {
                            visible = true
                        }
                    }

                    if (visible) {
                        // 简化光照强度模型：距离平方反比
                        // Minecraft 光源通常比较暗，这里手动增强一下强度
                        val attenuation = 1.0f / (distToLight * distToLight + 1.0f)
                        val lightIntensity = 10.0f * attenuation // 系数可调

                        val contribution = Vector3f(throughput)
                            .mul(albedoColor)
                            .mul(lightIntensity * nDotL) // Diffuse logic

                        accumulatedColor.add(contribution)
                    }
                }
            }

            // 4. 计算下一次反弹方向
            val idealReflect = Vector3f()
            VectorUtil.getReflectedVector(currentRayDirection, normal, idealReflect)

            val nextDir = Vector3f()
            if (materialData.spread < 0.001f) {
                // 全镜面
                nextDir.set(idealReflect)
            } else {
                // 漫反射/粗糙反射
                VectorUtil.perturbDirection(idealReflect, materialData.spread, nextDir)
                nextDir.normalize()
            }

            // 检查是否射入表面内部
            if (nextDir.dot(normal) < 0) {
                // 如果随机到了内部，对于不透明物体直接截断
                // 或者简单的反转法线方向（Hack）
                nextDir.add(normal).normalize()
            }

            // 5. 更新吞吐量
            // 标准路径追踪中，如果采样是按余弦分布的，这里只需要乘 Albedo
            // 如果不是余弦分布，需要乘 (nDotNext / pdf)
            // 假设 perturbDirection 是均匀分布或者我们简化处理：
            val nDotNext = max(0f, normal.dot(nextDir))
            throughput.mul(albedoColor).mul(nDotNext) // 简化模型

            // 6. 俄罗斯轮盘赌 (Russian Roulette)
            if (bounce > 2) {
                val p = max(throughput.x, max(throughput.y, throughput.z))
                // 增加存活概率，防止画面过暗
                if (Random.nextFloat() > p || p < 0.05f) {
                    break
                }
                throughput.mul(1.0f / p)
            }

            // 7. 推进
            currentRayDirection = nextDir
            // 起点偏移：HitPos + Normal * Epsilon
            currentRayOrigin = Vector3f(hitPos).add(Vector3f(normal).mul(EPSILON))

            currentHit = traceBukkit(currentRayOrigin, currentRayDirection)

            if (currentHit == null) {
                val sky = skyColorVector(location.world.time)
                accumulatedColor.add(sky.mul(throughput))
                break
            }
        }

        return vecToColor(accumulatedColor)
    }

    // --- 核心修复：坐标与方块获取 ---

    /**
     * 根据命中点和法线，安全地获取被命中的方块
     * 关键逻辑：向法线反方向偏移一点点，确保坐标落入方块 Block 坐标系内部
     */
    private fun getHitBlock(hitPos: Vector3f, normal: Vector3f): Block {
        val safeX = hitPos.x - (normal.x * 0.05)
        val safeY = hitPos.y - (normal.y * 0.05)
        val safeZ = hitPos.z - (normal.z * 0.05)
        return location.world.getBlockAt(safeX.toInt(), safeY.toInt(), safeZ.toInt())
    }

    private fun traceBukkit(origin: Vector3f, dir: Vector3f, maxDist: Double = this.distance): HitResult? {
        // 防止 NaN 或 0 向量
        if (dir.lengthSquared() < 0.0001f) return null

        val startLoc = Location(location.world, origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble())
        val bukkitDir = Vector(dir.x.toDouble(), dir.y.toDouble(), dir.z.toDouble())

        if (!(startLoc.isFinite && bukkitDir.toVector3f().isFinite))  {
            return null
        }
        // 你的服务端既然线程安全，这里直接调
        val result = location.world.rayTraceBlocks(
            startLoc,
            bukkitDir,
            maxDist,
            FluidCollisionMode.NEVER,
            false
        ) ?: return null

        val hitVec = result.hitPosition
        val hitFace = result.hitBlockFace ?: BlockFace.UP

        // 计算距离
        val dist = startLoc.toVector().distance(hitVec).toFloat()

        return HitResult(
            hitPosition = Vector3f(hitVec.x.toFloat(), hitVec.y.toFloat(), hitVec.z.toFloat()),
            distance = dist,
            face = hitFace,
            startPos = origin,
            direction = dir
        )
    }

    private fun ensureLightsCached() {
        if (cachedLights == null) {
            val clock = CountDownLatch(1)
            val range = distance
            // 假设这个方法在你项目中是安全的
            findSeaLanternsUsingThreads(
                location.world,
                location.clone().add(range, range, range),
                location.clone().subtract(range, range, range)
            ) {
                cachedLights = it
                clock.countDown()
            }
            try {
                // 设置个超时，防止死锁卡死渲染线程
                if (!clock.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    cachedLights = emptyList() // 超时就当没灯
                }
            } catch (e: Exception) {
                e.printStackTrace()
                cachedLights = emptyList()
            }
        }
    }

    // 修复：使用传入的 HitBlock 而不是重新 rayTrace 或错误计算坐标
    private fun getTextureColor(hitBlock: Block, hit: HitResult, hitPos: Vector3f): Vector3f? {
        val image = TextureManager.getTexture(hitBlock.type, hit.face!!) ?: return null

        // 计算相对坐标：HitPos - BlockPos
        // BlockPos 必须是该方块的左下角坐标
        val blockOrigin = hitBlock.location.toVector().toVector3f()
        val relativeHit = Vector3f(hitPos).sub(blockOrigin)

        // 防止相对坐标出现负数（浮点误差）或超过1.0
        relativeHit.x = relativeHit.x.coerceIn(0f, 1f)
        relativeHit.y = relativeHit.y.coerceIn(0f, 1f)
        relativeHit.z = relativeHit.z.coerceIn(0f, 1f)

        val (texX, texY) = TextureManager.getTextureCoords(hit.face!!, relativeHit.x, relativeHit.y, relativeHit.z, image.width, image.height)

        // 边界检查防止 getRGB 崩
        val safeX = texX.coerceIn(0, image.width - 1)
        val safeY = texY.coerceIn(0, image.height - 1)

        val rgb = image.getRGB(safeX, safeY)
        val c = java.awt.Color(rgb)
        return Vector3f(c.red / 255f, c.green / 255f, c.blue / 255f)
    }

    private fun getHitPosition(hit: HitResult): Vector3f {
        return hit.hitPosition ?: Vector3f(hit.startPos!!).add(Vector3f(hit.direction!!).mul(hit.distance))
    }

    private fun skyColorVector(time: Long): Vector3f {
        val sc = skyColor(time)
        return Vector3f(sc.red / 255f, sc.green / 255f, sc.blue / 255f)
    }

    private fun vecToColor(v: Vector3f): java.awt.Color {
        // 简单的 Reinhard Tone Mapping，防止过曝
        val mappedX = v.x / (1.0f + v.x)
        val mappedY = v.y / (1.0f + v.y)
        val mappedZ = v.z / (1.0f + v.z)

        val r = (mappedX.coerceIn(0f, 1f) * 255).toInt()
        val g = (mappedY.coerceIn(0f, 1f) * 255).toInt()
        val b = (mappedZ.coerceIn(0f, 1f) * 255).toInt()
        return java.awt.Color(r, g, b)
    }
}