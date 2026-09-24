/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.config

import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.stamp.StampManager
import kotlinx.serialization.Serializable

// 全プレイヤーが最初から使える既定スタンプの一覧。
// 描画パラメータ (size等) はconfig.jsonのstampセクションに移動した
// （旧フォーマットは起動時にConfigが自動移行する）
@Serializable
data class PlayerDefaultEmojiConfigData(
    val defaultEmoji: List<String> =
        listOf(
            ":cucumber:",
            ":thinking-face:",
            ":angry-face:",
            ":sleeping-face:"
        )
) {
    // 解決できない絵文字（フォント更新で描画不能になったもの等）は黙って除外する
    val defaultStamps: List<Stamp> by lazy {
        defaultEmoji.mapNotNull { runCatching { StampManager.getStamp(it) }.getOrNull() }
    }
}