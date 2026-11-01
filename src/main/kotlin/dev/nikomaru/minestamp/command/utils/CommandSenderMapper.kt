/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.command.utils

import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.ComponentLike
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.incendo.cloud.SenderMapper

class CommandSenderMapper : SenderMapper<CommandSourceStack, CommandSender> {
    override fun map(source: CommandSourceStack): CommandSender = source.sender

    override fun reverse(sender: CommandSender): CommandSourceStack {
        return object : CommandSourceStack {
            override fun getLocation(): Location {
                if (sender is Entity) {
                    return sender.location
                }
                val worlds = Bukkit.getWorlds()
                return Location(if (worlds.isEmpty()) null else worlds.first(), 0.0, 0.0, 0.0) // Best effort lol
            }

            override fun getSender(): CommandSender = sender

            override fun getExecutor(): Entity? = sender as? Entity

            // Paper 26.2 で追加された抽象メソッド。実行者がプレイヤー/エンティティでない場合はコマンド例外を投げる
            override fun getPlayerOrThrow(): Player =
                sender as? Player ?: throw SimpleCommandExceptionType(LiteralMessage("A player is required")).create()

            override fun getEntityOrThrow(): Entity =
                sender as? Entity ?: throw SimpleCommandExceptionType(LiteralMessage("An entity is required")).create()

            override fun withLocation(location: Location): CommandSourceStack = sender as CommandSourceStack

            override fun withExecutor(executor: Entity): CommandSourceStack = sender as CommandSourceStack

            // Paper 26.3 で追加された抽象メソッド。実行者がエンティティならそちらへ、そうでなければ送信者へ返信する
            override fun sendReply(message: ComponentLike) {
                (getExecutor() ?: sender).sendMessage(message)
            }

            // 逆変換したスタックには管理者・コンソールへの通知経路がないため、送信者への通知のみ行う
            override fun sendSuccess(
                message: ComponentLike,
                allowInformingAdmins: Boolean
            ) {
                sender.sendMessage(message)
            }

            // 失敗メッセージも送信者へそのまま送る
            override fun sendFailure(message: ComponentLike) {
                sender.sendMessage(message)
            }
        }
    }
}