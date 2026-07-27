import type { BaseLayoutProps } from "@/components/layout/shared";
import { i18n } from "@/lib/i18n";

/**
 * Shared layout options for the docs layout (MineAuth と同じカスタムレイアウトを使用)。
 * `locale` でナビゲーションのリンク先を現在の言語に合わせる。
 */
export function baseOptions(locale: string): BaseLayoutProps {
	return {
		i18n,
		nav: {
			title: (
				<div className="flex items-center gap-2">
					<span className="text-lg font-bold">MineStamp</span>
				</div>
			),
			url: `/${locale}`,
			transparentMode: "top",
		},
		// ライトテーマのみのため、テーマ切り替えスイッチを非表示にする
		themeSwitch: {
			enabled: false,
		},
		githubUrl: "https://github.com/morinoparty/MineStamp",
		modrinthUrl: "https://modrinth.com/plugin/MineStamp",
	};
}
