package dev.nikomaru.minestamp.config

import kotlinx.serialization.Serializable

@Serializable
data class LocalConfig(
    val type: FileType = FileType.LOCAL, val s3Config: S3Config? = null, val lang : String = "en_US"
)

@Serializable
data class S3Config(
    val url: String,val bucket: String, val accessKey: String, val secretKey: String
)

enum class FileType {
    LOCAL, S3
}