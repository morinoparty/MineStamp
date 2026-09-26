/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.gametest

import party.morino.fukurou.FukurouConfig
import party.morino.fukurou.junit.GameServerExtension
import party.morino.fukurou.pause
import party.morino.fukurou.player.Perspective
import party.morino.fukurou.plugin.PluginSource
import party.morino.fukurou.screenshot
import party.morino.fukurou.server.GameServer
import party.morino.fukurou.server.Isolation
import party.morino.fukurou.server.ServerSpec
import party.morino.fukurou.server.ServerType
import party.morino.fukurou.server.paper.Paper
import party.morino.fukurou.server.paper.PaperChannel
import party.morino.fukurou.world.BlockPos
import party.morino.fukurou.world.Location
import kotlin.time.Duration.Companion.seconds

/**
 * Alice（OP）と Bob が石の柱の上で向かい合う、スタンプ確認用のサーバー。
 *
 * 旧 game-test/fukurou.yml の players / isolation / arena・front-view fixture に相当する。
 * `@ExtendWith(StampArena::class)` を付けたテストクラスはすべてこの 1 台のサーバーを共有する。
 */
class StampArena : GameServerExtension() {
    // スタンプを送る側。/st を実行するため OP にする
    val alice by player("Alice", op = true)

    // スタンプを見る側。他プレイヤーからの見え方を撮る
    val bob by player("Bob")

    /**
     * 起動するサーバーの種類。
     *
     * CI は -Pfukurou.minecraftVersion / -Pfukurou.paperChannel を渡す。省略時（手元）は runServer と同じ 26.2 の alpha までを使う。
     */
    override fun type(config: FukurouConfig): ServerType =
        Paper.fromProperties(config, defaultVersion = "26.2", defaultChannel = PaperChannel.Alpha)

    /** サーバーの宣言。プラグインと隔離方法を決める。 */
    override fun ServerSpec.configure() {
        // result の id は paper-<version>-stamp-arena になる
        label = "stamp-arena"
        plugins {
            // gameTest タスクが shadowJar の成果物のパスを fukurou.plugin.minestamp に渡す
            underTest(PluginSource.systemProperty("minestamp"))
            // スタンプの描画に ProtocolLib を使う。dmulloy2 の Jenkins は 403 を返すため GitHub の開発版リリースから取得する
            dependency(PluginSource.githubRelease("dmulloy2/ProtocolLib", tag = "dev-build", asset = "ProtocolLib.jar"))
        }
        // スタンプのクールダウンは表示 3 秒 + waitSecond 5 秒 = 8 秒。
        // 前のテストの撮影前の 1.5 秒 + この settle 5 秒 + arena の 2 秒 = 8.5 秒空くので、次のテストの送信時には明けている
        isolation = Isolation.Reset(settle = 5.seconds)
    }

    /** 各テストのリセットの後に、アリーナと視点を準備する。 */
    override suspend fun GameServer.setUp() {
        // Alice と Bob が向かい合う石の柱。時刻・天候・インベントリのリセットは fukurou が行う
        fixture("arena") {
            fill(BlockPos(0, -60, 0), BlockPos(0, -50, 0), "minecraft:stone")
            fill(BlockPos(0, -60, 8), BlockPos(0, -50, 8), "minecraft:stone")
            alice.teleport(Location(0.5, -49.0, 0.5, yaw = 0f, pitch = 30f))
            bob.teleport(Location(0.5, -49.0, 8.5, yaw = 180f, pitch = -15f))
            // クライアントがブロック更新とテレポートを受け取るまで待つ
            pause(2.seconds)
        }
        // Alice を正面視点（F5 × 2）にして自分の頭上のスタンプも写す。fukurou が次のテストの前に一人称へ戻す
        fixture("front-view") {
            alice.perspective(Perspective.THIRD_PERSON_FRONT)
        }
    }

    /**
     * Alice からスタンプを送り、Alice と Bob の両方の視点で同時に撮影して、エラーのチャットが無いことを確かめる。
     *
     * @param shortcode 送るスタンプ（例: ":thinking-face:"）
     */
    suspend fun sendStampAndCapture(shortcode: String) {
        // チャット欄からコマンドを送り、サーバーが受け付けたログ（送信後のもの）を待つ
        alice.sendCommand("st $shortcode")
        // スタンプは 3 秒表示されるので、表示が落ち着いた 1.5 秒後に撮る
        pause(1.5.seconds)
        // 送った側と見た側を同時に撮る（1 プレイヤー 1 レーン）
        screenshot(alice, bob, name = "after-stamp")
        // コマンドの失敗やスタンプの未所持はチャットに出るので、それが無いことを確かめる
        alice.assertNoChat(STAMP_ERRORS)
    }

    companion object {
        /** MineStamp やサーバーが失敗時にチャットへ出す文言（旧シナリオの assert_no_log と同じ）。 */
        val STAMP_ERRORS =
            Regex("(Unknown( or incomplete)? command|Stamp not found|do not have that stamp|cannot summon)")
    }
}