/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

/**
 * ローカル保存のプレイヤーデータを読み取り専用で読み込めることを確認するテスト
 */
class LocalPlayerStampManagerReadTest {
    private val uuid: UUID = UUID.fromString("3c6d9e2a-0000-4000-8000-000000000abc")

    @Test
    @DisplayName("Stored short codes are read as-is")
    fun readsStoredShortCodes(
        @TempDir playerDir: File
    ) {
        playerDir.resolve("$uuid.json").writeText("""{"emoji":[":cucumber:","!logo.png",":unknown:"]}""")

        // 解決できないコードも含めて生のまま返す（Stampへの解決は呼び出し側で行う）
        assertEquals(
            listOf(":cucumber:", "!logo.png", ":unknown:"),
            LocalPlayerStampManager.readShortCodes(playerDir, uuid)
        )
    }

    @Test
    @DisplayName("Missing data is treated as no stamps without creating a file")
    fun missingFileIsEmpty(
        @TempDir playerDir: File
    ) {
        assertEquals(emptyList<String>(), LocalPlayerStampManager.readShortCodes(playerDir, uuid))
        // 読み取り専用なので、ログイン時のinitのようにファイルを作らない
        assertFalse(playerDir.resolve("$uuid.json").exists())
    }

    @Test
    @DisplayName("Corrupt data is reported as an error, not as no stamps")
    fun corruptFileThrows(
        @TempDir playerDir: File
    ) {
        playerDir.resolve("$uuid.json").writeText("{not json")

        assertThrows<Exception> { LocalPlayerStampManager.readShortCodes(playerDir, uuid) }
    }
}