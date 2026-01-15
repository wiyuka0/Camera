package com.methyleneblue.camera

import com.methyleneblue.camera.command.CameraCommand
import com.methyleneblue.camera.command.RealtimeCommand
import com.methyleneblue.camera.command.RecompileKernel
import com.methyleneblue.camera.command.TakePicture
import com.methyleneblue.camera.listener.TickListener
import com.methyleneblue.camera.obj.Realtime
import com.methyleneblue.camera.obj.raytrace.RayTraceMaterial
import com.methyleneblue.camera.test.BVHTest
import com.methyleneblue.camera.texture.TextureManager
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class Camera : JavaPlugin() {

    override fun onEnable() {
        // Plugin startup logic

        getCommand("cameracommand")?.setExecutor(CameraCommand())
        getCommand("takepicture")?.setExecutor(TakePicture())
        getCommand("recompilekernel")?.setExecutor(RecompileKernel())
        getCommand("realtime")?.setExecutor(RealtimeCommand())
        TextureManager.init()
//        JoclInterface.initialize(true)
        RayTraceMaterial.initialize()
        Realtime.initialize()

        Bukkit.getPluginManager().registerEvents(TickListener(), this)

//        Thread {
//            while (true) {
//                Realtime.tick()
//                Thread.onSpinWait()
//            }
//        }
    }

    companion object {

    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
