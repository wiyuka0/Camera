package com.methyleneblue.camera.command

import com.methyleneblue.camera.obj.Realtime
import com.methyleneblue.camera.obj.Realtime.RealtimeData
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class RealtimeCommand: CommandExecutor {
    override fun onCommand(
        p0: CommandSender,
        p1: Command,
        p2: String,
        p3: Array<out String>
    ): Boolean {

        Realtime.addMember(p0 as Player)
        return true
    }
}