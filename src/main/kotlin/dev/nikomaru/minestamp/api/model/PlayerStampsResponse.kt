package dev.nikomaru.minestamp.api.model

import kotlinx.serialization.Serializable

/**
 * プレイヤーの所持スタンプ一覧のレスポンス
 */
@Serializable
data class PlayerStampsResponse(
    val uuid: String,
    val stamps: List<StampData>
)
