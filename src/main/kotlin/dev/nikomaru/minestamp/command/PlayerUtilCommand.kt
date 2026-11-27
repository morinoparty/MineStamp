/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.command

import dev.nikomaru.minestamp.player.AbstractPlayerStampManager
import dev.nikomaru.minestamp.stamp.EmojiStamp
import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.stamp.name.EmojiNameRegistry
import dev.nikomaru.minestamp.utils.LangUtils
import dev.nikomaru.minestamp.utils.LangUtils.sendI18nRichMessage
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Default
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Command("minestamp")
class PlayerUtilCommand : KoinComponent {
    @Command("help")
    fun help(actor: CommandSender) {
        actor.sendI18nRichMessage("minestamp.help.header")
        actor.sendI18nRichMessage("minestamp.help.stamp")
        actor.sendI18nRichMessage("minestamp.help.list")
        actor.sendI18nRichMessage("minestamp.help.help")
        // 権限のあるコマンドだけを表示する
        if (actor.hasPermission("minestamp.command.advance")) {
            actor.sendI18nRichMessage("minestamp.help.advance")
        }
        if (actor.hasPermission("minestamp.command.publish.roulette")) {
            actor.sendI18nRichMessage("minestamp.help.publish-roulette")
        }
        if (actor.hasPermission("minestamp.command.publish.unique")) {
            actor.sendI18nRichMessage("minestamp.help.publish-unique")
        }
        if (actor.hasPermission("minestamp.command.publish.generate")) {
            actor.sendI18nRichMessage("minestamp.help.generate-keypair")
        }
        if (actor.hasPermission("minestamp.command.reload")) {
            actor.sendI18nRichMessage("minestamp.help.reload")
        }
        if (actor.hasPermission("minestamp.command.purge")) {
            actor.sendI18nRichMessage("minestamp.help.purge")
        }
        actor.sendI18nRichMessage("minestamp.help.url")
    }

    @Command("list [reset]")
    fun showEmojiList(
        actor: Player,
        @Argument("reset") @Default("4") resetCount: Int
    ) {
        actor.sendI18nRichMessage("minestamp.current-emoji-list")
        val playerStampManager: AbstractPlayerStampManager = get<AbstractPlayerStampManager>()
        val names = get<EmojiNameRegistry>()
        val miniMessage = MiniMessage.miniMessage()
        // よく使われる絵文字から順に並べ、画像スタンプは後ろにまとめる
        val byPopularity = compareBy<Stamp, String>(names.popularityComparator) { it.shortCode }
        val list = playerStampManager.getPlayerStamp(actor).sortedWith(byPopularity)
        // クリックで /stamp コマンドを入力欄に補完できるようにする
        val message =
            list.chunked(resetCount.coerceAtLeast(1)).joinToString("\n") { row ->
                row.joinToString("<gray> | </gray>") { stamp ->
                    // 名前はプレイヤーのロケールで選ぶ。名前に < などが含まれてもタグとして解釈されないようエスケープする
                    val name = names.name(stamp.shortCode, actor.locale(), LangUtils.serverLocale)
                    val text = miniMessage.escapeTags(name ?: stamp.shortCode)
                    val label = if (stamp is EmojiStamp) "${stamp.char} <aqua>$text</aqua>" else "<aqua>$text</aqua>"
                    // shortCodeはホバーに出し、名前がわからなくてもコマンドで使う値を確認できるようにする
                    "<click:suggest_command:'/stamp ${stamp.shortCode}'>" +
                        "<hover:show_text:'${stamp.shortCode}<newline>/stamp ${stamp.shortCode}'>$label</hover></click>"
                }
            }
        actor.sendRichMessage(message)
    }
}