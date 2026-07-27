package dev.nikomaru.minestamp.stamp

import dev.nikomaru.minestamp.utils.FluentEmojiFont
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.awt.image.BufferedImage
import java.util.*
import javax.imageio.ImageIO

class EmojiStamp(shortCode: String): Stamp(shortCode), KoinComponent {
    private val emojiProperties: Properties by inject()
    private val emojiFont: FluentEmojiFont by inject()
    var char: String

    /** false if no image could be resolved for this emoji. */
    var hasImage: Boolean = false
        private set

    init {
        val original = emojiProperties.getProperty(shortCode)
        if (original == null) {
            char = ""
            plugin.logger.warning("emoji definition for $shortCode is not found.")
        } else {
            val unicodePoints = original.split(" ")
            val chars = unicodePoints.flatMap { Character.toChars(Integer.parseInt(it, 16)).toList() }.toCharArray()
            char = chars.joinToString("")
            val emojiImage = emojiFont.getImage(original) ?: loadFallbackImage(original)
            if (emojiImage != null) {
                image = emojiImage
                hasImage = true
            } else {
                plugin.logger.warning("emoji image for $shortCode ($original) is not found.")
            }
        }
    }

    /** Fluent Emoji does not ship multi-person emojis; fall back to bundled Noto Emoji PNGs. */
    private fun loadFallbackImage(unicodeSpec: String): BufferedImage? {
        val unicode = unicodeSpec.lowercase().replace(" ", "_")
        val inputStream = plugin.javaClass.classLoader
            .getResourceAsStream("noto-emoji-fallback/emoji_u${unicode}.png") ?: return null
        return inputStream.use { runCatching { ImageIO.read(it) }.getOrNull() }
    }
}
