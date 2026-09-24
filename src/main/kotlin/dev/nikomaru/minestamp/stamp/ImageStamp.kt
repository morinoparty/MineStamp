/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.stamp

import dev.nikomaru.minestamp.config.FileType
import dev.nikomaru.minestamp.config.LocalConfig
import dev.nikomaru.minestamp.utils.Utils.getS3Client
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import javax.imageio.ImageIO

class ImageStamp(
    shortCode: String
) : Stamp(shortCode),
    KoinComponent {
    init {
        val config = get<LocalConfig>()
        if (config.type == FileType.LOCAL) {
            image =
                ImageIO.read(
                    plugin.dataFolder.resolve("image/${shortCode.removePrefix("!")}")
                )
        } else {
            val s3Config = config.s3Config!!
            val s3Client = getS3Client()
            s3Client
                .getObject { it.bucket(s3Config.bucket).key("image/${shortCode.removePrefix("!")}") }
                .use { stream -> image = ImageIO.read(stream) }
        }
    }
}