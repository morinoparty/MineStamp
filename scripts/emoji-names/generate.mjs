// emoji.properties から、絵文字スタンプの shortCode → { 絵文字, 並び順, 日本語名, 英語名 } の対応表を生成する
// 並び順はよく使われる絵文字ほど小さい。Unicode の使用頻度ランキング (data/) を先に、
// ランキングに無い絵文字は emojibase の order (CLDR の並び: 顔 → 人 → 動物 → 食べ物 …) で後ろに並べる
// MoriPath の app/scripts/generate-stamp-emoji.mjs と同じ規則で並び順と名前を決める
// 使い方: cd scripts/emoji-names && pnpm install && pnpm generate
import { readFile, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);

const SOURCE_PATH = new URL("../../src/main/resources/emoji.properties", import.meta.url);
const OUTPUT_PATH = new URL("../../src/main/resources/emoji-names.json", import.meta.url);
const FREQUENCY_PATH = new URL("./data/unicode-emoji-frequency-2019.txt", import.meta.url);
// 異体字セレクタ (絵文字表示の指定)。有無の違いだけなら同じ絵文字として扱う
const VARIATION_SELECTOR = "FE0F";
// 肌の色の修飾子。使用頻度ランキングは肌の色をまとめて数えているため、順位を引くときは外す
const SKIN_TONES = new Set(["1F3FB", "1F3FC", "1F3FD", "1F3FE", "1F3FF"]);
// 性別の付け足し (ZWJ + ♀ / ♂)。ランキングは性別もまとめて数えている
const GENDER_SUFFIX = /-200D-(2640|2642)$/;
// 人を表す先頭の文字。ランキングの代表は女性の形 (例: 👩‍🚀) なので、男性・人物の形も女性の形で引く
const PERSON_PREFIX = /^(1F468|1F9D1)-200D-/;

/** Java の .properties のエスケープ (\uXXXX, \:, \! など) を戻す */
const unescapeProperty = (text) =>
    text.replace(/\\(u[0-9a-fA-F]{4}|.)/g, (_, escaped) => {
        if (escaped.length === 5) {
            return String.fromCharCode(Number.parseInt(escaped.slice(1), 16));
        }
        return { t: "\t", n: "\n", r: "\r", f: "\f" }[escaped] ?? escaped;
    });

/** 1 行 1 項目の .properties を [キー, 値] の配列にする (MineStamp の形式だけを想定) */
const parseProperties = (text) =>
    text
        .split(/\r?\n/)
        .map((line) => line.trim())
        // 空行とコメント行は飛ばす
        .filter((line) => line && !line.startsWith("#") && !line.startsWith("!"))
        .map((line) => {
            // エスケープされていない最初の = か : が区切り
            const match = /^((?:\\.|[^\\=:])*)[=:](.*)$/.exec(line);
            if (!match) {
                throw new Error(`解釈できない行です: ${line}`);
            }
            return [unescapeProperty(match[1]), match[2].trim()];
        });

const stripVariationSelector = (hexcode) =>
    hexcode
        .split("-")
        .filter((code) => code !== VARIATION_SELECTOR)
        .join("-");

/** emojibase の hexcode (異体字セレクタを除く) → { 完全修飾の絵文字, CLDR の並び順, 英語名, 日本語名 } */
const buildEmojibaseIndex = () => {
    const index = new Map();
    const add = (emoji, field) => {
        const key = stripVariationSelector(emoji.hexcode);
        index.set(key, { ...index.get(key), ...field(emoji) });
        for (const skin of emoji.skins ?? []) {
            add(skin, field);
        }
    };
    for (const emoji of require("emojibase-data/en/data.json")) {
        add(emoji, (e) => ({ emoji: e.emoji, order: e.order, enLabel: e.label }));
    }
    for (const emoji of require("emojibase-data/ja/data.json")) {
        add(emoji, (e) => ({ jaLabel: e.label }));
    }
    return index;
};

/** 絵文字の字形 → emojibase と同じ形の hexcode (例: "🍕" → "1F355") */
const toHexcode = (glyph) =>
    [...glyph].map((char) => char.codePointAt(0).toString(16).toUpperCase().padStart(4, "0")).join("-");

