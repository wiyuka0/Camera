package com.methyleneblue.camera.raytracepack.bvh.jocl

import com.methyleneblue.camera.obj.raytrace.RayTraceMaterial
import com.methyleneblue.camera.raytracepack.bvh.BVHNode
import com.methyleneblue.camera.raytracepack.bvh.BVHTree
import org.bukkit.block.BlockFace
import com.methyleneblue.camera.raytracepack.bvh.FlatBVHNode
import com.methyleneblue.camera.raytracepack.bvh.HitResult
import com.methyleneblue.camera.raytracepack.bvh.jocl.async.AsyncFuture
import com.methyleneblue.camera.texture.TextureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bukkit.Material
import org.jocl.*
import org.jocl.CL.*
import org.joml.Vector3f
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random

object JoclInterface {
    lateinit var context: cl_context
    lateinit var device: cl_device_id
    lateinit var program: cl_program
    lateinit var rayTraceKernel: cl_kernel

    lateinit var commandQueue: cl_command_queue

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
    data class ColorTraceRequest(
        val cameraOrigin: Vector3f,
        val direction: Vector3f,
        val asyncFuture: AsyncFuture<Vector3f>
    )
    val requestColor = ConcurrentLinkedQueue<ColorTraceRequest>()
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

        val commandQueue = commandQueue

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

    fun processColors(bvhTree: BVHTree, flatBVHNode: Array<FlatBVHNode>) {
        getWorldColors(bvhTree, flatBVHNode)
    }
    fun postWorldColorRequest(cameraOrigin: Vector3f, direction: Vector3f): AsyncFuture<Vector3f> {
        val asyncFuture = AsyncFuture<Vector3f>()
        val request = ColorTraceRequest(
            cameraOrigin = cameraOrigin,
            direction = direction,
            asyncFuture = asyncFuture
        )

        this.requestColor.add(request)

        return asyncFuture
    }

