package dev.nikomaru.minestamp.utils

import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.config.LocalConfig
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore
import net.kyori.adventure.translation.GlobalTranslator
import net.kyori.adventure.translation.Translator
import org.bukkit.command.CommandSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.io.File
import java.util.Locale
import java.util.Properties

/**
 * MiniMessageTranslationStoreをGlobalTranslatorに登録するAdventureベースの多言語化。
 * Component.translatableは送信時に受信者のロケールで解決されるため、
 * プレイヤーごとの言語で表示される。引数は翻訳文字列側の<arg:0>タグで参照する。
 */
object LangUtils: KoinComponent {
    val plugin: MineStamp by inject()

    private val bundledLocales = listOf("ja_JP", "en_US")
    private var store: MiniMessageTranslationStore? = null
    private var defaultLocale: Locale = Locale.US

    fun CommandSender.sendI18nRichMessage(key: String, vararg args: Any) {
        sendMessage(Component.translatable(key, args.map { it.toComponent() }))
    }

    /**
     * アイテム名・loreなど、クライアント側で翻訳されない場所向けに
     * サーバー既定ロケールで解決済みのComponentを返す。
     */
    fun i18nComponent(key: String, vararg args: Any): Component =
        GlobalTranslator.render(Component.translatable(key, args.map { it.toComponent() }), defaultLocale)

    private fun Any.toComponent(): Component =
        if (this is ComponentLike) this.asComponent() else Component.text(this.toString())

    fun loadLocale() {
        // リロード時は古いストアを外してから作り直す（同一ストアへの再登録は重複キーで失敗するため）
        store?.let { GlobalTranslator.translator().removeSource(it) }
        defaultLocale = Translator.parseLocale(get<LocalConfig>().lang) ?: Locale.US
        val newStore = MiniMessageTranslationStore.create(Key.key("minestamp:translations"))
        newStore.defaultLocale(defaultLocale)

        val langDir = plugin.dataFolder.resolve("lang")
        langDir.mkdirs()
        extractBundledLocales(langDir)

        val loaded = mutableListOf<String>()
        langDir.listFiles()?.filter { it.extension == "properties" }?.forEach { file ->
            val locale = Translator.parseLocale(file.nameWithoutExtension) ?: run {
                plugin.logger.warning("Cannot parse locale from lang file ${file.name}. Skipping.")
                return@forEach
            }
            val properties = Properties()
            file.inputStream().use { properties.load(it.reader(Charsets.UTF_8)) }
            properties.forEach { (k, v) -> newStore.register(k.toString(), locale, v.toString()) }
            loaded += file.nameWithoutExtension
        }
        GlobalTranslator.translator().addSource(newStore)
        store = newStore
        plugin.logger.info("Loaded locales $loaded (default: $defaultLocale)")
    }

    private fun extractBundledLocales(langDir: File) {
        bundledLocales.forEach { name ->
            val file = langDir.resolve("$name.properties")
            if (file.exists()) {
                // 旧フォーマット（minestamp.プレフィックスなし・MessageFormat引数）のファイルは
                // 現在のキーを一切含まないため、退避して同梱リソースで置き換える
                val properties = Properties()
                file.inputStream().use { properties.load(it.reader(Charsets.UTF_8)) }
                if (properties.stringPropertyNames().any { it.startsWith("minestamp.") }) return@forEach
                val backup = langDir.resolve("$name.properties.old")
                file.copyTo(backup, overwrite = true)
                plugin.logger.warning(
                    "lang/$name.properties uses the legacy format. Backed it up to $name.properties.old and replaced it."
                )
            }
            plugin.javaClass.getResourceAsStream("/lang/$name.properties")?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }
    }
}
