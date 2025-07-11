package com.methyleneblue.camera.obj.raytrace

import com.methyleneblue.camera.obj.raytrace.ReflectionMaterial.Companion.getMaterialReflectionData
import com.methyleneblue.camera.util.nextFloat
import org.bukkit.Material
import kotlin.math.*
import kotlin.random.Random

class   ReflectionMaterial(
    val spread: Float,
    val reflectionTimes: Int,
    val func: (args: Array<Double>) -> Double,
    val elseArgs: Array<Double>,
){
    companion object {
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

        val defaultReflectionMaterial = ReflectionMaterial(1.0f, 20, ::gaussian, arrayOf(1.0, 1.0))

        val METAL = ReflectionMaterial(0.15f, 4, ::linear, arrayOf())
        val MIRROR = ReflectionMaterial(0.0f, 1, ::linear, arrayOf())
        val MATTE = ReflectionMaterial(1.0f, 10, ::cos, arrayOf())
        val DIAMOND = ReflectionMaterial(0.01f, 2, ::linear, arrayOf())

        val IRON_BLOCK = ReflectionMaterial(0.2f, 10,  ::linear, arrayOf()) // 散射率，射线数量，反射射线权重函数，权重函数参数
        val GOLD_BLOCK = ReflectionMaterial(0.1f, 10, ::linear, arrayOf())
        val STONE = ReflectionMaterial(0.6f, 4, ::linear, arrayOf())

        val materials = arrayOf(
            defaultReflectionMaterial, // 0
            METAL, MIRROR, MATTE, DIAMOND, // 1 2 3 4
            IRON_BLOCK, GOLD_BLOCK, STONE // 5 6 7
        )

        fun getReflectionMaterials(): Array<ReflectionMaterial> {
            return materials
        }

        fun getMaterialReflectionId(material: Material): Int {
            return when (material) {
                Material.IRON_BLOCK -> 5
                Material.GOLD_BLOCK -> 6
                Material.DIAMOND_BLOCK -> 4
                else -> 0
            }
        }

        fun getReflectionData(id: Int): ReflectionMaterial {
            if (id >= materials.size || id < 0) return materials[0]
            return materials[id]
        }

        fun getMaterialReflectionData(material: Material): ReflectionMaterial {
            return when (material) {
                Material.IRON_BLOCK -> IRON_BLOCK
                Material.GOLD_BLOCK -> GOLD_BLOCK
                Material.DIAMOND_BLOCK -> DIAMOND
                else -> defaultReflectionMaterial
            }
        }

        init {

        }
    }
    fun weight(x: Float): Double {
        return func.invoke(arrayOf(x.toDouble()) + elseArgs)
    }
}

fun Material.getReflectionMaterialData(): ReflectionMaterial {
    return getMaterialReflectionData(this)
}

fun Material.weight(x: Double): Double {
    val reflectionMaterial = getMaterialReflectionData(this)
    return reflectionMaterial.func.invoke(arrayOf(x) + reflectionMaterial.elseArgs)
}
fun Material.spread(): Float {
    return getMaterialReflectionData(this).spread
}