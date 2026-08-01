package dev.nikomaru.minestamp.utils

import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.data.I18nData
import dev.nikomaru.minestamp.config.LocalConfig
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.MessageFormat
import java.util.*

object LangUtils: KoinComponent {
    val plugin: MineStamp by inject()
    fun CommandSender.sendI18nRichMessage(key: String, vararg args: Any) {
        val map = get<I18nData>().propertiesMap
        val locale = this.locale()
        val properties = map[locale] ?: map[get<I18nData>().serverDefaultLocale]!!
        val raw = properties.getProperty(key) ?: key
        val message = MessageFormat.format(raw, *args)
        sendRichMessage(message)
    }

    private fun CommandSender.locale(): String {
        return if (this is Player) {
            this.locale().toString()
        } else {
            get<I18nData>().serverDefaultLocale
        }
    }

    fun getI18nMessage(key: String, vararg args: Any): String {
        val locale = get<I18nData>().serverDefaultLocale
        val properties = get<I18nData>().propertiesMap[locale]!!
        return MessageFormat.format(properties.getProperty(key), *args)
    }

    fun loadLocale() {
        val langCode = get<LocalConfig>().lang
        val hashMap = hashMapOf<String, Properties>()
        val resourceList = listOf("ja_JP", "en_US")
        resourceList.forEach {
            val inputStream = plugin.javaClass.getResourceAsStream("/lang/$it.properties") ?: return@forEach
            val isr = InputStreamReader(inputStream, "UTF-8")
            val reader = BufferedReader(isr)
            val properties = Properties() //UTF-8で読み込む
            properties.load(reader)
            hashMap[it] = properties
        }
        val file = plugin.dataFolder.resolve("lang")
        file.mkdirs()
        val listOfLangFiles = file.listFiles()!!.filter { it.extension == "properties" }
        listOfLangFiles.forEach {
            val inputStream = it.inputStream()
            val isr = InputStreamReader(inputStream, "UTF-8")
            val reader = BufferedReader(isr)
            val properties = Properties() //UTF-8で読み込む
            properties.load(reader)
            hashMap[it.nameWithoutExtension] = properties
        }
        val locale = if (!hashMap.map { it.key }.contains(langCode)) {
            plugin.logger.warning("Language file for $langCode is not found. Using default language.")
            "en_US"
        } else {
            langCode
        }
        plugin.logger.info("loaded locale ${hashMap.map { it.key }}")
        plugin.logger.info("Default $locale")

        loadKoinModules(module {
            single { I18nData(locale, hashMap) }
        })
    }
}