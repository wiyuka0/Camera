package com.methyleneblue.camera

import com.methyleneblue.camera.command.CameraCommand
import com.methyleneblue.camera.command.TakePicture
import com.methyleneblue.camera.test.BVHTest
import com.methyleneblue.camera.texture.TextureManager
import org.bukkit.plugin.java.JavaPlugin

class Camera : JavaPlugin() {

    override fun onEnable() {
        // Plugin startup logic

        getCommand("cameracommand")?.setExecutor(CameraCommand())
        getCommand("takepicture")?.setExecutor(TakePicture())

        TextureManager.init()

    }

    companion object {

    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
