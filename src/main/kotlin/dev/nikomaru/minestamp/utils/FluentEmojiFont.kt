package dev.nikomaru.minestamp.utils

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Fluent Emoji (CBDT/CBLC color bitmap) font parser.
 *
 * Java AWT cannot render color emoji fonts, so this class parses the sfnt binary
 * directly and extracts the embedded PNG bytes from the CBDT table.
 *
 * Supported:
 * - cmap format 4 / 12 (single codepoint -> glyph id)
 * - GSUB LookupType 4 ligature substitution (ZWJ sequences, skin tones), applied
 *   iteratively so that multi-stage ligatures also resolve. U+FE0F (variation
 *   selector) is treated as optional on both the input and the ligature definitions.
 * - CBLC IndexSubTable format 1 / 2 / 3, CBDT glyph format 17 / 18 / 19
 */
class FluentEmojiFont(private val data: ByteArray) {
    private val tables = HashMap<String, Pair<Int, Int>>() // tag -> (offset, length)
    private val cmap = HashMap<Int, Int>() // codepoint -> glyph id
    // first glyph -> ligatures (component glyphs after the first, ligature glyph), longest first
    private val ligatures = HashMap<Int, List<Pair<IntArray, Int>>>()
    private var strikeArrayOffset = 0
    private var strikeSubTableCount = 0

    companion object {
        private const val VARIATION_SELECTOR_16 = 0xFE0F
    }

    init {
        parseTableDirectory()
        parseCmap()
        parseGsub()
        parseCblc()
    }

    private fun u8(offset: Int): Int = data[offset].toInt() and 0xFF
    private fun u16(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)
    private fun s16(offset: Int): Int = u16(offset).toShort().toInt()
    private fun u32(offset: Int): Int = (u16(offset) shl 16) or u16(offset + 2)

    private fun parseTableDirectory() {
        val numTables = u16(4)
        for (i in 0 until numTables) {
            val record = 12 + 16 * i
            val tag = String(data, record, 4, Charsets.ISO_8859_1)
            tables[tag] = u32(record + 8) to u32(record + 12)
        }
    }

    private fun parseCmap() {
        val (base, _) = tables["cmap"] ?: error("cmap table not found")
        val numSubTables = u16(base + 2)
        var bestOffset = -1
        var bestScore = -1
        var bestFormat = 0
        for (i in 0 until numSubTables) {
            val platformId = u16(base + 4 + 8 * i)
            val encodingId = u16(base + 6 + 8 * i)
            val offset = base + u32(base + 8 + 8 * i)
            val format = u16(offset)
            if (format != 4 && format != 12) continue
            val score = when {
                platformId == 3 && encodingId == 10 -> 3
                platformId == 0 && (encodingId == 4 || encodingId == 6) -> 3
                platformId == 3 && encodingId == 1 -> 2
                platformId == 0 -> 2
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                bestOffset = offset
                bestFormat = format
            }
        }
        if (bestOffset < 0) error("no usable cmap subtable (format 4/12) found")
        if (bestFormat == 12) {
            val numGroups = u32(bestOffset + 12)
            for (i in 0 until numGroups) {
                val group = bestOffset + 16 + 12 * i
                val start = u32(group)
                val end = u32(group + 4)
                val startGlyph = u32(group + 8)
                for (cp in start..end) {
                    cmap[cp] = startGlyph + (cp - start)
                }
            }
        } else {
            val segCountX2 = u16(bestOffset + 6)
            val segCount = segCountX2 / 2
            val endBase = bestOffset + 14
            val startBase = bestOffset + 16 + segCountX2
            val deltaBase = bestOffset + 16 + 2 * segCountX2
            val rangeOffsetBase = bestOffset + 16 + 3 * segCountX2
            for (i in 0 until segCount) {
                val endCode = u16(endBase + 2 * i)
                val startCode = u16(startBase + 2 * i)
                if (startCode == 0xFFFF) continue
                val delta = s16(deltaBase + 2 * i)
                val rangeOffset = u16(rangeOffsetBase + 2 * i)
                for (cp in startCode..minOf(endCode, 0xFFFE)) {
                    val glyph = if (rangeOffset == 0) {
                        (cp + delta) and 0xFFFF
                    } else {
                        val address = rangeOffsetBase + 2 * i + rangeOffset + 2 * (cp - startCode)
                        val g = u16(address)
                        if (g == 0) 0 else (g + delta) and 0xFFFF
                    }
                    if (glyph != 0) cmap[cp] = glyph
                }
            }
        }
    }

