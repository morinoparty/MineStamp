package dev.nikomaru.minestamp.player

import dev.nikomaru.minestamp.MineStamp
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.*
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
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build())
        } catch (e: NoSuchKeyException) {
            val data = PlayerData(
                emoji = listOf()
            )
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build(),
                RequestBody.fromString(json.encodeToString(data))
            )
        }
    }

    override fun load(player: Player) {
        val s3Client = getS3Client()
        val bucketName = get<LocalConfig>().s3Config!!.bucket
        val key = "player/${player.uniqueId}.json"
        val playerData = json.decodeFromString(
            PlayerData.serializer(),
            s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucketName).key(key).build()).asUtf8String()
        )
        playerEmoji[player.uniqueId] = playerData.emoji
    }

    override fun getPlayerStamp(player: Player): ArrayList<Stamp> {
        val defaultStamp = get<PlayerDefaultEmojiConfigData>().defaultEmoji
        val playerStamp = playerEmoji[player.uniqueId] ?: emptyList()
        return (playerStamp + defaultStamp).toCollection(arrayListOf())
    }

    override fun addStamp(player: Player, stamp: Stamp) {
        plugin.logger.info("addStamp: ${stamp.shortCode} to ${player.name}")
        val playerStamp = playerEmoji[player.uniqueId] ?: emptyList()
        val newStamp = (playerStamp + stamp).toCollection(arrayListOf())
        playerEmoji[player.uniqueId] = newStamp
        val s3Client = getS3Client()
        val bucketName = get<LocalConfig>().s3Config!!.bucket
        val key = "player/${player.uniqueId}.json"
        val data = PlayerData(
            emoji = newStamp
        )
        val content = json.encodeToString(data)
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/json")
                .cacheControl("max-age=0")
                .build(),
            RequestBody.fromString(content)
        )
    }

    override fun removeStamp(player: Player, stamp: Stamp) {
        plugin.logger.info("removeStamp: ${stamp.shortCode} from ${player.name}")
        val playerStamp = playerEmoji[player.uniqueId] ?: emptyList()
        val newStamp = (playerStamp - stamp).toCollection(arrayListOf())
        playerEmoji[player.uniqueId] = newStamp
        val s3Client = getS3Client()
        val bucketName = get<LocalConfig>().s3Config!!.bucket
        val key = "player/${player.uniqueId}.json"
        val data = PlayerData(
            emoji = newStamp
        )
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build(),
            RequestBody.fromString(json.encodeToString(data))
        )
    }

    override fun availableStamp(player: Player, stamp: Stamp): Boolean {
        if(player.hasPermission("minestamp.stamp.all")) return true
        val default = get<PlayerDefaultEmojiConfigData>().defaultEmoji
        val playerStamp = playerEmoji[player.uniqueId] ?: emptyList()
        return (playerStamp + default).map{it.shortCode}.contains(stamp.shortCode)
    }
}