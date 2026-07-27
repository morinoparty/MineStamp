import { createTokenizer } from "@orama/tokenizers/japanese";
import { createFromSource } from "fumadocs-core/search/server";
import { source } from "@/lib/source";

export const revalidate = false;

// 日本語は Orama 標準トークナイザ非対応のため、専用トークナイザで索引を作る。
export const { staticGET: GET } = createFromSource(source, {
	localeMap: {
		ja: {
			components: {
				tokenizer: createTokenizer(),
			},
			search: {
				threshold: 0,
				tolerance: 0,
			},
		},
		en: "english",
	},
});
