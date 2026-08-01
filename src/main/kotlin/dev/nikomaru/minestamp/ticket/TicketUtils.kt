package dev.nikomaru.minestamp.ticket

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.stamp.EmojiStamp
import dev.nikomaru.minestamp.stamp.StampManager
import dev.nikomaru.minestamp.utils.LangUtils.getI18nMessage
import dev.nikomaru.minestamp.utils.Utils.mm
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object TicketUtils: KoinComponent {
    val plugin: MineStamp by inject()

    fun getRouletteTicket(jwt: String): ItemStack {
        val ticket = ItemStack(Material.PAPER)
        val meta = ticket.itemMeta
        meta.displayName(mm.deserialize(getI18nMessage("roulette-ticket")))
        meta.lore(listOf(mm.deserialize(getI18nMessage("generate-ticket-by-right-click"))))
        val namespaceKey = NamespacedKey(plugin, "ticket")
        meta.persistentDataContainer.set(namespaceKey, PersistentDataType.STRING, jwt)
        ticket.itemMeta = meta
        return ticket
    }

    fun getUniqueTicket(algorithm: Algorithm, stamp: Stamp): ItemStack {
        val ticket = ItemStack(Material.PAPER)
        val jwt =
            JWT.create().withIssuer("minestamp").withClaim("type", "unique").withClaim("shortCode", stamp.shortCode)
                .sign(algorithm)
        val meta = ticket.itemMeta
        lateinit var type: String
        lateinit var preview: String
        if (stamp is EmojiStamp) {
            type = getI18nMessage("type-emoji")
            preview = stamp.char
        } else {
            type = getI18nMessage("type-image")
            preview = ""
        }

        meta.displayName(mm.deserialize(getI18nMessage("emoji-ticket", preview)))
        meta.lore(
            listOf(
                mm.deserialize(getI18nMessage("get-stamp-by-right-click")),
                mm.deserialize(getI18nMessage("stamp-type", type)),
                mm.deserialize(getI18nMessage("stamp-shortcode", stamp.shortCode))
            )
        )
        val namespaceKey = NamespacedKey(plugin, "ticket")
        meta.persistentDataContainer.set(namespaceKey, PersistentDataType.STRING, jwt)
        ticket.itemMeta = meta
        return ticket
    }

    fun getRandomTicket(algorithm: Algorithm): ItemStack? {
        val randomStamp = StampManager.getRandomStamp() ?: return null
        return getUniqueTicket(algorithm, randomStamp)
    }
}