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
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLoginEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class LoginEvent :
    Listener,
    KoinComponent {
    @EventHandler
    suspend fun onLogin(event: PlayerLoginEvent) {
        withContext(Dispatchers.IO) {
            val playerStampManager = get<AbstractPlayerStampManager>()
            val player = event.player
            with(playerStampManager) {
                init(player)
                load(player)
            }
        }
    }
}