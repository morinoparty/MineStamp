package dev.nikomaru.minestamp.command

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.WrappedParticle
import dev.nikomaru.minestamp.config.PlayerDefaultEmojiConfigData
import dev.nikomaru.minestamp.player.AbstractPlayerStampManager
import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.utils.LangUtils.sendI18nRichMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
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

class ColorEmojiCommand: KoinComponent {
    // コルーチンから並行アクセスされるため並行コレクションを使う
    private val summonCooldown: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    @Command("minestamp advance <stamp> [time] [size] [particleSize] [accuracy]")
    @CommandDescription("advanced command")
    @Permission("minestamp.command.advance")
    suspend fun colorEmoji(
        sender: CommandSender, @Argument("stamp") stamp: Stamp,
        @Argument("time") @Range(min = "1", max = "10") @Default("3") time: Int,
        @Argument("size") @Range(min = "1.0", max = "20.0") @Default("1.5") size: Double,
        @Argument("particleSize") @Range(min = "0.01", max = "4.0") @Default("1.0") particleSize: Double,
        @Argument("accuracy") @Range(min = "1", max = "128") @Default("32") accuracy: Int,
    ) {
        if (sender !is Player) {
            sender.sendI18nRichMessage("only-execute-from-player")
            return
        }
        val config = PlayerDefaultEmojiConfigData(time, size, particleSize, accuracy)
        summonEmoji(sender, stamp, config)
    }

    @Command("stamp|st <stamp>")
    suspend fun summonEmoji(
        sender: CommandSender, @Argument("stamp") stamp: Stamp,
    ) {
        if (sender !is Player) {
            sender.sendI18nRichMessage("only-execute-from-player")
            return
        }
        val playerStampManager = get<AbstractPlayerStampManager>()
        if (!playerStampManager.availableStamp(sender, stamp)) {
            sender.sendI18nRichMessage("not-have-the-emoji")
            return
        }
        val config = get<PlayerDefaultEmojiConfigData>()
        summonEmoji(sender, stamp, config)
    }

    private suspend fun summonEmoji(
        sender: Player, stamp: Stamp, config: PlayerDefaultEmojiConfigData
    ) {
        val waitSecond = config.waitSecond
        if (!summonCooldown.add(sender.uniqueId)) {
            sender.sendI18nRichMessage("cannot-summon-in-a-row", waitSecond)
            return
        }
        try {
            val pm = ProtocolLibrary.getProtocolManager()
            // 位置・色はフレーム間で不変のため、パケットは1回だけ生成して各フレームで再送する
            val packets = buildParticlePackets(stamp.getStamp(), config, sender.location, pm)
            if (packets.isEmpty()) return
            val count = 8

            repeat(count * config.second) {
                coroutineScope {
                    packets.chunked(256).forEach { chunk ->
                        launch(Dispatchers.IO) {
                            chunk.forEach(pm::broadcastServerPacket)
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

    private data class ParticlePixel(val x: Int, val y: Int, val rgb: Int)

    private fun buildParticlePackets(
        image: BufferedImage, config: PlayerDefaultEmojiConfigData, location: Location, pm: ProtocolManager
    ): List<PacketContainer> {
        val stride = (image.width / config.accuracy).coerceAtLeast(1)
        val pixels = buildList {
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
            createParticlePacket(Color.fromARGB(pixel.rgb), particleSize, location, x, y, pm)
        }
    }

    private fun createParticlePacket(
        color: Color, particleSize: Float, location: Location, x: Double, y: Double, pm: ProtocolManager
    ): PacketContainer {
        val packet = pm.createPacket(PacketType.Play.Server.WORLD_PARTICLES)
        packet.newParticles.write(
            0, WrappedParticle.create(
                Particle.DUST, Particle.DustOptions(color, particleSize)
            )
        )
        val absX = location.x + (x * cos(-location.yaw.toDouble() / 180 * Math.PI))
        val absZ = location.z + (x * sin(location.yaw.toDouble() / 180 * Math.PI))
        val absY = location.y + y + 2
        packet.doubles.write(0, absX).write(1, absY).write(2, absZ)
        return packet
    }
}
