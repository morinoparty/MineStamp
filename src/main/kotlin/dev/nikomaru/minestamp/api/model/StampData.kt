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
        fun from(stamp: Stamp): StampData = StampData(
            shortCode = stamp.shortCode,
            type = if (stamp.shortCode.startsWith("!")) StampType.IMAGE else StampType.EMOJI
        )
    }
}
