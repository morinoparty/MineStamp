package dev.nikomaru.minestamp.listener

import dev.nikomaru.minestamp.player.AbstractPlayerStampManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class LoginEvent: Listener, KoinComponent {

    @EventHandler
    suspend fun onLogin(event: PlayerJoinEvent) {
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