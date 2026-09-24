/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.utils

import dev.nikomaru.minestamp.font.CbdtEmojiFont
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
    fun everyDefaultRandomEntryRendersWithCbdtEmojiFont() {
        val shortCodes =
            Json
                .parseToJsonElement(resource("default-random.json").use { it.readBytes().decodeToString() })
                .jsonObject.keys

        val emojiProperties = Properties().apply { resource("emoji.properties").use { load(it) } }

        val font = CbdtEmojiFont(resource("FluentEmojiColor-CBDT.ttf").use { it.readBytes() })

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

        assertTrue(failures.isEmpty()) {
            "${failures.size} of ${shortCodes.size} entries failed to render:\n" +
                failures.joinToString("\n")
        }
    }
}