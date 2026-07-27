"use client";
import { RootProvider } from "fumadocs-ui/provider/next";
import type { I18nProviderProps } from "fumadocs-ui/i18n";
import type { ReactNode } from "react";
import SearchDialog from "@/components/search";

export function Provider({
	children,
	i18n,
}: {
	children: ReactNode;
	i18n?: I18nProviderProps;
}) {
	return (
		<RootProvider
			i18n={i18n}
			// ライトテーマのみを強制し、ダークモードへの切り替えを無効化する
			theme={{ forcedTheme: "light" }}
			search={{
				SearchDialog,
			}}
		>
			{children}
		</RootProvider>
	);
}
