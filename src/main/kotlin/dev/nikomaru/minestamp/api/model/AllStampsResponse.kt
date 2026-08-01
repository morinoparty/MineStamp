package dev.nikomaru.minestamp.api.model

import kotlinx.serialization.Serializable

/**
 * 全スタンプ一覧のレスポンス
 */
@Serializable
data class AllStampsResponse(
    val stamps: List<StampData>
)
