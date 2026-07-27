import { defineI18nUI } from "fumadocs-ui/i18n";
import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Provider } from "@/components/provider";
import { i18n } from "@/lib/i18n";

export const metadata: Metadata = {
	title: {
		template: "%s | MineStamp",
		default: "MineStamp Documentation",
	},
	description:
		"Minecraft サーバー向けスタンプ/絵文字チケットプラグイン MineStamp のドキュメント",
};

const { provider } = defineI18nUI(i18n, {
	translations: {
		ja: { displayName: "日本語", search: "検索" },
		en: { displayName: "English", search: "Search" },
	},
});

export default async function Layout({
	params,
	children,
}: {
	params: Promise<{ lang: string }>;
	children: ReactNode;
}) {
	const { lang } = await params;

	return (
		<html lang={lang} suppressHydrationWarning>
			<head>
				{/* Satoshi font */}
				<link
					rel="stylesheet"
					href="https://api.fontshare.com/v2/css?f[]=satoshi@1&display=swap"
				/>
				{/* GenJyuuGothic Japanese font */}
				<link
					rel="stylesheet"
					type="text/css"
					href="https://shogo82148.github.io/genjyuugothic-subsets/GenJyuuGothicL-P-Medium/GenJyuuGothicL-P-Medium.css"
				/>
				<link
					rel="stylesheet"
					type="text/css"
					href="https://shogo82148.github.io/genjyuugothic-subsets/GenJyuuGothicL-P-Bold/GenJyuuGothicL-P-Bold.css"
				/>
			</head>
			<body className="flex flex-col min-h-screen">
				<Provider i18n={provider(lang)}>{children}</Provider>
			</body>
		</html>
	);
}

export function generateStaticParams() {
	return i18n.languages.map((lang) => ({ lang }));
}
