package dev.nikomaru.minestamp.player

import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.stamp.StampManager
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

abstract class AbstractPlayerStampManager {
    // MineAuth の HTTP スレッドからも読まれるため ConcurrentHashMap
    val playerEmoji: MutableMap<UUID, List<Stamp>> = ConcurrentHashMap()

    // 保存用の生shortCode。解決できない絵文字もここには残し、addStamp等の上書き保存で消さない
    protected val playerShortCodes: MutableMap<UUID, List<String>> = ConcurrentHashMap()

    protected fun store(player: Player, shortCodes: List<String>, logger: Logger) {
        playerShortCodes[player.uniqueId] = shortCodes
        playerEmoji[player.uniqueId] = shortCodes.mapNotNull { code ->
            val stamp = runCatching { StampManager.getStamp(code) }.getOrNull()
            if (stamp == null) {
                logger.warning("stamp $code owned by ${player.name} cannot be resolved; keeping it in data but it will be unusable.")
            }
            stamp
        }
    }

    abstract fun init(player: Player)
    abstract fun load(player: Player)
    abstract fun getPlayerStamp(player: Player): ArrayList<Stamp>
    abstract fun addStamp(player: Player, stamp: Stamp)
    abstract fun removeStamp(player: Player, stamp: Stamp)
    abstract fun availableStamp(player: Player, stamp: Stamp): Boolean
}