/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.config

import kotlinx.serialization.Serializable

/**
 * 絵文字スタンプの描画に使うフォントの設定。
 *
 * フォントは `plugins/MineStamp/fonts/[file]` から読み込み、存在しなければ [url] からダウンロードする。
 * CBDT/CBLC形式（カラービットマップ）の絵文字フォントのみ対応する。
 */
@Serializable
data class EmojiFontConfig(
    // fonts/ 配下のファイル名（パス区切りは不可）
    val file: String = DEFAULT_FILE,
    // ファイルが存在しない場合のダウンロード元。nullの場合はダウンロードしない
    val url: String? = DEFAULT_URL,
    // 指定した場合、ダウンロードしたファイルのSHA-256（16進）を検証する
    val sha256: String? = null
) {
    companion object {
        const val DEFAULT_FILE = "FluentEmojiColor-CBDT.ttf"
        const val DEFAULT_URL =
            "https://github.com/morinoparty/MineStamp/releases/download/fonts-v1/" + DEFAULT_FILE
    }
}