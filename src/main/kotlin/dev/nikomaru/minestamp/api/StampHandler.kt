package dev.nikomaru.minestamp.api

import dev.nikomaru.minestamp.data.ImageListData
import dev.nikomaru.minestamp.data.PlayerDefaultEmojiConfigData
import dev.nikomaru.minestamp.player.AbstractPlayerStampManager
import dev.nikomaru.minestamp.stamp.Stamp
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import party.morino.mineauth.api.annotations.Authenticated
import party.morino.mineauth.api.annotations.Caller
import party.morino.mineauth.api.annotations.Get
import party.morino.mineauth.api.annotations.Public
import party.morino.mineauth.api.auth.Principal
import party.morino.mineauth.api.http.HttpError
import party.morino.mineauth.api.http.HttpStatus
import java.util.Properties

/**
 * スタンプの種別
 */
@Serializable
enum class StampType {
    /** 絵文字スタンプ（shortCodeが`:`で始まる） */
    EMOJI,

    /** 画像スタンプ（shortCodeが`!`で始まる） */
    IMAGE
}

/**
 * スタンプ1件分のレスポンス
 */
@Serializable
data class StampData(
    val shortCode: String,
    val type: StampType
)

/**
 * プレイヤーの所持スタンプ一覧のレスポンス
 */
@Serializable
data class PlayerStampsResponse(
    val uuid: String,
    val stamps: List<StampData>
)

/**
 * 全スタンプ一覧のレスポンス
 */
@Serializable
data class AllStampsResponse(
    val stamps: List<StampData>
)

/**
 * MineAuth経由でスタンプ情報を提供するハンドラー
 * /api/v1/plugins/minestamp/ 配下にエンドポイントを提供する
 */
class StampHandler : KoinComponent {

    /**
     * 認証済みプレイヤー自身の所持スタンプ一覧を取得する
     * GET /stamps
     *
     * デフォルトスタンプ（全員に配布されるもの）も含めて返す。
     * プレイヤーがオフラインで、かつサーバー起動後に一度もログインしていない場合は
     * スタンプデータが未ロードのため404を返す。
     *
     * @param caller 認証済み呼び出し元プレイヤー
     * @return 所持スタンプ一覧
     */
    @Get("/stamps")
    @Authenticated
    suspend fun getOwnStamps(@Caller caller: Principal.User): PlayerStampsResponse {
        val stampManager = get<AbstractPlayerStampManager>()
        val onlinePlayer = caller.onlinePlayer

        val stamps: List<Stamp> = if (onlinePlayer != null) {
            stampManager.getPlayerStamp(onlinePlayer)
        } else {
            // オフラインの場合はメモリ上のデータ（最終ログイン時にロード済み）から取得する
            val stored = stampManager.playerEmoji[caller.uuid] ?: throw HttpError(
                HttpStatus.NOT_FOUND,
                "Stamp data is not loaded for this player. Join the server at least once after a restart."
            )
            stored + get<PlayerDefaultEmojiConfigData>().defaultEmoji
        }

        return PlayerStampsResponse(
            uuid = caller.uuid.toString(),
            stamps = stamps.map { it.toStampData() }.distinctBy { it.shortCode }
        )
    }

    /**
     * サーバーに存在する全スタンプの一覧を取得する
     * GET /stamps/all
     *
     * 絵文字スタンプ（emoji.properties由来）と画像スタンプ（image配下）を合わせて返す
     *
     * @return 全スタンプ一覧
     */
    @Get("/stamps/all")
    @Public(reason = "Stamp catalog contains no player data")
    suspend fun getAllStamps(): AllStampsResponse {
        val emojiProperties = get<Properties>()
        val emojiStamps = emojiProperties.stringPropertyNames().map { shortCode ->
            StampData(shortCode = shortCode, type = StampType.EMOJI)
        }

        // 画像一覧は非同期でロードされるため、未ロードの場合は空扱いにする
        val imageStamps = getKoin().getOrNull<ImageListData>()?.list?.map { name ->
            StampData(shortCode = "!$name", type = StampType.IMAGE)
        } ?: emptyList()

        return AllStampsResponse(
            stamps = (emojiStamps + imageStamps).sortedBy { it.shortCode }
        )
    }

    /**
     * StampをレスポンスDTOへ変換する
     */
    private fun Stamp.toStampData(): StampData = StampData(
        shortCode = shortCode,
        type = if (shortCode.startsWith("!")) StampType.IMAGE else StampType.EMOJI
    )
}
