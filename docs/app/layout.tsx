import "./global.css";
import type { ReactNode } from "react";

/**
 * Root layout is a pass-through. The real <html>/<body> live in
 * app/[lang]/layout.tsx so the `lang` attribute reflects the active locale.
 */
export default function RootLayout({ children }: { children: ReactNode }) {
	return children;
}
