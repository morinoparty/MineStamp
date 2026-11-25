/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.api.handler

import dev.nikomaru.minestamp.config.PlayerDefaultEmojiConfigData
import dev.nikomaru.minestamp.font.CbdtEmojiFont
import dev.nikomaru.minestamp.font.EmojiFont
import dev.nikomaru.minestamp.player.AbstractPlayerStampManager
import dev.nikomaru.minestamp.stamp.Stamp
import kotlinx.coroutines.runBlocking
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import party.morino.mineauth.api.auth.Principal
import party.morino.mineauth.api.http.HttpError
import party.morino.mineauth.api.http.HttpStatus
import java.io.IOException
import java.util.Properties
import java.util.UUID
import java.util.logging.Logger

/**
 * サーバー再起動後に未ログイン（メモリ上にデータがない）のプレイヤーでも
 * GET /stamps が保存済みのスタンプを返すことを確認するテスト
 */
class StampHandlerOfflineTest {
    private val uuid: UUID = UUID.fromString("3c6d9e2a-0000-4000-8000-000000000abc")

    // 既定のデフォルトスタンプ（:cucumber: :thinking-face: :angry-face: :sleeping-face:）
    private val defaultCodes = PlayerDefaultEmojiConfigData().defaultEmoji

    private lateinit var stampManager: FakeStampManager

    @BeforeEach
    fun setUp() {
        stampManager = FakeStampManager()
        val emojiProperties = Properties().apply { resource("emoji.properties").use { load(it) } }
        val emojiFont = CbdtEmojiFont(resource("FluentEmojiColor-CBDT.ttf").use { it.readBytes() })
        // 本番と同じくKoinから絵文字定義・フォント・デフォルトスタンプ・スタンプ管理クラスを取得させる
        startKoin {
            modules(
                module {
                    single { emojiProperties }
                    single<EmojiFont> { emojiFont }
                    single { PlayerDefaultEmojiConfigData() }
                    single<AbstractPlayerStampManager> { stampManager }
                }
            )
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    @DisplayName("Offline player not loaded since restart gets their stored stamps plus defaults")
    fun offlinePlayerWithStoredStamps() {
        stampManager.stored[uuid] = listOf(":grinning-face-with-big-eyes:", ":cucumber:")

        val response = runBlocking { StampHandler().getOwnStamps(OfflineCaller(uuid)) }

        assertEquals(uuid.toString(), response.uuid)
        // 所持スタンプの後にデフォルトスタンプが続き、重複（:cucumber:）は1件にまとめる
        assertEquals(listOf(":grinning-face-with-big-eyes:") + defaultCodes, response.stamps.map { it.shortCode })
        // 読み取り専用なので、ログイン時にロードされるメモリ上のデータには書き込まない
        assertFalse(stampManager.playerEmoji.containsKey(uuid))
    }

    @Test
    @DisplayName("Offline player without stored data gets the default stamps")
    fun offlinePlayerWithoutStoredData() {
        val response = runBlocking { StampHandler().getOwnStamps(OfflineCaller(uuid)) }

        assertEquals(defaultCodes, response.stamps.map { it.shortCode })
    }

    @Test
    @DisplayName("Loaded data in memory is used without reading the storage")
    fun loadedPlayerUsesMemory() {
        stampManager.playerEmoji[uuid] = emptyList()
        stampManager.failure = IOException("storage must not be read")

        val response = runBlocking { StampHandler().getOwnStamps(OfflineCaller(uuid)) }

        assertEquals(defaultCodes, response.stamps.map { it.shortCode })
    }

    @Test
    @DisplayName("Storage failure is reported as 503 instead of default stamps")
    fun storageFailureIsServiceUnavailable() {
        stampManager.failure = IOException("storage is down")

        val error = assertThrows<HttpError> { runBlocking { StampHandler().getOwnStamps(OfflineCaller(uuid)) } }

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status)
        assertEquals("stamp_storage_unavailable", error.code)
    }

    private fun resource(name: String) =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "$name not found on classpath" }

    /** オフラインの呼び出し元。ハンドラーはuuidとonlinePlayerしか参照しない */
    private class OfflineCaller(
        override val uuid: UUID
    ) : Principal.User {
        override val onlinePlayer: Player? = null
        override val scopes: Set<String> = setOf("plugin")
        override val clientId: String = "test-client"
        override val offlinePlayer: OfflinePlayer
            get() = error("not used in this test")

        override fun hasPermission(permission: String): Boolean = false
    }

    /** ストレージをメモリ上のMapで置き換えたスタンプ管理クラス */
    private class FakeStampManager : AbstractPlayerStampManager() {
        val stored = mutableMapOf<UUID, List<String>>()
        var failure: Exception? = null

        override val logger: Logger = Logger.getLogger("StampHandlerOfflineTest")

        override fun readStoredShortCodes(uuid: UUID): List<String> {
            failure?.let { throw it }
            return stored[uuid] ?: emptyList()
        }

        override fun init(player: Player) = error("not used in this test")

        override fun load(player: Player) = error("not used in this test")

        override fun getPlayerStamp(player: Player): ArrayList<Stamp> = error("not used in this test")

        override fun addStamp(
            player: Player,
            stamp: Stamp
        ) = error("not used in this test")

        override fun removeStamp(
            player: Player,
            stamp: Stamp
        ) = error("not used in this test")

        override fun availableStamp(
            player: Player,
            stamp: Stamp
        ): Boolean = error("not used in this test")

        override fun loadAllPlayerData(): Map<UUID, List<String>> = error("not used in this test")

        override fun savePlayerData(
            uuid: UUID,
            shortCodes: List<String>
        ) = error("not used in this test")
    }
}