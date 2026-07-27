# MineStamp Documentation

[Fumadocs](https://fumadocs.dev) (Next.js) 製のドキュメントサイトです。日本語 (`ja`, デフォルト) と英語 (`en`) のバイリンガル構成です。

公開ドメイン: `minestamp.plugin.morino.party` (GitHub Pages のカスタムドメイン設定で管理)

## 開発

```bash
pnpm install
pnpm dev
```

`http://localhost:3000` を開くと `/ja/docs` へリダイレクトされます。

## ビルド

```bash
pnpm build
```

静的サイトが `out/` に出力されます (`output: "export"`)。`scripts/postbuild.mjs` が検索インデックスの配置とルートリダイレクト (`/` → `/ja/docs/`) を生成します。

PR プレビューなどサブパス配信の場合は `BASE_PATH` 環境変数を指定してビルドします。

```bash
BASE_PATH=/minestamp/abc1234/docs pnpm build
```

## コンテンツ

- `content/docs/**` … MDX ドキュメント本体
  - `*.mdx` … 日本語 (デフォルト)
  - `*.en.mdx` … 英語
  - `meta.json` / `meta.en.json` … サイドバーの並び・カテゴリ
