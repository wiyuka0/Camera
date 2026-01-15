package com.methyleneblue.camera.listener

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import com.methyleneblue.camera.obj.Realtime
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class TickListener: Listener {

    @EventHandler
    fun onTick(event: ServerTickEndEvent){
        Realtime.tick()
    }
}