package com.methyleneblue.camera.command

import com.methyleneblue.camera.CameraManager
import com.methyleneblue.camera.imagepack.AfterEffect
import com.methyleneblue.camera.imagepack.aftereffect.Bloom
import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.io.File
import java.util.Random
import javax.imageio.ImageIO
import kotlin.Array

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

                Random().nextInt(0, 100)
                cameraInstance.progressBar?.removeAll()
                if (cameraInstance.progressBar == null) {
                    cameraInstance.progressBar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID)
                }
                for (player in Bukkit.getOnlinePlayers()) {
                    if (player.isOp) {
                        cameraInstance.progressBar?.addPlayer(player)
                    }
                }

                cameraInstance.updateCamera(p0 as Player, mixinTimes)

                val path = p3[1]
                val file = File(path)
                val fileName = p3[2]
                if (!file.exists()) file.mkdirs()

//                val output = AfterEffect.apply(cameraInstance.bufferedImage, cameraInstance.depthImage, cameraInstance.fov, cameraInstance.progressBar)
                val output = cameraInstance.bufferedImage

                cameraInstance.progressBar?.removeAll()

                val end = System.currentTimeMillis()
                val duration = end - start
                p0.sendMessage("Finished generate in time $duration ms")
                ImageIO.write(output, "png", File(file, "${fileName}.png"))
            } catch (e : Exception) {
                e.printStackTrace()
            }
        }.start()
        return true
    }
}