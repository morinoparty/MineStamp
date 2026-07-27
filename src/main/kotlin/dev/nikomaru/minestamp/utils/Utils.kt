package dev.nikomaru.minestamp.utils

import dev.nikomaru.minestamp.data.LocalConfig
import java.net.URI
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.minimessage.MiniMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception


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
        return S3Client.builder()
            .endpointOverride(URI.create(s3Config.url))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(credential))
            .forcePathStyle(true)
            .build()
    }

    fun S3Client.bucketExists(bucket: String): Boolean = try {
        headBucket { it.bucket(bucket) }
        true
    } catch (e: NoSuchBucketException) {
        false
    }

    fun S3Client.objectExists(bucket: String, key: String): Boolean = try {
        headObject { it.bucket(bucket).key(key) }
        true
    } catch (e: NoSuchKeyException) {
        false
    } catch (e: SdkException) {
        // MinIO等の互換実装は404をNoSuchKey以外で返すことがある
        if (e is S3Exception && e.statusCode() == 404) false else throw e
    }

    fun S3Client.getObjectAsString(bucket: String, key: String): String =
        getObjectAsBytes { it.bucket(bucket).key(key) }.asUtf8String()
}
