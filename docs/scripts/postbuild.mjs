import {
	existsSync,
	mkdirSync,
	renameSync,
	writeFileSync,
} from "node:fs";
import { join } from "node:path";

const outDir = "out";

// 静的ホスティング用にファイル構造を修正
// /api/search を /api/search/index.json に変換
const searchFile = join(outDir, "api", "search");
const searchDir = join(outDir, "api", "search");
const searchIndexFile = join(searchDir, "index.json");

if (existsSync(searchFile)) {
	// 一時的にリネーム
	const tempFile = join(outDir, "api", "search.tmp");
	renameSync(searchFile, tempFile);

	// ディレクトリ作成
	mkdirSync(searchDir, { recursive: true });

	// index.jsonとして配置
	renameSync(tempFile, searchIndexFile);

	console.log("✓ Converted /api/search to /api/search/index.json");
}

// ルート `/` をデフォルト言語 (ja) にリダイレクトする index.html を生成する。
// i18n では全ページが /ja/... と /en/... に出力され、`/` は生成されないため。
const redirectHtml = `<!doctype html>
<html lang="ja">
	<head>
		<meta charset="utf-8" />
		<meta http-equiv="refresh" content="0; url=./ja/docs/index.html" />
		<link rel="canonical" href="./ja/docs/" />
		<title>MineStamp Documentation</title>
	</head>
	<body>
		<p><a href="./ja/docs/index.html">MineStamp のドキュメントへ移動 / Go to the documentation</a></p>
	</body>
</html>
`;
writeFileSync(join(outDir, "index.html"), redirectHtml);
console.log("✓ Generated root redirect index.html -> /ja/docs/");

console.log("✓ Postbuild completed");
