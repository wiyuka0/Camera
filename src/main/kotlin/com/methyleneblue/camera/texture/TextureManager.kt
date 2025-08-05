package com.methyleneblue.camera.texture

import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockFace.DOWN
import org.bukkit.block.BlockFace.EAST
import org.bukkit.block.BlockFace.NORTH
import org.bukkit.block.BlockFace.SOUTH
import org.bukkit.block.BlockFace.UP
import org.bukkit.block.BlockFace.WEST
import org.jetbrains.annotations.Contract
import org.joml.Vector3f
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.*
import java.util.Map
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.math.floor

object TextureManager {
    private val textureBlockFaceCache = ConcurrentHashMap<Material?, EnumMap<BlockFace?, BufferedImage?>?>()
    private val materialOffset = hashMapOf<Material, Int>()
    private val textureCache = ConcurrentHashMap<Material?, BufferedImage?>()
    private var texturesPath: File? = null
    private var normalsPath: File? = null

    val textureArray: IntArray = IntArray(10020800) { 0 }

    internal val FACE_SUFFIXES: EnumMap<BlockFace?, String> = EnumMap<BlockFace?, String>(
        Map.of<BlockFace?, String?>(
            UP, "top",
            DOWN, "bottom",
            NORTH, "north",
            EAST, "east",
            SOUTH, "south",
            WEST, "west"
        )
    )

    fun init() {
        initialize()
        preloadAllTextures()
        prepareTextureBuffer()
    }
    private fun initialize() {
//        texturesPath = File(Bukkit.getPluginsFolder().getPath() + "\\Camera\\textures")
        texturesPath = File("I:\\downloads\\Downloads\\新建文件夹 (2)\\plugins\\Camera\\textures")
//        normalsPath = File(Bukkit.getPluginsFolder().getPath() + "\\Camera\\normals")
        normalsPath = File("I:\\downloads\\Downloads\\新建文件夹 (2)\\plugins\\Camera\\normals")

        if (!texturesPath!!.exists()) {
            // noinspection ResultOfMethodCallIgnored
            texturesPath!!.mkdirs()
        }
        if (!normalsPath!!.exists()) {
            // noinspection ResultOfMethodCallIgnored
            normalsPath!!.mkdirs()
        }
    }

    private fun preloadAllTextures() {
        for (material in Material.entries) {
            val namespaceKey = material.toString().lowercase(Locale.getDefault())

            var textureFileName = "$namespaceKey.png"
            var textureFile = File(texturesPath, textureFileName)

            if (textureFile.exists()) {
                try {
                    cacheTexture(material, ImageIO.read(textureFile))
                    continue
                } catch (_: IOException) {
                }
            }
            for (entry in FACE_SUFFIXES.entries) {
                val blockFace: BlockFace = entry.key!!
                val key = entry.value

                textureFileName = namespaceKey + "_" + key + ".png"
                textureFile = File(texturesPath, textureFileName)

                if (textureFile.exists()) {
                    try {
                        cacheTexture(material, blockFace, ImageIO.read(textureFile))
                        continue
                    } catch (ignored: IOException) {
                    }
                }
                textureFileName = namespaceKey + "_side.png"
                textureFile = File(texturesPath, textureFileName)
                if (textureFile.exists()) {
                    try {
                        cacheTexture(material, blockFace, ImageIO.read(textureFile))
                    } catch (ignored: IOException) {
                    }
                }
            }
        }
    }

    fun getMaterialTextureNamespaceKey(material: Material, blockFace: BlockFace?): String {
        val namespaceKey = material.toString().lowercase(Locale.getDefault())

        var textureFileName = namespaceKey + ".png"
        var textureFile = File(texturesPath, textureFileName)

        if (textureFile.exists()) {
            return texturesPath!!.getName()
        }
        val faceSuffix: String = FACE_SUFFIXES.get(blockFace)!!
        requireNotNull(faceSuffix) { "Unknown block face: " + blockFace }
        textureFileName = namespaceKey + "_" + faceSuffix + ".png"
        textureFile = File(texturesPath, textureFileName)

        if (textureFile.exists()) {
            return textureFile.getName()
        }

        textureFileName = namespaceKey + "_side.png"
        textureFile = File(texturesPath, textureFileName)
        if (textureFile.exists()) {
            return textureFile.getName()
        }

        throw IllegalArgumentException("Unknown block texture: " + textureFileName)
    }

