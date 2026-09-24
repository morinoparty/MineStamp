/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.api.model

import dev.nikomaru.minestamp.stamp.Stamp
import kotlinx.serialization.Serializable

/**
 * スタンプ1件分のレスポンス
 */
@Serializable
data class StampData(
    val shortCode: String,
    val type: StampType
) {
    companion object {
        /**
         * StampをレスポンスDTOへ変換する
         */
        fun from(stamp: Stamp): StampData =
            StampData(
                shortCode = stamp.shortCode,
                type = if (stamp.shortCode.startsWith("!")) StampType.IMAGE else StampType.EMOJI
            )
    }
}