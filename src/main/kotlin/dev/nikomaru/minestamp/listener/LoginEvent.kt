/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.listener

import dev.nikomaru.minestamp.player.AbstractPlayerStampManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class LoginEvent :
    Listener,
    KoinComponent {
    // PlayerLoginEventはBAN・ホワイトリスト・満員で拒否されたログインでも発火するため、
    // 実際に参加したプレイヤーだけを読み込むようPlayerJoinEventを使う
    @EventHandler
    suspend fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        withContext(Dispatchers.IO) {
            val playerStampManager = get<AbstractPlayerStampManager>()
            with(playerStampManager) {
                init(player)
                load(player)
            }
            // 読み込み中にログアウトした場合、onQuitより後に書き込まれて残らないよう破棄する
            // （すぐに再ログインした場合は新しいセッションのデータを消さないよう、UUIDでオンラインか確認する）
            if (Bukkit.getPlayer(player.uniqueId) == null) {
                playerStampManager.unload(player.uniqueId)
            }
        }
    }

    // ログアウトしたプレイヤーのスタンプ（デコード済み画像を含む）をメモリから解放する
    // オフラインのプレイヤーはStampHandlerがストレージから読み取るため、ここで消しても問題ない
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        get<AbstractPlayerStampManager>().unload(event.player.uniqueId)
    }
}
