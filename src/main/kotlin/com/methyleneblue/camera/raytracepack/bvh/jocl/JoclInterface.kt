package com.methyleneblue.camera.raytracepack.bvh.jocl

import com.methyleneblue.camera.raytracepack.bvh.BVHTree
import org.bukkit.block.BlockFace
import com.methyleneblue.camera.raytracepack.bvh.FlatBVHNode
import com.methyleneblue.camera.raytracepack.bvh.HitResult
import com.methyleneblue.camera.raytracepack.bvh.jocl.async.AsyncFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jocl.*
import org.jocl.CL.*
import org.joml.Vector3f
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue

object JoclInterface {
    lateinit var context: cl_context
//    lateinit var commandQueue: cl_command_queue
    lateinit var device: cl_device_id
    lateinit var program: cl_program
    lateinit var rayTraceKernel: cl_kernel

    lateinit var commandQueueThreadPool: ThreadLocal<cl_command_queue>

    var isCalculating = false
    var lastStatusUpdate = -1L
    private val lock: Object = Object()

    val kernelPath: String = "C:\\image\\kernels"

    private fun updateStatus(){
        lastStatusUpdate = System.currentTimeMillis()
    }
    private fun startProcess(){
        isCalculating = true
        updateStatus()
    }
    private fun endProcess(){
//        synchronized(lock) {
            isCalculating = false
            updateStatus()
//            lock.notifyAll()
//        }
    }
    private fun awaitLock() {
        synchronized(lock) {
            while (isCalculating) {
                lock.wait()
            }
        }
    }

    const val OUT_OF_TIME_THRESHOLD = 10L // second

    private var bvhTreeTemp: BVHTree? = null
    private var flatBVHNodesTemp: Array<FlatBVHNode>? = null

    private fun updateBVHTree(bvhTree: BVHTree, flatBVHNodes: Array<FlatBVHNode>) {
        this.bvhTreeTemp = bvhTree
        this.flatBVHNodesTemp = flatBVHNodes
    }

    private fun checkTaskQueue(){
        val timeToLastUpdate = System.currentTimeMillis() - lastStatusUpdate
        if(!(requests.peek() != null && timeToLastUpdate >= OUT_OF_TIME_THRESHOLD * 1000)) return
        if(bvhTreeTemp == null || flatBVHNodesTemp == null) return
        processResults(flatBVHNodesTemp!!, bvhTreeTemp!!)
    }

