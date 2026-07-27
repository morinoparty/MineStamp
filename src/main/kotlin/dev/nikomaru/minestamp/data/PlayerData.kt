package dev.nikomaru.minestamp.data

import kotlinx.serialization.Serializable

// shortCode文字列のまま保持する。Stampへの解決はロード時に行い、
// 解決できない絵文字（フォント更新等で描画不能になったもの）でもデータを失わないようにする
@Serializable
data class PlayerData(
    val emoji: List<String>
)
