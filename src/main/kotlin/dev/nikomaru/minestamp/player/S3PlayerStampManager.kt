package dev.nikomaru.minestamp.player

import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.data.LocalConfig
import dev.nikomaru.minestamp.data.PlayerData
import dev.nikomaru.minestamp.data.PlayerDefaultEmojiConfigData
import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.utils.Utils.getS3Client
import dev.nikomaru.minestamp.utils.Utils.json
import kotlinx.serialization.encodeToString
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import kotlin.collections.ArrayList
import kotlin.collections.arrayListOf
import kotlin.collections.emptyList
import kotlin.collections.listOf
import kotlin.collections.minus
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.collections.toCollection


class S3PlayerStampManager: AbstractPlayerStampManager(), KoinComponent {
    val plugin : MineStamp by inject()
    override fun init(player: Player) {
        val s3Client = getS3Client()
        val bucketName = get<LocalConfig>().s3Config!!.bucket
        val key = "player/${player.uniqueId}.json"
        if (!s3Client.doesObjectExist(bucketName, key)) {
            val data = PlayerData(
                emoji = listOf()
            )
            s3Client.putObject(bucketName, key, json.encodeToString(data))
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
        val inputStream =  json.encodeToString(data).byteInputStream()
        val metadata = ObjectMetadata()
        metadata.contentLength = inputStream.available().toLong()
        metadata.contentType = "application/json"
        metadata.cacheControl = "max-age=0"
        val req = PutObjectRequest(bucketName, key, inputStream, metadata)
        req.requestClientOptions.readLimit = 1024 * 1024 * 10
        s3Client.putObject(req)
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
        val req = PutObjectRequest(bucketName, key, json.encodeToString(data).byteInputStream(), null)
        req.requestClientOptions.readLimit = 1024 * 1024 * 10
        s3Client.putObject(req)
    }

    override fun availableStamp(player: Player, stamp: Stamp): Boolean {
        if(player.hasPermission("minestamp.stamp.all")) return true
        val default = get<PlayerDefaultEmojiConfigData>().defaultStamps
        val playerStamp = playerEmoji[player.uniqueId] ?: emptyList()
        return (playerStamp + default).map{it.shortCode}.contains(stamp.shortCode)
    }
}