package dev.nikomaru.minestamp.config

import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.config.FileType
import dev.nikomaru.minestamp.data.ImageListData
import dev.nikomaru.minestamp.config.LocalConfig
import dev.nikomaru.minestamp.config.PlayerDefaultEmojiConfigData
import dev.nikomaru.minestamp.utils.FluentEmojiFont
import dev.nikomaru.minestamp.utils.LangUtils
import dev.nikomaru.minestamp.utils.Utils
import dev.nikomaru.minestamp.utils.Utils.bucketExists
import dev.nikomaru.minestamp.utils.Utils.getObjectAsString
import dev.nikomaru.minestamp.utils.Utils.getS3Client
import dev.nikomaru.minestamp.utils.Utils.json
import dev.nikomaru.minestamp.utils.Utils.objectExists
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import software.amazon.awssdk.core.sync.RequestBody

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
            // リロードでS3設定が変わり得るため、キャッシュ済みクライアントを破棄する
            Utils.resetS3Client()

            if (localConfig.type == FileType.S3) {
                plugin.logger.info("File type is s3. Loading from s3.")
                // 並列実行される各処理がバケット作成で競合しないよう、先に一度だけ確認する
                ensureBucket()
            } else {
                plugin.logger.info("File type is local.")
            }

            // 以降は互いに独立した処理のため並列に実行する
            coroutineScope {
                launch { LangUtils.loadLocale() }
                launch {
                    if (localConfig.type == FileType.LOCAL) {
                        loadConfigForSingle()
                    } else {
                        loadConfigForProxy()
                    }
                }
                launch {
                    makeFolder()
                    loadImages()
                }
            }
        }
    }

    private fun ensureBucket() {
        val s3 = getS3Client()
        val s3Config = get<LocalConfig>().s3Config ?: throw IllegalStateException("S3 config is not found")
        if (!s3.bucketExists(s3Config.bucket)) {
            s3.createBucket { it.bucket(s3Config.bucket) }
        }
    }

    private fun loadConfigForSingle() {
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
        val randomConfig =
            sanitizeRandomConfig(json.decodeFromString<HashMap<String, Int>>(randomConfigFile.readText()))
        val playerDefaultConfig =
            json.decodeFromString<PlayerDefaultEmojiConfigData>(playerDefaultConfigFile.readText())
        loadKoinModules(module {
            single { randomConfig }
            single { playerDefaultConfig }
        })
    }

    private suspend fun loadConfigForProxy() = coroutineScope {
        val s3 = getS3Client()
        val s3Config = get<LocalConfig>().s3Config ?: throw IllegalStateException("S3 config is not found")

        // random.jsonとplayer-default.jsonは独立しているため並列に初期化・取得する
        val randomConfigDeferred = async {
            if (!s3.objectExists(s3Config.bucket, "random.json")) {
                val defaultRandom = plugin.javaClass.getResourceAsStream("/default-random.json")
                    ?: throw IllegalStateException("default-random.json is not found")
                s3.putObject(
                    { it.bucket(s3Config.bucket).key("random.json") },
                    RequestBody.fromBytes(defaultRandom.use { stream -> stream.readAllBytes() })
                )
            }
            sanitizeRandomConfig(
                json.decodeFromString<HashMap<String, Int>>(
                    s3.getObjectAsString(s3Config.bucket, "random.json")
                )
            )
        }
        val playerDefaultConfigDeferred = async {
            if (!s3.objectExists(s3Config.bucket, "player-default.json")) {
                val defaultPlayerConfig = PlayerDefaultEmojiConfigData()
                s3.putObject(
                    { it.bucket(s3Config.bucket).key("player-default.json") },
                    RequestBody.fromString(json.encodeToString(defaultPlayerConfig))
                )
            }
            json.decodeFromString<PlayerDefaultEmojiConfigData>(
                s3.getObjectAsString(s3Config.bucket, "player-default.json")
            )
        }

        val randomConfig = randomConfigDeferred.await()
        val playerDefaultConfig = playerDefaultConfigDeferred.await()
        loadKoinModules(module {
            single { randomConfig }
            single { playerDefaultConfig }
        })
    }

    // 既存サーバーのrandom.jsonにはフォント更新で描画できなくなった絵文字が残り得るため、
    // 抽選対象からメモリ上で除外する（ファイルは管理者のデータなので書き換えない）
    private fun sanitizeRandomConfig(randomConfig: HashMap<String, Int>): HashMap<String, Int> {
        val emojiProperties = get<Properties>()
        val emojiFont = get<FluentEmojiFont>()
        val unrenderable = randomConfig.keys.filter { code ->
            code.startsWith(":") && run {
                val spec = emojiProperties.getProperty(code)
                spec == null || !emojiFont.hasGlyph(spec)
            }
        }
        if (unrenderable.isNotEmpty()) {
            plugin.logger.warning(
                "random.json contains ${unrenderable.size} emoji(s) that cannot be rendered; they are excluded from random tickets."
            )
            unrenderable.forEach(randomConfig::remove)
        }
        return randomConfig
    }

    private fun makeFolder() {
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
            if (!s3Client.objectExists(s3Config.bucket, "image/test.jpg")) {
                s3Client.putObject({ it.bucket(s3Config.bucket).key("image/") }, RequestBody.empty())
                val inputStream = plugin.javaClass.classLoader.getResourceAsStream("test.jpg")
                    ?: throw IllegalStateException("test.jpg is not found")
                s3Client.putObject(
                    { it.bucket(s3Config.bucket).key("image/test.jpg") },
                    RequestBody.fromBytes(inputStream.use { stream -> stream.readAllBytes() })
                )
            }
        }
    }

    // 呼び出し元（loadConfig）のDispatchers.IOコンテキストで実行する。
    // 以前はplugin.launch（メインスレッド）でS3のブロッキングIOを行っていた
    private fun loadImages() {
        val localConfig = get<LocalConfig>()
        val imageList = if (localConfig.type == FileType.LOCAL) {
            plugin.dataFolder.resolve("image").listFiles()?.map { it.name } ?: emptyList()
        } else {
            val s3Client = getS3Client()
            val s3Config = localConfig.s3Config ?: throw IllegalStateException("S3 config is not found")
            s3Client.listObjectsV2Paginator { it.bucket(s3Config.bucket).prefix("image/") }
                .contents().map { it.key().removePrefix("image/") }.filter { it.isNotEmpty() }
        }
        loadKoinModules(module {
            single { ImageListData(imageList) }
        })
    }
}