    private fun parseGsub() {
        val (base, _) = tables["GSUB"] ?: return
        val fe0fGlyph = cmap[VARIATION_SELECTOR_16]
        // first glyph -> (FE0F-stripped components -> ligature glyph), first definition wins
        val collected = HashMap<Int, LinkedHashMap<List<Int>, Int>>()
        val lookupListOffset = base + u16(base + 8)
        val lookupCount = u16(lookupListOffset)
        for (lookupIndex in 0 until lookupCount) {
            val lookupOffset = lookupListOffset + u16(lookupListOffset + 2 + 2 * lookupIndex)
            val lookupType = u16(lookupOffset)
            val subTableCount = u16(lookupOffset + 4)
            for (subIndex in 0 until subTableCount) {
                var subOffset = lookupOffset + u16(lookupOffset + 6 + 2 * subIndex)
                var type = lookupType
                if (type == 7) { // extension substitution
                    type = u16(subOffset + 2)
                    subOffset += u32(subOffset + 4)
                }
                if (type != 4) continue
                parseLigatureSubst(subOffset, fe0fGlyph, collected)
            }
        }
        for ((first, map) in collected) {
            ligatures[first] = map.entries
                .map { (components, ligature) -> components.toIntArray() to ligature }
                .sortedByDescending { it.first.size }
        }
    }

    private fun coverageGlyphs(offset: Int): List<Int> {
        return when (u16(offset)) {
            1 -> {
                val count = u16(offset + 2)
                (0 until count).map { u16(offset + 4 + 2 * it) }
            }

            2 -> {
                val count = u16(offset + 2)
                val glyphs = mutableListOf<Int>()
                for (i in 0 until count) {
                    val start = u16(offset + 4 + 6 * i)
                    val end = u16(offset + 6 + 6 * i)
                    for (g in start..end) glyphs.add(g)
                }
                glyphs
            }

            else -> emptyList()
        }
    }

    private fun parseLigatureSubst(
        offset: Int,
        fe0fGlyph: Int?,
        collected: HashMap<Int, LinkedHashMap<List<Int>, Int>>,
    ) {
        val coverage = coverageGlyphs(offset + u16(offset + 2))
        val ligatureSetCount = u16(offset + 4)
        for (i in 0 until minOf(ligatureSetCount, coverage.size)) {
            val firstGlyph = coverage[i]
            val setOffset = offset + u16(offset + 6 + 2 * i)
            val ligatureCount = u16(setOffset)
            val map = collected.getOrPut(firstGlyph) { LinkedHashMap() }
            for (j in 0 until ligatureCount) {
                val ligatureOffset = setOffset + u16(setOffset + 2 + 2 * j)
                val ligatureGlyph = u16(ligatureOffset)
                val componentCount = u16(ligatureOffset + 2)
                val components = (0 until componentCount - 1)
                    .map { u16(ligatureOffset + 4 + 2 * it) }
                    .filter { it != fe0fGlyph }
                map.putIfAbsent(components, ligatureGlyph)
            }
        }
    }

    private fun parseCblc() {
        val (base, _) = tables["CBLC"] ?: error("CBLC table not found")
        val numSizes = u32(base + 4)
        var bestPpem = -1
        for (i in 0 until numSizes) {
            val record = base + 8 + 48 * i
            val ppem = u8(record + 45) // ppemY
            if (ppem > bestPpem) {
                bestPpem = ppem
                strikeArrayOffset = base + u32(record)
                strikeSubTableCount = u32(record + 8)
            }
        }
        if (bestPpem < 0) error("no bitmap strike found in CBLC")
    }

