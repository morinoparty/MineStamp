package dev.nikomaru.minestamp.ticket

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.stamp.EmojiStamp
import dev.nikomaru.minestamp.stamp.StampManager
import dev.nikomaru.minestamp.utils.LangUtils.i18nComponent
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
        meta.displayName(i18nComponent("minestamp.roulette-ticket"))
        meta.lore(listOf(i18nComponent("minestamp.generate-ticket-by-right-click")))
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
        val type = if (stamp is EmojiStamp) i18nComponent("minestamp.type-emoji") else i18nComponent("minestamp.type-image")
        val preview = if (stamp is EmojiStamp) stamp.char else ""

        meta.displayName(i18nComponent("minestamp.emoji-ticket", preview))
        meta.lore(
            listOf(
                i18nComponent("minestamp.get-stamp-by-right-click"),
                i18nComponent("minestamp.stamp-type", type),
                i18nComponent("minestamp.stamp-shortcode", stamp.shortCode)
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