    private fun flatImage(image: BufferedImage): IntArray {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)
        return pixels
    }

    fun getMaterialOffset(material: Material): Int = materialOffset[material] ?: 0

    private val blockFaceToCLIndex = mapOf(
        BlockFace.WEST  to 0,   // X-
        BlockFace.EAST  to 1,   // X+
        BlockFace.DOWN  to 2,   // Y-
        BlockFace.UP    to 3,     // Y+
        BlockFace.NORTH to 4,  // Z-
        BlockFace.SOUTH to 5   // Z+
    )

    fun prepareTextureBuffer() {
        var currentIndex = 0
        Material.entries.forEach { material ->
            if (material.isBlock && material != Material.AIR) {
                materialOffset[material] = currentIndex
                for (face in BlockFace.entries) {
                    val clIndex = blockFaceToCLIndex[face] ?: continue
                    val flatTexture = flatImage(getTexture(material, face)
                        ?: continue)
                    val faceOffset = clIndex * 256
                    val absoluteOffset = currentIndex + faceOffset
                    flatTexture.copyInto(textureArray, destinationOffset = absoluteOffset)
                }
                currentIndex += 6 * 256
            }
        }
    }

    fun getTexture(material: Material, face: BlockFace): BufferedImage? {
        if (textureCache.containsKey(material)) return textureCache.get(material)
        if (textureBlockFaceCache.containsKey(material)) textureBlockFaceCache.get(material)!!.get(face)
        return null
    }

    private fun cacheTexture(material: Material, face: BlockFace, image: BufferedImage) {
        textureBlockFaceCache.computeIfAbsent(material) { k: Material? ->
            EnumMap<BlockFace?, BufferedImage?>(
                BlockFace::class.java
            )
        }!!.put(face, image)
    }

    private fun cacheTexture(material: Material, image: BufferedImage) {
        textureCache.put(material, image)
    }

    fun getTexturesPath(): File {
        return texturesPath!!
    }

    fun getNormalsPath(): File {
        return normalsPath!!
    }


    @Contract("_, _, _, _, _, _ -> new")
    fun getTextureCoords(face: BlockFace, hitX: Float, hitY: Float, hitZ: Float, width: Int, height: Int): IntArray {
        val u: Float
        val v: Float

        when (face) {
            NORTH -> {
                u = 1.0f - hitX
                v = 1.0f - hitY
            }

            SOUTH -> {
                u = hitX
                v = 1.0f - hitY
            }

            EAST -> {
                u = 1.0f - hitZ
                v = 1.0f - hitY
            }

            WEST -> {
                u = hitZ
                v = 1.0f - hitY
            }

            UP -> {
                u = hitX
                v = 1.0f - hitZ
            }

            DOWN -> {
                u = hitX
                v = hitZ
            }

            else -> {
                return intArrayOf(0, 0)
            }
        }

        var x = (u * (width - 1)).toInt()
        var y = (v * (height - 1)).toInt()

        x = Math.clamp(x.toLong(), 0, width - 1)
        y = Math.clamp(y.toLong(), 0, height - 1)

        return intArrayOf(x, y)
    }

    fun getWorldColorTexture(
        hitBlockType: Material,
        hitPosition: Vector3f,
        hitFace: BlockFace,
        currentTime: Long = 6000,
        sideShadow: Float = 0.8f
    ): Color? {

        val image = getTexture(
            material = hitBlockType,
            face = hitFace,
        )

        // 应该是因为CPU始终不返回NULL
        if(image == null) {
            // println("Material: ${hitBlockType} Face: $hitFace")
            // Bukkit.getWorlds()[0].spawnParticle(Particle.ELECTRIC_SPARK, hitPosition.x.toDouble(), hitPosition.y.toDouble(), hitPosition.z.toDouble(), 1, 0.0, 0.0, 0.0, 0.0, null, true)
            return skyColor(currentTime)
        }

        //        val hitPosition = rayTraceResult.startPos?.add(rayTraceResult.direction?.mul(rayTraceResult.distance))!!

        val integerLocation = Vector3f(
            floor(hitPosition.x).toFloat(),
            floor(hitPosition.y).toFloat(),
            floor(hitPosition.z).toFloat()
        )
        val relativeHit = Vector3f(hitPosition).sub(integerLocation)

        relativeHit.x.toFloat()
        relativeHit.y.toFloat()
        relativeHit.z.toFloat()
        val texX: Float
        val texY: Float
        val width = image.width.toFloat()
        val height = image.height.toFloat()

//        when (hitFace) {
//            NORTH -> {
//                texX = (1f - x) * width
//                texY = (1f - y) * height
//            }
//
//            SOUTH -> {
//                texX = x * width
//                texY = (1f - y) * height
//            }
//
//            EAST -> {
//                texX = (1f - z) * width
//                texY = (1f - y) * height
//            }
//
//            WEST -> {
//                texX = z * width
//                texY = (1f - y) * height
//            }
//
//            UP -> {
//                texX = x * width
//                texY = (1f - z) * height
//            }
//
//            DOWN -> {
//                texX = x * width
//                texY = z * height
//            }
//
//            else -> return skyColor(currentTime)
//        }


//        println(relativeHit)
        val pts = getTextureCoords(hitFace, relativeHit.x, relativeHit.y, relativeHit.z, width.toInt(), height.toInt())
//        println(pts.toTypedArray().contentToString())
//        println()
        texX = pts[0].toFloat()
        texY = pts[1].toFloat()

        val clampedX = texX.coerceIn(0f, width - 1)
        val clampedY = texY.coerceIn(0f, height - 1)

        val baseColor = Color(image.getRGB(clampedX.toInt(), clampedY.toInt()))
        if (hitFace == UP) {
            return baseColor
        } else {
            return Color(
                (baseColor.red * sideShadow).toInt(),
                (baseColor.green * sideShadow).toInt(),
                (baseColor.blue * sideShadow).toInt(),
                baseColor.alpha
            )
        }
    }

    private val times = longArrayOf(
        0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000, 11000, 12000,
        13000, 14000, 15000, 16000, 17000, 18000, 19000, 20000, 21000, 22000, 23000, 24000
    )
    private val rValues = intArrayOf(
        111, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 111,
        45, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 45
    )
    private val gValues = intArrayOf(
        155, 167, 167, 167, 167, 167, 167, 167, 167, 167, 167, 167, 155,
        63, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 63
    )
    private val bValues = intArrayOf(
        237, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 237,
        96, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 96
    )

    private fun cubicSpline(x: Long, ys: IntArray): Int {
        val n = times.size
        if (x <= times.first()) return ys.first()
        if (x >= times.last()) return ys.last()

        var i = 0
        while (i < n - 1 && x > times[i + 1]) i++

        val x0 = times[i]
        val x1 = times[i + 1]
        val y0 = ys[i]
        val y1 = ys[i + 1]

        val t = (x - x0).toDouble() / (x1 - x0)
        val t2 = t * t
        val t3 = t2 * t

        return ((2 * t3 - 3 * t2 + 1) * y0 +
                (t3 - 2 * t2 + t) * (y1 - y0) +
                (-2 * t3 + 3 * t2) * y1).toInt().coerceIn(0, 255)
    }

    fun skyColor(time: Long): Color {
        val t = time % 24000
        val r = cubicSpline(t, rValues)
        val g = cubicSpline(t, gValues)
        val b = cubicSpline(t, bValues)
        return Color(r, g, b)
    }
}