    private fun startDaemonThreads() {
        CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(OUT_OF_TIME_THRESHOLD * 1000)
                checkTaskQueue()
            }
        }
    }

    // 4 byte align
    fun vector3fToFloats(arrays: Array<Vector3f>): FloatArray {
        val result = FloatArray(arrays.size * 4) // 4 floats per vector (3 + 1 padding)
        for (i in arrays.indices) {
            val vec = arrays[i]
            val baseIndex = i * 4
            result[baseIndex] = vec.x
            result[baseIndex + 1] = vec.y
            result[baseIndex + 2] = vec.z
            result[baseIndex + 3] = 0f // padding for 4-byte alignment
        }
        return result
    }

    data class TraceRayRequest(
        val rayOrigin: Vector3f,
        val rayDirection: Vector3f,
        val asyncFuture: AsyncFuture<HitResult?>
    )

    val requests = ConcurrentLinkedQueue<TraceRayRequest>()

    fun traceRay(
        rayOrigin: Vector3f,
        rayDirection: Vector3f,
    ): AsyncFuture<HitResult?> {
        val asyncFuture = AsyncFuture<HitResult?>()
        val packedRequest = TraceRayRequest(
            rayOrigin,
            rayDirection,
            asyncFuture,
        )
        requests += packedRequest

        return asyncFuture
    }

    fun processResults(flatBvhNode: Array<FlatBVHNode>, bvhTree: BVHTree) {
        if (requests.peek() == null) return
//        awaitLock()
        updateBVHTree(bvhTree, flatBvhNode)
        startProcess()
        val rayOrigins = requests.map { it.rayOrigin }
        val rayDirections = requests.map { it.rayDirection   }
        val asyncFutures = requests.map { it.asyncFuture }

        var results: Array<out HitResult?> = if(rayOrigins.size > 400)
            traceRaysGPU(flatBvhNode, rayOrigins.toTypedArray(), rayDirections.toTypedArray())
        else
            traceRaysCPU(
                bvhTree = bvhTree,
                rayOrigins.toTypedArray(),
                rayDirections.toTypedArray(),
            )

        for ((index, result) in results.withIndex()) {
            asyncFutures[index].set(result)
        }

        requests.clear()
        endProcess()
    }

    fun traceRaysCPU(
        bvhTree: BVHTree,
        rayOrigins: Array<Vector3f>,
        rayDirections: Array<Vector3f>
    ): Array<HitResult?>{
        val result = arrayOfNulls<HitResult>(rayOrigins.size)
        for ((index, origin) in rayOrigins.withIndex()) {
            val direction = rayDirections[index]
            val hitResult = bvhTree.rayTrace(origin, direction)
            // 为什么不能放null
            // result[index] = (hitResult ?: HitResult(Vector3f(Float.NaN, Float.NaN, Float.NaN), -1f, BlockFace.SELF, origin, direction)) as HitResult?
            result[index] = hitResult
        }

        return result as Array<HitResult?>
    }

    fun traceRaysGPU(
        flatBVHNode: Array<FlatBVHNode>,
        rayOrigins: Array<Vector3f>,
        rayDirections: Array<Vector3f>
    ): Array<HitResult> {

        val flattenedBVH = flatBVHNode

        val aabbMins = Array(flattenedBVH.size) { Vector3f() }
        val aabbMaxs = Array(flattenedBVH.size) { Vector3f() }
        val leftNodeIndex = IntArray(flattenedBVH.size)
        val rightNodeIndex = IntArray(flattenedBVH.size)
        val isLeaf = IntArray(flattenedBVH.size)
        val blockPosition = Array(flattenedBVH.size) { Vector3f() }

        for ((index, node) in flattenedBVH.withIndex()) {
            aabbMins[index] = node.min
            aabbMaxs[index] = node.max
            leftNodeIndex[index] = node.leftIndex
            rightNodeIndex[index] = node.rightIndex
            isLeaf[index] = node.isLeaf
            blockPosition[index] = node.blockPosition
        }

        val aabbMinFloats = vector3fToFloats(aabbMins)
        val aabbMaxFloats = vector3fToFloats(aabbMaxs)
        val blockPosFloats = vector3fToFloats(blockPosition)
        val rayOriginFloats = vector3fToFloats(rayOrigins)
        val rayDirFloats = vector3fToFloats(rayDirections)

        val leftLeafInts = leftNodeIndex
        val rightLeafInts = rightNodeIndex
        val isLeafInts = isLeaf

        val aabbMinBuffer = createReadOnlyFloatBuffer(aabbMinFloats)
        val aabbMaxBuffer = createReadOnlyFloatBuffer(aabbMaxFloats)
        val leftBuffer = createReadOnlyIntBuffer(leftLeafInts)
        val rightBuffer = createReadOnlyIntBuffer(rightLeafInts)
        val isLeafBuffer = createReadOnlyIntBuffer(isLeafInts)
        val blockBuffer = createReadOnlyFloatBuffer(blockPosFloats)

        val rayOriginBuffer = createReadOnlyFloatBuffer(rayOriginFloats)
        val rayDirBuffer = createReadOnlyFloatBuffer(rayDirFloats)

        val commandQueue = commandQueueThreadPool.get()

// 输出缓冲区
        val rayCount = rayOrigins.size
        val distanceBuffer = clCreateBuffer(context, CL_MEM_WRITE_ONLY, rayCount * 4L, null, null)
        val hitFaceBuffer = clCreateBuffer(context, CL_MEM_WRITE_ONLY, rayCount * 4L, null, null)
        val hitPointBuffer = clCreateBuffer(context, CL_MEM_WRITE_ONLY, rayCount * 4L * 4L, null, null)

        var argIndex = 0
        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(aabbMinBuffer))
        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(aabbMaxBuffer))
        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(leftBuffer))
        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(rightBuffer))
        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(isLeafBuffer))
        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(blockBuffer))

        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(rayOriginBuffer))
        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(rayDirBuffer))

        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_long.toLong(), Pointer.to(LongArray(1) { flattenedBVH.size.toLong() }))

        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(distanceBuffer))
        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(hitFaceBuffer))
        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(hitPointBuffer))

        val globalSize = longArrayOf(rayCount.toLong())
        val err = clEnqueueNDRangeKernel(commandQueue, rayTraceKernel, 1, null, globalSize, null, 0, null, null)
        check(err == CL_SUCCESS) { "clEnqueueNDRangeKernel failed with error code: $err" }

        clFinish(commandQueue)

        val distances = FloatArray(rayCount * 1)
        val hitFacesInts = IntArray(rayCount * 1)
        val hitPoints = FloatArray(rayCount * 4)

        clEnqueueReadBuffer(commandQueue, hitPointBuffer, true, 0, hitPoints.size.toLong() * Sizeof.cl_float, Pointer.to(hitPoints), 0, null, null)
        clEnqueueReadBuffer(commandQueue, distanceBuffer, true, 0, distances.size.toLong() * 4, Pointer.to(distances), 0, null, null)
        clEnqueueReadBuffer(commandQueue, hitFaceBuffer, true, 0, hitFacesInts.size.toLong() * 4, Pointer.to(hitFacesInts), 0, null, null)

        val hitFaces = hitFacesInts.map { faceIndexToBlockFace(it) }

        //Release GPU Memory
        releaseAllGPUMemory(
            aabbMinBuffer, aabbMaxBuffer,
            leftBuffer, rightBuffer, isLeafBuffer, blockBuffer,
            rayOriginBuffer, rayDirBuffer,
            distanceBuffer, hitFaceBuffer, hitPointBuffer
        )

        val hitResults = mutableListOf<HitResult>()

