package com.methyleneblue.camera.command

import com.methyleneblue.camera.CameraManager
import com.methyleneblue.camera.obj.EffectBasedCamera
import com.methyleneblue.camera.obj.RayTraceCamera
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.awt.image.BufferedImage

class CameraCommand: CommandExecutor {

    override fun onCommand(p0: CommandSender, p1: Command, p2: String, p3: Array<out String>): Boolean {
        val player = p0 as Player
        val type = p3[0]
        val width = p3[1].toInt()
        val height = p3[2].toInt()
        val fov = p3[3].toFloat()
        val distance = p3[4].toDouble()
        var index = -1
        if(p3.size >= 6) index = p3[5].toInt()

        return when(type){
            "effect_based" -> {
                val cameraInstance = EffectBasedCamera(
                    player.eyeLocation,
                    width to height,
                    fov.toDouble(),
                    distance,
                    BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                )

                var cameraId = -1;
                cameraId = if(index != -1) {
                    CameraManager.addCameraByIndex(cameraInstance, index)
                }else {
                    CameraManager.addCamera(cameraInstance)
                }
                player.sendMessage("Generated Camera Id $cameraId")
                true
            }
            "raytrace" -> {
                val cameraInstance = RayTraceCamera(
                    player.eyeLocation,
                    width to height,
                    fov.toDouble(),
                    distance,
                    BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                )

                var cameraId = -1
                cameraId = if(index != -1) {
                    CameraManager.addCameraByIndex(cameraInstance, index)
                }else {
                    CameraManager.addCamera(cameraInstance)
                }
                player.sendMessage("Generated Camera Id $cameraId")
                true
            }

            else -> {
                player.sendMessage("Camera type is not exist.")
                true
            }
        }
    }
}