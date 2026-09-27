/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.command

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import dev.nikomaru.minestamp.config.LocalConfig
import dev.nikomaru.minestamp.config.StampRenderConfig
import dev.nikomaru.minestamp.player.AbstractPlayerStampManager
import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.utils.LangUtils.sendI18nRichMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.annotation.specifier.Range
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Default
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.awt.image.BufferedImage
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

class ColorEmojiCommand : KoinComponent {
    // コルーチンから並行アクセスされるため並行コレクションを使う
    private val summonCooldown: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    @Command("minestamp advance <stamp> [time] [size] [particleSize] [accuracy]")
    @CommandDescription("advanced command")
    @Permission("minestamp.command.advance")
    suspend fun colorEmoji(
        sender: CommandSender,
        @Argument("stamp") stamp: Stamp,
        @Argument("time") @Range(min = "1", max = "10") @Default("3") time: Int,
        @Argument("size") @Range(min = "1.0", max = "20.0") @Default("1.5") size: Double,
        @Argument("particleSize") @Range(min = "0.01", max = "4.0") @Default("1.0") particleSize: Double,
        @Argument("accuracy") @Range(min = "1", max = "128") @Default("32") accuracy: Int
    ) {
        if (sender !is Player) {
            sender.sendI18nRichMessage("minestamp.only-execute-from-player")
            return
        }
        val config =
            get<LocalConfig>().stamp.copy(
                second = time,
                size = size,
                particleSize = particleSize,
                accuracy = accuracy
            )
        summonEmoji(sender, stamp, config)
    }

    @Command("stamp|st <stamp>")
    suspend fun summonEmoji(
        sender: CommandSender,
        @Argument("stamp") stamp: Stamp
    ) {
        if (sender !is Player) {
            sender.sendI18nRichMessage("minestamp.only-execute-from-player")
            return
        }
        val playerStampManager = get<AbstractPlayerStampManager>()
        if (!playerStampManager.availableStamp(sender, stamp)) {
            sender.sendI18nRichMessage("minestamp.not-have-the-emoji")
            return
        }
        val config = get<LocalConfig>().stamp
        summonEmoji(sender, stamp, config)
    }

    private suspend fun summonEmoji(
        sender: Player,
        stamp: Stamp,
        config: StampRenderConfig
    ) {
        val waitSecond = config.waitSecond
        if (!summonCooldown.add(sender.uniqueId)) {
            sender.sendI18nRichMessage("minestamp.cannot-summon-in-a-row", waitSecond)
            return
        }
        try {
            val playerManager = PacketEvents.getAPI().playerManager
            // 位置・色はフレーム間で不変のため、パケットは1回だけ生成して各フレームで再送する
            val packets = buildParticlePackets(stamp.getStamp(), config, sender.location)
            if (packets.isEmpty()) return
            val count = 8

            repeat(count * config.second) {
                // PacketEvents にはブロードキャストがないため、メインスレッドでオンラインプレイヤーを取得してから各自に送る
                val players = Bukkit.getOnlinePlayers().toList()
                coroutineScope {
                    packets.chunked(256).forEach { chunk ->
                        launch(Dispatchers.IO) {
                            // ラッパーは送信のたびにロックして再エンコードされるため、複数プレイヤーへの使い回しは安全
                            chunk.forEach { packet ->
                                players.forEach { player -> playerManager.sendPacket(player, packet) }
                            }
                        }
                    }
                }
                delay(1000L / count)
            }

            delay((1000L * waitSecond).toLong())
        } finally {
            // 例外時もクールダウンが残らないよう必ず解除する
            summonCooldown.remove(sender.uniqueId)
        }
    }

    private data class ParticlePixel(
        val x: Int,
        val y: Int,
        val rgb: Int
    )

    private fun buildParticlePackets(
        image: BufferedImage,
        config: StampRenderConfig,
        location: Location
    ): List<WrapperPlayServerParticle> {
        val stride = (image.width / config.accuracy).coerceAtLeast(1)
        val pixels =
            buildList {
                for (x in 0 until image.width step stride) {
                    for (y in 0 until image.height step stride) {
                        val rgb = image.getRGB(x, y)
                        if (rgb != 0) add(ParticlePixel(x, y, rgb))
                    }
                }
            }
        if (pixels.isEmpty()) return emptyList()

        val xMin = pixels.minOf { it.x }
        val xMax = pixels.maxOf { it.x }
        val yMax = pixels.maxOf { it.y }
        val yMin = pixels.minOf { it.y }
        val width = (xMax - xMin).toDouble()
        val midWidth = width / 2
        val height = (yMax - yMin).toDouble()
        val particleSize = config.particleSize.toFloat()
        val size = config.size

        return pixels.map { pixel ->
            val x = (pixel.x - (xMin + midWidth)) / width * 3 * width / height * size
            val y = (yMax - pixel.y) / height * 3 * size
            createParticlePacket(pixel.rgb, particleSize, location, x, y)
        }
    }

    private fun createParticlePacket(
        rgb: Int,
        particleSize: Float,
        location: Location,
        x: Double,
        y: Double
    ): WrapperPlayServerParticle {
        // DUST の色は RGB のみ扱うため、アルファ値は捨てる
        val dust = ParticleDustData(particleSize, (rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
        val absX = location.x + (x * cos(-location.yaw.toDouble() / 180 * Math.PI))
        val absZ = location.z + (x * sin(location.yaw.toDouble() / 180 * Math.PI))
        val absY = location.y + y + 2
        // count = 0 はオフセットを使わず指定位置にちょうど1つだけ表示する (ProtocolLib 版の既定値と同じ)
        return WrapperPlayServerParticle(
            Particle(ParticleTypes.DUST, dust),
            false,
            Vector3d(absX, absY, absZ),
            Vector3f.zero(),
            0f,
            0
        )
    }
}