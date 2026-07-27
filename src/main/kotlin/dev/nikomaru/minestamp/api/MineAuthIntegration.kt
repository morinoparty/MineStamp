package dev.nikomaru.minestamp.api

import dev.nikomaru.minestamp.MineStamp
import party.morino.mineauth.api.EndpointRegistrationException
import party.morino.mineauth.api.MineAuthApi

/**
 * MineAuth連携（オプション依存）
 *
 * MineAuthはsoftdependかつcompileOnlyのため、MineAuth不在時に
 * `party.morino.mineauth.api.*` を参照すると[NoClassDefFoundError]になる。
 * そのためAPIに触れるコードをこのクラスに隔離し、呼び出し側で
 * `server.pluginManager.getPlugin("MineAuth") != null` を確認してから
 * このクラスをロードすること。
 */
class MineAuthIntegration(private val plugin: MineStamp) {

    /**
     * MineAuthにスタンプ関連のHTTPエンドポイントを登録する
     * 登録されたエンドポイントは /api/v1/plugins/minestamp 配下で利用可能
     */
    fun register() {
        // MineAuthはロード済みだがサービス登録前という狭い窓のみnullになる
        val api = MineAuthApi.get(plugin.server) ?: run {
            plugin.logger.warning("MineAuth service is not registered yet - HTTP endpoints disabled")
            return
        }

        try {
            val registration = api.register(plugin, "minestamp", StampHandler())
            plugin.logger.info("Mounted ${registration.endpoints.size} endpoints under ${registration.basePath}")
        } catch (e: EndpointRegistrationException) {
            // 登録は全件失敗（all-or-nothing）のため、検証エラーの詳細をログに出力する
            plugin.logger.severe("Failed to register MineAuth endpoints: ${e.message}")
        }
    }
}
