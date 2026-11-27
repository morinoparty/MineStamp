/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.stamp.name

import dev.nikomaru.minestamp.stamp.name.data.EmojiNameData
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.Locale

/**
 * 絵文字スタンプのshortCodeから、絵文字・名前・並び順を引く対応表
 *
 * 画像スタンプ（`!`始まり）や対応表にないshortCodeは項目を持たず、名前はshortCodeそのものとして扱う。
 *
 * @param entries shortCode → 表示用情報
 */
class EmojiNameRegistry(
    private val entries: Map<String, EmojiNameData>
) {
    /** 登録件数 */
    val size: Int get() = entries.size

    /**
     * shortCodeに対応する表示用情報を返す
     *
     * @param shortCode スタンプのshortCode
     * @return 表示用情報。絵文字スタンプでない、または対応表にない場合はnull
     */
    operator fun get(shortCode: String): EmojiNameData? = entries[shortCode]

    /**
     * ロケールに合わせた名前を返す
     *
     * 日本語なら日本語名、英語なら英語名を返す。それ以外のロケールはサーバーの既定ロケールで選ぶ
     * （メッセージの翻訳と同じく、未対応の言語は既定の言語に寄せる）。
     *
     * @param shortCode スタンプのshortCode
     * @param locale 表示先のロケール
     * @param fallbackLocale ja/en以外のロケールで使うサーバーの既定ロケール
     * @return 名前。対応表にない場合はnull
     */
    fun name(
        shortCode: String,
        locale: Locale,
        fallbackLocale: Locale
    ): String? {
        val entry = entries[shortCode] ?: return null
        return if (isJapanese(locale, fallbackLocale)) entry.ja else entry.en
    }

    /**
     * 人が読む表示用ラベルを返す（例: `ピザ (:pizza:)`）
     *
     * コマンド引数として使えるよう、shortCodeは必ず残す。
     *
     * @param shortCode スタンプのshortCode
     * @param locale 表示先のロケール
     * @param fallbackLocale ja/en以外のロケールで使うサーバーの既定ロケール
     * @return 名前付きのラベル。名前がない場合はshortCodeのみ
     */
    fun label(
        shortCode: String,
        locale: Locale,
        fallbackLocale: Locale
    ): String = name(shortCode, locale, fallbackLocale)?.let { "$it ($shortCode)" } ?: shortCode

    /**
     * よく使われる順に並べるためのshortCodeの比較器
     *
     * 対応表にあるもの（絵文字スタンプ）を並び順で先に、ないもの（画像スタンプなど）をshortCode順で後ろに並べる。
     */
    val popularityComparator: Comparator<String> =
        compareBy<String, Int?>(nullsLast()) { entries[it]?.order }.thenBy { it }

    /** ロケールが日本語か（ja/en以外は既定ロケールで判定する） */
    private fun isJapanese(
        locale: Locale,
        fallbackLocale: Locale
    ): Boolean {
        // 対応している言語ならそのまま使い、それ以外は既定ロケールの言語にする
        val language = if (locale.language in SUPPORTED_LANGUAGES) locale.language else fallbackLocale.language
        return language == JAPANESE
    }

    companion object {
        /** クラスパス上の対応表のリソース名 */
        const val RESOURCE_NAME = "emoji-names.json"

        private const val JAPANESE = "ja"
        private val SUPPORTED_LANGUAGES = setOf(JAPANESE, "en")

        /**
         * emoji-names.json を読み込む
         *
         * @param input 対応表のJSON（UTF-8）
         * @return 読み込んだ対応表
         */
        fun load(input: InputStream): EmojiNameRegistry {
            val text = input.readBytes().toString(Charsets.UTF_8)
            return EmojiNameRegistry(Json.decodeFromString<Map<String, EmojiNameData>>(text))
        }
    }
}