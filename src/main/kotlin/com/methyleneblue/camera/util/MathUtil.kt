package com.methyleneblue.camera.util

import kotlin.random.Random

class MathUtil {
}

fun Random.nextFloat(from: Float, to: Float): Float {
    require(to >= from) { "to must be >= from" }
    val delta = to - from
    return this.nextFloat() * delta + from
}