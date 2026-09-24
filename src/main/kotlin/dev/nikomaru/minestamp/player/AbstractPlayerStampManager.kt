/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

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

    protected fun store(
        player: Player,
        shortCodes: List<String>,
        logger: Logger
    ) {
        playerShortCodes[player.uniqueId] = shortCodes
        playerEmoji[player.uniqueId] =
            shortCodes.mapNotNull { code ->
                val stamp = runCatching { StampManager.getStamp(code) }.getOrNull()
                if (stamp == null) {
                    logger.warning(
                        "stamp $code owned by ${player.name} cannot be resolved; keeping it in data but it will be unusable."
                    )
                }
                stamp
            }
    }

    abstract fun init(player: Player)

    abstract fun load(player: Player)

    abstract fun getPlayerStamp(player: Player): ArrayList<Stamp>

    abstract fun addStamp(
        player: Player,
        stamp: Stamp
    )

    abstract fun removeStamp(
        player: Player,
        stamp: Stamp
    )

    abstract fun availableStamp(
        player: Player,
        stamp: Stamp
    ): Boolean

    /** 保存済みの全プレイヤーデータ（生shortCode）を読み込む。purge等の全探索用 */
    abstract fun loadAllPlayerData(): Map<UUID, List<String>>

    /** 指定プレイヤーの所持スタンプを保存する。オンラインならメモリ上のデータも更新する */
    abstract fun savePlayerData(
        uuid: UUID,
        shortCodes: List<String>
    )
}