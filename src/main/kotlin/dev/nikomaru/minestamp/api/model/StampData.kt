/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.api.model

import dev.nikomaru.minestamp.stamp.name.EmojiNameRegistry
import kotlinx.serialization.Serializable

/**
 * スタンプ1件分のレスポンス
 *
 * emoji・nameJa・nameEn・sortOrder は後から追加した項目で、絵文字スタンプのみ値を持つ。
 * 画像スタンプや対応表にない絵文字ではnullになる（既存の項目は変更しない）。
 *
 * @property shortCode スタンプのshortCode（コマンド引数に使う値）
 * @property type スタンプの種別
 * @property emoji 完全修飾（FE0F付き）の絵文字
 * @property nameJa 日本語名
 * @property nameEn 英語名
 * @property sortOrder よく使われる順の並び順（0が最も多い）
 */
@Serializable
data class StampData(
    val shortCode: String,
    val type: StampType,
    val emoji: String? = null,
    val nameJa: String? = null,
    val nameEn: String? = null,
    val sortOrder: Int? = null
) {
    companion object {
        /**
         * shortCodeからレスポンスDTOを作る
         *
         * MineAuthから呼ばれるハンドラーがデフォルト引数用の合成コンストラクタを参照しないよう、
         * 生成はこの関数に集め、コンストラクタには全ての引数を渡す。
         *
         * @param shortCode スタンプのshortCode
         * @param names 絵文字の対応表
         * @return レスポンスDTO
         */
        fun of(
            shortCode: String,
            names: EmojiNameRegistry
        ): StampData {
            val type = if (shortCode.startsWith("!")) StampType.IMAGE else StampType.EMOJI
            // 画像スタンプは対応表を引かない
            val entry = if (type == StampType.EMOJI) names[shortCode] else null
            return StampData(shortCode, type, entry?.emoji, entry?.ja, entry?.en, entry?.order)
        }

        /**
         * よく使われる順（sortOrderの昇順、値のないものはshortCode順で後ろ）の比較器
         */
        val POPULARITY_ORDER: Comparator<StampData> =
            compareBy<StampData, Int?>(nullsLast()) { it.sortOrder }.thenBy { it.shortCode }
    }
}