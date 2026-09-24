/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.stamp

import dev.nikomaru.minestamp.utils.FluentEmojiFont
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class EmojiStamp(
    shortCode: String
) : Stamp(shortCode),
    KoinComponent {
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
            val emojiImage = emojiFont.getImage(original)
            if (emojiImage != null) {
                image = emojiImage
                hasImage = true
            } else {
                plugin.logger.warning("emoji image for $shortCode ($original) is not found.")
            }
        }
    }
}