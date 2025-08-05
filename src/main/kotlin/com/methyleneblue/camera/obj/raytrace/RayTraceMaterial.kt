package com.methyleneblue.camera.obj.raytrace

import com.methyleneblue.camera.obj.raytrace.RayTraceMaterial.Companion.getMaterialReflectionData
import org.bukkit.Material
import org.joml.Vector3i
import java.awt.Color
import kotlin.math.*

class RayTraceMaterial(
    val materialId: Int,

    val isLight: Boolean,

    val lightColor: Vector3i,
    val brightness: Float,

    val spread: Float,
    val reflectionTimes: Int,
    val func: (args: Array<Double>) -> Double,
    val elseArgs: Array<Double>,
    val funcId: Int,
){
    constructor(
        lightColor: Vector3i,
        brightness: Float
    ): this(id++, true, lightColor, brightness, 0f, 0, ::linear, arrayOf<Double>(), -1)

    constructor(
        spread: Float,
        reflectionTimes: Int,
        func: (args: Array<Double>) -> Double,
        elseArgs: Array<Double>,
        funcId: Int
    ): this(id++, false, Vector3i(0, 0, 0), 0f, spread, reflectionTimes, func, elseArgs, funcId)

    companion object {
        var id = 0

        fun gaussian(params: Array<Double>): Double {
            val x = params.getOrNull(0) ?: error("参数缺少 x")
            val m = params.getOrNull(1) ?: error("参数缺少 m")

            val xm = x * m
            val coefficient = 1.0 / (0.4 * sqrt(2 * PI))
            val exponent = -(xm * xm) / 0.8

            return coefficient * exp(exponent)
        }
        fun linear(args: Array<Double>): Double {
            return 1 - args[0].toDouble()
        }
        fun cos(args: Array<Double>): Double {
            return cos(args[0] * args[1]).toDouble()
        }
        fun delta(args: Array<Double>): Double {
            val x = args.getOrNull(0) ?: error("参数缺少 x")
            return if (x == 1.0) 1.0 else 0.0
        }
        fun flat(args: Array<Double>): Double {
            return 1.0
        }
        fun randomized(args: Array<Double>): Double {
            val base = args.getOrNull(0) ?: 1.0
            return base * (0.8 + Math.random() * 0.2)
        }
        fun cosSquared(args: Array<Double>): Double {
            val base = cos(args)
            return base * base
        }
        fun expDecay(args: Array<Double>): Double {
            val x = args.getOrNull(0)?.toDouble() ?: error("参数缺少 x")
            val k = args.getOrNull(1)?.toDouble() ?: 3.0  // 衰减速度，默认值为 3.0
            return kotlin.math.exp(-x * k)
        }
        fun sharpPeak(args: Array<Double>): Double {
            val x = args.getOrNull(0)?.toDouble() ?: error("参数缺少 x")
            val n = args.getOrNull(1)?.toDouble() ?: 30.0
            return kotlin.math.exp(-((x - 1).pow(2)) * n)
        }
        fun stepLike(args: Array<Double>): Double {
            val x = args.getOrNull(0) ?: error("参数缺少 x")
            val threshold = args.getOrNull(1) ?: 0.9
            return if (x > threshold) 1.0 else 0.0
        }

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

        val defaultReflectionMaterial = RayTraceMaterial(1.0f, 15, ::linear, arrayOf(1.0, 1.0), 0)

//        val METAL      = RayTraceMaterial(0.15f, 4 , ::linear, arrayOf(), 1)
//        val MIRROR     = RayTraceMaterial(0.0f , 1 , ::linear, arrayOf(), 1)
//        val MATTE      = RayTraceMaterial(1.0f , 10, ::cos   , arrayOf(), 2)

        val DIAMOND    = RayTraceMaterial(0.01f, 2 , ::linear, arrayOf(), 1)
        val IRON_BLOCK = RayTraceMaterial(0.2f, 10,  ::linear, arrayOf(), 1) // 散射率，射线数量，反射射线权重函数，权重函数参数
        val GOLD_BLOCK = RayTraceMaterial(0.1f, 10,  ::linear, arrayOf(), 1)

//        val STONE      = RayTraceMaterial(0.6f, 4 ,  ::linear, arrayOf(), 1)

        val LIGHT_GLOWSTONE = RayTraceMaterial(Vector3i(255, 245, 205), 0.95f)
        val LIGHT_SEA_LANTERN = RayTraceMaterial(Vector3i(255, 255, 255), 5f)
        val LIGHT_SKY = RayTraceMaterial(Vector3i(235, 245, 255), 0.8f)

        val materials = mutableMapOf<Material, RayTraceMaterial>().apply {
            put(Material.DIAMOND_BLOCK, DIAMOND)
            put(Material.IRON_BLOCK, IRON_BLOCK)
            put(Material.GOLD_BLOCK, GOLD_BLOCK)
            put(Material.GLOWSTONE, LIGHT_GLOWSTONE)
            put(Material.SEA_LANTERN, LIGHT_SEA_LANTERN)
            put(Material.AIR, LIGHT_SKY)
        }

        fun getReflectionMaterials(): Map<Material, RayTraceMaterial> {
            return materials
        }

        fun getMaterialReflectionId(material: Material): Int {
            return materials.getOrDefault(material, defaultReflectionMaterial).materialId
        }

        fun getMaterialReflection(id: Int): RayTraceMaterial {
            return materials.values.find { it.materialId == id } ?: defaultReflectionMaterial
        }

        fun getMaterialReflectionData(material: Material): RayTraceMaterial {
            return materials.getOrDefault(material, defaultReflectionMaterial)
        }
    }
    fun weight(x: Float): Double {
        return func.invoke(arrayOf(x.toDouble()) + elseArgs)
    }
}

fun Material.getReflectionMaterialData(): RayTraceMaterial {
    return getMaterialReflectionData(this)
}

fun Material.weight(x: Double): Double {
    val reflectionMaterial = getMaterialReflectionData(this)
    return reflectionMaterial.func.invoke(arrayOf(x) + reflectionMaterial.elseArgs)
}
fun Material.spread(): Float {
    return getMaterialReflectionData(this).spread
}