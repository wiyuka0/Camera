package com.methyleneblue.camera.obj

import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Vector3f
import java.awt.image.BufferedImage

class Realtime {

    data class Pixel(
        val entity: TextDisplay
    )

    data class RealtimeData(
        val textDisplays: ArrayList<Pixel>,
        val camera: CLRTCamera,
        val bufferImage: BufferedImage,
        val depthBuffer: Array<FloatArray>,
        // 新增：用于检测移动
        var lastLocation: Location,
        var lastDirection: Vector
    )

    companion object {
        val playerList = hashMapOf<Player, RealtimeData>()

        const val WIDTH = 200
        const val HEIGHT = 150

        // 阈值：移动超过多少算“动了”
        // 0.0001 对应非常微小的移动，确保画面不产生拖影
        private const val MOVE_THRESHOLD_SQUARED = 0.0001
        private const val ROTATION_THRESHOLD = 0.001

        fun tick() {
            val entries = playerList.entries.toList()

            for ((player, data) in entries) {
                // 1. 获取当前状态
                val currentLoc = player.eyeLocation
                val currentDir = player.location.direction

                // 2. 检测是否移动
                // 检查位置距离平方
                val isPosChanged = data.lastLocation.distanceSquared(currentLoc) > MOVE_THRESHOLD_SQUARED
                // 检查朝向角度变化 (Math.abs(x - y) 简单判断或者 angle)
                // 这里用简单的向量距离判断朝向变化，比计算角度快
                val isDirChanged = data.lastDirection.distanceSquared(currentDir) > ROTATION_THRESHOLD

                val isMoving = isPosChanged || isDirChanged

                // 更新上一帧记录
                if (isMoving) {
                    data.lastLocation = currentLoc.clone()
                    data.lastDirection = currentDir.clone()
                }

                // 3. 计算屏幕位置 (TextDisplay 锚点)
                val playerViewPoint = currentLoc.clone().add(currentDir.clone().multiply(2.0))
                val lookVec = playerViewPoint.clone().subtract(currentLoc)
                val finalLoc = playerViewPoint.clone().setDirection(Vector(-lookVec.x, -lookVec.y, -lookVec.z))

                // 4. 更新相机
                data.camera.location = currentLoc

                // 关键逻辑：
                // 如果移动了，resetAccumulation = true (重置缓冲，快速响应)
                // 如果没动，resetAccumulation = false (累加缓冲，去噪)
                // mixinTimes 设为 1 或 2，保证 TPS 不掉，靠时间堆积质量
                data.camera.updateCamera1(player, 2, 100f, resetAccumulation = isMoving)

                // 5. 同步像素到实体
                val image = data.bufferImage
                val pixels = data.textDisplays
                var pixelIndex = 0

                for (pixel in pixels) {
                    if (pixelIndex >= image.width * image.height) break

                    // 优化：只有需要重置画面（移动中）或者已经积累了一定帧数（画面变清晰）时才更新 TextDisplay
                    // 过于频繁更新 7500 个实体可能会闪烁或卡顿，但为了实时性先保持每 tick 更新

                    val imgX = pixelIndex % WIDTH
                    val imgY = pixelIndex / WIDTH
                    val argb = image.getRGB(imgX, imgY)

                    val r = (argb shr 16) and 0xFF
                    val g = (argb shr 8) and 0xFF
                    val b = argb and 0xFF

                    // 这里可以加个缓存判断：如果颜色没变就不 set，节省包发送量
                    // 但 TextDisplay 内部可能有优化，视情况而定
                    pixel.entity.backgroundColor = Color.fromRGB(r, g, b)
                    pixel.entity.teleportAsync(finalLoc)

                    pixelIndex++
                }
            }
        }

        fun initialize() {
        }

        fun addMember(player: Player) {
            val bufferImage = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
            val depthBuffer = Array(WIDTH) { FloatArray(HEIGHT) }

            val camera = CLRTCamera(
                location = player.eyeLocation,
                size = Pair(WIDTH, HEIGHT),
                fov = 70.0,
                distance = 160.0,
                progressBar = null,
                bufferedImage = bufferImage,
                depthImage = depthBuffer
            )

            val listOfEntity = arrayListOf<Pixel>()
            val scale = 0.025f
            val totalWidth = WIDTH * scale
            val totalHeight = HEIGHT * scale

            val startX = -totalWidth / 2f
            val startY = totalHeight / 2f

            val playerViewPoint = player.eyeLocation.add(player.location.direction.multiply(2.0))
            val direction = playerViewPoint.clone().subtract(player.eyeLocation)
            val baseLoc = playerViewPoint.clone().setDirection(Vector(-direction.x, -direction.y, -direction.z))

            for (y in 0 until HEIGHT) {
                for (x in 0 until WIDTH) {
                    val offsetX = startX + x * scale
                    val offsetY = startY - y * scale
                    val translation = Vector3f(offsetX, offsetY, 0f)

                    // 缩放修正：为了让像素看起来连贯，可以稍微调大一点点比例
                    val scaleVector = Vector3f(0.5f, 0.25f, 0.5f)

                    val entityInstance = player.world.spawnEntity(baseLoc, EntityType.TEXT_DISPLAY) as TextDisplay

                    entityInstance.text = " "
                    entityInstance.isSeeThrough = true
                    entityInstance.lineWidth = 1000
                    entityInstance.backgroundColor = Color.BLACK
                    entityInstance.viewRange = 5.0f

                    val oldTrans = entityInstance.transformation
                    val newTrans = Transformation(
                        translation,
                        oldTrans.leftRotation,
                        scaleVector,
                        oldTrans.rightRotation
                    )
                    entityInstance.transformation = newTrans

                    listOfEntity.add(Pixel(entityInstance))
                }
            }

            // 初始化时记录当前位置
            playerList[player] = RealtimeData(
                listOfEntity,
                camera,
                bufferImage,
                depthBuffer,
                player.eyeLocation.clone(),
                player.location.direction.clone()
            )
        }

        fun removeMember(player: Player) {
            val data = playerList.remove(player) ?: return
            data.camera.cleanup()
            for (pixel in data.textDisplays) {
                pixel.entity.remove()
            }
        }
    }
}