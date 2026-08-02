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
import dev.nikomaru.minestamp.utils.FluentEmojiFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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


open class MineStamp: SuspendingJavaPlugin(), KoinComponent {
    lateinit var plugin: JavaPlugin
    override suspend fun onEnableAsync() {
        logger.info("Is starting on Thread:${Thread.currentThread().name}/${Thread.currentThread().threadId()}/primaryThread=${Bukkit.isPrimaryThread()}")
        plugin = this
        setKoin()

        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdir()
        }
        // フォントロードは重いIOのため、コマンド登録と並行して実行する
        val emojiProperties = Properties()
        coroutineScope {
            val emojiFontDeferred = async(Dispatchers.IO) {
                val br = plugin.javaClass.classLoader.getResourceAsStream("emoji.properties")
                emojiProperties.load(br)
                val fontData = plugin.javaClass.classLoader.getResourceAsStream("FluentEmojiColor-CBDT.ttf")
                    ?.use { it.readBytes() }
                    ?: error("FluentEmojiColor-CBDT.ttf is not found in resources.")
                FluentEmojiFont(fontData)
            }
            logger.info("command setting")
            setCommand()

            // sanitizeRandomConfig（loadConfig内）がフォントに依存するため、Koin登録を待ってから先へ進む
            val emojiFont = emojiFontDeferred.await()
            loadKoinModules(module {
                single { emojiProperties }
                single { emojiFont }
            })
        }
        logger.info("config setting")
        Config.loadConfig()
        logger.info("stamp manager setting")
        val stampManager: AbstractPlayerStampManager =
            if (get<LocalConfig>().type == FileType.S3) {
                S3PlayerStampManager()
            } else {
                LocalPlayerStampManager()
            }

        loadKoinModules(module {
            single<AbstractPlayerStampManager> { stampManager }
        })
        logger.info("listener setting")
        setListener()
        logger.info("mineauth setting")
        setMineAuth()
    }

    private fun setKoin() {
        val appModule = module {
            single<MineStamp> { this@MineStamp }
        }

        GlobalContext.getOrNull() ?: GlobalContext.startKoin {
            printLogger()
            modules(appModule)
        }
    }

    private fun setCommand() {
        val commandManager = LegacyPaperCommandManager.createNative(
            this,
            ExecutionCoordinator.simpleCoordinator()
        )


        commandManager.settings().set(ManagerSetting.ALLOW_UNSAFE_REGISTRATION, true)

        commandManager.parserRegistry().registerParser(StampArgumentParser.stampParser())

        val annotationParser = AnnotationParser(commandManager, CommandSender::class.java)
        annotationParser.installCoroutineSupport()

        with(annotationParser) {
            parse(
                ColorEmojiCommand(), PublishTicketCommand(), ReloadCommand(), PlayerUtilCommand(), PurgeCommand()
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