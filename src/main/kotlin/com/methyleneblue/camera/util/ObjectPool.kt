package com.methyleneblue.camera.util

import com.methyleneblue.camera.obj.RayTraceCamera.RayTraceData
import org.bukkit.util.Vector
import org.joml.Vector3f
import java.awt.Color
import java.util.concurrent.ConcurrentLinkedQueue

class ObjectPool<T>(
    val creator: () -> T,
    val reset: (T) -> Unit,
) {
    companion object {
        // internal val vectorPool = ObjectPool<Vector>({ Vector() }, { it.zero() })
        // internal val vector3fPool = ObjectPool<Vector3f>({ Vector3f() }, { it.zero() })
        // internal val mutableListPool = ObjectPool<MutableList<Any>> ({ mutableListOf() }, { it.clear() })
        // internal val rayTraceDataPool = ObjectPool<RayTraceData> ({ RayTraceData(vector3fPool.alloc(), 0f, vector3fPool.alloc()) }, {})
    }
    val objectQueue = ConcurrentLinkedQueue<T>()

    fun alloc(): T = objectQueue.poll() ?: creator()

    fun free(t: T){
        reset(t)
        objectQueue.offer(t)
    }
}