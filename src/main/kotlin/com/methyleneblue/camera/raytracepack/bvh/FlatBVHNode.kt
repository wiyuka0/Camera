package com.methyleneblue.camera.raytracepack.bvh

import org.joml.Vector3f

data class FlatBVHNode(
    val min: Vector3f,
    val max: Vector3f,
    val leftIndex: Int,
    val rightIndex: Int,
    val isLeaf: Int,
    val blockPosition: Vector3f,
//    val blockMaterialIndex: Int,
)