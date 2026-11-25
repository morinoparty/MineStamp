/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.player

import dev.nikomaru.minestamp.stamp.Stamp
import dev.nikomaru.minestamp.stamp.StampManager
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

abstract class AbstractPlayerStampManager {
    // MineAuth の HTTP スレッドからも読まれるため ConcurrentHashMap
    val playerEmoji: MutableMap<UUID, List<Stamp>> = ConcurrentHashMap()

    // 保存用の生shortCode。解決できない絵文字もここには残し、addStamp等の上書き保存で消さない
    protected val playerShortCodes: MutableMap<UUID, List<String>> = ConcurrentHashMap()

    /** 警告等の出力先。各実装はプラグインのロガーを返す */
    abstract val logger: Logger

    protected fun store(
        player: Player,
        shortCodes: List<String>,
        logger: Logger
    ) {
        playerShortCodes[player.uniqueId] = shortCodes
        playerEmoji[player.uniqueId] = resolveStamps(player.name, shortCodes, logger)
    }

    /**
     * 保存済みの生shortCodeをStampへ解決する。メモリ上のデータには一切書き込まない。
     * 解決できないコードは警告を出して除外する（保存データからは消さない）。
     *
     * @param owner ログ出力用の所有者名（プレイヤー名またはUUID）
     * @param shortCodes 保存済みの生shortCode
     * @param logger 警告の出力先
     * @return 解決できたスタンプ一覧
     */
    protected fun resolveStamps(
        owner: String,
        shortCodes: List<String>,
        logger: Logger
    ): List<Stamp> =
        shortCodes.mapNotNull { code ->
            val stamp = runCatching { StampManager.getStamp(code) }.getOrNull()
            if (stamp == null) {
                logger.warning(
                    "stamp $code owned by $owner cannot be resolved; keeping it in data but it will be unusable."
                )
            }
            stamp
        }

    /**
     * ストレージに保存されている所持スタンプを読み取り専用で取得する。
     * サーバー再起動後に未ログインのプレイヤー（メモリ上にデータがない）向けで、
     * playerEmoji / playerShortCodes には書き込まない（後のログイン時のロードや保存と競合させないため）。
     * ファイルI/OやS3通信を伴うため、メインスレッド以外から呼ぶこと。
     *
     * @param uuid 対象プレイヤーのUUID
     * @return 所持スタンプ一覧（保存データがなければ空）。デフォルトスタンプは含まない
     * @throws Exception ストレージの読み込みに失敗した場合
     */
    fun readStoredStamps(uuid: UUID): List<Stamp> = resolveStamps(uuid.toString(), readStoredShortCodes(uuid), logger)

    /**
     * ストレージに保存されている生shortCodeを読み取り専用で取得する。
     *
     * @param uuid 対象プレイヤーのUUID
     * @return 保存済みの生shortCode。保存データが存在しなければ空リスト
     * @throws Exception ストレージの読み込みに失敗した場合（データなしとは区別する）
     */
    abstract fun readStoredShortCodes(uuid: UUID): List<String>

    abstract fun init(player: Player)

    abstract fun load(player: Player)

    abstract fun getPlayerStamp(player: Player): ArrayList<Stamp>

    abstract fun addStamp(
        player: Player,
        stamp: Stamp
    )

    abstract fun removeStamp(
        player: Player,
        stamp: Stamp
    )

    abstract fun availableStamp(
        player: Player,
        stamp: Stamp
    ): Boolean

    /** 保存済みの全プレイヤーデータ（生shortCode）を読み込む。purge等の全探索用 */
    abstract fun loadAllPlayerData(): Map<UUID, List<String>>

    /** 指定プレイヤーの所持スタンプを保存する。オンラインならメモリ上のデータも更新する */
    abstract fun savePlayerData(
        uuid: UUID,
        shortCodes: List<String>
    )
}