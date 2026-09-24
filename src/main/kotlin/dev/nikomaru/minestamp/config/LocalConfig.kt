/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.config

import kotlinx.serialization.Serializable

@Serializable
data class LocalConfig(
    val type: FileType = FileType.LOCAL,
    val s3Config: S3Config? = null,
    val lang: String = "en_US",
    val stamp: StampRenderConfig = StampRenderConfig()
)

@Serializable
data class S3Config(
    val url: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String
)

/** スタンプ召喚時の描画パラメータ。/minestamp advance では引数で上書きできる */
@Serializable
data class StampRenderConfig(
    val second: Int = 3,
    val size: Double = 1.8,
    val particleSize: Double = 1.0,
    val accuracy: Int = 32,
    val waitSecond: Double = 5.0
)

enum class FileType {
    LOCAL,
    S3
}