    fun getWorldColors(

//        rayOrigins: Array<Vector3f>,
//        directions: Array<Vector3f>,
        root: BVHTree,
        flattenRoot: Array<FlatBVHNode>
    ) {
        updateBVHTree(root, flattenRoot)

        val bvhSize = flattenRoot.size

        val localRequestList = requestColor.toList()

        val rayOrigins = localRequestList.map { it.cameraOrigin }
        val directions = localRequestList.map { it.direction }


        val requestSize = rayOrigins.size
//        println(requestSize)
        if(requestSize == 0) return
        val rayOriginsBuffer = ByteBuffer.allocate(4 * 4 * requestSize); rayOriginsBuffer.order(ByteOrder.nativeOrder())
        val directionsBuffer = ByteBuffer.allocate(4 * 4 * requestSize); directionsBuffer.order(ByteOrder.nativeOrder())

        for (origin in rayOrigins)
            rayOriginsBuffer.putFloat(origin.x)   .putFloat(origin.y)   .putFloat(origin.z)   .putFloat(0.0f)
        for (direction in directions)
            directionsBuffer.putFloat(direction.x).putFloat(direction.y).putFloat(direction.z).putFloat(0.0f)

        /* BVH Node Data */
        val aabbMinBuffer                    = ByteBuffer.allocate(bvhSize * 16);        aabbMinBuffer                 .order(ByteOrder.nativeOrder())
        val aabbMaxBuffer                    = ByteBuffer.allocate(bvhSize * 16);        aabbMaxBuffer                 .order(ByteOrder.nativeOrder())
        val leftLeafIndexBuffer              = ByteBuffer.allocate(bvhSize * 4);         leftLeafIndexBuffer           .order(ByteOrder.nativeOrder())
        val rightLeafIndexBuffer             = ByteBuffer.allocate(bvhSize * 4);         rightLeafIndexBuffer          .order(ByteOrder.nativeOrder())
        val isLeafBuffer                     = ByteBuffer.allocate(bvhSize * 4);         isLeafBuffer                  .order(ByteOrder.nativeOrder())
        val blockPositionBuffer              = ByteBuffer.allocate(bvhSize * 16);        blockPositionBuffer           .order(ByteOrder.nativeOrder())
        val materialOffsetBuffer             = ByteBuffer.allocate(bvhSize * 4);         materialOffsetBuffer          .order(ByteOrder.nativeOrder())
        val materialIndexBuffer              = ByteBuffer.allocate(bvhSize * 4);         materialIndexBuffer           .order(ByteOrder.nativeOrder())
        val weightFunctionArgumentsBuffer    = ByteBuffer.allocate(bvhSize * 16);        weightFunctionArgumentsBuffer .order(ByteOrder.nativeOrder())

        val materialSize = RayTraceMaterial.materials.size
        /* Material Type Data */
        val materialFuncIdBuffer             = ByteBuffer.allocate(materialSize * 4);    materialFuncIdBuffer          .order(ByteOrder.nativeOrder())
        val materialSpreadBuffer             = ByteBuffer.allocate(materialSize * 4);    materialSpreadBuffer          .order(ByteOrder.nativeOrder())
        val materialSampleBuffer             = ByteBuffer.allocate(materialSize * 4);    materialSampleBuffer          .order(ByteOrder.nativeOrder())
        val materialIsLightBuffer            = ByteBuffer.allocate(materialSize * 4);    materialIsLightBuffer         .order(ByteOrder.nativeOrder())
        val lightColor                       = ByteBuffer.allocate(materialSize * 16);   lightColor                    .order(ByteOrder.nativeOrder())
        val lightBrightness                  = ByteBuffer.allocate(materialSize * 4);    lightBrightness               .order(ByteOrder.nativeOrder())

        val randoms                          = ByteBuffer.allocate(10001 * 4);           randoms                       .order(ByteOrder.nativeOrder())

        val maxReflectionTimes = 3
        val currentTime = 6000
        val maxSingleRayCount = 20
        val maxPixelRaysCount = 20 * 20 * 20 // 20 ^ 3


        for (i in 0 until 10000) {
            randoms.putFloat(Random.nextFloat())
        }
        run {
            for (node in flattenRoot) {  // Init by default value
                aabbMinBuffer.putFloat(node.min.x).putFloat(node.min.y).putFloat(node.min.z).putFloat(0f)
                aabbMaxBuffer.putFloat(node.max.x).putFloat(node.max.y).putFloat(node.max.z).putFloat(0f)
                leftLeafIndexBuffer.putInt(node.leftIndex)
                rightLeafIndexBuffer.putInt(node.rightIndex)
                isLeafBuffer.putInt(node.isLeaf)
                blockPositionBuffer.putFloat(node.blockPosition.x).putFloat(node.blockPosition.y)
                    .putFloat(node.blockPosition.z).putFloat(0f)
                materialOffsetBuffer.putInt(node.textureOffset)
                val materialInstance = RayTraceMaterial.getMaterialReflection(node.materialIndex)
                val argsLength = materialInstance.elseArgs.size

                materialIndexBuffer.putInt(node.materialIndex)
                weightFunctionArgumentsBuffer
                    .putFloat(if (argsLength >= 1) materialInstance.elseArgs[0].toFloat() else 0.0f)
                    .putFloat(if (argsLength >= 2) materialInstance.elseArgs[1].toFloat() else 0.0f)
                    .putFloat(if (argsLength >= 3) materialInstance.elseArgs[2].toFloat() else 0.0f).putFloat(0f)
            }

            val orderedMaterials = RayTraceMaterial.materials.values.sortedBy { it.materialId }

            for (material in orderedMaterials) {
                materialFuncIdBuffer.putInt(material.funcId)
                materialSpreadBuffer.putFloat(material.spread)
                materialSampleBuffer.putInt(material.reflectionTimes)
                if (material.isLight) {
                    materialIsLightBuffer.putInt(1)
                    lightColor.putFloat((material.lightColor.x / 255.0f)).putFloat(material.lightColor.y / 255.0f)
                        .putFloat(material.lightColor.z / 255.0f).putFloat(0.0f)
                    lightBrightness.putFloat(material.brightness)
                } else {
                    materialIsLightBuffer.putInt(0)
                    lightColor.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f)
                    lightBrightness.putFloat(0.0f)
                }
            }
        }
        run {
            aabbMinBuffer.rewind()
            aabbMaxBuffer.rewind()
            leftLeafIndexBuffer.rewind()
            rightLeafIndexBuffer.rewind()
            isLeafBuffer.rewind()
            blockPositionBuffer.rewind()
            materialOffsetBuffer.rewind()
            weightFunctionArgumentsBuffer.rewind()

            materialFuncIdBuffer.rewind()
            materialSpreadBuffer.rewind()
            materialSampleBuffer.rewind()
            materialIsLightBuffer.rewind()
            lightColor.rewind()
            lightBrightness.rewind()
            randoms.rewind()
        }

