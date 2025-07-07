package com.methyleneblue.camera.obj.raytrace

import java.awt.Color
import org.bukkit.Material
import org.joml.Vector3i
import kotlin.math.max
import kotlin.math.pow

class LightMaterial(
    val lightColor: Vector3i,
    val brightness: Float
){
    companion object {
        val LIGHT_TYPES: HashMap<Material, LightMaterial> = hashMapOf<Material, LightMaterial>().apply {
            put(Material.GLOWSTONE, LightMaterial(Vector3i(255, 245, 205), 0.95f))
            put(Material.SEA_LANTERN, LightMaterial(Vector3i(255, 255, 255), 5f))
        }

        val SKY_LIGHT = LightMaterial(
            Vector3i(235, 245, 255),
            0.8f
        )
        fun isLight(material: Material): Boolean = material in LIGHT_TYPES.keys
        fun getLight(material: Material): LightMaterial? = LIGHT_TYPES[material]


        private fun bleaching(x: Double, n: Double) = 2.0.pow(x.pow(n)) - 1
        fun colorBleaching(color: Color, brightness: Double): Color {
            val mixProportion = bleaching(brightness, 7.0)

            return Color(mixColors(color.rgb, Color.WHITE.rgb, mixProportion))
        }

        fun mixColors(color1: Int, color2: Int, ratio: Double): Int {
            require(ratio in 0.0..1.0) { "Ratio must be between 0 and 1" }

            val inverseRatio = 1 - ratio

            val r = ((color1 shr 16 and 0xFF) * inverseRatio + (color2 shr 16 and 0xFF) * ratio).toInt()
            val g = ((color1 shr 8 and 0xFF) * inverseRatio + (color2 shr 8 and 0xFF) * ratio).toInt()
            val b = ((color1 and 0xFF) * inverseRatio + (color2 and 0xFF) * ratio).toInt()

            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

fun Material.isLight(): Boolean {
    return LightMaterial.isLight(this)
}

fun Material.getLight(): LightMaterial? {
    return LightMaterial.getLight(this)
}