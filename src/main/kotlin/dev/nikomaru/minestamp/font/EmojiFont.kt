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
 * 絵文字スタンプの画像を提供するフォント。
 *
 * [unicodeSpec] はスペース区切りの16進コードポイント列（例: "1F468 1F3FB 200D 1F373"）。
 */
interface EmojiFont {
    /** PNGのデコードを行わずに、指定した絵文字の画像を持っているかを返す */
    fun hasGlyph(unicodeSpec: String): Boolean

    /** 指定した絵文字の画像を返す。存在しない場合はnull */
    fun getImage(unicodeSpec: String): BufferedImage?
}