/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.api.handler

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * MineAuth APIの呼び出しがクラスローダーをまたいで解決できることを確認するテスト
 *
 * MineStampとMineAuthはPaperのlibrariesで別々にKotlin標準ライブラリを読み込むため、
 * Kotlinのクラスをシグネチャに含むMineAuthのメソッドを呼ぶとLinkageErrorになる。
 * デフォルト引数用の合成コンストラクタはDefaultConstructorMarkerを引数に取るので、その参照がないことを確認する
 */
class StampHandlerLinkageTest {
    @Test
    @DisplayName("StampHandler does not call default-argument synthetic constructors")
    fun doesNotReferenceDefaultConstructorMarker() {
        // suspend関数やラムダの本体は StampHandler$... の別クラスにコンパイルされるため、それらも含めて検索する
        val packageDir =
            File(
                StampHandler::class.java
                    .getResource("StampHandler.class")!!
                    .toURI()
            ).parentFile
        val classFiles = packageDir.listFiles { file -> file.name.matches(Regex("""StampHandler(\$.*)?\.class""")) }
        assertNotNull(classFiles)
        assertTrue(classFiles!!.isNotEmpty())

        classFiles.forEach { classFile ->
            // クラスファイルの定数プールはUTF-8系の文字列で格納されるため、ISO-8859-1でそのまま文字列化して検索する
            val content = String(classFile.readBytes(), Charsets.ISO_8859_1)
            assertFalse(content.contains("kotlin/jvm/internal/DefaultConstructorMarker")) {
                "${classFile.name} references DefaultConstructorMarker"
            }
        }
    }
}