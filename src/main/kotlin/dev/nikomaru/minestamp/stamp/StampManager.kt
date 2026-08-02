package dev.nikomaru.minestamp.stamp

import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.config.FileType
import dev.nikomaru.minestamp.config.LocalConfig
import dev.nikomaru.minestamp.utils.FluentEmojiFont
import dev.nikomaru.minestamp.utils.Utils.objectExists
import org.apache.commons.math3.distribution.EnumeratedDistribution
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
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
            if (s3Client.objectExists(s3Config.bucket, "image/${shortCode.removePrefix("!")}").not()) return null
        }
        return ImageStamp(shortCode)
    }

    private fun getEmojiStamp(shortCode: String): EmojiStamp? {
        emojiProperties.getProperty(shortCode) ?: return null
        val stamp = EmojiStamp(shortCode)
        // EmojiStamp already logs a warning when the image cannot be resolved
        return if (stamp.hasImage) stamp else null
    }

    /**
     * フォント更新（Noto emoji fallback削除）で描画できなくなった絵文字コードか。
     * 絵文字以外のコード（画像スタンプ等）はfalseを返す。
     */
    fun isUnrenderableEmoji(code: String): Boolean {
        if (!code.startsWith(":")) return false
        val spec = emojiProperties.getProperty(code) ?: return true
        return !get<FluentEmojiFont>().hasGlyph(spec)
    }

    fun getRandomStamp(): Stamp? {
        val map = get<HashMap<String, Int>>().map { (k, v) -> org.apache.commons.math3.util.Pair(k, v.toDouble()) }
            .toMutableList()
        val enumeratedDistribution = EnumeratedDistribution(map)
        val randomShortCode = enumeratedDistribution.sample()
        return getStamp(randomShortCode)
    }

}