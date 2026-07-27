package dev.nikomaru.minestamp.command.parser

import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.data.ImageListData
import dev.nikomaru.minestamp.player.AbstractPlayerStampManager
import dev.nikomaru.minestamp.stamp.EmojiStamp
import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.stamp.StampManager
import dev.nikomaru.minestamp.utils.FluentEmojiFont
import org.bukkit.command.CommandSender
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.context.CommandInput
import org.incendo.cloud.parser.ArgumentParseResult
import org.incendo.cloud.parser.ArgumentParser
import org.incendo.cloud.parser.ParserDescriptor
import org.incendo.cloud.suggestion.BlockingSuggestionProvider
import org.incendo.cloud.suggestion.Suggestion
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.util.Properties


class StampArgumentParser<CommandSender> : ArgumentParser<CommandSender, Stamp>, BlockingSuggestionProvider<CommandSender>, KoinComponent {
    val plugin: MineStamp by inject()
    private val emojiProperties: Properties by inject()
    private val emojiFont: FluentEmojiFont by inject()

    // フォントで実際に描画できる絵文字のみをタブ補完に出す。初回参照時に一度だけ計算する
    private val renderableEmojiSuggestions: List<String> by lazy {
        emojiProperties.stringPropertyNames()
            .filter { key -> emojiFont.hasGlyph(emojiProperties.getProperty(key) ?: "") }
            .map { key -> toEmojiChar(emojiProperties.getProperty(key)) + key }
    }

    companion object {
        fun stampParser(): ParserDescriptor<CommandSender, Stamp> {
            return ParserDescriptor.of(StampArgumentParser(), Stamp::class.java)
        }

        /**
         * emoji.properties の値 (スペース区切りの16進コードポイント) から実際の絵文字文字列を生成する。
         * 例: "1F634" -> "😴"
         */
        private fun toEmojiChar(codePoints: String?): String {
            if (codePoints.isNullOrBlank()) return ""
            return runCatching {
                codePoints.trim().split(" ")
                    .flatMap { Character.toChars(Integer.parseInt(it, 16)).toList() }
                    .joinToString("")
            }.getOrDefault("")
        }

        /**
         * タブ補完で先頭に付与した絵文字文字を取り除き、shortCode を取り出す。
         * ":sleeping-face:" と "😴:sleeping-face:" の両方を受理する。
         */
        fun stripEmojiPrefix(input: String): String {
            if (input.startsWith(":") || input.startsWith("!") || input.startsWith("&")) return input
            val index = input.indexOf(':')
            return if (index > 0) input.substring(index) else input
        }
    }

    override fun suggestions(
        context: CommandContext<CommandSender>,
        input: CommandInput
    ): Iterable<Suggestion> {
        val sender = context.sender() as org.bukkit.command.CommandSender

        val candidates: List<String> = if (sender.hasPermission("minestamp.advanced")) {
            val images = get<ImageListData>().list.map { "!$it" }
            images + renderableEmojiSuggestions
        } else if (sender is org.bukkit.entity.Player) {
            get<AbstractPlayerStampManager>().getPlayerStamp(sender).map { stamp ->
                if (stamp is EmojiStamp) stamp.char + stamp.shortCode else stamp.shortCode
            }
        } else {
            emptyList()
        }

        // cloud の既定フィルタは前方一致相当のため、名前の一部 ("sleeping" 等) でも
        // 候補が出るようにサーバー側で部分一致 (contains) フィルタを行う。
        // クライアントは送られた候補をそのまま表示するので、ここで制御できる。
        val query = input.lastRemainingToken().lowercase()
        return candidates
            .filter { query.isEmpty() || it.lowercase().contains(query) }
            .map { Suggestion.suggestion(it) }
    }


    override fun parse(
        commandContext: CommandContext<CommandSender & Any>,
        commandInput: CommandInput
    ): ArgumentParseResult<Stamp> {
        val code = stripEmojiPrefix(commandInput.readString())
        val stamp = StampManager.getStamp(code)
        if (stamp == null) {
            return ArgumentParseResult.failure(IllegalArgumentException("Stamp not found"))
        }
        return ArgumentParseResult.success(stamp)
    }
}
