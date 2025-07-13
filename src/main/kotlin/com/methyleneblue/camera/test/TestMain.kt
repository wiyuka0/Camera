package com.methyleneblue.camera.test

import com.methyleneblue.camera.imagepack.AfterEffect
import com.methyleneblue.camera.raytracepack.bvh.BVHTree
import com.methyleneblue.camera.raytracepack.bvh.HitResult
import com.methyleneblue.camera.raytracepack.bvh.jocl.JoclInterface
import com.methyleneblue.camera.raytracepack.bvh.jocl.async.AsyncFuture
import com.methyleneblue.camera.texture.TextureManager
import com.methyleneblue.camera.util.VectorUtil
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.util.Vector
import org.joml.Vector3f
import org.joml.Vector3i
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.tan
import kotlin.random.Random


fun main() {
    // main1()
    aeTest()
}

fun aeTest() {
    // val input = ImageIO.read(File("C:\\image\\12input1.png"))
    // val output = AfterEffect.apply(input, 90.0)
    // ImageIO.write(output, "png", File("C:\\image\\output1.png"))
}

fun main1() {

    val defaultMaterial = Material.DIRT
    val bvhTree = BVHTree()
    val rng = Random(42) // 固定种子，保证可复现

    TextureManager.init()

    val fixedBlocks = listOf(
//        Vector3i(0, 0, 0),
//        Vector3i(0, 1, 0), Vector3i(1, 1, 0), Vector3i(-1, 1, 0),
        //Vector3i(0, 2, 0),
        //Vector3i(1, 2, 0),
        Vector3i(-2, 2, -3),
        Vector3i(-1, 2, -3),
        Vector3i(0, 2, -3),
        Vector3i(1, 2, -3),
        Vector3i(2, 2, -3),
        Vector3i(3, 2, -3),
        Vector3i(0, 3, -3),
        Vector3i(1, 3, -3),
        Vector3i(2, 3, -3),
        Vector3i(3, 3, -3),

        Vector3i(0, 0, -3),
        Vector3i(1, 0, -3),
        Vector3i(2, 0, -3),
        Vector3i(3, 0, -3),
        Vector3i(0, 0, -2),
        Vector3i(1, 0, -2),
        Vector3i(2, 0, -2),
        Vector3i(3, 0, -2),
        Vector3i(0, 0, -1),
        Vector3i(1, 0, -1),
        Vector3i(2, 0, -1),
        Vector3i(3, 0, -1),
        Vector3i(0, 0, 0),
        Vector3i(1, 0, 0),
        Vector3i(2, 0, 0),
        Vector3i(3, 0, 0),

        Vector3i(0, 0, -4),
        Vector3i(1, 0, -4),
        Vector3i(2, 0, -4),
        Vector3i(3, 0, -4),

        Vector3i(0, 0, -5),
        Vector3i(1, 0,  -5),
        Vector3i(2, 0,  -5),
        Vector3i(3, 0,  -5),

        Vector3i(0, 0, -6),
        Vector3i(1, 0, -6),
        Vector3i(2, 0, -6),
        Vector3i(3, 0, -6),

        Vector3i(0, 0, -7),
        Vector3i(1, 0, -7),
        Vector3i(2, 0, -7),
        Vector3i(3, 0, -7),

        Vector3i(0, 0, -8),
        Vector3i(1, 0, -8),
        Vector3i(2, 0, -8),
        Vector3i(3, 0, -8),

        Vector3i(0, 0, -9),
        Vector3i(1, 0, -9),
        Vector3i(2, 0, -9),
        Vector3i(3, 0, -9),
    ) // 方块坐标
    for (block in fixedBlocks) {
        bvhTree.addBlock(block, material = Material.DIRT)
    }

    // 添加随机方块
    val randomBlockCount = 10
    val addedBlocks = mutableSetOf<Vector3i>()
    repeat(randomBlockCount) {
        val x = rng.nextInt(-10, 10)
        val y = rng.nextInt(0, 5)
        val z = rng.nextInt(-10, 10)
        val block = Vector3i(x, y, z)
        if (addedBlocks.add(block)) {
//            bvhTree.addBlock(block, material = Material.STONE)
        }
    }

    bvhTree.buildTree()
    val flatBVHNode = bvhTree.root!!.flatten().toTypedArray()

    // 固定测试射线
//    val testRays = listOf(
//        Pair(Vector3f(-2.324f, 1.224f, 1.065f), Vector3f(0.98f, 0f, 0.196f)),
//        Pair(Vector3f(1f, 8f, 1f), Vector3f(0f, -1f, 0f)),
//        Pair(Vector3f(1f, 3f, -2f), Vector3f(0f, 0f, 1f)),
//        Pair(Vector3f(0f, 0f, 0f), Vector3f(-1f, 0f, 0f))
//    )
    val testRays = listOf(
        Pair(Vector3f(-2.324f, 1.224f, 1.065f), Vector3f(0.98f, 0f, 0.196f)),
        Pair(Vector3f(1f, 8f, 1f), Vector3f(0f, -1f, 0f)),
        Pair(Vector3f(1f, 3f, -2f), Vector3f(0f, 0f, 1f)),
        Pair(Vector3f(0f, 0f, 0f), Vector3f(-1f, 0f, 0f))
    )

    // 随机射线测试
    val randomRayCount = 10
    val randomRays = (0 until randomRayCount).map {
        val start = Vector3f(
            rng.nextFloat() * 20f - 10f,
            rng.nextFloat() * 10f,
            rng.nextFloat() * 20f - 10f
        )
        val dir = Vector3f(
            rng.nextFloat() * 2f - 1f,
            rng.nextFloat() * 2f - 1f,
            rng.nextFloat() * 2f - 1f
        ).normalize()
        Pair(start, dir)
    }

    val allRays = testRays + randomRays

    val futures = allRays.map { (start, dirRaw) ->
        val dir = dirRaw.normalize()
//        reflectionRayTest(bvhTree, start, dir)
        Triple(start, dir, JoclInterface.traceRay(start, dir))
//        Triple(start, dir, bvhTree.rayTrace(start, dir))
    }

    JoclInterface.processResults(flatBVHNode, bvhTree)

    for ((index, triple) in futures.withIndex()) {
        val (start, dir, future) = triple
        val result = future.get()
        val hitPoint = result!!.distance.let { Vector3f(start).add(Vector3f(dir).mul(it)) }

        if(result == null || hitPoint == null){
            println("Ray #$index")
            println("  Start        : ${formatVec(start)}")
            println("  Direction    : ${formatVec(dir)}")
            println("  Hit point    : Non-Hit")
            println("  t (distance) : -1")
            println("  Real distance: Non-Hit")
            println("  Voxel hit    : Non-Hit")
            println()
            continue
        }
        val realDistance = start.distance(hitPoint)

        val voxelHit = Vector3i(
            floor(hitPoint.x).toInt(),
            floor(hitPoint.y).toInt(),
            floor(hitPoint.z).toInt()
        )

        println("Ray #$index")
        println("  Start        : ${formatVec(start)}")
        println("  Direction    : ${formatVec(dir)}")
        println("  Hit point    : ${formatVec(hitPoint)}")
        println("  t (distance) : ${formatFloat(result.distance)}")
        println("  Real distance: ${formatFloat(realDistance)}")
        println("  Voxel hit    : ${formatVecInt(voxelHit)}")
        println()
    }

    val fov = 140.0
    val width = 1920
    val height = 1920
    val aspectRatio = width.toFloat() / height.toFloat()
    val fovRad = Math.toRadians(fov)

    val location = Location (null, 1.0, 3.4, -8.0, 0.0f, 20.0f) // 摄像机坐标

    val forward = location.direction.normalize()
    val upVector = Vector(0.0, 1.0, 0.0)
    val right = forward.clone().crossProduct(upVector).normalize()
    val up = right.clone().crossProduct(forward).normalize()

    val halfWidth = tan(fovRad / 2.0)
    val halfHeight = halfWidth / aspectRatio

    val totalRayTraceCount = width * height


    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    var count = 0

    data class Pack(val i: Int, val j: Int, val result: AsyncFuture<HitResult?>)

    val future1: MutableList<Pack> = mutableListOf()

    for (j in 0 until height) {
        val v = (1.0 - (j + 0.5) / height) * 2 - 1
        for (i in 0 until width) {
            val u = ((i + 0.5) / width) * 2 - 1
            count++

            val dir = forward.clone()
                .add(right.clone().multiply(u * halfWidth))
                .add(up.clone().multiply(v * halfHeight))
                .normalize()

//            val hit = bvhTree.rayTrace(location.toVector().toVector3f(), dir.toVector3f())
            val hitFuture = JoclInterface.traceRay(location.toVector().toVector3f(), dir.toVector3f())
            future1.add(Pack(i, j, hitFuture))

            if(count > 50000) {
                JoclInterface.processResults(flatBVHNode, bvhTree)
                count = 0
            }
        }
    }

    JoclInterface.processResults(flatBVHNode, bvhTree)
    fun blendColors(colorA: Color, colorB: Color, ratio: Float): Color {
        val clampedRatio = ratio.coerceIn(0f, 1f)

        val r = (colorA.red * (1 - clampedRatio) + colorB.red * clampedRatio).toInt()
        val g = (colorA.green * (1 - clampedRatio) + colorB.green * clampedRatio).toInt()
        val b = (colorA.blue * (1 - clampedRatio) + colorB.blue * clampedRatio).toInt()
        val a = (colorA.alpha * (1 - clampedRatio) + colorB.alpha * clampedRatio).toInt()

        return Color(r, g, b, a)
    }
    var index = 0
    for ((i, j, hitFuture) in future1) {
        val hit = hitFuture.get()

        if(hit == null) continue
        if(index == future1.size - 1){
            println()
        }
        index++
        val color = if(hit!!.startPos == null) Color.WHITE else {

            var finalColor: Color? = null
            val hitPos = hit.hitPosition
            val hitFace = hit.face

            if (hitPos == null || hitFace == null || hitPos.x.isNaN()) {
                finalColor = Color.WHITE
            } else {

                finalColor = TextureManager.getWorldColorTexture(defaultMaterial, hitPos, hitFace)
            }
//
////            } else
//                if (hitPos.x.isNaN()) {
//                    finalColor = Color.WHITE
//                } else {
////
////            finalColor
//
////                    if (hit.hitPosition.x < 1) {
////                        finalColor = Color.RED
////                    } else {
//                        val k = 10f
//                        val fr = Math.clamp(abs(hit.hitPosition.x / k * 255).toLong(), 0, 255)
//                        val fg = Math.clamp(abs(hit.hitPosition.y / k * 255).toLong(), 0, 255)
//                        val fb = Math.clamp(abs(hit.hitPosition.z / k * 255).toLong(), 0, 255)
////                println("$fr, $fg, $fb")
//                        finalColor = Color(fr, fr, fr)
////                    }
//                }
//            }
//            if(finalColor.red == 255){
////                println()
//            }
//            if(finalColor.red == 0){
////                println()
//            }
            finalColor
        }
/*
//            val hitPos = Vector3f(hit.startPos).add(hit.direction!!.mul(hit.distance))
//                val realDistance = hitPos.distance(hit.startPos)

//            val realDistance = hit.distance
//            val realDistance = location.toVector().toVector3f().distance(Vector3f(hit.hitPosition).normalize())
//            val distanceK = realDistance / 30.5
//            val distanceColor = Color(
//                255, 255, 255
//            )
//            if(distanceK.isNaN()) {
//                println(hit.hitPosition)
//                println(hit.distance)
//            }
//            var newColor: Color? = null
//            if(hit.face == org.bukkit.block.BlockFace.UP) {
//                newColor = Color(0, 0, distanceColor.blue)
//            } else if(hit.face == org.bukkit.block.BlockFace.EAST) {
////                println(distanceK)
//                newColor = Color(0, distanceColor.green, 0)
//            } else if(hit.face == org.bukkit.block.BlockFace.NORTH) {
//                newColor = Color(distanceColor.red, 0, 0)
//            } else if(hit.face == org.bukkit.block.BlockFace.SOUTH) {
//                newColor = Color(0, distanceColor.green, distanceColor.blue)
//            } else if (hit.face == org.bukkit.block.BlockFace.WEST) {
//                newColor = Color(distanceColor.red, 0, distanceColor.blue)
//            } else if (hit.face == org.bukkit.block.BlockFace.DOWN) {
//                newColor = Color(distanceColor.red, distanceColor.green, 0)
//            } else newColor = Color.WHITE
////            newColor
//            blendColors(newColor, Color.WHITE, distanceK.toFloat())
//        }*/
        image.setRGB(i, j, color!!.rgb)
    }
    ImageIO.write(image, "png", File("test.png"))
}


