package dev.nikomaru.minestamp.command

import dev.nikomaru.minestamp.config.Config
import dev.nikomaru.minestamp.utils.LangUtils.sendI18nRichMessage
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission

@Command("minestamp")
class ReloadCommand {

    @Command("reload")
    @Permission("minestamp.command.reload")
    suspend fun reload(sender: CommandSender) {
        Config.loadConfig()
        sender.sendI18nRichMessage("minestamp.reloaded-config")
    }
}