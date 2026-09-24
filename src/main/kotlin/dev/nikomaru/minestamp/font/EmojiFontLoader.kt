/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.font

import dev.nikomaru.minestamp.MineStamp
import dev.nikomaru.minestamp.config.EmojiFontConfig
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

/**
 * `plugins/MineStamp/fonts/` 配下の絵文字フォントを読み込む。
 * ファイルが存在しない場合は設定のURLからダウンロードする。
 */
object EmojiFontLoader : KoinComponent {
    private val plugin: MineStamp by inject()

    private const val FONT_DIRECTORY = "fonts"
    private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(30)
    private val DOWNLOAD_TIMEOUT: Duration = Duration.ofMinutes(10)

    // GitHub Releasesはリダイレクトを返すため追従させる
    private val httpClient: HttpClient by lazy {
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .build()
    }

    // リロードのたびに数十MBのフォントを読み直さないよう、読み込み済みのフォントをファイルの状態と組で保持する
    @Volatile
    private var cache: Pair<String, EmojiFont>? = null

    /**
     * 設定に従ってフォントを読み込む。取得・読み込みに失敗した場合は [EmptyEmojiFont] を返す。
     */
    fun load(config: EmojiFontConfig): EmojiFont {
        val fontFile = resolveFontFile(config.file) ?: return EmptyEmojiFont
        if (!fontFile.exists() && !downloadIfConfigured(config, fontFile)) {
            return EmptyEmojiFont
        }

        // パス・更新日時・サイズが同じなら同一ファイルとみなして再利用する
        val cacheKey = "${fontFile.absolutePath}:${fontFile.lastModified()}:${fontFile.length()}"
        cache?.let { (key, font) -> if (key == cacheKey) return font }

        val font =
            runCatching { CbdtEmojiFont(fontFile.readBytes()) }
                .getOrElse {
                    plugin.logger.severe(
                        "Failed to load emoji font ${fontFile.path} (only CBDT color bitmap fonts are supported): ${it.message}"
                    )
                    return EmptyEmojiFont
                }
        plugin.logger.info("Loaded emoji font ${fontFile.name}.")
        cache = cacheKey to font
        return font
    }

    // fonts/ の外を指せないよう、ファイル名のみを許可する
    private fun resolveFontFile(fileName: String): File? {
        if (fileName.isBlank() || File(fileName).name != fileName || fileName == "." || fileName == "..") {
            plugin.logger.severe(
                "Invalid emoji font file name: \"$fileName\". Specify a file name under $FONT_DIRECTORY/."
            )
            return null
        }
        return plugin.dataFolder.resolve(FONT_DIRECTORY).resolve(fileName)
    }

    private fun downloadIfConfigured(
        config: EmojiFontConfig,
        fontFile: File
    ): Boolean {
        val url = config.url
        if (url.isNullOrBlank()) {
            plugin.logger.severe(
                "Emoji font ${fontFile.path} is not found and no download URL is configured. Emoji stamps are disabled."
            )
            return false
        }
        return runCatching { download(url, fontFile, config.sha256) }
            .onFailure {
                plugin.logger.severe(
                    "Failed to download emoji font from $url: ${it.message}. Emoji stamps are disabled."
                )
            }.isSuccess
    }

    private fun download(
        url: String,
        fontFile: File,
        expectedSha256: String?
    ) {
        plugin.logger.info("Downloading emoji font from $url ...")
        fontFile.parentFile.mkdirs()
        // 途中で失敗しても壊れたフォントが残らないよう、一時ファイルに書いてから移動する
        val tempFile = fontFile.resolveSibling("${fontFile.name}.part")
        try {
            val request =
                HttpRequest
                    .newBuilder(URI.create(url))
                    .timeout(DOWNLOAD_TIMEOUT)
                    .GET()
                    .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile.toPath()))
            check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()}" }

            if (!expectedSha256.isNullOrBlank()) {
                val actual = sha256(tempFile)
                check(actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                    "SHA-256 mismatch (expected $expectedSha256, actual $actual)"
                }
            }
            Files.move(
                tempFile.toPath(),
                fontFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
            plugin.logger.info("Downloaded emoji font to ${fontFile.path} (${fontFile.length() / 1024} KB).")
        } finally {
            tempFile.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }
}