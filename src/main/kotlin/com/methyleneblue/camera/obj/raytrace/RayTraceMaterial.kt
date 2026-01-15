//package com.methyleneblue.camera.obj.raytrace
//
//import com.methyleneblue.camera.obj.raytrace.RayTraceMaterial.Companion.getMaterialReflectionData
//import org.bukkit.Material
//import org.joml.Vector3i
//import java.awt.Color
//import kotlin.invoke
//import kotlin.math.*
//
//class RayTraceMaterial(
//    val materialId: Int,
//
//    val isLight: Boolean,
//
//    val lightColor: Vector3i,
//    val brightness: Float,
//
//    val spread: Float,
//    val reflectionTimes: Int,
//    val func: (args: Array<Double>) -> Double,
//    val elseArgs: Array<Double>,
//    val funcId: Int,
//
//    val minProbability: Float,
//    val maxProbability: Float,
//) {
//    constructor(
//        lightColor: Vector3i,
//        brightness: Float
//    ) : this(id++, true, lightColor, brightness, 0f, 0, ::linear, arrayOf<Double>(), -1, -1f, -1f)
//
//    constructor(
//        spread: Float,
//        reflectionTimes: Int,
//        func: (args: Array<Double>) -> Double,
//        elseArgs: Array<Double>,
//        funcId: Int,
//        minProbability: Float = 0.0f,
//        maxProbability: Float = 0.2f,
//    ) : this(id++, false, Vector3i(0, 0, 0), 0f, spread, reflectionTimes, func, elseArgs, funcId, minProbability, maxProbability)
//
//    companion object {
//        var id = 0
//
//        fun gaussian(params: Array<Double>): Double {
//            val x = params.getOrNull(0) ?: error("参数缺少 x")
//            val m = params.getOrNull(1) ?: error("参数缺少 m")
//
//            val xm = x * m
//            val coefficient = 1.0 / (0.4 * sqrt(2 * PI))
//            val exponent = -(xm * xm) / 0.8
//
//            return coefficient * exp(exponent)
//        }
//
//        fun linear(args: Array<Double>): Double {
//            return 1 - args[0].toDouble()
//        }
//
//        fun cos(args: Array<Double>): Double {
//            return cos(args[0] * args[1]).toDouble()
//        }
//
//        fun delta(args: Array<Double>): Double {
//            val x = args.getOrNull(0) ?: error("参数缺少 x")
//            return if (x == 1.0) 1.0 else 0.0
//        }
//
//        fun flat(args: Array<Double>): Double {
//            return 1.0
//        }
//
//        fun randomized(args: Array<Double>): Double {
//            val base = args.getOrNull(0) ?: 1.0
//            return base * (0.8 + Math.random() * 0.2)
//        }
//
//        fun cosSquared(args: Array<Double>): Double {
//            val base = cos(args)
//            return base * base
//        }
//
//        fun expDecay(args: Array<Double>): Double {
//            val x = args.getOrNull(0)?.toDouble() ?: error("参数缺少 x")
//            val k = args.getOrNull(1)?.toDouble() ?: 3.0  // 衰减速度，默认值为 3.0
//            return kotlin.math.exp(-x * k)
//        }
//
//        fun sharpPeak(args: Array<Double>): Double {
//            val x = args.getOrNull(0)?.toDouble() ?: error("参数缺少 x")
//            val n = args.getOrNull(1)?.toDouble() ?: 30.0
//            return kotlin.math.exp(-((x - 1).pow(2)) * n)
//        }
//
//        fun stepLike(args: Array<Double>): Double {
//            val x = args.getOrNull(0) ?: error("参数缺少 x")
//            val threshold = args.getOrNull(1) ?: 0.9
//            return if (x > threshold) 1.0 else 0.0
//        }
//
//        private fun bleaching(x: Double, n: Double) = 2.0.pow(x.pow(n)) - 1
//
////        fun colorBleaching(color: Color, brightness: Double): Color {
////        }
//
//        fun mixColors(color1: Int, color2: Int, ratio: Double): Int {
//            require(ratio in 0.0..1.0) { "Ratio must be between 0 and 1" }
//
//            val inverseRatio = 1 - ratio
//
//            val r = ((color1 shr 16 and 0xFF) * inverseRatio + (color2 shr 16 and 0xFF) * ratio).toInt()
//            val g = ((color1 shr 8 and 0xFF) * inverseRatio + (color2 shr 8 and 0xFF) * ratio).toInt()
//            val b = ((color1 and 0xFF) * inverseRatio + (color2 and 0xFF) * ratio).toInt()
//
//            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
//        }
//
//        val defaultReflectionMaterial = RayTraceMaterial(1.0f, 6, ::linear, arrayOf(1.0, 1.0), 0)
//
////        val METAL      = RayTraceMaterial(0.15f, 4 , ::linear, arrayOf(), 1)
////        val MIRROR     = RayTraceMaterial(0.0f , 1 , ::linear, arrayOf(), 1)
////        val MATTE      = RayTraceMaterial(1.0f , 10, ::cos   , arrayOf(), 2)
//
//        val DIAMOND = RayTraceMaterial(0.01f, 2, ::linear, arrayOf(), 1)
//        val IRON_BLOCK = RayTraceMaterial(0.2f, 10, ::linear, arrayOf(), 1) // 散射率，射线数量，反射射线权重函数，权重函数参数
//        val GOLD_BLOCK = RayTraceMaterial(0.1f, 10, ::linear, arrayOf(), 1)
//
////        val STONE      = RayTraceMaterial(0.6f, 4 ,  ::linear, arrayOf(), 1)
//
//        val LIGHT_GLOWSTONE = RayTraceMaterial(Vector3i(255, 245, 205), 0.95f)
//        val LIGHT_SEA_LANTERN = RayTraceMaterial(Vector3i(255, 255, 255), 1115f)
//        val LIGHT_SKY = RayTraceMaterial(Vector3i(235, 245, 255), 0.8f)
//
////        val materials = mutableMapOf<Material, RayTraceMaterial>().apply {
////            put(Material.DIAMOND_BLOCK, DIAMOND)
////            put(Material.IRON_BLOCK, IRON_BLOCK)
////            put(Material.GOLD_BLOCK, GOLD_BLOCK)
////            put(Material.GLOWSTONE, LIGHT_GLOWSTONE)
////            put(Material.SEA_LANTERN, LIGHT_SEA_LANTERN)
////            put(Material.AIR, LIGHT_SKY)
////        }
//
//        val materials = ArrayList<RayTraceMaterial> (Material.entries.size)
//
//        fun initialize(){
//            for (mat in Material.entries) {
//                materials.add(defaultReflectionMaterial)
//            }
//
//            materials[Material.DIAMOND_BLOCK.ordinal] = DIAMOND
//            materials[Material.IRON_BLOCK.ordinal] = IRON_BLOCK
//            materials[Material.GOLD_BLOCK.ordinal] = GOLD_BLOCK
//            materials[Material.GLOWSTONE.ordinal] = LIGHT_GLOWSTONE
//            materials[Material.SEA_LANTERN.ordinal] = LIGHT_SEA_LANTERN
//            materials[Material.AIR.ordinal] = LIGHT_SKY
//        }
//
//
//        fun getMaterialReflection(id: Int): RayTraceMaterial {
//            return materials.find { it.materialId == id } ?: defaultReflectionMaterial
//        }
//
//        fun getMaterialReflectionData(material: Material): RayTraceMaterial {
//            return materials[material.ordinal]
//        }
//
//
//    }
//    fun weight(x: Float): Double {
//        return func.invoke(arrayOf(x.toDouble()) + elseArgs)
////        return cos(arrayOf(x.toDouble()) + elseArgs)
////        return cos(x * elseArgs[0]).toDouble()
//    }
//}
//
//fun Material.getReflectionMaterialData(): RayTraceMaterial {
//    return getMaterialReflectionData(this)
//}
//
//fun Material.weight(x: Double): Double {
//    val reflectionMaterial = RayTraceMaterial.materials[this.ordinal]
//    if(false) {
//        return 0.0
//    }
//    return reflectionMaterial.func.invoke(arrayOf(x) + reflectionMaterial.elseArgs)
//}
//fun Material.spread(): Float {
//    return getMaterialReflectionData(this).spread
//}