        fun clBuffer(data: ByteBuffer, flags: Long, size: Long): cl_mem {
//            val bufferSize = data.remaining().toLong()
            val bufferSize = size
//            println("Allocate buffer size $bufferSize")
            return clCreateBuffer(context, flags or CL_MEM_COPY_HOST_PTR.toLong(), bufferSize, Pointer.to(data), null)
        }
        fun intArrayToClMem(array: IntArray): cl_mem {
            val buffer = ByteBuffer.allocateDirect(array.size * 4)  // 每个 int 4 字节
                .order(ByteOrder.nativeOrder())
            for (value in array) buffer.putInt(value)
            buffer.rewind()

            return clCreateBuffer(
                context,
                CL_MEM_READ_ONLY or CL_MEM_COPY_HOST_PTR.toLong(),
                buffer.remaining().toLong(),
                Pointer.to(buffer),
                null
            )
        }

        val clRayOrigins = clBuffer(rayOriginsBuffer, CL_MEM_READ_ONLY, bvhSize * 16L)
        val clRayDirs = clBuffer(directionsBuffer, CL_MEM_READ_ONLY, bvhSize * 16L)
        val clAabbMin = clBuffer(aabbMinBuffer, CL_MEM_READ_ONLY, bvhSize * 16L)
        val clAabbMax = clBuffer(aabbMaxBuffer, CL_MEM_READ_ONLY, bvhSize * 16L)
        val clLeft = clBuffer(leftLeafIndexBuffer, CL_MEM_READ_ONLY, bvhSize * 4L)
        val clRight = clBuffer(rightLeafIndexBuffer, CL_MEM_READ_ONLY, bvhSize * 4L)
        val clLeaf = clBuffer(isLeafBuffer, CL_MEM_READ_ONLY, bvhSize * 4L)
        val clBlock = clBuffer(blockPositionBuffer, CL_MEM_READ_ONLY, bvhSize * 16L)
        val clMatOffset = clBuffer(materialOffsetBuffer, CL_MEM_READ_ONLY, bvhSize * 4L)
        val clMaterialIndex = clBuffer(materialIndexBuffer, CL_MEM_READ_ONLY, bvhSize * 4L)
        val clWeightArgs = clBuffer(weightFunctionArgumentsBuffer, CL_MEM_READ_ONLY, bvhSize * 16L)

        val clRandoms = clBuffer(randoms, CL_MEM_READ_ONLY, 10001 * 4L)

        val clMatFunc = clBuffer(materialFuncIdBuffer, CL_MEM_READ_ONLY, materialSize * 4L)
        val clMatSpread = clBuffer(materialSpreadBuffer, CL_MEM_READ_ONLY, materialSize * 4L)
        val clMatSample = clBuffer(materialSampleBuffer, CL_MEM_READ_ONLY, materialSize * 4L)
        val clMatLight = clBuffer(materialIsLightBuffer, CL_MEM_READ_ONLY, materialSize * 4L)
        val clLightColor = clBuffer(lightColor, CL_MEM_READ_ONLY, materialSize * 16L)
        val clLightBright = clBuffer(lightBrightness, CL_MEM_READ_ONLY, materialSize * 4L)
        val clTextureArray = intArrayToClMem(TextureManager.textureArray)

        val dummyRayBuffer = clCreateBuffer(context, CL_MEM_READ_WRITE, (650 * 1024 * 1024).toLong(), null, null)
        val dummyColorBuffer = clCreateBuffer(context, CL_MEM_READ_WRITE, (380 * 1024 * 1024).toLong(), null, null)
        val dummyUpdateBuffer = clCreateBuffer(context, CL_MEM_READ_WRITE, (100 * 1024 * 1024).toLong(), null, null)

//        return clCreateBuffer(context, flags or CL_MEM_COPY_HOST_PTR.toLong(), bufferSize, Pointer.to(data), null)
        println(requestSize)
        val outputColorBuffer = clCreateBuffer(context, CL_MEM_WRITE_ONLY, 16L * requestSize, null, null)

        var argIndex = 0
        fun arg(buffer: cl_mem, size: Long) {
//            println("size: $size")
            clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(buffer))
        }
        val float3Size = 12 + 4L
        val intSize = 4L
        val floatSize = 4L

        arg(clRayOrigins, float3Size * requestSize)
        arg(clRayDirs, float3Size * requestSize)
        arg(clAabbMin, float3Size * bvhSize)
        arg(clAabbMax, float3Size * bvhSize)
        arg(clLeft, intSize * bvhSize)
        arg(clRight, intSize * bvhSize)
        arg(clLeaf, intSize * bvhSize)
        arg(clBlock, float3Size * bvhSize)
        arg(clMatOffset, intSize * bvhSize)
        arg(clMaterialIndex, intSize * bvhSize)
        arg(clWeightArgs, float3Size * bvhSize)
        arg(clMatFunc, intSize * materialSize)
        arg(clMatSpread, floatSize * materialSize)
        arg(clMatSample, intSize * materialSize)
        arg(clMatLight, intSize * materialSize)
        arg(clLightColor, float3Size * materialSize)
        arg(clLightBright, floatSize * materialSize)
        arg(clTextureArray, intSize * TextureManager.textureArray.size)
        arg(clRandoms, floatSize * 10001)

