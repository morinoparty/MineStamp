/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.gametest

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * スタンプが送った側と周りのプレイヤーの両方に表示されることを、実際のクライアントで確かめる。
 *
 * メソッド名がそのまま fukurou のテスト id になる（旧シナリオのファイル名と同じ id を保ち、ビューアの履歴をそろえる）。
 */
@Tag("stamps")
@ExtendWith(StampArena::class)
class StampTest {
    /** 考える顔のスタンプを送る。 */
    @Test
    @DisplayName("Thinking face stamp is shown to both players")
    suspend fun `stamp-thinking-face`(arena: StampArena) {
        arena.sendStampAndCapture(":thinking-face:")
    }

    /** 眠る顔のスタンプを送る。 */
    @Test
    @DisplayName("Sleeping face stamp is shown to both players")
    suspend fun `stamp-sleeping-face`(arena: StampArena) {
        arena.sendStampAndCapture(":sleeping-face:")
    }
}