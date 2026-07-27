package dev.nikomaru.minestamp.player

import dev.nikomaru.minestamp.stamp.Stamp
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractPlayerStampManager {
    // MineAuth の HTTP スレッドからも読まれるため ConcurrentHashMap
    val playerEmoji: MutableMap<UUID, List<Stamp>> = ConcurrentHashMap()

    abstract fun init(player: Player)
    abstract fun load(player: Player)
    abstract fun getPlayerStamp(player: Player): ArrayList<Stamp>
    abstract fun addStamp(player: Player, stamp: Stamp)
    abstract fun removeStamp(player: Player, stamp: Stamp)
    abstract fun availableStamp(player: Player, stamp: Stamp): Boolean
}