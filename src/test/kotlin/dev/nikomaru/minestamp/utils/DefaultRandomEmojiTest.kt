package dev.nikomaru.minestamp.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties

/**
 * Verifies that every shortcode in default-random.json resolves via emoji.properties
 * and renders to a non-empty image with the bundled Fluent Emoji font.
 */
class DefaultRandomEmojiTest {

    private fun resource(name: String) =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "$name not found on classpath" }

    @Test
    fun everyDefaultRandomEntryRendersWithFluentEmojiFont() {
        val shortCodes = Json.parseToJsonElement(resource("default-random.json").use { it.readBytes().decodeToString() })
            .jsonObject.keys

        val emojiProperties = Properties().apply { resource("emoji.properties").use { load(it) } }

        val font = FluentEmojiFont(resource("FluentEmojiColor-CBDT.ttf").use { it.readBytes() })

        val failures = mutableListOf<String>()
        for (shortCode in shortCodes) {
            val codepointSpec = emojiProperties.getProperty(shortCode)
            if (codepointSpec == null) {
                failures += "$shortCode: missing in emoji.properties"
                continue
            }
            val image = font.getImage(codepointSpec)
            if (image == null) {
                failures += "$shortCode ($codepointSpec): no glyph image"
            } else if (image.width <= 0 || image.height <= 0) {
                failures += "$shortCode ($codepointSpec): empty image ${image.width}x${image.height}"
            } else if (!font.hasGlyph(codepointSpec)) {
                // random.jsonのサニタイズとタブ補完はhasGlyphに依存しているため、getImageとの整合を確認する
                failures += "$shortCode ($codepointSpec): renders but hasGlyph() returned false"
            }
        }

        assertTrue(failures.isEmpty()) { "${failures.size} of ${shortCodes.size} entries failed to render:\n" + failures.joinToString("\n") }
    }
}
