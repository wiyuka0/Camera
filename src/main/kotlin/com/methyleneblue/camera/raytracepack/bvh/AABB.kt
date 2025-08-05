package com.methyleneblue.camera.raytracepack.bvh

import com.methyleneblue.camera.raytracepack.bvh.jocl.JoclInterface
import org.bukkit.block.BlockFace
import org.joml.Vector3f
import kotlin.math.*

data class AABB(val min: Vector3f, val max: Vector3f) {

    fun rayIntersect(origin: Vector3f, dir: Vector3f, tMin: Float, tMax: Float): HitInfo? {

//        return JoclInterface.traceRayAABB(origin, dir, min, max)

        val EPSILON = 1e-6f

        var tMinLocal = tMin
        var tMaxLocal = tMax
        var hitFace: BlockFace? = null

        // 判断是否在盒子内部
        var inside = true
        for (i in 0..2) {
            if (origin[i] < min[i] - EPSILON || origin[i] > max[i] + EPSILON) {
                inside = false
                break
            }
        }



        if (inside) {
            for (i in 0..2) {
                if (abs(dir[i]) >= EPSILON) {
                    val face = getAxisFace(i, dir[i] < 0)
                    return HitInfo(0f, face)
                }
            }
            return HitInfo(0f, BlockFace.UP) // fallback
        }

        for (i in 0..2) {
            if (abs(dir[i]) < EPSILON) {
                if (origin[i] < min[i] - EPSILON || origin[i] > max[i] + EPSILON) {
                    return null // 射线与该轴平行且起点不在盒子内
                }
                continue
            }

            val invD = 1.0f / dir[i]
            var t0 = (min[i] - origin[i]) * invD
            var t1 = (max[i] - origin[i]) * invD

            if (invD < 0f) {
                val temp = t0
                t0 = t1
                t1 = temp
            }

            if (t0 > tMinLocal) {
                tMinLocal = t0
                hitFace = getAxisFace(i, invD < 0)
            }

            tMaxLocal = min(tMaxLocal, t1)

            if (tMaxLocal + EPSILON < tMinLocal) {
                return null
            }
        }

        return HitInfo(tMinLocal, hitFace ?: BlockFace.UP) // fallback to UP if unknown
        /*
    //        var tMinLocal = tMin
//        var tMaxLocal = tMax
//        var hitFace: BlockFace? = null
//
//        var inside = true
//        for (i in 0..2) {
//            if (origin[i] < min[i] || origin[i] > max[i]) {
//                inside = false
//                break
//            }
//        }
//
//        if (inside) {
//            for (i in 0..2) {
//                if (dir[i] != 0f) {
//                    val face = getAxisFace(i, dir[i] < 0)
//                    return HitInfo(0f, face)
//                }
//            }
//            return HitInfo(0f, BlockFace.UP) // fallback
//        }
//
//        for (i in 0..2) {
//            if (dir[i] == 0f) {
//                if (origin[i] < min[i] || origin[i] > max[i]) {
//                    return null // 为什么每次for执行第二次的时候大概率会在这里返回
//                }
//                continue
//            }
// // 为什么啊？？？？？？？？？》》。。。。。
//            val invD = 1.0f / dir[i]
//            var t0 = (min[i] - origin[i]) * invD
//            var t1 = (max[i] - origin[i]) * invD
//
//            if (invD < 0f) {
//                val temp = t0
//                t0 = t1
//                t1 = temp
//            }
//
//            if (t0 > tMinLocal) {
//                tMinLocal = t0
//                hitFace = getAxisFace(i, invD < 0)
//            }
//
//            tMaxLocal = min(tMaxLocal, t1)
//
//            if (tMaxLocal < tMinLocal) {
//                return null
//            }
//        }
//     // 我要死了。。。。。。。。。。。。。。。。。。。。。。。。。。。。热封盖色乳房v非人非v贴吧乳房v给厂的工人发v恶臭的下4分成八个婚姻节目额超过部分很可能率文艺节目改版以后你节目覅绿城的
//        return hitFace?.let { HitInfo(tMinLocal, it) }*/
    }
/*
//    fun rayIntersect(origin: Vector3f, dir: Vector3f, tMin: Float, tMax: Float): HitInfo? {
//        var tMinLocal = tMin
//        var tMaxLocal = tMax
//        var hitFace: BlockFace? = null
//
//        var inside = true
//        for (i in 0..2) {
//            if (origin[i] < min[i] || origin[i] > max[i]) {
//                inside = false
//                break
//            }
//        }
//
//        if (inside) {
//            for (i in 0..2) {
//                if (dir[i] != 0f) { //全是inside
//                    val face = getAxisFace(i, dir[i] < 0)
//                    return HitInfo(0f, face)
//                }
//            }
//            return HitInfo(0f, BlockFace.UP)
//        }
//        for (i in 0..2) {
//            if (dir[i] == 0f) {
//                if (origin[i] < min[i] || origin[i] > max[i]) { // equals
//                    return null
//                } // run
//                continue
//            }
//
//            val invD = 1.0f / dir[i]
//            var t0 = (min[i] - origin[i]) * invD
//            var t1 = (max[i] - origin[i]) * invD
//            val face = getAxisFace(i, invD < 0)
//
//            if (invD < 0.0f) {
//                val temp = t0
//                t0 = t1
//                t1 = temp
//            }
//
//            if (t0 > tMinLocal) {
//                tMinLocal = t0
//                hitFace = face
//            }
//            tMaxLocal = min(tMaxLocal, t1)
//            if (tMaxLocal < tMinLocal) {
//                if (t0 > 1000f || t1 > 1000f || tMinLocal > 1000f || tMaxLocal > 1000f) {
////                    println("[Large t] Axis $i")
////                    println("origin[i]=${origin[i]}, min=${min[i]}, max=${max[i]}")
////                    println("dir[i]=${dir[i]}, invD=$invD")
////                    println("t0=$t0, t1=$t1")
////                    println("tMinLocal=$tMinLocal, tMaxLocal=$tMaxLocal")
//                }
//                return null
//            }
//        } // 纯黑
//
//        if (hitFace == null) {
//            return null
//        }
//        return HitInfo(tMinLocal, hitFace)
//    }

//    fun rayIntersect(origin: Vector3f, dir: Vector3f, tMin: Float, tMax: Float): HitInfo? {
//        var tMinLocal = tMin
//        var tMaxLocal = tMax
//        var hitFace: BlockFace? = null
//        val EPSILON = 1e-6f
//
//        for (i in 0..2) {
//            val o = origin[i]
//            val d = dir[i]
//            val minB = min[i]
//            val maxB = max[i]
//
//            if (abs(d) < EPSILON) {
//                // 射线平行该轴方向，如果在 AABB 外，永不相交
//                if (o < minB || o > maxB) return null
//                continue
//            }
//
//            val invD = 1.0f / d
//            var t0 = (minB - o) * invD
//            var t1 = (maxB - o) * invD
//            val face = getAxisFace(i, invD < 0)
//
//            if (t0 > t1) {
//                val tmp = t0
//                t0 = t1
//                t1 = tmp
//            }
//
//            if (t0 > tMinLocal) {
//                tMinLocal = t0
//                hitFace = face
//            }
//
//            tMaxLocal = min(tMaxLocal, t1)
//            if (tMaxLocal < tMinLocal) return null
//        }
//
//        return hitFace?.let { HitInfo(tMinLocal, it) }
//    }
为什么啊为什么啊为什么啊为什么啊为什么啊为什么啊为什么啊我想死
*/

    private fun getAxisFace(axis: Int, negative: Boolean): BlockFace = when (axis) {
        0 -> if (negative) BlockFace.EAST else BlockFace.WEST
        1 -> if (negative) BlockFace.UP else BlockFace.DOWN
        2 -> if (negative) BlockFace.SOUTH else BlockFace.NORTH
        else -> throw IllegalArgumentException("Invalid axis")
    }

    fun contains(point: Vector3f): Boolean {
        return point.x > min.x && point.x < max.x &&
                point.y > min.y && point.y < max.y &&
                point.z > min.z && point.z < max.z
    }

    data class HitInfo(val t: Float, val face: BlockFace)
}
