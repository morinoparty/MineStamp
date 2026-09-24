/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.font

import java.awt.image.BufferedImage

/**
 * フォントを取得・読み込みできなかった場合の代替。
 * 絵文字スタンプは使えなくなるが、画像スタンプなど他の機能は動作させるために用いる。
 */
object EmptyEmojiFont : EmojiFont {
    override fun hasGlyph(unicodeSpec: String): Boolean = false

    override fun getImage(unicodeSpec: String): BufferedImage? = null
}