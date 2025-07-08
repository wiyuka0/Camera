package com.methyleneblue.camera.util

import java.util.concurrent.ConcurrentLinkedQueue

class ObjectPool<T>(
    val creator: () -> T,
    val reset: (T) -> Unit,
) {
    val objectQueue = ConcurrentLinkedQueue<T>()

    fun alloc(): T = objectQueue.poll() ?: creator()

    fun free(t: T){
        reset(t)
        objectQueue.offer(t)
    }
}