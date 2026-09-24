/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.ticket

import dev.nikomaru.minestamp.MineStamp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

object RSAUtils : KoinComponent {
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