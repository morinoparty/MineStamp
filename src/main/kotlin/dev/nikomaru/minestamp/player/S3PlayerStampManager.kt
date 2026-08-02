package dev.nikomaru.minestamp.player

import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.config.LocalConfig
import dev.nikomaru.minestamp.config.PlayerDefaultEmojiConfigData
import dev.nikomaru.minestamp.data.PlayerData
import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.utils.Utils.getObjectAsString
import dev.nikomaru.minestamp.utils.Utils.getS3Client
import dev.nikomaru.minestamp.utils.Utils.json
import dev.nikomaru.minestamp.utils.Utils.objectExists
import kotlinx.serialization.encodeToString
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import software.amazon.awssdk.core.sync.RequestBody
import java.util.UUID


class S3PlayerStampManager: AbstractPlayerStampManager(), KoinComponent {
    val plugin : MineStamp by inject()
    override fun init(player: Player) {
        val s3Client = getS3Client()
        val bucketName = get<LocalConfig>().s3Config!!.bucket
        val key = "player/${player.uniqueId}.json"
        if (!s3Client.objectExists(bucketName, key)) {
            val data = PlayerData(
                emoji = listOf()
            )
            s3Client.putObject(
                { it.bucket(bucketName).key(key) },
                RequestBody.fromString(json.encodeToString(data))
            )
        }
    }

    override fun load(player: Player) {
        val s3Client = getS3Client()
        val bucketName = get<LocalConfig>().s3Config!!.bucket
        val key = "player/${player.uniqueId}.json"
        val playerData = json.decodeFromString(PlayerData.serializer(), s3Client.getObjectAsString(bucketName, key))
        store(player, playerData.emoji, plugin.logger)
    }

    override fun getPlayerStamp(player: Player): ArrayList<Stamp> {
        val defaultStamp = get<PlayerDefaultEmojiConfigData>().defaultStamps
        val playerStamp = playerEmoji[player.uniqueId] ?: emptyList()
        return (playerStamp + defaultStamp).toCollection(arrayListOf())
    }

    override fun addStamp(player: Player, stamp: Stamp) {
        plugin.logger.info("addStamp: ${stamp.shortCode} to ${player.name}")
        val newCodes = (playerShortCodes[player.uniqueId] ?: emptyList()) + stamp.shortCode
        playerShortCodes[player.uniqueId] = newCodes
        playerEmoji[player.uniqueId] = (playerEmoji[player.uniqueId] ?: emptyList()) + stamp
        val s3Client = getS3Client()
        val bucketName = get<LocalConfig>().s3Config!!.bucket
        val key = "player/${player.uniqueId}.json"
        val data = PlayerData(
            emoji = newCodes
        )
        s3Client.putObject(
            { it.bucket(bucketName).key(key).contentType("application/json").cacheControl("max-age=0") },
            RequestBody.fromString(json.encodeToString(data))
        )
    }

    override fun removeStamp(player: Player, stamp: Stamp) {
        plugin.logger.info("removeStamp: ${stamp.shortCode} from ${player.name}")
        val newCodes = (playerShortCodes[player.uniqueId] ?: emptyList()) - stamp.shortCode
        playerShortCodes[player.uniqueId] = newCodes
        playerEmoji[player.uniqueId] =
            (playerEmoji[player.uniqueId] ?: emptyList()).filterNot { it.shortCode == stamp.shortCode }
        val s3Client = getS3Client()
        val bucketName = get<LocalConfig>().s3Config!!.bucket
        val key = "player/${player.uniqueId}.json"
        val data = PlayerData(
            emoji = newCodes
        )
        s3Client.putObject(
            { it.bucket(bucketName).key(key) },
            RequestBody.fromString(json.encodeToString(data))
        )
    }

    override fun availableStamp(player: Player, stamp: Stamp): Boolean {
        if(player.hasPermission("minestamp.stamp.all")) return true
        val default = get<PlayerDefaultEmojiConfigData>().defaultStamps
        val playerStamp = playerEmoji[player.uniqueId] ?: emptyList()
        return (playerStamp + default).map{it.shortCode}.contains(stamp.shortCode)
    }

    override fun loadAllPlayerData(): Map<UUID, List<String>> {
        val s3Client = getS3Client()
        val bucketName = get<LocalConfig>().s3Config!!.bucket
        return s3Client.listObjectsV2Paginator { it.bucket(bucketName).prefix("player/") }
            .contents().mapNotNull { obj ->
                val uuid = runCatching {
                    UUID.fromString(obj.key().removePrefix("player/").removeSuffix(".json"))
                }.getOrNull() ?: return@mapNotNull null
                val data = json.decodeFromString(
                    PlayerData.serializer(), s3Client.getObjectAsString(bucketName, obj.key())
                )
                uuid to data.emoji
            }.toMap()
    }

    override fun savePlayerData(uuid: UUID, shortCodes: List<String>) {
        val s3Client = getS3Client()
        val bucketName = get<LocalConfig>().s3Config!!.bucket
        s3Client.putObject(
            { it.bucket(bucketName).key("player/$uuid.json") },
            RequestBody.fromString(json.encodeToString(PlayerData(emoji = shortCodes)))
        )
        org.bukkit.Bukkit.getPlayer(uuid)?.let { store(it, shortCodes, plugin.logger) }
    }
}