    /** Resolves a codepoint sequence to a single glyph id, or null. */
    private fun resolveGlyph(codePoints: List<Int>): Int? {
        val effective = codePoints.filter { it != VARIATION_SELECTOR_16 }
        if (effective.isEmpty()) return null
        var glyphs = effective.map { cmap[it] ?: return null }
        // apply ligature substitutions repeatedly (multi-stage ligatures)
        // 循環する合字定義を持つフォントでも停止するよう反復上限を設ける
        var rounds = 0
        while (glyphs.size > 1 && rounds++ < 32) {
            var changed = false
            val next = mutableListOf<Int>()
            var i = 0
            while (i < glyphs.size) {
                val candidates = ligatures[glyphs[i]]
                var matched: Pair<IntArray, Int>? = null
                if (candidates != null) {
                    for (candidate in candidates) {
                        val components = candidate.first
                        if (i + 1 + components.size <= glyphs.size &&
                            components.indices.all { components[it] == glyphs[i + 1 + it] }
                        ) {
                            matched = candidate
                            break
                        }
                    }
                }
                if (matched != null) {
                    next.add(matched.second)
                    i += 1 + matched.first.size
                    changed = true
                } else {
                    next.add(glyphs[i])
                    i++
                }
            }
            glyphs = next
            if (!changed) break
        }
        return if (glyphs.size == 1) glyphs[0] else null
    }

    /** Extracts the raw PNG bytes for a glyph id from the CBDT table, or null. */
    private fun glyphPngBytes(glyphId: Int): ByteArray? {
        val (cbdtBase, _) = tables["CBDT"] ?: return null
        for (i in 0 until strikeSubTableCount) {
            val record = strikeArrayOffset + 8 * i
            val firstGlyph = u16(record)
            val lastGlyph = u16(record + 2)
            if (glyphId < firstGlyph || glyphId > lastGlyph) continue
            val subOffset = strikeArrayOffset + u32(record + 4)
            val indexFormat = u16(subOffset)
            val imageFormat = u16(subOffset + 2)
            val imageDataOffset = u32(subOffset + 4)
            val index = glyphId - firstGlyph
            val (dataStart, dataEnd) = when (indexFormat) {
                1 -> u32(subOffset + 8 + 4 * index) to u32(subOffset + 12 + 4 * index)
                2 -> {
                    val imageSize = u32(subOffset + 8)
                    imageSize * index to imageSize * (index + 1)
                }

                3 -> u16(subOffset + 8 + 2 * index) to u16(subOffset + 10 + 2 * index)
                else -> return null
            }
            if (dataEnd <= dataStart) return null
            val glyphOffset = cbdtBase + imageDataOffset + dataStart
            val (pngOffset, pngLength) = when (imageFormat) {
                17 -> glyphOffset + 9 to u32(glyphOffset + 5) // smallGlyphMetrics(5) + dataLen(4)
                18 -> glyphOffset + 12 to u32(glyphOffset + 8) // bigGlyphMetrics(8) + dataLen(4)
                19 -> glyphOffset + 4 to u32(glyphOffset) // dataLen(4)
                else -> return null
            }
            if (pngLength <= 0 || pngOffset.toLong() + pngLength.toLong() > data.size) return null
            return data.copyOfRange(pngOffset, pngOffset + pngLength)
        }
        return null
    }

    /** Returns true if the codepoint sequence resolves to a glyph with an embedded bitmap. PNGのデコードを行わないため一覧の事前フィルタに使える。 */
    fun hasGlyph(unicodeSpec: String): Boolean {
        val codePoints = unicodeSpec.trim().split(" ").mapNotNull { it.toIntOrNull(16) }
        if (codePoints.isEmpty()) return false
        val glyphId = resolveGlyph(codePoints) ?: return false
        return glyphPngBytes(glyphId) != null
    }

    /** Returns the embedded PNG bytes for the codepoint sequence, or null if unavailable. */
    fun getPngBytes(codePoints: List<Int>): ByteArray? {
        val glyphId = resolveGlyph(codePoints) ?: return null
        return glyphPngBytes(glyphId)
    }

    /** Returns the emoji image for the codepoint sequence, or null if unavailable. */
    fun getImage(codePoints: List<Int>): BufferedImage? {
        val png = getPngBytes(codePoints) ?: return null
        return runCatching { ImageIO.read(ByteArrayInputStream(png)) }.getOrNull()
    }

    /** [unicodeSpec] is a space-separated hex codepoint list, e.g. "1F468 1F3FB 200D 1F373". */
    fun getImage(unicodeSpec: String): BufferedImage? {
        val codePoints = unicodeSpec.trim().split(" ").mapNotNull { it.toIntOrNull(16) }
        if (codePoints.isEmpty()) return null
        return getImage(codePoints)
    }
}
