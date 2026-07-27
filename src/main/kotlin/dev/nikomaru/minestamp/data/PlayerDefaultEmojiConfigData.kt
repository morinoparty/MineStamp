package dev.nikomaru.minestamp.data

import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.stamp.StampManager
import kotlinx.serialization.Serializable

@Serializable
data class PlayerDefaultEmojiConfigData(
    val second: Int = 3,
    val size: Double = 1.8,
    val particleSize: Double = 1.0,
    val accuracy: Int = 32,
    val defaultEmoji: List<String> = listOf(
        ":cucumber:", ":thinking-face:", ":angry-face:", ":sleeping-face:"
    ),
    val waitSecond : Double = 5.0
) {
    // 解決できない絵文字（フォント更新で描画不能になったもの等）は黙って除外する
    val defaultStamps: List<Stamp> by lazy {
        defaultEmoji.mapNotNull { runCatching { StampManager.getStamp(it) }.getOrNull() }
    }
}
