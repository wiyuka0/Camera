package com.methyleneblue.camera.raytracepack.bvh.jocl.async

class AsyncFuture<T> {
    private val lock = java.lang.Object()
    private var result: T? = null
    private var finished = false

    fun set(value: T) {
//        synchronized(lock) {
//            if (!finished) {
                result = value
//                finished = true
//                lock.notifyAll()
//            }
//        }
    }

    fun get(): T {
//        synchronized(lock) {
//            while (!finished) {
//                try {
//                    lock.wait()
//                } catch (e: InterruptedException) {
//                    Thread.currentThread().interrupt()
//                    throw RuntimeException("Thread interrupted while waiting for result", e)
//                }
//            }
//            @Suppress("UNCHECKED_CAST")
            return result as T
//        }
    }

    fun isFinished(): Boolean {
        synchronized(lock) {
            return finished
        }
    }
}