//        for (i in 0 until rayCount) {
//            val blockLoc = rayOrigins[i].add(rayDirections[i]).mul(distances[i])
//            val hitPos: Vector3f= //...
//            val hitResultInstance = com.methyleneblue.camera.raytracepack.bvh.HitResult(hitPos, distances[i], hitFaces[i], rayOrigins[i], rayDirections[i])
//            hitResults.add(hitResultInstance)
//        }

        for (i in 0 until rayCount) {
            val hitPointStartIndex = i * 4 //4 bytes align
            val hitPos = Vector3f(
                hitPoints[hitPointStartIndex],
                hitPoints[hitPointStartIndex + 1],
                hitPoints[hitPointStartIndex + 2]
            )
            val hitResultInstance = HitResult(
                hitPos,
                distances[i],
                hitFaces[i],
                rayOrigins[i],
                rayDirections[i]
            )
            hitResults.add(hitResultInstance)
        }
        return hitResults.toTypedArray()
    }

    fun releaseAllGPUMemory(vararg mems: cl_mem) {
        for (mem in mems) {
            clReleaseMemObject(mem)
        }
    }

    fun createReadOnlyFloatBuffer(array: FloatArray): cl_mem {
        return clCreateBuffer(
            context,
            CL_MEM_READ_ONLY or CL_MEM_COPY_HOST_PTR,
            (array.size * 4).toLong(),
            Pointer.to(array),
            null
        )
    }

    fun createReadOnlyIntBuffer(array: IntArray): cl_mem {
        return clCreateBuffer(
            context,
            CL_MEM_READ_ONLY or CL_MEM_COPY_HOST_PTR,
            (array.size * 4).toLong(),
            Pointer.to(array),
            null
        )
    }


    fun faceIndexToBlockFace(index: Int): BlockFace {
        return when (index) {
            0 -> BlockFace.WEST
            1 -> BlockFace.EAST
            2 -> BlockFace.DOWN
            3 -> BlockFace.UP
            4 -> BlockFace.NORTH
            5 -> BlockFace.SOUTH
            else -> {
//                println(index)
                BlockFace.SELF
            }// fallback
        }
    }

