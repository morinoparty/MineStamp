/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.data

import kotlinx.serialization.Serializable

// shortCode文字列のまま保持する。Stampへの解決はロード時に行い、
// 解決できない絵文字（フォント更新等で描画不能になったもの）でもデータを失わないようにする
@Serializable
data class PlayerData(
    val emoji: List<String>
)