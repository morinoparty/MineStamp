package dev.nikomaru.minestamp.stamp

import dev.nikomaru.minestamp.config.FileType
import dev.nikomaru.minestamp.config.LocalConfig
import dev.nikomaru.minestamp.utils.Utils.getS3Client
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import javax.imageio.ImageIO

class ImageStamp(shortCode: String): Stamp(shortCode), KoinComponent {
    init {
        val config = get<LocalConfig>()
        if (config.type == FileType.LOCAL) {
            image = ImageIO.read(
                plugin.dataFolder.resolve("image/${shortCode.removePrefix("!")}")
            )
        } else {
            val s3Config = config.s3Config!!
            val s3Client = getS3Client()
            s3Client.getObject { it.bucket(s3Config.bucket).key("image/${shortCode.removePrefix("!")}") }
                .use { stream -> image = ImageIO.read(stream) }
        }

    }
}
