/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.font

import java.util.Properties
import java.util.logging.Logger

/**
 * 絵文字画像のデコードを起動時に1回だけ行い、ImageIO の初期化を先に済ませる。
 *
 * ImageIO は最初の読み込み時にプラグイン探索などの初期化を行うため、1回目のデコードだけ
 * 0.7〜1.5秒ほどかかる。これを最初の `/stamp` 実行時に行うと、サーバーのメインスレッドが止まり、
 * スタンプの表示も遅れる。起動時にメインスレッド以外で済ませておくことで、それを避ける。
 */
object EmojiImageWarmup {
    /**
     * フォントが持っている最初の絵文字を1つデコードする。
     *
     * @param emojiFont 登録済みの絵文字フォント
     * @param emojiProperties shortCode から Unicode のコードポイントへの対応表（emoji.properties）
     * @param logger かかった時間を記録するロガー
     */
    fun warmUp(
        emojiFont: EmojiFont,
        emojiProperties: Properties,
        logger: Logger
    ) {
        // フォントで描画できる絵文字を1つ選ぶ（フォントの差し替えで欠けている絵文字があり得るため）
        val spec =
            emojiProperties
                .stringPropertyNames()
                .sorted()
                .asSequence()
                .mapNotNull { emojiProperties.getProperty(it) }
                .firstOrNull { emojiFont.hasGlyph(it) }
                ?: return
        val start = System.nanoTime()
        // 結果の画像は使わない。デコードを1回通すこと自体が目的
        emojiFont.getImage(spec)
        logger.info("Emoji image decoder warmed up in ${(System.nanoTime() - start) / 1_000_000} ms")
    }
}