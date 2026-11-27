/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.api.handler

import dev.nikomaru.minestamp.api.model.AllStampsResponse
import dev.nikomaru.minestamp.api.model.PlayerStampsResponse
import dev.nikomaru.minestamp.api.model.StampData
import dev.nikomaru.minestamp.config.PlayerDefaultEmojiConfigData
import dev.nikomaru.minestamp.data.ImageListData
import dev.nikomaru.minestamp.player.AbstractPlayerStampManager
import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.stamp.name.EmojiNameRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.util.UUID
import java.util.logging.Level

/**
 * MineAuth経由でスタンプ情報を提供するハンドラー
 * /api/v1/plugins/minestamp/ 配下にエンドポイントを提供する
 */
class StampHandler : KoinComponent {
    /**
     * 認証済みプレイヤー自身の所持スタンプ一覧を取得する
     * GET /stamps
     *
     * デフォルトスタンプ（全員に配布されるもの）も含めて、よく使われる順（[StampData.POPULARITY_ORDER]）で返す。
     * サーバー起動後に一度もログインしていない（メモリ上にデータがない）プレイヤーは、
     * 保存済みのデータをストレージから読み取り専用で読み込む。保存データがなければデフォルトスタンプのみを返す。
     * ストレージの読み込みに失敗した場合は503を返す。
     *
     * @param caller 認証済み呼び出し元プレイヤー
     * @return 所持スタンプ一覧
     */
    @Get("/stamps")
    @Authenticated
    suspend fun getOwnStamps(
        @Caller caller: Principal.User
    ): PlayerStampsResponse {
        val stampManager = get<AbstractPlayerStampManager>()

        // ログイン時にロード済みならメモリ上のデータを使う（オンラインのプレイヤーはaddStamp等で常に最新）
        // 未ロードならストレージから読む。メモリには書き込まないので、後のログイン時のロードや保存とは競合しない
        val owned: List<Stamp> =
            stampManager.playerEmoji[caller.uuid]
                ?: readStoredStamps(stampManager, caller.uuid)
                // HttpErrorの引数は省略せずに全て渡す。省略するとデフォルト引数用の合成コンストラクタ
                // (kotlin.jvm.internal.DefaultConstructorMarkerを含む)が呼ばれ、MineStampとMineAuthで
                // Kotlin標準ライブラリのクラスローダーが異なるためLinkageErrorになる
                ?: throw HttpError(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Stamp storage is unavailable. Try again later.",
                    "stamp_storage_unavailable",
                    emptyMap()
                )

        val names = get<EmojiNameRegistry>()
        val stamps = owned + get<PlayerDefaultEmojiConfigData>().defaultStamps
        return PlayerStampsResponse(
            uuid = caller.uuid.toString(),
            stamps =
                stamps
                    .map { StampData.of(it.shortCode, names) }
                    .distinctBy { it.shortCode }
                    .sortedWith(StampData.POPULARITY_ORDER)
        )
    }

    /**
     * ストレージから所持スタンプを読み取り専用で読み込む
     *
     * ファイルI/OやS3通信を伴うため、メインスレッドを避けてIOディスパッチャーで実行する。
     * HttpErrorの生成は呼び出し元で行い、ここでは失敗をnullで返すだけにする。
     *
     * @param stampManager プレイヤースタンプの管理クラス
     * @param uuid 対象プレイヤーのUUID
     * @return 所持スタンプ一覧（保存データがなければ空）。ストレージの読み込みに失敗した場合はnull
     */
    private suspend fun readStoredStamps(
        stampManager: AbstractPlayerStampManager,
        uuid: UUID
    ): List<Stamp>? =
        try {
            withContext(Dispatchers.IO) { stampManager.readStoredStamps(uuid) }
        } catch (e: CancellationException) {
            // コルーチンのキャンセルは握りつぶさずに伝播させる
            throw e
        } catch (e: Exception) {
            // ストレージ障害は「データなし」と区別し、原因をログに残してから503にする
            stampManager.logger.log(Level.WARNING, "failed to read stored stamps of $uuid", e)
            null
        }

    /**
     * サーバーに存在する全スタンプの一覧を取得する
     * GET /stamps/all
     *
     * 絵文字スタンプ（emoji.properties由来）と画像スタンプ（image配下）を合わせて返す。
     * 絵文字スタンプをよく使われる順に先に、画像スタンプをshortCode順で後ろに並べる
     *
     * @return 全スタンプ一覧
     */
    @Get("/stamps/all")
    @Public(reason = "Stamp catalog contains no player data")
    suspend fun getAllStamps(): AllStampsResponse {
        val emojiProperties = get<Properties>()
        val names = get<EmojiNameRegistry>()
        val emojiStamps = emojiProperties.stringPropertyNames().toList()

        // 画像一覧は非同期でロードされるため、未ロードの場合は空扱いにする
        val imageStamps = getKoin().getOrNull<ImageListData>()?.list?.map { name -> "!$name" } ?: emptyList()

        return AllStampsResponse(
            stamps = (emojiStamps + imageStamps).map { StampData.of(it, names) }.sortedWith(StampData.POPULARITY_ORDER)
        )
    }
}