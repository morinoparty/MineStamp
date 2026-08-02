package dev.nikomaru.minestamp.config

import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.stamp.StampManager
import kotlinx.serialization.Serializable

// 全プレイヤーが最初から使える既定スタンプの一覧。
// 描画パラメータ (size等) はconfig.jsonのstampセクションに移動した
// （旧フォーマットは起動時にConfigが自動移行する）
@Serializable
data class PlayerDefaultEmojiConfigData(
    val defaultEmoji: List<String> = listOf(
        ":cucumber:", ":thinking-face:", ":angry-face:", ":sleeping-face:"
    )
) {
    // 解決できない絵文字（フォント更新で描画不能になったもの等）は黙って除外する
    val defaultStamps: List<Stamp> by lazy {
        defaultEmoji.mapNotNull { runCatching { StampManager.getStamp(it) }.getOrNull() }
    }
}
