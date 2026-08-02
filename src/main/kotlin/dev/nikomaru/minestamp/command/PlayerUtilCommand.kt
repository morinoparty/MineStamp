package dev.nikomaru.minestamp.command

import dev.nikomaru.minestamp.player.AbstractPlayerStampManager
import dev.nikomaru.minestamp.stamp.EmojiStamp
import dev.nikomaru.minestamp.utils.LangUtils.sendI18nRichMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Default

@Command("minestamp")
class PlayerUtilCommand: KoinComponent {

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
    fun showEmojiList(actor: Player , @Argument("reset") @Default("4") resetCount: Int) {
        actor.sendI18nRichMessage("minestamp.current-emoji-list")
        val playerStampManager: AbstractPlayerStampManager = get<AbstractPlayerStampManager>()
        val list = playerStampManager.getPlayerStamp(actor)
        list.sortBy { it.shortCode }
        // クリックで /stamp コマンドを入力欄に補完できるようにする
        val message = list.chunked(resetCount.coerceAtLeast(1)).joinToString("\n") { row ->
            row.joinToString("<gray> | </gray>") { stamp ->
                val label =
                    if (stamp is EmojiStamp) "${stamp.char} <aqua>${stamp.shortCode}</aqua>"
                    else "<aqua>${stamp.shortCode}</aqua>"
                "<click:suggest_command:'/stamp ${stamp.shortCode}'><hover:show_text:'/stamp ${stamp.shortCode}'>$label</hover></click>"
            }
        }
        actor.sendRichMessage(message)
    }

}
