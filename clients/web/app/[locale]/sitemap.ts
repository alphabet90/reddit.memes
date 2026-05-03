import type { MetadataRoute } from "next";
import { type Locale } from "@/i18n/routing";
import { site } from "@/lib/site";
import { getCategories } from "@/lib/data/categories";
import { fetchMemes } from "@/lib/api";
import { toMeme } from "@/lib/data/memes";
import type { LocaleCode } from "@/lib/api";

export const revalidate = 3600;

const MEME_LIMIT = 1000;
const PAGE_SIZE = 100;

export default async function sitemap(
  props?: { params?: { locale?: string } },
): Promise<MetadataRoute.Sitemap> {
  const locale = ((props?.params?.locale) ?? "en") as Locale;
  const apiLocale = locale as LocaleCode;
  const base = `${site.url}/${locale}`;
  const now = new Date();

  const staticEntries: MetadataRoute.Sitemap = [
    { url: `${base}/`,            lastModified: now, changeFrequency: "hourly",  priority: 1.0 },
    { url: `${base}/categorias`,  lastModified: now, changeFrequency: "weekly",  priority: 0.8 },
    { url: `${base}/buscar`,      lastModified: now, changeFrequency: "monthly", priority: 0.4 },
    { url: `${base}/memes/populares`, lastModified: now, changeFrequency: "daily", priority: 0.7 },
  ];

  let categoryEntries: MetadataRoute.Sitemap = [];
  let memeEntries: MetadataRoute.Sitemap = [];

  try {
    const categories = await getCategories(apiLocale);
    categoryEntries = categories.map((c) => ({
      url: `${base}/categorias/${c.slug}`,
      lastModified: now,
      changeFrequency: "daily",
      priority: 0.7,
    }));
  } catch {
    // API unreachable at build time — emit only static entries.
  }

  try {
    const collected: ReturnType<typeof toMeme>[] = [];
    let page = 0;
    while (collected.length < MEME_LIMIT) {
      const res = await fetchMemes({ page, limit: PAGE_SIZE, sort: "score", locale: apiLocale });
      collected.push(...res.data.map((m) => toMeme(m, apiLocale)));
      if (res.page >= res.total_pages - 1 || res.data.length === 0) break;
      page += 1;
    }
    memeEntries = collected.slice(0, MEME_LIMIT).map((m) => ({
      url: `${base}${m.href}`,
      lastModified: new Date(m.createdAt),
      changeFrequency: "monthly",
      priority: 0.6,
    }));
  } catch {
    // ignore — keep partial sitemap
  }

  return [...staticEntries, ...categoryEntries, ...memeEntries];
}
