package dev.nikomaru.minestamp.stamp

import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.data.FileType
import dev.nikomaru.minestamp.data.LocalConfig
import org.apache.commons.math3.distribution.EnumeratedDistribution
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.util.*

object StampManager: KoinComponent {
    private val plugin: MineStamp by inject()
    private val emojiProperties: Properties by inject()
    fun getStamp(shortCode: String): Stamp? {
        return when {
            shortCode.startsWith("!") -> getImageStamp(shortCode)
            shortCode.startsWith(":") -> getEmojiStamp(shortCode)
            shortCode.startsWith("&") -> TODO() //参照によるアクセス
            else -> null
        }
    }


    private fun getImageStamp(shortCode: String): ImageStamp? {
        if (get<LocalConfig>().type == FileType.LOCAL) {
            val file = plugin.dataFolder.resolve("image").resolve(shortCode.removePrefix("!"))
            if (!file.exists()) return null
        } else {
            val s3Client = dev.nikomaru.minestamp.utils.Utils.getS3Client()
            val s3Config = get<LocalConfig>().s3Config!!
            try {
                s3Client.headObject(
                    HeadObjectRequest.builder()
                        .bucket(s3Config.bucket)
                        .key("image/${shortCode.removePrefix("!")}")
                        .build()
                )
            } catch (e: NoSuchKeyException) {
                return null
            }
        }
        return ImageStamp(shortCode)
    }

    private fun getEmojiStamp(shortCode: String): EmojiStamp? {
        val unicode = emojiProperties.getProperty(shortCode)?.lowercase()?.replace(" ", "_") ?: return null
        val pictureName = "emoji_u${unicode}.png"
        plugin.javaClass.getResourceAsStream("/noto-emoji_128/$pictureName") ?: run {
            plugin.logger.warning("/noto-emoji_128/$pictureName is not found.")
            return null
        }
        return EmojiStamp(shortCode)
    }

    fun getRandomStamp(): Stamp? {
        val map = get<HashMap<String, Int>>().map { (k, v) -> org.apache.commons.math3.util.Pair(k, v.toDouble()) }
            .toMutableList()
        val enumeratedDistribution = EnumeratedDistribution(map)
        val randomShortCode = enumeratedDistribution.sample()
        return getStamp(randomShortCode)
    }

}