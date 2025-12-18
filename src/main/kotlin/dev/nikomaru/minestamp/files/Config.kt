package dev.nikomaru.minestamp.files

import com.github.shynixn.mccoroutine.bukkit.launch
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.*
import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.data.FileType
import dev.nikomaru.minestamp.data.ImageListData
import dev.nikomaru.minestamp.data.LocalConfig
import dev.nikomaru.minestamp.data.PlayerDefaultEmojiConfigData
import dev.nikomaru.minestamp.utils.LangUtils
import dev.nikomaru.minestamp.utils.Utils.getS3Client
import dev.nikomaru.minestamp.utils.Utils.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

object Config: KoinComponent {
    val plugin: MineStamp by inject()

    suspend fun loadConfig() {
        withContext(Dispatchers.IO) {
            plugin.logger.info("Loading config.")
            val configFile = plugin.dataFolder.resolve("config.json")
            if (!configFile.exists()) {
                val defaultEmojiConfig = LocalConfig()
                configFile.parentFile.mkdirs()
                configFile.createNewFile()
                configFile.writeText(json.encodeToString(defaultEmojiConfig))
            }
            val localConfig = json.decodeFromString<LocalConfig>(configFile.readText())
            loadKoinModules(module {
                single { localConfig }
            })
            LangUtils.loadLocale()
            if (localConfig.type == FileType.LOCAL) {
                plugin.logger.info("File type is local.")
                loadConfigForSingle()
            } else {
                plugin.logger.info("File type is s3. Loading from s3.")
                loadConfigForProxy()
            }
            makeFolder()
            loadImages()
        }
    }

    private suspend fun loadConfigForSingle() {
        withContext(Dispatchers.IO) {
            val randomConfigFile = plugin.dataFolder.resolve("random.json")
            val playerDefaultConfigFile = plugin.dataFolder.resolve("player-default.json")
            if (!randomConfigFile.exists()) {
                randomConfigFile.parentFile.mkdirs()
                randomConfigFile.createNewFile()
                val resourceStream = plugin.javaClass.getResourceAsStream("/default-random.json")
                if (resourceStream != null) {
                    randomConfigFile.writeText(resourceStream.bufferedReader().readText())
                } else {
                    plugin.logger.warning("default-random.json is not found.")
                }
            }
            if (!playerDefaultConfigFile.exists()) {
                val defaultPlayerConfig = PlayerDefaultEmojiConfigData()
                playerDefaultConfigFile.parentFile.mkdirs()
                playerDefaultConfigFile.createNewFile()
                playerDefaultConfigFile.writeText(json.encodeToString(defaultPlayerConfig))
            }
            val randomConfig = json.decodeFromString<HashMap<String, Int>>(randomConfigFile.readText())
            val playerDefaultConfig =
                json.decodeFromString<PlayerDefaultEmojiConfigData>(playerDefaultConfigFile.readText())
            loadKoinModules(module {
                single { randomConfig }
                single { playerDefaultConfig }
            })
        }
    }

