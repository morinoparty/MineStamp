/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.registerSuspendingEvents
import dev.nikomaru.minestamp.api.MineAuthIntegration
import dev.nikomaru.minestamp.command.ColorEmojiCommand
import dev.nikomaru.minestamp.command.PlayerUtilCommand
import dev.nikomaru.minestamp.command.PublishTicketCommand
import dev.nikomaru.minestamp.command.PurgeCommand
import dev.nikomaru.minestamp.command.ReloadCommand
import dev.nikomaru.minestamp.command.parser.StampArgumentParser
import dev.nikomaru.minestamp.config.Config
import dev.nikomaru.minestamp.config.FileType
import dev.nikomaru.minestamp.config.LocalConfig
import dev.nikomaru.minestamp.listener.LoginEvent
import dev.nikomaru.minestamp.listener.TicketInteractEvent
import dev.nikomaru.minestamp.player.AbstractPlayerStampManager
import dev.nikomaru.minestamp.player.LocalPlayerStampManager
import dev.nikomaru.minestamp.player.S3PlayerStampManager
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.kotlin.coroutines.annotations.installCoroutineSupport
import org.incendo.cloud.paper.LegacyPaperCommandManager
import org.incendo.cloud.setting.ManagerSetting
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.util.*

open class MineStamp :
    SuspendingJavaPlugin(),
    KoinComponent {
    lateinit var plugin: JavaPlugin

    override suspend fun onEnableAsync() {
        logger.info(
            "Is starting on Thread:${Thread.currentThread().name}/${Thread.currentThread().threadId()}/primaryThread=${Bukkit.isPrimaryThread()}"
        )
        plugin = this
        setKoin()

        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdir()
        }
        val emojiProperties =
            Properties().apply {
                plugin.javaClass.classLoader
                    .getResourceAsStream("emoji.properties")
                    .use { load(it) }
            }
        loadKoinModules(module { single { emojiProperties } })
        logger.info("command setting")
        setCommand()
        logger.info("config setting")
        Config.loadConfig()
        logger.info("stamp manager setting")
        val stampManager: AbstractPlayerStampManager =
            if (get<LocalConfig>().type == FileType.S3) {
                S3PlayerStampManager()
            } else {
                LocalPlayerStampManager()
            }

        loadKoinModules(
            module {
                single<AbstractPlayerStampManager> { stampManager }
            }
        )
        logger.info("listener setting")
        setListener()
        logger.info("mineauth setting")
        setMineAuth()
    }

    private fun setKoin() {
        val appModule =
            module {
                single<MineStamp> { this@MineStamp }
            }

        GlobalContext.getOrNull() ?: GlobalContext.startKoin {
            printLogger()
            modules(appModule)
        }
    }

    private fun setCommand() {
        val commandManager =
            LegacyPaperCommandManager.createNative(
                this,
                ExecutionCoordinator.simpleCoordinator()
            )

        commandManager.settings().set(ManagerSetting.ALLOW_UNSAFE_REGISTRATION, true)

        commandManager.parserRegistry().registerParser(StampArgumentParser.stampParser())

        val annotationParser = AnnotationParser(commandManager, CommandSender::class.java)
        annotationParser.installCoroutineSupport()

        with(annotationParser) {
            parse(
                ColorEmojiCommand(),
                PublishTicketCommand(),
                ReloadCommand(),
                PlayerUtilCommand(),
                PurgeCommand()
            )
        }
    }

    private fun setListener() {
        server.pluginManager.registerSuspendingEvents(LoginEvent(), this)
        server.pluginManager.registerSuspendingEvents(TicketInteractEvent(), this)
    }

    private fun setMineAuth() {
        // Bukkitレベルの存在確認のみ行う（MineAuthのAPIクラスには触れない）
        // MineAuth不在時にAPIクラスを解決するとNoClassDefFoundErrorになるため、
        // API利用コードはMineAuthIntegrationに隔離している
        if (server.pluginManager.getPlugin("MineAuth") != null) {
            MineAuthIntegration(this).register()
        } else {
            logger.info("MineAuth not found - HTTP endpoints disabled")
        }
    }
}