//package com.methyleneblue.camera.obj.raytrace
//
//import org.bukkit.Material
//import org.joml.Vector3i
//import kotlin.math.*
//
///**
// * 整理后的 RayTraceMaterial
// * 接口与构造保持不变，优化了内部材质预设和初始化逻辑
// */
//class RayTraceMaterial(
//    val materialId: Int,
//
//    val isLight: Boolean,
//    val lightColor: Vector3i,
//    val brightness: Float,
//
//    val spread: Float, // 粗糙度：0.0 = 镜面，1.0 = 漫反射
//    val reflectionTimes: Int,
//    val func: (args: Array<Double>) -> Double,
//    val elseArgs: Array<Double>,
//    val funcId: Int,
//
//    val minProbability: Float,
//    val maxProbability: Float,
//) {
//    // --- 构造函数保持不变 ---
//
//    constructor(
//        lightColor: Vector3i,
//        brightness: Float
//    ) : this(id++, true, lightColor, brightness, 0f, 0, ::linear, arrayOf<Double>(), -1, -1f, -1f)
//
//    constructor(
//        spread: Float,
//        reflectionTimes: Int,
//        func: (args: Array<Double>) -> Double,
//        elseArgs: Array<Double>,
//        funcId: Int,
//        minProbability: Float = 0.0f,
//        maxProbability: Float = 0.2f,
//    ) : this(id++, false, Vector3i(0, 0, 0), 0f, spread, reflectionTimes, func, elseArgs, funcId, minProbability, maxProbability)
//
//    companion object {
//        var id = 0
//
//        // --- 数学函数 (保持不变) ---
//        fun gaussian(params: Array<Double>): Double {
//            val x = params.getOrNull(0) ?: error("参数缺少 x")
//            val m = params.getOrNull(1) ?: error("参数缺少 m")
//            val xm = x * m
//            val coefficient = 1.0 / (0.4 * sqrt(2 * PI))
//            val exponent = -(xm * xm) / 0.8
//            return coefficient * exp(exponent)
//        }
//
//        fun linear(args: Array<Double>): Double = 1 - args[0]
//        fun cos(args: Array<Double>): Double = kotlin.math.cos(args[0] * args[1])
//        fun delta(args: Array<Double>): Double = if (args.getOrNull(0) == 1.0) 1.0 else 0.0
//        fun flat(args: Array<Double>): Double = 1.0
//        fun randomized(args: Array<Double>): Double = (args.getOrNull(0) ?: 1.0) * (0.8 + Math.random() * 0.2)
//        fun cosSquared(args: Array<Double>): Double { val base = cos(args); return base * base }
//        fun expDecay(args: Array<Double>): Double = kotlin.math.exp(-(args.getOrNull(0) ?: error("No x")) * (args.getOrNull(1) ?: 3.0))
//        fun sharpPeak(args: Array<Double>): Double = kotlin.math.exp(-((args.getOrNull(0) ?: error("No x")) - 1).pow(2) * (args.getOrNull(1) ?: 30.0))
//        fun stepLike(args: Array<Double>): Double = if ((args.getOrNull(0) ?: error("No x")) > (args.getOrNull(1) ?: 0.9)) 1.0 else 0.0
//
//        // 辅助工具 (未使用的已移除或保留)
//        fun mixColors(color1: Int, color2: Int, ratio: Double): Int {
//            require(ratio in 0.0..1.0) { "Ratio must be between 0 and 1" }
//            val inverseRatio = 1 - ratio
//            val r = ((color1 shr 16 and 0xFF) * inverseRatio + (color2 shr 16 and 0xFF) * ratio).toInt()
//            val g = ((color1 shr 8 and 0xFF) * inverseRatio + (color2 shr 8 and 0xFF) * ratio).toInt()
//            val b = ((color1 and 0xFF) * inverseRatio + (color2 and 0xFF) * ratio).toInt()
//            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
//        }
//
//        // --- 预设材质定义 (整理分类) ---
//
//        // 1. 默认材质 (粗糙，漫反射)
//        val DEFAULT_MAT = RayTraceMaterial(1.0f, 6, ::linear, arrayOf(1.0, 1.0), 0)
//
//        // 2. 金属/光滑物体 (低 spread)
//        val METAL_IRON    = RayTraceMaterial(0.2f, 10, ::linear, arrayOf(), 1)
//        val METAL_GOLD    = RayTraceMaterial(0.1f, 10, ::linear, arrayOf(), 1)
//        val GEM_DIAMOND   = RayTraceMaterial(0.01f, 2, ::linear, arrayOf(), 1) // 极度光滑
//
//        // 3. 透明/半透明/液体 (新增：让画面有倒影的关键)
//        val WATER_MAT     = RayTraceMaterial(0.05f, 10, ::linear, arrayOf(), 1) // 水面
//        val GLASS_MAT     = RayTraceMaterial(0.02f, 10, ::linear, arrayOf(), 1) // 玻璃
//        val ICE_MAT       = RayTraceMaterial(0.15f, 8, ::linear, arrayOf(), 1)  // 冰
//        val POLISHED_MAT  = RayTraceMaterial(0.4f, 6, ::linear, arrayOf(), 1)   // 磨制方块
//
//        // 4. 发光物体
//        // 注意：海晶灯亮度原为1115f，可能过曝，这里稍微保守一点或者保持原样
//        val LIGHT_GLOWSTONE   = RayTraceMaterial(Vector3i(255, 245, 205), 0.95f)
//        val LIGHT_SEA_LANTERN = RayTraceMaterial(Vector3i(255, 255, 255), 2000.0f) // 修正：建议调低一点，太亮会白屏
//        val LIGHT_TORCH       = RayTraceMaterial(Vector3i(255, 200, 100), 5.0f)
//        val LIGHT_LAVA        = RayTraceMaterial(Vector3i(255, 100, 0), 3.0f)
//        val LIGHT_SKY         = RayTraceMaterial(Vector3i(235, 245, 255), 0.8f)
//
//
//        // --- 材质列表容器 ---
//        val materials = ArrayList<RayTraceMaterial>(Material.entries.size)
//
//        /**
//         * 初始化材质列表
//         * 必须在 OpenCL 初始化前调用
//         */
//        fun initialize() {
//            materials.clear()
//
//            // 1. 先用默认材质填满所有 ID
//            for (mat in Material.entries) {
//                materials.add(DEFAULT_MAT)
//            }
//
//            // 2. 批量注册特殊材质
//            register(Material.AIR, LIGHT_SKY)
//            register(Material.CAVE_AIR, LIGHT_SKY)
//            register(Material.VOID_AIR, LIGHT_SKY)
//
//            // 金属与宝石
//            register(Material.IRON_BLOCK, METAL_IRON)
//            register(Material.GOLD_BLOCK, METAL_GOLD)
//            register(Material.DIAMOND_BLOCK, GEM_DIAMOND)
//            register(Material.EMERALD_BLOCK, GEM_DIAMOND)
//
//            // 光源
//            register(Material.GLOWSTONE, LIGHT_GLOWSTONE)
//            register(Material.SEA_LANTERN, LIGHT_SEA_LANTERN)
//            register(Material.TORCH, LIGHT_TORCH)
//            register(Material.LAVA, LIGHT_LAVA)
//            register(Material.MAGMA_BLOCK, LIGHT_LAVA)
//            register(Material.LANTERN, LIGHT_TORCH)
//            register(Material.JACK_O_LANTERN, LIGHT_TORCH)
//            register(Material.SHROOMLIGHT, LIGHT_GLOWSTONE)
//
//            // 水面与玻璃 (高光/倒影来源)
//            register(Material.WATER, WATER_MAT)
//            register(Material.GLASS, GLASS_MAT)
//            register(Material.GLASS_PANE, GLASS_MAT)
//            // 简单批量处理所有染色玻璃
//            Material.entries.filter { it.name.contains("STAINED_GLASS") }.forEach { register(it, GLASS_MAT) }
//
//            // 冰与光滑方块
//            register(Material.ICE, ICE_MAT)
//            register(Material.PACKED_ICE, ICE_MAT)
//            register(Material.BLUE_ICE, ICE_MAT)
//
//            // 磨制/光滑方块 (半反射)
//            Material.entries.filter { it.name.startsWith("POLISHED_") || it.name.startsWith("SMOOTH_") }
//                .forEach { register(it, POLISHED_MAT) }
//        }
//
//        private fun register(mat: Material, rtMat: RayTraceMaterial) {
//            if (mat.ordinal < materials.size) {
//                materials[mat.ordinal] = rtMat
//            }
//        }
//
//        fun getMaterialReflection(id: Int): RayTraceMaterial {
//            return materials.getOrNull(id) ?: DEFAULT_MAT
//        }
//
//        fun getMaterialReflectionData(material: Material): RayTraceMaterial {
//            return materials.getOrNull(material.ordinal) ?: DEFAULT_MAT
//        }
//    }
//
//    // --- 实例方法 ---
//    fun weight(x: Float): Double {
//        return func.invoke(arrayOf(x.toDouble()) + elseArgs)
//    }
//}
//
//// --- 扩展函数 (保持接口兼容) ---
//
//fun Material.getReflectionMaterialData(): RayTraceMaterial {
//    return RayTraceMaterial.getMaterialReflectionData(this)
//}
//
//fun Material.weight(x: Double): Double {
//    val reflectionMaterial = RayTraceMaterial.getMaterialReflectionData(this)
//    return reflectionMaterial.func.invoke(arrayOf(x) + reflectionMaterial.elseArgs)
//}
//
//fun Material.spread(): Float {
//    return RayTraceMaterial.getMaterialReflectionData(this).spread
//}


