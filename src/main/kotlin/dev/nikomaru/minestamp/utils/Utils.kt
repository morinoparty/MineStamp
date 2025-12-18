package dev.nikomaru.minestamp.utils

import dev.nikomaru.minestamp.data.LocalConfig
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.minimessage.MiniMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI


object Utils: KoinComponent {
    val json = Json {
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    val mm = MiniMessage.miniMessage()

    fun getS3Client(): S3Client {
        val config = get<LocalConfig>()
        val s3Config = config.s3Config ?: throw IllegalStateException("S3 config is not found")
        val credential = AwsBasicCredentials.create(s3Config.accessKey, s3Config.secretKey)
        val s3Client: S3Client = S3Client.builder()
            .endpointOverride(URI.create(s3Config.url))
            .region(Region.AWS_GLOBAL)
            .credentialsProvider(StaticCredentialsProvider.create(credential))
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build()
            )
            .build()

        return s3Client
    }




}