    private suspend fun loadConfigForProxy() {
        withContext(Dispatchers.IO) {
            val s3 = getS3Client()
            val s3Config = get<LocalConfig>().s3Config ?: throw IllegalStateException("S3 config is not found")
            // Create bucket if it doesn't exist
            try {
                s3.headBucket(HeadBucketRequest.builder().bucket(s3Config.bucket).build())
            } catch (e: NoSuchBucketException) {
                s3.createBucket(CreateBucketRequest.builder().bucket(s3Config.bucket).build())
            }
            // Check and create random.json if it doesn't exist
            try {
                s3.headObject(HeadObjectRequest.builder().bucket(s3Config.bucket).key("random.json").build())
            } catch (e: NoSuchKeyException) {
                val inputStream = plugin.javaClass.getResourceAsStream("/default-random.json")
                    ?: throw IllegalStateException("default-random.json is not found")
                s3.putObject(
                    PutObjectRequest.builder()
                        .bucket(s3Config.bucket)
                        .key("random.json")
                        .build(),
                    RequestBody.fromInputStream(inputStream, inputStream.available().toLong())
                )
            }
            // Check and create player-default.json if it doesn't exist
            try {
                s3.headObject(HeadObjectRequest.builder().bucket(s3Config.bucket).key("player-default.json").build())
            } catch (e: NoSuchKeyException) {
                val defaultPlayerConfig = PlayerDefaultEmojiConfigData()
                val content = json.encodeToString(defaultPlayerConfig)
                s3.putObject(
                    PutObjectRequest.builder()
                        .bucket(s3Config.bucket)
                        .key("player-default.json")
                        .build(),
                    RequestBody.fromString(content)
                )
            }
            val randomConfig = json.decodeFromString<HashMap<String, Int>>(
                s3.getObjectAsBytes(GetObjectRequest.builder().bucket(s3Config.bucket).key("random.json").build()).asUtf8String()
            )
            val playerDefaultConfig = json.decodeFromString<PlayerDefaultEmojiConfigData>(
                s3.getObjectAsBytes(GetObjectRequest.builder().bucket(s3Config.bucket).key("player-default.json").build()).asUtf8String()
            )

            loadKoinModules(module {
                single { randomConfig }
                single { playerDefaultConfig }
            })
        }
    }

    private suspend fun makeFolder() {
        withContext(Dispatchers.IO) {
            if (!plugin.dataFolder.exists()) {
                plugin.dataFolder.mkdir()
            }
            if (!plugin.dataFolder.resolve("image").exists()) {
                plugin.dataFolder.resolve("image").mkdir()
                val file = plugin.dataFolder.resolve("image").resolve("test.jpg")
                val inputStream = plugin.javaClass.classLoader.getResourceAsStream("test.jpg")
                if (inputStream != null) {
                    file.writeBytes(inputStream.readAllBytes())
                }
            }
            if (get<LocalConfig>().type == FileType.S3) {
                val s3Client = getS3Client()
                val s3Config = get<LocalConfig>().s3Config!!
                // Create bucket if it doesn't exist
                try {
                    s3Client.headBucket(HeadBucketRequest.builder().bucket(s3Config.bucket).build())
                } catch (e: NoSuchBucketException) {
                    s3Client.createBucket(CreateBucketRequest.builder().bucket(s3Config.bucket).build())
                }
                // Create image/ folder
                s3Client.putObject(
                    PutObjectRequest.builder()
                        .bucket(s3Config.bucket)
                        .key("image/")
                        .build(),
                    RequestBody.fromString("")
                )
                val inputStream = plugin.javaClass.classLoader.getResourceAsStream("test.jpg")
                if (inputStream != null) {
                    s3Client.putObject(
                        PutObjectRequest.builder()
                            .bucket(s3Config.bucket)
                            .key("image/test.jpg")
                            .build(),
                        RequestBody.fromInputStream(inputStream, inputStream.available().toLong())
                    )
                } else {
                    throw IllegalStateException("test.jpg is not found")
                }
            }
        }
    }

    private fun loadImages() {
        plugin.launch {
            val localConfig = get<LocalConfig>()
            if (localConfig.type == FileType.LOCAL) {
                val imageList = plugin.dataFolder.resolve("image").listFiles()?.map { it.name } ?: emptyList()
                loadKoinModules(module {
                    single { ImageListData(imageList) }
                })
            } else {
                val s3Client = getS3Client()
                val s3Config = localConfig.s3Config ?: throw IllegalStateException("S3 config is not found")
                val imageList = s3Client.listObjectsV2(
                    ListObjectsV2Request.builder()
                        .bucket(s3Config.bucket)
                        .prefix("image/")
                        .build()
                ).contents().map { it.key().removePrefix("image/") }.filter { it.isNotEmpty() }
                loadKoinModules(module {
                    single { ImageListData(imageList) }
                })
            }
        }
    }
}

