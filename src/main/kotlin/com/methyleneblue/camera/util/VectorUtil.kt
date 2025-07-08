package com.methyleneblue.camera.util

import org.bukkit.block.BlockFace
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object VectorUtil {
    fun getReflectedVector(
        incident: Vector3f,
        planeNormal: Vector3f,
        output: Vector3f? = null,
    ): Vector3f {val normal = Vector3f(planeNormal).normalize()
        if (normal.length().isNaN()) {
            throw IllegalArgumentException("Plane normal cannot be zero vector")
        }

        // 使用反射公式：R = I - 2*(I·N)*N
        val dot = incident.dot(normal)
        val reflection = Vector3f(incident)
        val temp = Vector3f(normal).mul(2 * dot)
        reflection.sub(temp)
        output?.x = reflection.x
        output?.y = reflection.y
        output?.z = reflection.z

        return reflection
    }
    fun angleBetweenVectors(v1: Vector3f, v2: Vector3f): Float {
        // 1. 计算点积
        val dotProduct = v1.dot(v2)

        // 2. 计算向量模长
        val v1Length = v1.length()
        val v2Length = v2.length()

        // 3. 检查零向量
        if (v1Length == 0f || v2Length == 0f) {
            return Float.NaN // 返回 NaN 表示无定义夹角
        }

        // 4. 计算余弦值（限制在 [-1, 1] 范围内）
        val cosTheta = dotProduct / (v1Length * v2Length)
        val clampedCos = max(-1.0f, min(cosTheta, 1.0f))

        // 5. 反余弦求弧度并转为角度
        return Math.toDegrees(acos(clampedCos.toDouble())).toFloat()
    }

    val faceToNormalMap = hashMapOf<BlockFace, Vector3f>().apply {
        this[BlockFace.UP] = Vector3f(0f, 1f, 0f)
        this[BlockFace.DOWN] = Vector3f(0f, -1f, 0f)
        this[BlockFace.NORTH] = Vector3f(0f, 0f, -1f)
        this[BlockFace.SOUTH] = Vector3f(0f, 0f, 1f)
        this[BlockFace.EAST] = Vector3f(1f, 0f, 0f)
        this[BlockFace.WEST] = Vector3f(-1f, 0f, 0f)

        this[BlockFace.NORTH_EAST] = Vector3f(1f, 0f, -1f).normalize()
        this[BlockFace.NORTH_WEST] = Vector3f(-1f, 0f, -1f).normalize()
        this[BlockFace.SOUTH_EAST] = Vector3f(1f, 0f, 1f).normalize()
        this[BlockFace.SOUTH_WEST] = Vector3f(-1f, 0f, 1f).normalize()

        this[BlockFace.SELF] = Vector3f(0f, 0f, 0f).normalize()
    }

    fun normalFromBlockFace(face: BlockFace): Vector3f {
        return when (face) {
            BlockFace.UP       -> Vector3f(0f, 1f, 0f)
            BlockFace.DOWN     -> Vector3f(0f, -1f, 0f)
            BlockFace.NORTH    -> Vector3f(0f, 0f, -1f)
            BlockFace.SOUTH    -> Vector3f(0f, 0f, 1f)
            BlockFace.EAST     -> Vector3f(1f, 0f, 0f)
            BlockFace.WEST     -> Vector3f(-1f, 0f, 0f)

            BlockFace.NORTH_EAST -> Vector3f(1f, 0f, -1f).normalize()
            BlockFace.NORTH_WEST -> Vector3f(-1f, 0f, -1f).normalize()
            BlockFace.SOUTH_EAST -> Vector3f(1f, 0f, 1f).normalize()
            BlockFace.SOUTH_WEST -> Vector3f(-1f, 0f, 1f).normalize()

            BlockFace.SELF -> Vector3f(0f, 0f, 0f)
            else -> Vector3f(0f, 0f, 0f)
        }
    }

    fun  perturbDirection(base: Vector3f, spread: Double, output: Vector3f? = null): Vector3f {
        require(spread in 0.0..1.0)

        if (spread == 0.0) return Vector3f(base).normalize()
        if (spread == 1.0) {
            // Uniform over the entire sphere
            return randomUnitVector()
        }

        // Sample a direction within a cone around `base`
        val coneAngle = spread * PI / 2  // spread = 1.0 → 90°, max

        // Step 1: Sample uniformly in cone
        val cosTheta = lerp(1.0, cos(coneAngle), Random.Default.nextDouble())
        val sinTheta = sqrt(1.0 - cosTheta * cosTheta)
        val phi = Random.Default.nextDouble(0.0, 2 * PI)

        // Local direction in Z-up space
        val x = (cos(phi) * sinTheta).toFloat()
        val y = (sin(phi) * sinTheta).toFloat()
        val z = cosTheta.toFloat()

        val localDirection = Vector3f(x, y, z)

        // Step 2: Rotate local vector to align with `base`
        return if(output == null) {
            rotateVectorFromZAxis(localDirection, base)
            /**
             * [22:45:56 WARN]: Exception in thread "Thread-17" java.lang.ArrayIndexOutOfBoundsException: Index 3 out of bounds for length 3
             * [22:45:56 WARN]: 	at Camera-1.0-SNAPSHOT.jar//com.methyleneblue.camera.command.TakePicture.onCommand$lambda$0(TakePicture.kt:25)
             * [22:45:56 WARN]: 	at java.base/java.lang.Thread.run(Thread.java:1570)
             */
        } else {
            val normalize = rotateVectorFromZAxis(localDirection, base).normalize()
            output.x = normalize.x
            output.y = normalize.y
            output.z = normalize.z
            output!!
        }
    }

    // Uniform random unit vector on the whole sphere
    fun randomUnitVector(): Vector3f {
        val theta = Random.Default.nextDouble(0.0, 2 * PI)
        val z = Random.Default.nextDouble(-1.0, 1.0)
        val r = sqrt(1.0 - z * z)
        return Vector3f(
            (r * cos(theta)).toFloat(),
            (r * sin(theta)).toFloat(),
            z.toFloat()
        )
    }

    // Rotates a vector from Z+ to targetDir
    fun rotateVectorFromZAxis(vec: Vector3f, targetDir: Vector3f): Vector3f {
        val up = Vector3f(0f, 0f, 1f)
        val axis = up.cross(Vector3f(targetDir), Vector3f())
        val angle = acos(up.dot(Vector3f(targetDir).normalize()).coerceIn(-1f, 1f))

        if (axis.lengthSquared() < 1e-6f) return Vector3f(vec).rotateAxis(angle, 1f, 0f, 0f) // parallel or opposite
        return Vector3f(vec).rotateAxis(angle, axis.x, axis.y, axis.z)
    }

    // Helper
    fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}