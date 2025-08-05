package com.methyleneblue.camera.command

import com.methyleneblue.camera.raytracepack.bvh.jocl.JoclInterface
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class RecompileKernel: CommandExecutor {
    override fun onCommand(
        p0: CommandSender,
        p1: Command,
        p2: String,
        p3: Array<out String>
    ): Boolean {
        JoclInterface.preCompilePrograms()
        p0.sendMessage("Kernel recompiled.")
        return true
    }
}