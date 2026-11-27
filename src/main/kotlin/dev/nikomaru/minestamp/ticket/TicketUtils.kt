/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.ticket

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.stamp.EmojiStamp
import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.stamp.StampManager
import dev.nikomaru.minestamp.stamp.name.EmojiNameRegistry
import dev.nikomaru.minestamp.utils.LangUtils
import dev.nikomaru.minestamp.utils.LangUtils.i18nComponent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

object TicketUtils : KoinComponent {
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

    fun getUniqueTicket(
        algorithm: Algorithm,
        stamp: Stamp
    ): ItemStack {
        val ticket = ItemStack(Material.PAPER)
        val jwt =
            JWT
                .create()
                .withIssuer("minestamp")
                .withClaim("type", "unique")
                .withClaim("shortCode", stamp.shortCode)
                .sign(algorithm)
        val meta = ticket.itemMeta
        val type =
            if (stamp is EmojiStamp) {
                i18nComponent(
                    "minestamp.type-emoji"
                )
            } else {
                i18nComponent("minestamp.type-image")
            }
        // アイテム名・loreはクライアントで翻訳されないため、名前はサーバーの既定ロケールで選ぶ
        val names = get<EmojiNameRegistry>()
        val locale = LangUtils.serverLocale
        val name = names.name(stamp.shortCode, locale, locale)
        // 例: "🍕 ピザ"。名前がなければ従来どおり絵文字のみ
        val preview = if (stamp is EmojiStamp) listOfNotNull(stamp.char, name).joinToString(" ") else ""

        meta.displayName(i18nComponent("minestamp.emoji-ticket", preview))
        meta.lore(
            listOf(
                i18nComponent("minestamp.get-stamp-by-right-click"),
                i18nComponent("minestamp.stamp-type", type),
                // 例: "ピザ (:pizza:)"。shortCodeも残して管理者がコマンドで使えるようにする
                i18nComponent("minestamp.stamp-shortcode", names.label(stamp.shortCode, locale, locale))
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