//        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(Pointer.to(buffer)))
//        arg(dummyRayBuffer, (650 * 1024 * 1024).toLong())
//        arg(dummyColorBuffer, (380 * 1024 * 1024).toLong())
//        arg(dummyUpdateBuffer, (55 * 1024 * 1024).toLong())

        val tempMaxReflectionBuffer = ByteBuffer.allocate(maxReflectionTimes); tempMaxReflectionBuffer.order(ByteOrder.nativeOrder())
        val tempCurrentTimeBuffer = ByteBuffer.allocate(maxReflectionTimes); tempCurrentTimeBuffer.order(ByteOrder.nativeOrder())
        val tempMaxSingleRayCountBuffer = ByteBuffer.allocate(maxSingleRayCount); tempMaxSingleRayCountBuffer.order(ByteOrder.nativeOrder())
        val tempMaxPixelRaysCountBuffer = ByteBuffer.allocate(maxPixelRaysCount); tempMaxPixelRaysCountBuffer.order(ByteOrder.nativeOrder())

        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to(tempMaxReflectionBuffer))

        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to((dummyRayBuffer)))
        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to((dummyColorBuffer)))
        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to((dummyUpdateBuffer)))

        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to((tempCurrentTimeBuffer)))
        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to((tempMaxSingleRayCountBuffer)))
        clSetKernelArg(rayTraceKernel, argIndex++, Sizeof.cl_mem.toLong(), Pointer.to((tempMaxPixelRaysCountBuffer)))

        arg(outputColorBuffer, float3Size * requestSize)
//        println(commandQueue)

        clEnqueueNDRangeKernel(commandQueue, rayTraceKernel, 1, null, longArrayOf(requestSize.toLong()), null, 0, null, null)

//            val status = clFinish(commandQueue)
//        if(status != CL_SUCCESS) error("[GPU] OpenCL failed in $status") // 未输出

        val bufferSize = requestSize * 16
        val outputBuffer = ByteBuffer.allocateDirect(bufferSize); outputBuffer.order(ByteOrder.nativeOrder())

//        val outputBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        outputBuffer.position(0)

// 从 GPU 读取结果数据到 outputBuffer
        clEnqueueReadBuffer(
            commandQueue,
            outputColorBuffer,
            CL.CL_TRUE,
            0,
            bufferSize.toLong(), // 读取字节数
            Pointer.to(outputBuffer),
            0,
            null,
            null
        )

// 将 ByteBuffer 映射为 FloatBuffer，便于以 float 形式读取
        val floatBuffer: FloatBuffer = outputBuffer.asFloatBuffer()

// 构造结果数组，忽略每个元素的第 4 个 float（padding）
        val result = Array(requestSize) {
            val x = floatBuffer.get()
            val y = floatBuffer.get()
            val z = floatBuffer.get()
            floatBuffer.get() // 跳过 padding
            Vector3f(x, y, z)
        }
        for ((index, f) in result.withIndex()) localRequestList[index].asyncFuture.set(f)
        requestColor.clear()
        fun releaseClMem(vararg mems: cl_mem?) {
            for (mem in mems) {
                if (mem != null) clReleaseMemObject(mem)
            }
        }
        releaseClMem(
            clRayOrigins, clRayDirs, clAabbMin, clAabbMax, clLeft, clRight, clLeaf, clBlock,
            clMatOffset, clMaterialIndex, clWeightArgs,
            clMatFunc, clMatSpread, clMatSample, clMatLight, clLightColor, clLightBright,
            clTextureArray, dummyRayBuffer, dummyColorBuffer, dummyUpdateBuffer, outputColorBuffer
        )
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
        val err = IntArray(1) { 0 }
        commandQueue = clCreateCommandQueueWithProperties(context, device, null, err)
        if(err[0] != CL_SUCCESS) {
            error("[GPU] OpenCL failed in $err")
        }
        println("[GPU] Compiling CLScripts...")
        preCompilePrograms()
        println("[GPU] OpenCL Initialized.")
        startDaemonThreads()
    }

    fun preCompilePrograms() {
        rayTraceKernel = (preCompileProgram("rt3", "getWorldColor"))
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