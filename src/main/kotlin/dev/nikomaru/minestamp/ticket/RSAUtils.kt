package dev.nikomaru.minestamp.ticket

import dev.nikomaru.minestamp.MineStamp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

object RSAUtils: KoinComponent {
    val plugin: MineStamp by inject()

    fun getRSAKeyPair(): Pair<RSAPrivateKey, RSAPublicKey>? {
        val privateKeyFile = plugin.dataFolder.resolve("privateKey")
        val publicKeyFile = plugin.dataFolder.resolve("publicKey")
        if (!privateKeyFile.exists() || !publicKeyFile.exists()) {
            return null
        }
        val privateKeyBytes = privateKeyFile.readBytes()
        val privateKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec) as RSAPrivateKey
        val publicKeyBytes = publicKeyFile.readBytes()
        val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec) as RSAPublicKey
        return Pair(privateKey, publicKey)

    }
}