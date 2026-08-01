package dev.nikomaru.minestamp.api.model

import kotlinx.serialization.Serializable

/**
 * スタンプの種別
 */
@Serializable
enum class StampType {
    /** 絵文字スタンプ（shortCodeが`:`で始まる） */
    EMOJI,

    /** 画像スタンプ（shortCodeが`!`で始まる） */
    IMAGE
}