package com.methyleneblue.camera.obj.raytrace

import org.bukkit.Material
import org.joml.Vector3i
import kotlin.invoke
import kotlin.math.*

class RayTraceMaterial(
    val materialId: Int,

    val isLight: Boolean,
    val lightColor: Vector3i,
    val brightness: Float,

    val spread: Float, // 粗糙度 (Roughness): 0.0=光滑镜面, 1.0=完全漫反射
    val refractiveIndex: Float, // 新增：折射率 (IOR). 水=1.33, 玻璃=1.5, 钻石=2.4, 空气=1.0

    val reflectionTimes: Int,
    val func: (args: Array<Double>) -> Double,
    val elseArgs: Array<Double>,
    val funcId: Int,
    val minProbability: Float,
    val maxProbability: Float,
) {

    // 简化的构造函数
    constructor(
        lightColor: Vector3i,
        brightness: Float
    ) : this(id++, true, lightColor, brightness, 0f, 1.0f, 0, ::linear, arrayOf<Double>(), -1, -1f, -1f)

    constructor(
        spread: Float,
        ior: Float, // 新增参数
        reflectionTimes: Int,
        func: (args: Array<Double>) -> Double,
        elseArgs: Array<Double>,
        funcId: Int,
        minProbability: Float = 0.0f,
        maxProbability: Float = 0.2f,
    ) : this(id++, false, Vector3i(0, 0, 0), 0f, spread, ior, reflectionTimes, func, elseArgs, funcId, minProbability, maxProbability)

    companion object {
        var id = 0

        // --- 函数定义 (保持不变) ---
        fun linear(args: Array<Double>): Double = 1 - args[0]
        fun cos(args: Array<Double>): Double = kotlin.math.cos(args[0] * args[1])
        fun flat(args: Array<Double>): Double = 1.0

        // --- 预设材质 ---

        // 默认: 粗糙，无光泽，IOR 1.5 (标准电介质)
        val DEFAULT_MAT = RayTraceMaterial(0.95f, 1.5f, 6, ::linear, arrayOf(1.0, 1.0), 0)

        // 金属: 光滑，IOR 较高 (模拟金属反射)
        val METAL_IRON    = RayTraceMaterial(0.2f, 2.5f, 10, ::linear, arrayOf(), 1)
        val METAL_GOLD    = RayTraceMaterial(0.1f, 1.8f, 10, ::linear, arrayOf(), 1)

        // 透明/折射物体
        val GEM_DIAMOND   = RayTraceMaterial(0.01f, 2.42f, 10, ::linear, arrayOf(), 1) // 钻石折射率
        val WATER_MAT     = RayTraceMaterial(0.02f, 1.33f, 10, ::linear, arrayOf(), 1) // 水
        val GLASS_MAT     = RayTraceMaterial(0.01f, 1.52f, 10, ::linear, arrayOf(), 1) // 玻璃
        val ICE_MAT       = RayTraceMaterial(0.1f, 1.31f, 8, ::linear, arrayOf(), 1)   // 冰

        // 光源
        val LIGHT_GLOWSTONE   = RayTraceMaterial(Vector3i(255, 245, 205), 15.0f) // 亮度提高以适配 ToneMapping
        val LIGHT_SEA_LANTERN = RayTraceMaterial(Vector3i(255, 255, 255), 2000.0f)
        val LIGHT_TORCH       = RayTraceMaterial(Vector3i(255, 200, 100), 10.0f)
        val LIGHT_LAVA        = RayTraceMaterial(Vector3i(255, 40, 0), 8.0f)
        val LIGHT_SKY         = RayTraceMaterial(Vector3i(235, 245, 255), 1.0f)

        val materials = ArrayList<RayTraceMaterial>(Material.entries.size)

        fun initialize() {
            materials.clear()
            // 填充默认值
            for (mat in Material.entries) materials.add(DEFAULT_MAT)

            // 注册空气
            register(Material.AIR, LIGHT_SKY)
            register(Material.CAVE_AIR, LIGHT_SKY)
            register(Material.VOID_AIR, LIGHT_SKY)

            // 注册特殊材质
            register(Material.IRON_BLOCK, METAL_IRON)
            register(Material.GOLD_BLOCK, METAL_GOLD)
            register(Material.DIAMOND_BLOCK, GEM_DIAMOND)

            // 水和玻璃 (关键)
            register(Material.WATER, WATER_MAT)
            register(Material.GLASS, GLASS_MAT)
            register(Material.GLASS_PANE, GLASS_MAT)
            Material.entries.filter { it.name.contains("STAINED_GLASS") }.forEach { register(it, GLASS_MAT) }

            // 光源
            register(Material.GLOWSTONE, LIGHT_GLOWSTONE)
            register(Material.SEA_LANTERN, LIGHT_SEA_LANTERN)
            register(Material.TORCH, LIGHT_TORCH)
            register(Material.LAVA, LIGHT_LAVA)
            register(Material.MAGMA_BLOCK, LIGHT_LAVA)
        }

        private fun register(mat: Material, rtMat: RayTraceMaterial) {
            if (mat.ordinal < materials.size) materials[mat.ordinal] = rtMat
        }

        fun getMaterialReflectionData(material: Material): RayTraceMaterial {
            return materials.getOrNull(material.ordinal) ?: DEFAULT_MAT
        }
    }
    fun weight(x: Float): Double {
        return func.invoke(arrayOf(x.toDouble()) + elseArgs)
    }
}

fun Material.weight(x: Double): Double {
    val reflectionMaterial = RayTraceMaterial.getMaterialReflectionData(this)
    return reflectionMaterial.func.invoke(arrayOf(x) + reflectionMaterial.elseArgs)
}

fun Material.getReflectionMaterialData(): RayTraceMaterial = RayTraceMaterial.getMaterialReflectionData(this)