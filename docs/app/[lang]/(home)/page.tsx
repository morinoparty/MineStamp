import { redirect } from "next/navigation";

export default async function HomePage({
	params,
}: {
	params: Promise<{ lang: string }>;
}) {
	const { lang } = await params;
	redirect(`/${lang}/docs`);
}

export function generateStaticParams() {
	return [{ lang: "ja" }, { lang: "en" }];
}