/*
//    fun traceRays(rays: Array<Vector3f>, root: BVHNode) {
//        val flattenRoot = root.flatten()
//
//        val size = flattenRoot.size * 44
//        val nodesBuffer = ByteBuffer.allocate(size)
//        nodesBuffer.order(ByteOrder.nativeOrder())
//        for (node in flattenRoot) {
//            nodesBuffer.putFloat(node.min.x).putFloat(node.min.y).putFloat(node.min.z)
//                       .putFloat(node.max.x).putFloat(node.max.y).putFloat(node.max.z)
//                       .putInt(node.leftIndex).putInt(node.rightIndex)
//                       .putFloat(node.blockPosition.x).putFloat(node.blockPosition.y).putFloat(node.blockPosition.z)
//        }
//        nodesBuffer.rewind()
//        val resultUnitSize = 12 + 12 + 12 + 4 // startPos:Vector3f + direction:Vector3f + endPos:Vector3f + distance:Float
//
//        val raysMem = createRayBuffer(rays)
//        val resultsMem = clCreateBuffer(context, CL.CL_MEM_WRITE_ONLY, rays.size * 8L, null, null)
//        val bvhMem = clCreateBuffer(context, CL.CL_MEM_READ_ONLY or CL.CL_MEM_COPY_HOST_PTR, size.toLong(), Pointer.to(nodesBuffer), null)
//
//        val kernel = loadProgramFromFile("${kernelPath}\\traceRays.cl", "traceRays")
//        clSetKernelArg(kernel, 0, Sizeof.cl_mem.toLong(), Pointer.to(raysMem))
//        clSetKernelArg(kernel, 1, Sizeof.cl_mem.toLong(), Pointer.to(bvhMem))
//        clSetKernelArg(kernel, 2, Sizeof.cl_mem.toLong(), Pointer.to(resultsMem))
//        clSetKernelArg(kernel, 3, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(flattenRoot.size)))
//        clSetKernelArg(kernel, 4, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(rays.size)))
//
//        val globalWorkSize = longArrayOf(rays.size.toLong())
//        clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, globalWorkSize, null, 0, null, null)
//
//        val raysSize = rays.size * 8
//
//        val results = ByteBuffer.allocateDirect(raysSize)
//        results.order(ByteOrder.nativeOrder())
//
//        clEnqueueReadBuffer(commandQueue, resultsMem, true, 0, raysSize.toLong(), Pointer.to(results), 0, null, null)
//
//        for (i in 0 until rays.size) {
//            // val
//        }
//
//        CL.clReleaseMemObject(raysMem)
//        CL.clReleaseMemObject(resultsMem)
//        CL.clReleaseMemObject(bvhMem)
//        CL.clReleaseKernel(kernel)
//        CL.clReleaseProgram(program)
//        CL.clReleaseCommandQueue(commandQueue)
//        CL.clReleaseContext(context)
//    }

 */

    init {
        initialize(true)
    }

    fun initialize(debug: Boolean = false) {
        setExceptionsEnabled(debug)

        val numPlatformsArray = IntArray(1)
        clGetPlatformIDs(0, null, numPlatformsArray)
        val numPlatforms = numPlatformsArray[0]
        val platforms = arrayOfNulls<cl_platform_id>(numPlatforms)
        clGetPlatformIDs(numPlatforms, platforms, null)

        val platform = platforms[0]!!
        val devices = arrayOfNulls<cl_device_id>(1)
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 1, devices, null)
        device = devices[0]!!

        context = clCreateContext(null, 1, arrayOf(device), null, null, null)
        commandQueueThreadPool = ThreadLocal.withInitial {
            clCreateCommandQueueWithProperties(context, device, null, null)
        }

        println("[GPU] Compiling CLScripts...")
        preCompilePrograms()
        println("[GPU] OpenCL Initialized.")
        startDaemonThreads()
        println("[GPU] Daemon threads initialized.")
    }

    fun preCompilePrograms() {
        rayTraceKernel = (preCompileProgram("rt2", "rayTrace"))
    }

    fun preCompileProgram(kernelFileName: String, kernelName: String): cl_kernel{
        val source = Files.readString(File(kernelPath, "$kernelFileName.cl").toPath())
        program = clCreateProgramWithSource(context, 1, arrayOf(source), null, null)
        clBuildProgram(program, 0, null, null, null, null)
        println("[GPU] Kernel program $kernelName has compiled.")
        return clCreateKernel(program, kernelName, null)
    }

//    fun loadProgramFromFile(filePath: String, kernelName: String): cl_kernel {
//        val source = File(filePath).readText(Charsets.UTF_8)
//        program = clCreateProgramWithSource(context, 1, arrayOf(source), null, null)
//        clBuildProgram(program, 0, null, null, null, null)
//
//        return clCreateKernel(program, kernelName, null)
//    }

    fun loadProgramFromFile(filePath: String, kernelName: String): cl_kernel {
        val source = File(filePath).readText(Charsets.UTF_8)
        program = clCreateProgramWithSource(context, 1, arrayOf(source), null, null)

        val buildResult = clBuildProgram(program, 0, null, null, null, null)

        // <-- 不论成功与否，都打印日志
        val logSize = LongArray(1)
        clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, 0, null, logSize)
        val logData = ByteArray(logSize[0].toInt())
        clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, logData.size.toLong(), Pointer.to(logData), null)
        val log = String(logData)
        println("-------- OpenCL Build Log --------")
        println(log)
        println("----------------------------------")

        if (buildResult != CL_SUCCESS) {
            throw CLException("CL_BUILD_PROGRAM_FAILURE: $buildResult")
        }

        return clCreateKernel(program, kernelName, null)
    }

    private fun getDeviceName(device: cl_device_id): String {
        val size = LongArray(1)
        clGetDeviceInfo(device, CL_DEVICE_NAME, 0, null, size)
        val buffer = ByteArray(size[0].toInt())
        clGetDeviceInfo(device, CL_DEVICE_NAME, buffer.size.toLong(), Pointer.to(buffer), null)
        return String(buffer).trim()
    }
}