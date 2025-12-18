package dev.nikomaru.minestamp.stamp

import dev.nikomaru.minestamp.data.FileType
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import dev.nikomaru.minestamp.data.LocalConfig
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
            val req = GetObjectRequest.builder()
                .bucket(s3Config.bucket)
                .key("image/${shortCode.removePrefix("!")}")
                .build()
            val responseInputStream = s3Client.getObject(req)
            image = ImageIO.read(responseInputStream)

        }

    }
}