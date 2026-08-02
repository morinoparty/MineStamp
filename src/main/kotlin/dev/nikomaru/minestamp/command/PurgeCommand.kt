package dev.nikomaru.minestamp.command

import dev.nikomaru.minestamp.config.Config
import dev.nikomaru.minestamp.player.AbstractPlayerStampManager
import dev.nikomaru.minestamp.stamp.StampManager
import dev.nikomaru.minestamp.utils.LangUtils.sendI18nRichMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * フォント更新（Noto emoji fallback削除, PR #148）で描画できなくなった絵文字を、
 * 全プレイヤーデータとrandom.jsonから完全に削除するOP用コマンド。
 * 通常起動時はメモリ上で除外しているだけでファイルには残っているため、
 * このコマンドで永続データからも取り除ける。
 */
@Command("minestamp")
class PurgeCommand: KoinComponent {

    @Command("purge")
    @CommandDescription("Permanently remove unrenderable emojis from all player data and random.json.")
    @Permission("minestamp.command.purge")
    suspend fun purge(sender: CommandSender) {
        sender.sendI18nRichMessage("minestamp.purge.start")
        withContext(Dispatchers.IO) {
            val playerStampManager = get<AbstractPlayerStampManager>()
            var removedEntries = 0
            var affectedPlayers = 0
            playerStampManager.loadAllPlayerData().toSortedMap().forEach { (uuid, shortCodes) ->
                val (removed, kept) = shortCodes.partition { StampManager.isUnrenderableEmoji(it) }
                if (removed.isEmpty()) return@forEach
                playerStampManager.savePlayerData(uuid, kept)
                affectedPlayers++
                val name = Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
                removed.forEach { code ->
                    removedEntries++
                    sender.sendI18nRichMessage("minestamp.purge.deleted-player", name, code)
                }
            }
            val removedRandom = Config.purgeRandomConfigFile()
            removedRandom.forEach { code ->
                sender.sendI18nRichMessage("minestamp.purge.deleted-random", code)
            }
            if (removedEntries == 0 && removedRandom.isEmpty()) {
                sender.sendI18nRichMessage("minestamp.purge.nothing")
            } else {
                sender.sendI18nRichMessage(
                    "minestamp.purge.result", removedEntries, affectedPlayers, removedRandom.size
                )
            }
        }
    }
}
