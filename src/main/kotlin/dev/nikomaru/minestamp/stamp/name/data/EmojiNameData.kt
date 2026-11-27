/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.stamp.name.data

import kotlinx.serialization.Serializable

/**
 * 絵文字スタンプ1件分の表示用情報（emoji-names.jsonの1項目）
 *
 * scripts/emoji-names/generate.mjs が emoji.properties・emojibase-data・Unicodeの使用頻度ランキングから生成する。
 *
 * @property emoji 完全修飾（FE0F付き）の絵文字
 * @property order よく使われる順の並び順（0が最も多い）。全項目で一意
 * @property ja 日本語名（CLDR）
 * @property en 英語名（CLDR）
 */
@Serializable
data class EmojiNameData(
    val emoji: String,
    val order: Int,
    val ja: String,
    val en: String
)