fun reflectionRayTest(bvhTree: BVHTree, startPos: Vector3f, direction: Vector3f, reflectionTimes: Int = 2){
    val (cpu, gpu) = doubleTest(bvhTree, startPos, direction.normalize())
    if(cpu == null || gpu == null){
        return
    }
    if(cpu == null) {
        if(gpu!!.distance != 0f && gpu.distance != -1f) {
            throw IllegalStateException("GPU accidental hit")
        } else return
    }

    if(gpu.distance == -1f) {
        throw IllegalStateException("GPU accidental miss")
    }
    if(cpu.distance != gpu.distance) {
        val cpuDirNormalize = cpu.direction!!.normalize()

        val cpuHitPos = cpu.startPos!!.add(cpuDirNormalize.mul(cpu.distance))
        val realDistance = cpuHitPos.distance(startPos)

        println("Ray #CPU")
        println("  Start        : ${formatVec(startPos)}")
        println("  Direction    : ${formatVec(direction)}")
        println("  Hit point    : ${formatVec(cpuHitPos)}")
        println("  t (distance) : ${formatFloat(cpu.distance)}")
        println("  Real distance: ${formatFloat(realDistance)}")

        val goyDirNormalize = gpu.direction!!.normalize()
        val gpuHitPos = gpu.hitPosition
//        val gpuHitPos = gpu.startPos!!.add(goyDirNormalize!!.mul(gpu.distance))
        val realDistanceGPU = gpuHitPos!!.distance(startPos)


        println()

        println("Ray #GPU")

        println("  Start        : ${formatVec(startPos)}")
        println("  Direction    : ${formatVec(direction)}")
        println("  Hit point    : ${formatVec(gpuHitPos!!)}")
        println("  t (distance) : ${formatFloat(gpu.distance)}")
        println("  Real distance: ${formatFloat(realDistanceGPU)}")

        throw IllegalStateException("Result is not equal")
    }
    val hitPos = Vector3f(cpu.startPos).add(Vector3f(direction).mul(cpu.distance))

    var reflectionVector = Vector3f()
    reflectionVector = VectorUtil.getReflectedVector(direction, cpu.face!!.direction.toVector3f(), reflectionVector)

    reflectionRayTest(bvhTree, Vector3f(hitPos).add(VectorUtil.faceToNormalMap[cpu.face]?.mul(0.01f)), reflectionVector, reflectionTimes - 1)
}


