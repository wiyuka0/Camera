package com.methyleneblue.camera

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

class BlockUpdateListener: Listener{

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        CameraManager.update()
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        CameraManager.update()
    }
}