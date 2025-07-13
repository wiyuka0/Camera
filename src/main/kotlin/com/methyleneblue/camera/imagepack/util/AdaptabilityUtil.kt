package com.methyleneblue.camera.imagepack.util

import kotlin.div
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlin.times

object AdaptabilityUtil {
    const val REFERENCE_FOV = 90.0
    const val REFERENCE_WIDTH = 1920
    val TAN_REFERENCE_FOV = tan(Math.toRadians(REFERENCE_FOV / 2))

    fun calculateRadius(
        width: Int,
        fov: Double,
        baseRadius: Float
    ): Float {
        val fovRatio = TAN_REFERENCE_FOV / tan(Math.toRadians(fov / 2))
        val resRatio = width.toDouble() / REFERENCE_WIDTH

        return (baseRadius * fovRatio * resRatio).toFloat() // Fixed Double -> Float
    }
}