fun doubleTest(bvhTree: BVHTree, origin: Vector3f, direction: Vector3f): Pair<HitResult?, HitResult?>{
    val flatBVHNode = bvhTree.root!!.flatten()

    val GPUResultFuture = JoclInterface.traceRay(Vector3f(origin), Vector3f(direction))
    val CPUResult = bvhTree.rayTrace(Vector3f(origin), Vector3f(direction))
    JoclInterface.processResults(flatBVHNode.toTypedArray(), bvhTree)

    val GPUResult = GPUResultFuture.get()

    return CPUResult to GPUResult
}


fun formatVec(v: Vector3f): String {
    return "(%7.3E %7.3E %7.3E)".format(v.x, v.y, v.z)
}

fun formatVecInt(v: Vector3i): String {
    return "(%7.3E %7.3E %7.3E)".format(v.x.toFloat(), v.y.toFloat(), v.z.toFloat())
}

fun formatFloat(f: Float): String {
    return "%7.3f".format(f)
}

fun oldMain() {
    println("Running BVH tests...")

    val curLoc = Vector(0.5, 0.5, 0.5)
    val dir = Vector(1.0, -0.5, 0.0)

    val bvhTree = BVHTree()
    repeat(10) {
        val rL = randomLocation(Vector3i(-10, -10, -10), Vector3i(10, 10, 10))
        val rV = Vector3i(rL.x.toInt(), rL.y.toInt(), rL.z.toInt())
        bvhTree.addBlock(//原来是测试用例的问题吗）
            location = rV,
            material = randomMaterial(),
            scale = Vector3f(1f, 1f, 1f)
        )
    }
    bvhTree.buildTree()

    val width = 1280
    val height = 720
    val aspectRatio = width.toFloat() / height.toFloat()
    val fovRad = Math.toRadians(90.0)

    val forward = dir
    val upVector = Vector(0.0, 1.0, 0.0)
    val right = forward.clone().crossProduct(upVector).normalize()
    val up = right.clone().crossProduct(forward).normalize()

    val halfWidth = tan(fovRad / 2.0)
    val halfHeight = halfWidth / aspectRatio

    val numThreads = Runtime.getRuntime().availableProcessors()
    val rowsPerThread = height / numThreads

    val totalRayTraceCount = width * height
    // var currentRayTraceCount = 0

    for (t in 0 until numThreads) {
        val startRow = t * rowsPerThread
        val endRow = if (t == numThreads - 1) height else (t + 1) * rowsPerThread

        for (j in startRow until endRow) {
            val v = (1.0 - (j + 0.5) / height) * 2 - 1
            for (i in 0 until width) {
                val u = ((i + 0.5) / width) * 2 - 1
                // currentRayTraceCount += 1
                // if (currentRayTraceCount % 10000 == 0) {
                    // println("渲染进度: " + (currentRayTraceCount.toFloat() / totalRayTraceCount.toFloat() * 100).toInt() + "%")
                // }

                val dir = forward.clone()
                    .add(right.clone().multiply(u * halfWidth))
                    .add(up.clone().multiply(v * halfHeight))
                    .normalize()

                val result = bvhTree.rayTrace(curLoc.toVector3f(), dir.toVector3f())
                if (result == null) {

                } else {

                }
                // run test main
            }
        }
    }
}

fun randomLocation(min: Vector3i, max: Vector3i): Location{
    val randomX = Random.nextInt(min.x, max.x)
    val randomY = Random.nextInt(min.y, max.y)
    val randomZ = Random.nextInt(min.z, max.z)
    return Location(null, randomX.toDouble(), randomY.toDouble(), randomZ.toDouble())
}

fun randomMaterial(): Material { // code with me 卡了喵
    if (Random.nextBoolean()) { //这里怎么了
        return Material.IRON_BLOCK
    }
    return Material.GLOWSTONE
}