/** ランキングを引くための形。異体字セレクタ・肌の色・性別の付け足しを外す */
const toFrequencyKey = (hexcode) =>
    stripVariationSelector(hexcode)
        .split("-")
        .filter((code) => !SKIN_TONES.has(code))
        .join("-")
        .replace(GENDER_SUFFIX, "");

/** 使用頻度ランキングを読み、ランキング用の形 → 順位 (0 が最も多い) にする */
const readFrequencyRanks = async () => {
    const text = await readFile(FREQUENCY_PATH, "utf8");
    const ranks = new Map();
    // 行はグループの順、行の中は多い順なので、出てきた順がそのまま順位になる
    const glyphs = text
        .split("\n")
        .filter((line) => line.trim() && !line.startsWith("#"))
        .flatMap((line) => line.trim().split(/\s+/).slice(1));
    for (const glyph of glyphs) {
        const key = toFrequencyKey(toHexcode(glyph));
        if (!ranks.has(key)) {
            ranks.set(key, ranks.size);
        }
    }
    return ranks;
};

/** 絵文字の順位。ランキングに無ければ undefined */
const findFrequencyRank = (ranks, hexcode) => {
    const key = toFrequencyKey(hexcode);
    return ranks.get(key) ?? ranks.get(key.replace(PERSON_PREFIX, "1F469-200D-"));
};

/** 並び替えの比較。値が無いものは後ろに回す */
const compareOptional = (a, b) => (a ?? Number.POSITIVE_INFINITY) - (b ?? Number.POSITIVE_INFINITY) || 0;

const toGlyph = (codePoints) => String.fromCodePoint(...codePoints.map((code) => Number.parseInt(code, 16)));

const main = async () => {
    // .properties は ISO-8859-1 で書かれているため latin1 として読む
    const entries = parseProperties((await readFile(SOURCE_PATH)).toString("latin1"));
    const emojibase = buildEmojibaseIndex();
    const ranks = await readFrequencyRanks();
    const stamps = [];
    for (const [shortCode, value] of entries) {
        const codePoints = value.split(/\s+/).filter(Boolean);
        if (codePoints.length === 0) {
            throw new Error(`値が空です: ${shortCode}`);
        }
        const hexcode = codePoints.join("-").toUpperCase();
        // emoji.properties の値は FE0F を省いたものがあるため、emojibase に同じ絵文字があれば完全修飾の形を使う
        const base = emojibase.get(stripVariationSelector(hexcode));
        // 名前が見つからない場合は、記号を外した shortCode を使う
        const fallback = shortCode.replace(/^:/, "").replace(/:$/, "");
        const en = base?.enLabel ?? fallback;
        stamps.push({
            shortCode,
            emoji: base?.emoji ?? toGlyph(codePoints),
            rank: findFrequencyRank(ranks, hexcode),
            cldrOrder: base?.order,
            ja: base?.jaLabel ?? en,
            en,
            named: base?.jaLabel !== undefined,
        });
    }
    // よく使われる順 → CLDR の並び → shortCode の順に並べ、その位置を並び順にする
    const byPopularity = [...stamps].sort(
        (a, b) =>
            compareOptional(a.rank, b.rank) ||
            compareOptional(a.cldrOrder, b.cldrOrder) ||
            (a.shortCode < b.shortCode ? -1 : 1),
    );
    const sortIndex = new Map(byPopularity.map((stamp, index) => [stamp.shortCode, index]));
    // 差分が読みやすいよう、ファイルの中は shortCode 順に 1 行 1 件で並べる
    const lines = [...stamps]
        .sort((a, b) => (a.shortCode < b.shortCode ? -1 : 1))
        .map((stamp) => {
            const entry = { emoji: stamp.emoji, order: sortIndex.get(stamp.shortCode), ja: stamp.ja, en: stamp.en };
            return `  ${JSON.stringify(stamp.shortCode)}: ${JSON.stringify(entry)}`;
        });
    await writeFile(OUTPUT_PATH, `{\n${lines.join(",\n")}\n}\n`);
    const ranked = stamps.filter((stamp) => stamp.rank !== undefined).length;
    const named = stamps.filter((stamp) => stamp.named).length;
    console.info(`${stamps.length} 件を書き出しました (ランキングあり: ${ranked} 件, 日本語名あり: ${named} 件)`);
};

await main();
