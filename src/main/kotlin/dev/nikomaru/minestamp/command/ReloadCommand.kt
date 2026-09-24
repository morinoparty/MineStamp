/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

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