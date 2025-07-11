package com.methyleneblue.camera.texture

import com.methyleneblue.camera.util.ObjectPool
import com.methyleneblue.camera.util.VectorUtil
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.joml.Vector2f
import org.joml.Vector3f
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

object NormalMap {
    private val normalTextures = hashMapOf<Material, MutableMap<BlockFace, BufferedImage>>()

    init {
        initialize()
    }

    fun initialize() {
        preloadNormalTextures()
    }

    private fun preloadNormalTextures() {
        val texturesFolder: File = TextureManager.getNormalsPath()

        texturesFolder.listFiles()?.forEach { file ->
            val name = file.nameWithoutExtension
            val parts = name.split("_")

            if (parts.isNotEmpty()) {
                val materialName = parts[0].uppercase()
                val blockFace = when {
                    parts.size == 2 -> faceFromKey(parts[1])
                    else -> BlockFace.UP
                }

                val material = Material.matchMaterial(materialName)
                if (material != null && blockFace != null) {
                    val bufferedImage = ImageIO.read(file)
                    normalTextures.computeIfAbsent(material) { HashMap() }[blockFace] = bufferedImage
                }
            }
        }
    }

    fun getNormal(material: Material, texturePos: Vector2f, blockFace: BlockFace): Vector3f? {
        val faceMap = normalTextures[material] ?: return null
        val image = faceMap[blockFace] ?: return null

        val x = (texturePos.x * image.width).toInt().coerceIn(0, image.width - 1)
        val y = (texturePos.y * image.height).toInt().coerceIn(0, image.height - 1)

        val rgb = Color(image.getRGB(x, y))
        val nx = rgb.red / 255.0f * 2f - 1f
        val ny = rgb.green / 255.0f * 2f - 1f
        val nz = rgb.blue / 255.0f * 2f - 1f

        return Vector3f(nx, ny, nz).normalize()
    }

    fun buildTBN(normal: Vector3f): Array<Vector3f> {
        val up = if (abs(normal.y) < 0.999f) Vector3f().set(0f, 1f, 0f) else Vector3f().set(1f, 0f, 0f)
        val tangent = up.cross(Vector3f(normal)).normalize()
        val bitangent = Vector3f(normal).cross(tangent).normalize()
        return arrayOf(tangent, bitangent, normal)
    }

    fun applyNormalMap(material: Material, face: BlockFace, uv: Vector2f): Vector3f {
        val baseNormal = VectorUtil.faceToNormalMap[face]
        require(baseNormal != null)
        val normalMapVec = getNormal(material, uv, face) ?: return baseNormal
        val (tangent, bitangent, normal) = buildTBN(baseNormal)

        return Vector3f()
            .add(tangent.mul(normalMapVec.x, Vector3f()))
            .add(bitangent.mul(normalMapVec.y, Vector3f()))
            .add(normal.mul(normalMapVec.z, Vector3f()))
            .normalize()
    }

    private fun faceFromKey(key: String): BlockFace? {
        return when (key.lowercase()) {
            "top" -> BlockFace.UP
            "bottom" -> BlockFace.DOWN
            "north" -> BlockFace.NORTH
            "south" -> BlockFace.SOUTH
            "east" -> BlockFace.EAST
            "west" -> BlockFace.WEST
            "side" -> BlockFace.NORTH // fallback for blocks with one side texture
            else -> null
        }
    }
}
