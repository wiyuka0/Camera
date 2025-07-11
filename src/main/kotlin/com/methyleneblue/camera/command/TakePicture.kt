package com.methyleneblue.camera.command

import com.methyleneblue.camera.CameraManager
import com.methyleneblue.camera.imagepack.aftereffect.Bloom
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.io.File
import javax.imageio.ImageIO

class TakePicture: CommandExecutor {

    override fun onCommand(p0: CommandSender, p1: Command, p2: String, p3: Array<out String>): Boolean {
        Thread {
            try {
                val cameraId = p3[0].toInt()
                val cameraInstance = CameraManager.getCamera(cameraId)

                if (cameraInstance == null) {
                    p0.sendMessage("No Camera")
                    return@Thread
                }

                p0.sendMessage("Generating Picture...")
                val start = System.currentTimeMillis()
                val mixinTimes = p3[3].toInt()
                cameraInstance.updateCamera(p0 as Player, mixinTimes)
                val end = System.currentTimeMillis()
                val duration = end - start
                p0.sendMessage("Finished generate in time $duration ms")

                val path = p3[1]
                val file = File(path)
                val fileName = p3[2]
                if (!file.exists()) file.mkdirs()
                val output = Bloom.applyEffect(cameraInstance.bufferedImage, 90.0, 50f)

                ImageIO.write(output, "png", File(file, "${fileName}.png"))
            } catch (e : Exception) {
                e.printStackTrace()
            }
        }.start()
        return true
    }
}