package dev.nikomaru.minestamp.command

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.ticket.RSAUtils.getRSAKeyPair
import dev.nikomaru.minestamp.ticket.TicketUtils.getRouletteTicket
import dev.nikomaru.minestamp.ticket.TicketUtils.getUniqueTicket
import dev.nikomaru.minestamp.utils.LangUtils.sendI18nRichMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.KeyPairGenerator


@Command("minestamp")
@Permission("minestamp.command.publish")
class PublishTicketCommand: KoinComponent {
    val plugin: MineStamp by inject()

    @Command("generate keyPair")
    @CommandDescription("Generate private and public keys.")
    @Permission("minestamp.command.publish.generate")
    fun generateKeyPairs(actor: CommandSender) {
        val privateKeyFile = plugin.dataFolder.resolve("privateKey")
        val publicKeyFile = plugin.dataFolder.resolve("publicKey")

        if (privateKeyFile.exists() || publicKeyFile.exists()) {
            actor.sendI18nRichMessage("already-exist-keyPair")
            actor.sendI18nRichMessage("execute-after-remove")
            return
        }
        val keyGenerator = KeyPairGenerator.getInstance("RSA")
        keyGenerator.initialize(2048)
        val keyPair = keyGenerator.genKeyPair()
        val privateKey = keyPair.private
        privateKeyFile.writeBytes(privateKey.encoded)
        val publicKey = keyPair.public
        publicKeyFile.writeBytes(publicKey.encoded)
        actor.sendI18nRichMessage("generate-keyPair")
    }

    @Command("publish roulette")
    @CommandDescription("Generate tickets for roulette.")
    @Permission("minestamp.command.publish.roulette")
    fun publishRandom(actor: Player) {
        val rsaKey = getRSAKeyPair() ?: run {
            actor.sendI18nRichMessage("not-found-keyPair")
            actor.sendI18nRichMessage("need-generate-keyPair")
            return
        }
        val algorithm = Algorithm.RSA256(rsaKey.second, rsaKey.first)
        val jwt = JWT.create().withIssuer("minestamp").withClaim("type", "roulette").sign(algorithm)
        val ticket = getRouletteTicket(jwt)
        actor.inventory.addItem(ticket)
    }

    @Command("publish unique")
    @CommandDescription("Generate unique tickets.")
    @Permission("minestamp.command.publish.unique")
    fun publishUnique(actor: Player, stamp: Stamp) {
        val rsaKey = getRSAKeyPair() ?: run {
            actor.sendI18nRichMessage("not-found-keyPair")
            actor.sendI18nRichMessage("need-generate-keyPair")
            return
        }
        val algorithm = Algorithm.RSA256(rsaKey.second, rsaKey.first)
        val ticket = getUniqueTicket(algorithm, stamp)
        actor.inventory.addItem(ticket)
    }

}