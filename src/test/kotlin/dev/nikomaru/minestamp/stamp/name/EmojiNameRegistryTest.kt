/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.stamp.name

import dev.nikomaru.minestamp.api.model.StampData
import dev.nikomaru.minestamp.api.model.StampType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.Properties

/**
 * 同梱の emoji-names.json と、名前・並び順の引き方を確認するテスト
 */
class EmojiNameRegistryTest {
    private val names = resource(EmojiNameRegistry.RESOURCE_NAME).use { EmojiNameRegistry.load(it) }

    @Test
    @DisplayName("Every emoji.properties entry has a name and orders are unique")
    fun coversEmojiProperties() {
        val shortCodes = Properties().apply { resource("emoji.properties").use { load(it) } }.stringPropertyNames()

        // emoji.properties を更新したのに対応表を再生成し忘れた場合に気付けるようにする
        val missing = shortCodes.filter { names[it] == null }
        assertTrue(missing.isEmpty()) { "regenerate emoji-names.json (scripts/emoji-names): missing $missing" }
        assertEquals(shortCodes.size, names.size)
        // 並び順は 0 から件数-1 までの一意な値
        assertEquals((0 until names.size).toSet(), shortCodes.map { names[it]!!.order }.toSet())
    }

    @Test
    @DisplayName("Name follows the locale and falls back to the server locale")
    fun nameByLocale() {
        assertEquals("ピザ", names.name(":pizza:", Locale.JAPAN, Locale.US))
        assertEquals("pizza", names.name(":pizza:", Locale.US, Locale.JAPAN))
        // 未対応の言語はサーバーの既定ロケールで選ぶ
        assertEquals("ピザ", names.name(":pizza:", Locale.GERMANY, Locale.JAPAN))
        assertEquals("ピザ (:pizza:)", names.label(":pizza:", Locale.JAPAN, Locale.US))
        // 画像スタンプは名前を持たず、shortCodeのまま表示する
        assertNull(names.name("!logo.png", Locale.JAPAN, Locale.US))
        assertEquals("!logo.png", names.label("!logo.png", Locale.JAPAN, Locale.US))
    }

    @Test
    @DisplayName("StampData carries emoji info and sorts emoji by popularity before images")
    fun stampDataByPopularity() {
        val stamps = listOf("!logo.png", ":cucumber:", ":thinking-face:").map { StampData.of(it, names) }

        assertEquals(
            listOf(":thinking-face:", ":cucumber:", "!logo.png"),
            stamps.sortedWith(StampData.POPULARITY_ORDER).map { it.shortCode }
        )
        val image = stamps.first()
        assertEquals(StampType.IMAGE, image.type)
        assertNull(image.emoji)
        assertNull(image.sortOrder)
        val cucumber = stamps[1]
        assertEquals(StampType.EMOJI, cucumber.type)
        assertEquals("🥒", cucumber.emoji)
        assertEquals("キュウリ", cucumber.nameJa)
        assertEquals("cucumber", cucumber.nameEn)
    }

    private fun resource(name: String) =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "$name not found on classpath" }
}