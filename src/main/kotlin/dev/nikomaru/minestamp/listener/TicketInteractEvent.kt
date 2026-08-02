package dev.nikomaru.minestamp.listener

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.player.AbstractPlayerStampManager
import dev.nikomaru.minestamp.stamp.StampManager
import dev.nikomaru.minestamp.utils.LangUtils.sendI18nRichMessage
import dev.nikomaru.minestamp.ticket.RSAUtils
import dev.nikomaru.minestamp.ticket.TicketUtils
import kotlinx.coroutines.delay
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataType
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.util.*


class TicketInteractEvent: Listener, KoinComponent {
    val plugin: MineStamp by inject()

    private var rejectInteract = hashMapOf<UUID, Boolean>()

    @EventHandler
    suspend fun onTicketInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        if (rejectInteract[event.player.uniqueId] == true) {
            event.isCancelled = true
            return
        }
        if (item.type != Material.PAPER) {
            return
        }
        if (!event.action.isRightClick) {
            return
        }
        if (item.itemMeta.persistentDataContainer.isEmpty) {
            return
        }
        val pdc = item.itemMeta.persistentDataContainer
        val namespaceKey = NamespacedKey(plugin, "ticket")
        val jwt = pdc.get(namespaceKey, PersistentDataType.STRING) ?: return
        val player = event.player
        when (JWT.decode(jwt).claims["type"]?.asString()) {
            "roulette" -> {
                item.amount -= 1
                event.hand?.let { player.inventory.setItem(it, item) }
                val rsaKey = RSAUtils.getRSAKeyPair() ?: run {
                    player.sendI18nRichMessage("minestamp.not-found-keypair-need-report-admin")
                    return
                }
                val algorithm = Algorithm.RSA256(rsaKey.second, rsaKey.first)
                val randomTicket = TicketUtils.getRandomTicket(algorithm) ?: run {
                    player.sendI18nRichMessage("minestamp.not-found-stamp-need-report-admin")
                    return
                }
                player.inventory.addItem(randomTicket)
            }
            "unique" -> {
                val playerStampManager = get<AbstractPlayerStampManager>()
                val shortCode = JWT.decode(jwt).claims["shortCode"]?.asString() ?: return
                val stamp = StampManager.getStamp(shortCode) ?: return
                if(playerStampManager.availableStamp(player, stamp)) {
                    player.sendI18nRichMessage("minestamp.already-haven")
                    return
                }
                item.amount -= 1
                event.hand?.let { player.inventory.setItem(it, item) }
                playerStampManager.addStamp(player, stamp)
                player.sendI18nRichMessage("minestamp.available-stamp", stamp.shortCode)
            }
            else -> {
                player.sendI18nRichMessage("minestamp.invalid-ticket")
            }
        }
        rejectInteract[player.uniqueId] = true
        delay(50)
        rejectInteract.remove(player.uniqueId)
    }
}